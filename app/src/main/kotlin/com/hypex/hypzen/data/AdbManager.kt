package com.hypex.hypzen.data

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.hypex.hypzen.model.LogLine
import com.hypex.hypzen.model.LogType
import io.github.muntashirakon.adb.AdbStream
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min

	class AdbManager(private val context: Context) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val connectionManager = HypZenAdbConnectionManager(context)
    private var proxy: UsbProxy? = null
    private var proxyPort: Int? = null

    private var shellStream: AdbStream? = null
    private var shellJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _shellOutput = MutableSharedFlow<LogLine>()
    val shellOutput = _shellOutput.asSharedFlow()

	    fun currentProxyPort(): Int? = proxyPort
	    fun isConnected(): Boolean = connectionManager.isConnected

    suspend fun connect(device: UsbDevice): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d("AdbManager", "Connecting to device: ${device.deviceName}")
            val connection = usbManager.openDevice(device) ?: return@withContext false
            val adbInterface = (0 until device.interfaceCount)
                .map { device.getInterface(it) }
                .firstOrNull { it.interfaceClass == 0xff && it.interfaceSubclass == 0x42 && it.interfaceProtocol == 0x01 }
                ?: return@withContext false

            if (!connection.claimInterface(adbInterface, true)) {
                Log.e("AdbManager", "Failed to claim ADB interface")
                return@withContext false
            }

            val inEndpoint = (0 until adbInterface.endpointCount)
                .map { adbInterface.getEndpoint(it) }
                .firstOrNull { it.direction == android.hardware.usb.UsbConstants.USB_DIR_IN }
                ?: return@withContext false

            val outEndpoint = (0 until adbInterface.endpointCount)
                .map { adbInterface.getEndpoint(it) }
                .firstOrNull { it.direction == android.hardware.usb.UsbConstants.USB_DIR_OUT }
                ?: return@withContext false

            proxy?.stop()
            val p = UsbProxy(connection, inEndpoint, outEndpoint)
            proxy = p
            val port = p.start()
            proxyPort = port

            connectionManager.setHostAddress("127.0.0.1")
            Log.d("AdbManager", "Connecting to proxy on port $port")
            connectionManager.connect(port)

            openInteractiveShell()
            true
        } catch (e: Exception) {
            Log.e("AdbManager", "Connection error: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    // Starts the ADB USB proxy but does not connect libadb-android to it.
    // This is used when the app wants to drive the connection via external adb (TCP connect to proxyPort).
    suspend fun startProxyOnly(device: UsbDevice): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d("AdbManager", "Starting proxy-only for device: ${device.deviceName}")
            val connection = usbManager.openDevice(device) ?: return@withContext false
            val adbInterface = (0 until device.interfaceCount)
                .map { device.getInterface(it) }
                .firstOrNull { it.interfaceClass == 0xff && it.interfaceSubclass == 0x42 && it.interfaceProtocol == 0x01 }
                ?: return@withContext false

            if (!connection.claimInterface(adbInterface, true)) {
                Log.e("AdbManager", "Failed to claim ADB interface (proxy-only)")
                return@withContext false
            }

            val inEndpoint = (0 until adbInterface.endpointCount)
                .map { adbInterface.getEndpoint(it) }
                .firstOrNull { it.direction == android.hardware.usb.UsbConstants.USB_DIR_IN }
                ?: return@withContext false

            val outEndpoint = (0 until adbInterface.endpointCount)
                .map { adbInterface.getEndpoint(it) }
                .firstOrNull { it.direction == android.hardware.usb.UsbConstants.USB_DIR_OUT }
                ?: return@withContext false

            // Ensure libadb is not holding a stream while platform-tools uses the proxy.
            shellJob?.cancel()
            shellStream = null
            runCatching { connectionManager.disconnect() }

            proxy?.stop()
            val p = UsbProxy(connection, inEndpoint, outEndpoint)
            proxy = p
            val port = p.start()
            proxyPort = port
            true
        } catch (e: Exception) {
            Log.e("AdbManager", "Proxy-only start error: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    private fun openInteractiveShell() {
        shellJob?.cancel()
        shellJob = scope.launch {
            try {
                if (!connectionManager.isConnected) return@launch
                val stream = connectionManager.openStream("shell:")
                shellStream = stream
                val buffer = ByteArray(4096)
                while (isActive && !stream.isClosed) {
                    val read = stream.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        _shellOutput.emit(LogLine(dateFormat.format(Date()), String(buffer, 0, read), LogType.INFO))
                    }
                }
            } catch (e: Exception) {
                Log.e("AdbManager", "Interactive shell loop error: ${e.message}")
            }
        }
    }

    suspend fun runInteractiveCommand(command: String) = withContext(Dispatchers.IO) {
        try {
            if (shellStream == null || shellStream?.isClosed == true) {
                openInteractiveShell()
                delay(500)
            }
            val cmdBytes = (command + "\n").toByteArray()
            shellStream?.write(cmdBytes, 0, cmdBytes.size)
        } catch (e: Exception) {
            Log.e("AdbManager", "Failed to write to shell: ${e.message}")
        }
    }

    fun shell(command: String): Flow<LogLine> = callbackFlow {
        withContext(Dispatchers.IO) {
            try {
                if (!connectionManager.isConnected) {
                    trySend(LogLine(dateFormat.format(Date()), "ADB Not Connected", LogType.ERROR))
                    close()
                    return@withContext
                }
                val stream = connectionManager.openStream("shell:$command")
                val buffer = ByteArray(4096)
                while (!stream.isClosed) {
                    val read = stream.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        trySend(LogLine(dateFormat.format(Date()), String(buffer, 0, read), LogType.INFO))
                    }
                }
            } catch (e: Exception) {
                trySend(LogLine(dateFormat.format(Date()), "ADB Error: ${e.message}", LogType.ERROR))
            }
            close()
        }
    }

	    fun sideload(zipFile: File): Flow<LogLine> = callbackFlow {
	        withContext(Dispatchers.IO) {
	            var stream: AdbStream? = null
	            try {
                if (!connectionManager.isConnected) {
                    trySend(LogLine(now(), "ADB Not Connected", LogType.ERROR))
                    close(); return@withContext
                }
                if (!zipFile.exists() || !zipFile.isFile) {
                    trySend(LogLine(now(), "Sideload ZIP not found", LogType.ERROR))
                    close(); return@withContext
                }

                val fileSize = zipFile.length()
                if (fileSize <= 0L) {
                    trySend(LogLine(now(), "Sideload ZIP is empty", LogType.ERROR))
                    close(); return@withContext
                }

	                data class Candidate(
	                    val service: String,
	                    val isHostMode: Boolean,
	                    val blockSize: Int? = null
	                )

	                val defaultBlockSize = 64 * 1024
	                val candidates = listOf(
	                    Candidate("sideload-host:$fileSize:$defaultBlockSize", isHostMode = true, blockSize = defaultBlockSize),
	                    Candidate("sideload-host:$fileSize:4096", isHostMode = true, blockSize = 4096),
	                    Candidate("sideload-host:$fileSize", isHostMode = true, blockSize = defaultBlockSize),
	                    Candidate("sideload:$fileSize", isHostMode = false),
	                    Candidate("sideload", isHostMode = false)
	                )

	                var selected: String? = null
	                var hostMode = false
	                var selectedBlockSize = defaultBlockSize
	                var initialPending = ByteArray(0)
	                var lastError: String? = null
	                val hostProbeTimeoutMs = 5_000L

	                for (candidate in candidates) {
	                    val service = candidate.service
	                    try {
	                        trySend(LogLine(now(), "[SIDELOAD] Connecting to service: $service", LogType.INFO))
	                        stream = connectionManager.openStream(service)
	                        val isHost = candidate.isHostMode
	                        if (isHost) {
	                            selectedBlockSize = candidate.blockSize ?: defaultBlockSize
	                            val first = probeFirstSideloadBytes(stream!!, hostProbeTimeoutMs)
	                            if (first == null) {
	                                lastError = "no block requests from device (timeout ${hostProbeTimeoutMs}ms)"
	                                runCatching { stream?.close() }
	                                stream = null
	                                continue
	                            }
	                            initialPending = first
	                        }
	                        selected = service
	                        hostMode = isHost
	                        break
	                    } catch (e: Exception) {
	                        lastError = e.message
	                        runCatching { stream?.close() }
	                        stream = null
	                    }
                }

                if (stream == null || selected == null) {
                    trySend(LogLine(now(), "Failed to open sideload service: ${lastError ?: "unknown"}", LogType.ERROR))
                    close(); return@withContext
	                }

	                trySend(LogLine(now(), "[SIDELOAD] Service established: $selected", LogType.INFO))
	                if (hostMode) {
	                    trySend(LogLine(now(), "[SIDELOAD] Host block size: $selectedBlockSize bytes", LogType.INFO))
	                    sideloadHostMode(stream!!, zipFile, selectedBlockSize, initialPending) { log -> trySend(log) }
	                } else {
	                    sideloadStreamMode(stream!!, zipFile) { log -> trySend(log) }
	                }
	            } catch (e: Exception) {
	                trySend(LogLine(now(), "Sideload failed: ${e.message}", LogType.ERROR))
            } finally {
                runCatching { stream?.close() }
                close()
            }
        }
    }

    private suspend fun probeFirstSideloadBytes(stream: AdbStream, timeoutMs: Long): ByteArray? =
        withContext(Dispatchers.IO) {
            val buf = ByteArray(4096)
            val deferred = CoroutineScope(Dispatchers.IO).async {
                runCatching { stream.read(buf, 0, buf.size) }.getOrDefault(-1)
            }
            val read = withTimeoutOrNull(timeoutMs) { deferred.await() } ?: run {
                deferred.cancel()
                // Ensure the blocked read unblocks promptly (caller will fall back to another service anyway).
                runCatching { stream.close() }
                return@withContext null
            }
            if (read <= 0) return@withContext null
            buf.copyOf(read)
        }

    private suspend fun sideloadStreamMode(stream: AdbStream, zipFile: File, emit: (LogLine) -> Unit) {
        emit(LogLine(now(), "[SIDELOAD] Using stream mode", LogType.INFO))
        val total = zipFile.length().coerceAtLeast(1L)
        var sent = 0L
        var bucket = -1
        val buffer = ByteArray(256 * 1024)

        FileInputStream(zipFile).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                stream.write(buffer, 0, read)
                sent += read
                val progress = min(100, ((sent * 100) / total).toInt())
                val b = progress / 10
                if (b > bucket) {
                    bucket = b
                    emit(LogLine(now(), "[SIDELOAD] Upload $progress% ($sent/$total)", LogType.INFO))
                }
            }
        }
        emit(LogLine(now(), "[SIDELOAD] Upload complete; waiting for device processing", LogType.OK))
        waitForSideloadResult(stream, emit)
    }


    private suspend fun sideloadHostMode(
        stream: AdbStream,
        zipFile: File,
        blockSize: Int,
        initialPending: ByteArray,
        emit: (LogLine) -> Unit
    ) {
        emit(LogLine(now(), "[SIDELOAD] Using host block mode", LogType.INFO))
        if (initialPending.isEmpty()) {
            emit(LogLine(now(), "[SIDELOAD] Waiting for device block requests...", LogType.INFO))
        } else {
            emit(LogLine(now(), "[SIDELOAD] Received first request; starting transfer", LogType.INFO))
        }

	        val total = zipFile.length()
	        val totalBlocks = ((total + blockSize - 1) / blockSize).toLong().coerceAtLeast(1L)
	        val served = HashSet<Long>()
	        var servedLogCount = 0
	        var requestLogCount = 0

	        val reqBuf = ByteArray(4096)
	        val blockBuf = ByteArray(blockSize)

        var pendingRaw = initialPending
        var lastProgressBucket = -1

        RandomAccessFile(zipFile, "r").use { raf ->
            val hexRegex = Regex("[0-9a-fA-F]{8}")
            var shouldStop = false

            fun tail(bytes: ByteArray, max: Int): ByteArray {
                if (bytes.size <= max) return bytes
                return bytes.copyOfRange(bytes.size - max, bytes.size)
            }

            fun processPending(): Boolean {
                if (pendingRaw.isEmpty()) return false

                val pendingText = String(pendingRaw, Charsets.ISO_8859_1)
                val upper = pendingText.uppercase(Locale.ROOT)
                if (upper.contains("FAIL")) {
                    emit(LogLine(now(), "Device sideload failure: $pendingText", LogType.ERROR))
                    shouldStop = true
                    return true
                }
                if (upper.contains("DONE")) {
                    emit(LogLine(now(), "[SIDELOAD] Device reported DONE", LogType.OK))
                    shouldStop = true
                    return true
                }

	                val matches = hexRegex.findAll(pendingText).toList()
	                if (matches.isEmpty()) {
                    // If it's not ASCII-ish, try binary 4-byte block requests.
                    val looksAscii = pendingRaw.all { b ->
                        val c = b.toInt() and 0xFF
                        (c in 0x20..0x7E) || c == 0x0A || c == 0x0D || c == 0x09
                    }
	                    if (!looksAscii && pendingRaw.size >= 4) {
	                        val chunks = pendingRaw.size / 4
	                        var consumed = 0
	                        for (i in 0 until chunks) {
                            val base = i * 4
                            val b0 = pendingRaw[base].toInt() and 0xFF
                            val b1 = pendingRaw[base + 1].toInt() and 0xFF
                            val b2 = pendingRaw[base + 2].toInt() and 0xFF
                            val b3 = pendingRaw[base + 3].toInt() and 0xFF

	                            val le = (b0.toLong()) or (b1.toLong() shl 8) or (b2.toLong() shl 16) or (b3.toLong() shl 24)
	                            val be = (b3.toLong()) or (b2.toLong() shl 8) or (b1.toLong() shl 16) or (b0.toLong() shl 24)
	                            if (requestLogCount < 5) {
	                                requestLogCount++
	                                emit(LogLine(now(), "[SIDELOAD] Device block request (binary): le=$le be=$be", LogType.DIM))
	                            }

	                            fun serveBlock(blockIndex: Long) {
	                                if (blockIndex == 0xFFFFFFFFL || blockIndex == 0x7FFFFFFFL) {
	                                    stream.write(ByteArray(0), 0, 0)
	                                    emit(LogLine(now(), "[SIDELOAD] Device requested end of transfer", LogType.OK))
                                    shouldStop = true
                                    return
                                }
                                val offset = blockIndex * blockSize.toLong()
                                if (offset >= total) {
                                    stream.write(ByteArray(0), 0, 0)
                                    return
                                }
                                raf.seek(offset)
	                                val count = min(blockSize.toLong(), total - offset).toInt()
	                                raf.readFully(blockBuf, 0, count)
	                                stream.write(blockBuf, 0, count)

	                                val firstTime = served.add(blockIndex)
	                                if (firstTime) {
	                                    val progress = ((served.size.toLong() * 100L) / totalBlocks)
	                                        .toInt()
	                                        .coerceIn(0, 100)
	                                    val bucket = progress / 10
	                                    if (bucket > lastProgressBucket) {
	                                        lastProgressBucket = bucket
	                                        emit(LogLine(now(), "[SIDELOAD] Upload $progress% (${served.size}/$totalBlocks blocks)", LogType.INFO))
	                                        if (servedLogCount < 10) {
	                                            servedLogCount++
	                                            emit(LogLine(now(), "[SIDELOAD] Serving block $blockIndex at offset $offset ($count bytes)", LogType.DIM))
	                                        }
	                                    } else if (servedLogCount < 5) {
	                                        servedLogCount++
	                                        emit(LogLine(now(), "[SIDELOAD] Serving block $blockIndex at offset $offset ($count bytes)", LogType.DIM))
	                                    }
	                                }
	                            }

                            val leOffset = le * blockSize.toLong()
                            val beOffset = be * blockSize.toLong()
                            val pick = if (leOffset < total || le == 0xFFFFFFFFL || le == 0x7FFFFFFFL) le else be
                            serveBlock(pick)
                            if (shouldStop) {
                                consumed = (i + 1) * 4
                                break
                            }
                            consumed = (i + 1) * 4
                        }
                        pendingRaw = tail(pendingRaw.copyOfRange(consumed, pendingRaw.size), 64)
                        return true
                    }

                    // Keep a tiny tail so tokens split across reads can be completed.
                    pendingRaw = tail(pendingRaw, 7)
	                    return false
	                }

	                if (requestLogCount < 5) {
	                    requestLogCount++
	                    val shown = matches.take(4).joinToString(separator = ", ") { "0x${it.value}" }
	                    emit(
	                        LogLine(
	                            now(),
	                            if (matches.size <= 4) "[SIDELOAD] Device block request(s): $shown"
	                            else "[SIDELOAD] Device block request(s): $shown (+${matches.size - 4} more)",
	                            LogType.DIM
	                        )
	                    )
	                }

	                var lastConsumed = 0
	                for (m in matches) {
                    lastConsumed = maxOf(lastConsumed, m.range.last + 1)
                    val blockIndex = runCatching { m.value.toLong(16) }.getOrNull() ?: continue

                    // Special end markers observed in some recoveries.
                    if (blockIndex == 0xFFFFFFFFL || blockIndex == 0x7FFFFFFFL) {
                        stream.write(ByteArray(0), 0, 0)
                        emit(LogLine(now(), "[SIDELOAD] Device requested end of transfer", LogType.OK))
                        shouldStop = true
                        return true
                    }

                    val offset = blockIndex * blockSize.toLong()
                    if (offset >= total) {
                        // Out of bounds: respond with empty to avoid stalling.
                        stream.write(ByteArray(0), 0, 0)
                        continue
                    }

                    raf.seek(offset)
                    val count = min(blockSize.toLong(), total - offset).toInt()
	                    raf.readFully(blockBuf, 0, count)
	                    stream.write(blockBuf, 0, count)

	                    val firstTime = served.add(blockIndex)
	                    if (firstTime) {
	                        val progress = ((served.size.toLong() * 100L) / totalBlocks)
	                            .toInt()
	                            .coerceIn(0, 100)
	                        val bucket = progress / 10
	                        if (bucket > lastProgressBucket) {
	                            lastProgressBucket = bucket
	                            emit(LogLine(now(), "[SIDELOAD] Upload $progress% (${served.size}/$totalBlocks blocks)", LogType.INFO))
	                            if (servedLogCount < 10) {
	                                servedLogCount++
	                                emit(LogLine(now(), "[SIDELOAD] Serving block $blockIndex at offset $offset ($count bytes)", LogType.DIM))
	                            }
	                        } else if (servedLogCount < 5) {
	                            servedLogCount++
	                            emit(LogLine(now(), "[SIDELOAD] Serving block $blockIndex at offset $offset ($count bytes)", LogType.DIM))
	                        }
	                    }
	                }

                // Drop consumed bytes; keep only a small tail.
                pendingRaw = pendingRaw.copyOfRange(
                    min(lastConsumed, pendingRaw.size),
                    pendingRaw.size
                )
                pendingRaw = tail(pendingRaw, 64)
                return true
            }

            while (!stream.isClosed) {
                // If we already have buffered data (e.g., from probe), process it before reading again.
                if (pendingRaw.isNotEmpty()) {
                    val didWork = processPending()
                    if (didWork && shouldStop) break
                }

                val read = try {
                    stream.read(reqBuf, 0, reqBuf.size)
                } catch (_: Exception) {
                    -1
                }
                if (read <= 0) break

                val incoming = reqBuf.copyOf(read)
                pendingRaw = (pendingRaw + incoming)
                if (pendingRaw.size > 64 * 1024) {
                    pendingRaw = tail(pendingRaw, 64)
                }
                processPending()
                if (shouldStop) break
            }
        }

        emit(LogLine(now(), "[SIDELOAD] Host mode transfer finished; waiting for device processing", LogType.OK))
        waitForSideloadResult(stream, emit)
    }

	    private suspend fun waitForSideloadResult(stream: AdbStream, emit: (LogLine) -> Unit) {
	        var pending = ""
	        var lastActivityMs = System.currentTimeMillis()
	        var lastHeartbeatMs = 0L
	        val startedMs = System.currentTimeMillis()
	        val maxWaitMs = 10L * 60L * 1000L
	        val heartbeatEveryMs = 15_000L
	        val readTimeoutMs = 5_000L

	        val incoming = Channel<ByteArray>(capacity = Channel.BUFFERED)
	        val reader = scope.launch {
	            val buffer = ByteArray(4096)
	            try {
	                while (!stream.isClosed) {
	                    val read = runCatching { stream.read(buffer, 0, buffer.size) }.getOrDefault(-1)
	                    if (read <= 0) break
	                    incoming.send(buffer.copyOf(read))
	                }
	            } catch (_: Exception) {
	                // Treat any read failure as end-of-stream; consumer will time out / exit naturally.
	            } finally {
	                incoming.close()
	            }
	        }

	        try {
	            while (!stream.isClosed && (System.currentTimeMillis() - startedMs) < maxWaitMs) {
	                val chunkBytes = withTimeoutOrNull(readTimeoutMs) {
	                    try {
	                        incoming.receive()
	                    } catch (_: ClosedReceiveChannelException) {
	                        null
	                    }
	                }
	                if (chunkBytes != null) {
	                    lastActivityMs = System.currentTimeMillis()
	                    val chunk = String(chunkBytes, Charsets.ISO_8859_1).replace("\u0000", "")
	                    pending += chunk
	                    if (pending.length > 64 * 1024) pending = pending.takeLast(1024)

	                    val upper = pending.uppercase(Locale.ROOT)
	                    if (upper.contains("FAIL")) {
	                        emit(LogLine(now(), "[SIDELOAD] Device reported FAIL: ${pending.takeLast(512)}", LogType.ERROR))
	                        return
	                    }
	                    if (upper.contains("OKAY") || upper.contains("DONE") || upper.contains("SUCCESS")) {
	                        emit(LogLine(now(), "[SIDELOAD] Device reported success: ${pending.takeLast(256)}", LogType.OK))
	                        return
	                    }

	                    val printable = chunk.filter { it == '\n' || it == '\r' || it == '\t' || (it.code in 0x20..0x7E) }
	                    val lines = printable.split('\n').map { it.trim() }.filter { it.isNotBlank() }
	                    for (line in lines.take(3)) {
	                        emit(LogLine(now(), "[SIDELOAD] $line", LogType.INFO))
	                    }
	                } else {
	                    val nowMs = System.currentTimeMillis()
	                    if (nowMs - lastHeartbeatMs >= heartbeatEveryMs && nowMs - lastActivityMs >= heartbeatEveryMs) {
	                        lastHeartbeatMs = nowMs
	                        emit(LogLine(now(), "[SIDELOAD] Waiting for recovery to finish (no status yet)...", LogType.DIM))
	                    }
	                }
	            }
	        } finally {
	            reader.cancel()
	            incoming.close()
	        }

	        emit(LogLine(now(), "[SIDELOAD] No final status received (recovery may still be working).", LogType.WARN))
	    }

    fun disconnect() {
        Log.d("AdbManager", "Disconnecting")
        shellJob?.cancel()
        shellStream = null
        proxy?.stop()
        proxy = null
        proxyPort = null
        try {
            connectionManager.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun now(): String = dateFormat.format(Date())
}
