package com.hypex.hyflash.ipc

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import com.hypex.hyflash.model.LogType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Runs platform-tools commands in a separate app process (see AndroidManifest `:cmd_runner`)
 * and streams logs back to the caller using Messenger (no AIDL toolchain required).
 */
class CommandRunnerService : Service() {
    companion object {
        const val MSG_RUN = 1
        const val MSG_LOG = 2
        const val MSG_FINISHED = 3

        const val KEY_CMD = "cmd"
        const val KEY_ENV_KEYS = "env_keys"
        const val KEY_ENV_VALUES = "env_values"

        const val KEY_TS = "ts"
        const val KEY_TEXT = "text"
        const val KEY_TYPE = "type"

        const val KEY_EXIT = "exit"
        const val KEY_ELAPSED_MS = "elapsed_ms"
        const val KEY_LINES = "lines"
    }

    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()

    private fun now(): String = dateFormat.format(Date())

    private val handler = Handler(Looper.getMainLooper()) { msg ->
        if (msg.what != MSG_RUN) return@Handler false
        val reply = msg.replyTo ?: return@Handler true

        val data = msg.data ?: Bundle.EMPTY
        val cmd = data.getStringArrayList(KEY_CMD)?.filter { it.isNotBlank() } ?: emptyList()
        val envKeys = data.getStringArrayList(KEY_ENV_KEYS) ?: arrayListOf()
        val envValues = data.getStringArrayList(KEY_ENV_VALUES) ?: arrayListOf()

        val env = mutableMapOf<String, String>()
        if (envKeys.size == envValues.size) {
            for (i in envKeys.indices) {
                val k = envKeys[i]
                val v = envValues[i]
                if (k.isNotBlank()) env[k] = v
            }
        }

        scope.launch {
            // Serialize to avoid multiple adb-server instances fighting over ports.
            mutex.withLock {
                val startedAt = System.currentTimeMillis()
                var lineCount = 0

                fun sendLog(type: LogType, text: String) {
                    val b = Bundle().apply {
                        putString(KEY_TS, now())
                        putString(KEY_TEXT, text)
                        putInt(KEY_TYPE, type.ordinal)
                    }
                    val out = Message.obtain(null, MSG_LOG).apply { setData(b) }
                    try {
                        reply.send(out)
                    } catch (_: RemoteException) {
                        throw IllegalStateException("client disconnected")
                    }
                }

                fun sendFinished(exit: Int, elapsedMs: Long, lines: Int) {
                    val b = Bundle().apply {
                        putInt(KEY_EXIT, exit)
                        putLong(KEY_ELAPSED_MS, elapsedMs)
                        putInt(KEY_LINES, lines)
                    }
                    val out = Message.obtain(null, MSG_FINISHED).apply { setData(b) }
                    runCatching { reply.send(out) }
                }

                try {
                    if (cmd.isEmpty()) {
                        sendLog(LogType.ERROR, "[PT] Platform-tools command is empty")
                        sendFinished(2, 0L, 1)
                        return@withLock
                    }

                    sendLog(LogType.CMD, "[PT] >>> ${cmd.joinToString(" ")}")
                    if (env.isNotEmpty()) {
                        val envPreview = env.entries.joinToString(" ") { "${it.key}=${it.value}" }
                        sendLog(LogType.DIM, "[PT] env: $envPreview")
                    }

                    val processBuilder = ProcessBuilder(cmd).redirectErrorStream(true)
                    if (env.isNotEmpty()) processBuilder.environment().putAll(env)
                    val process = processBuilder.start()

                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            lineCount++
                            val type = if (
                                line.contains("error", ignoreCase = true) ||
                                line.contains("failed", ignoreCase = true)
                            ) LogType.ERROR else LogType.INFO
                            sendLog(type, "[PT] $line")
                        }
                    }

                    val code = process.waitFor()
                    val elapsed = System.currentTimeMillis() - startedAt
                    sendLog(
                        if (code == 0) LogType.OK else LogType.ERROR,
                        if (code == 0) "[PT] Command finished (exit=0, ${elapsed}ms, lines=$lineCount)"
                        else "[PT] Command failed (exit=$code, ${elapsed}ms, lines=$lineCount)"
                    )
                    sendFinished(code, elapsed, lineCount)
                } catch (e: IOException) {
                    val reason = e.message ?: "unknown"
                    sendLog(LogType.ERROR, "[PT] Start failed: ${e.javaClass.simpleName}: $reason")
                    e.cause?.let { cause ->
                        sendLog(LogType.ERROR, "[PT] Root cause: ${cause.javaClass.simpleName}: ${cause.message}")
                    }
                    if (reason.contains("error=13", ignoreCase = true) || reason.contains("permission denied", ignoreCase = true)) {
                        sendLog(
                            LogType.WARN,
                            "[PT] Hint: Android may block executing binaries from app-writable directories (noexec/SELinux). Prefer jniLibs (nativeLibraryDir) executables."
                        )
                    }
                    sendFinished(13, System.currentTimeMillis() - startedAt, lineCount)
                } catch (_: Throwable) {
                    sendFinished(1, System.currentTimeMillis() - startedAt, lineCount)
                }
            }
        }

        true
    }

    private val messenger = Messenger(handler)

    override fun onBind(intent: Intent?): IBinder = messenger.binder
}
