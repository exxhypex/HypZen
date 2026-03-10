package com.hypex.hypzen.data

import com.hypex.hypzen.model.LogLine
import com.hypex.hypzen.model.LogType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PlatformToolsManager {
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun runCommand(command: List<String>, extraEnv: Map<String, String> = emptyMap()): Flow<LogLine> = callbackFlow {
        withContext(Dispatchers.IO) {
            try {
                if (command.isEmpty()) {
                    trySend(LogLine(now(), "Platform-tools command is empty", LogType.ERROR))
                    close()
                    return@withContext
                }

                val cmdString = command.joinToString(" ")
                val startedAt = System.currentTimeMillis()
                trySend(LogLine(now(), "[PT] >>> $cmdString", LogType.CMD))
                if (extraEnv.isNotEmpty()) {
                    val envPreview = extraEnv.entries.joinToString(" ") { "${it.key}=${it.value}" }
                    trySend(LogLine(now(), "[PT] env: $envPreview", LogType.DIM))
                }

                val processBuilder = ProcessBuilder(command).redirectErrorStream(true)
                if (extraEnv.isNotEmpty()) {
                    processBuilder.environment().putAll(extraEnv)
                }
                val process = processBuilder.start()

                var lineCount = 0
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        lineCount++
                        val type = if (
                            line.contains("error", ignoreCase = true) ||
                            line.contains("failed", ignoreCase = true)
                        ) LogType.ERROR else LogType.INFO
                        trySend(LogLine(now(), "[PT] $line", type))
                    }
                }

                val code = process.waitFor()
                val elapsed = System.currentTimeMillis() - startedAt
                if (code == 0) {
                    trySend(LogLine(now(), "[PT] Command finished (exit=0, ${elapsed}ms, lines=$lineCount)", LogType.OK))
                } else {
                    trySend(LogLine(now(), "[PT] Command failed (exit=$code, ${elapsed}ms, lines=$lineCount)", LogType.ERROR))
                }
            } catch (e: IOException) {
                val reason = e.message ?: "unknown"
                trySend(LogLine(now(), "[PT] Start failed: ${e.javaClass.simpleName}: $reason", LogType.ERROR))
                e.cause?.let { cause ->
                    trySend(LogLine(now(), "[PT] Root cause: ${cause.javaClass.simpleName}: ${cause.message}", LogType.ERROR))
                }
                if (reason.contains("error=13", ignoreCase = true) || reason.contains("permission denied", ignoreCase = true)) {
                    trySend(
                        LogLine(
                            now(),
                            "[PT] Hint: Android may block executing binaries from app-writable directories (noexec/SELinux). Prefer Embedded Lib engine or ship executables via jniLibs (nativeLibraryDir).",
                            LogType.WARN
                        )
                    )
                }
            } catch (e: Exception) {
                trySend(LogLine(now(), "Platform-tools error: ${e.javaClass.simpleName}: ${e.message}", LogType.ERROR))
            }
            close()
        }
    }

    private fun now(): String = dateFormat.format(Date())
}
