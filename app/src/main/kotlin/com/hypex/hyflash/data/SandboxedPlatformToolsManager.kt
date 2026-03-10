package com.hypex.hyflash.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import com.hypex.hyflash.ipc.CommandRunnerService
import com.hypex.hyflash.model.LogLine
import com.hypex.hyflash.model.LogType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SandboxedPlatformToolsManager(private val context: Context) {
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private fun now(): String = dateFormat.format(Date())

    fun runCommand(command: List<String>, extraEnv: Map<String, String> = emptyMap()): Flow<LogLine> = callbackFlow {
        if (command.isEmpty()) {
            trySend(LogLine(now(), "Platform-tools command is empty", LogType.ERROR))
            close()
            return@callbackFlow
        }

        val serviceDeferred = CompletableDeferred<Messenger?>()

        val incomingHandler = Handler(Looper.getMainLooper()) { msg ->
            when (msg.what) {
                CommandRunnerService.MSG_LOG -> {
                    val data = msg.data ?: Bundle.EMPTY
                    val ts = data.getString(CommandRunnerService.KEY_TS) ?: now()
                    val text = data.getString(CommandRunnerService.KEY_TEXT) ?: ""
                    val typeOrdinal = data.getInt(CommandRunnerService.KEY_TYPE, LogType.INFO.ordinal)
                    val type = LogType.values().getOrNull(typeOrdinal) ?: LogType.INFO
                    trySend(LogLine(ts, text, type))
                    true
                }
                CommandRunnerService.MSG_FINISHED -> {
                    close()
                    true
                }
                else -> false
            }
        }

        val replyMessenger = Messenger(incomingHandler)

        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                serviceDeferred.complete(if (service != null) Messenger(service) else null)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                if (!serviceDeferred.isCompleted) serviceDeferred.complete(null)
            }
        }

        val intent = Intent(context, CommandRunnerService::class.java)
        val bound = runCatching { context.bindService(intent, conn, Context.BIND_AUTO_CREATE) }.getOrDefault(false)
        if (!bound) {
            trySend(LogLine(now(), "[PT] Failed to bind sandbox runner service", LogType.ERROR))
            close()
            return@callbackFlow
        }

        val job: Job = launch(Dispatchers.IO) {
            try {
                val svc = serviceDeferred.await()
                if (svc == null) {
                    trySend(LogLine(now(), "[PT] Sandbox runner unavailable", LogType.ERROR))
                    close()
                    return@launch
                }

                val b = Bundle().apply {
                    putStringArrayList(CommandRunnerService.KEY_CMD, ArrayList(command))
                    putStringArrayList(CommandRunnerService.KEY_ENV_KEYS, ArrayList(extraEnv.keys))
                    putStringArrayList(CommandRunnerService.KEY_ENV_VALUES, ArrayList(extraEnv.values))
                }
                val m = Message.obtain(null, CommandRunnerService.MSG_RUN).apply {
                    data = b
                    replyTo = replyMessenger
                }
                svc.send(m)
            } catch (e: RemoteException) {
                trySend(LogLine(now(), "[PT] Sandbox runner IPC failed: ${e.message}", LogType.ERROR))
                close()
            } catch (t: Throwable) {
                trySend(LogLine(now(), "[PT] Sandbox runner error: ${t.message}", LogType.ERROR))
                close()
            }
        }

        awaitClose {
            job.cancel()
            runCatching { context.unbindService(conn) }
        }
    }
}

