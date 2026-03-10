package com.hypex.hypzen.data

import android.content.Context
import android.hardware.usb.*
import com.hypex.hypzen.model.LogLine
import com.hypex.hypzen.model.LogType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

class FastbootManager(private val context: Context) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private var usbConnection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var inEndpoint: UsbEndpoint? = null
    private var outEndpoint: UsbEndpoint? = null

    private data class FastbootFrame(val type: String, val payload: String)

    suspend fun connect(device: UsbDevice): Boolean = withContext(Dispatchers.IO) {
        disconnect()
        val iface = findFastbootInterface(device) ?: return@withContext false
        val connection = usbManager.openDevice(device) ?: return@withContext false

        if (!connection.claimInterface(iface, true)) {
            connection.close()
            return@withContext false
        }

        usbConnection = connection
        usbInterface = iface
        inEndpoint = null
        outEndpoint = null

        for (i in 0 until iface.endpointCount) {
            val ep = iface.getEndpoint(i)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (ep.direction == UsbConstants.USB_DIR_IN) inEndpoint = ep else outEndpoint = ep
            }
        }

        if (inEndpoint == null || outEndpoint == null) {
            disconnect()
            false
        } else true
    }

    fun disconnect() {
        try {
            val conn = usbConnection
            val iface = usbInterface
            if (conn != null && iface != null) {
                runCatching { conn.releaseInterface(iface) }
            }
            conn?.close()
        } catch (_: Exception) {
        }
        usbConnection = null
        usbInterface = null
        inEndpoint = null
        outEndpoint = null
    }


    fun execute(command: String): Flow<LogLine> = callbackFlow {
        withContext(Dispatchers.IO) {
            try {
                trySend(logInfo("[FASTBOOT] >>> $command"))
                sendCommandOrThrow(command)

                val frames = try {
                    readFramesUntilTerminalOrThrow("execute:$command")
                } catch (e: Exception) {
                    if (command.startsWith("reboot")) {
                        trySend(logWarn("[FASTBOOT] Reboot command sent; device may disconnect before OKAY response"))
                        emptyList()
                    } else {
                        throw e
                    }
                }

                var failed = false
                for (frame in frames) {
                    when (frame.type) {
                        "FAIL" -> {
                            failed = true
                            trySend(LogLine(now(), "[FAIL] ${frame.payload.ifBlank { "Unknown failure" }}", LogType.ERROR))
                        }
                        "OKAY" -> trySend(LogLine(now(), "[OKAY] ${frame.payload}", LogType.OK))
                        "INFO" -> trySend(LogLine(now(), "[INFO] ${frame.payload}", LogType.INFO))
                        else -> trySend(LogLine(now(), "[${frame.type}] ${frame.payload}", LogType.WARN))
                    }
                }

                if (!failed) {
                    if (command.startsWith("reboot") && frames.isEmpty()) {
                        trySend(logOk("[FASTBOOT] Reboot command accepted (no response before disconnect)"))
                    } else {
                        trySend(logOk("[FASTBOOT] Command finished"))
                    }
                }
            } catch (e: Exception) {
                trySend(LogLine(now(), "Fastboot execute failed: ${e.message}", LogType.ERROR))
            }
            close()
        }
    }

    suspend fun flash(partition: String, inputStream: InputStream, size: Long, onProgress: (Float) -> Unit): Flow<LogLine> = callbackFlow {
        withContext(Dispatchers.IO) {
            try {
                if (size <= 0L) throw IllegalStateException("Selected image size is 0 bytes")

                trySend(logInfo("[FLASH] Step 1/5: Preparing flash for partition '$partition' ($size bytes)"))

                val downloadCmd = "download:${String.format("%08x", size)}"
                trySend(logInfo("[FLASH] Step 2/5: Requesting download slot ($downloadCmd)"))
                sendCommandOrThrow(downloadCmd)

                val first = readSingleFrameOrThrow("download-handshake")
                when (first.type) {
                    "DATA" -> trySend(logInfo("[FLASH] Device accepted DATA request: ${first.payload}"))
                    "FAIL" -> throw IllegalStateException("Device rejected download: ${first.payload.ifBlank { "unknown" }}")
                    else -> throw IllegalStateException("Expected DATA response, got ${first.type}:${first.payload}")
                }

                trySend(logInfo("[FLASH] Step 3/5: Streaming image chunks"))
                val chunk = ByteArray(256 * 1024)
                var totalSent = 0L
                var lastProgressBucket = -1

                while (true) {
                    val bytesRead = inputStream.read(chunk)
                    if (bytesRead <= 0) break
                    writeBulkOrThrow(chunk, bytesRead, "image chunk")
                    totalSent += bytesRead

                    val progress = min(1f, totalSent.toFloat() / size.toFloat())
                    onProgress(progress)

                    val bucket = (progress * 100).toInt() / 10
                    if (bucket > lastProgressBucket) {
                        lastProgressBucket = bucket
                        trySend(logInfo("[FLASH] Upload progress ${(progress * 100).toInt()}% ($totalSent/$size bytes)"))
                    }
                }

                if (totalSent != size) throw IllegalStateException("Transferred bytes mismatch. Expected=$size, Sent=$totalSent")

                val downloadResult = readFramesUntilTerminalOrThrow("download-complete")
                val downloadFail = downloadResult.firstOrNull { it.type == "FAIL" }
                for (frame in downloadResult) {
                    if (frame.type == "INFO") trySend(logInfo("[FLASH] ${frame.payload}"))
                }
                if (downloadFail != null) throw IllegalStateException("Download failed: ${downloadFail.payload.ifBlank { "unknown" }}")

                trySend(logInfo("[FLASH] Step 4/5: Issuing flash command (flash:$partition)"))
                sendCommandOrThrow("flash:$partition")
                val flashResult = readFramesUntilTerminalOrThrow("flash:$partition")
                val flashFail = flashResult.firstOrNull { it.type == "FAIL" }
                for (frame in flashResult) {
                    when (frame.type) {
                        "INFO" -> trySend(logInfo("[FLASH] ${frame.payload}"))
                        "OKAY" -> if (frame.payload.isNotBlank()) trySend(logOk("[FLASH] ${frame.payload}"))
                    }
                }
                if (flashFail != null) throw IllegalStateException("Flash failed for $partition: ${flashFail.payload.ifBlank { "unknown" }}")

                onProgress(1f)
                trySend(logOk("[FLASH] Step 5/5: Partition '$partition' flashed successfully"))
            } catch (e: Exception) {
                trySend(LogLine(now(), "Flash failed: ${e.message}", LogType.ERROR))
            }
            close()
        }
    }

    suspend fun getVar(variable: String): String? = withContext(Dispatchers.IO) {
        try {
            sendCommandOrThrow("getvar:$variable")
            val frames = readFramesUntilTerminalOrThrow("getvar:$variable")
            frames.firstOrNull { it.type == "OKAY" }?.payload
        } catch (_: Exception) {
            null
        }
    }

    private fun sendCommandOrThrow(cmd: String) {
        val bytes = cmd.toByteArray(Charsets.US_ASCII)
        writeBulkOrThrow(bytes, bytes.size, "command '$cmd'")
    }

    private fun writeBulkOrThrow(buffer: ByteArray, length: Int, label: String) {
        val connection = usbConnection ?: throw IllegalStateException("USB connection is not open")
        val endpoint = outEndpoint ?: throw IllegalStateException("Fastboot OUT endpoint is missing")

        var offset = 0
        var retries = 0
        while (offset < length) {
            val chunk = length - offset
            val wrote = connection.bulkTransfer(endpoint, buffer, offset, chunk, 12000)
            if (wrote > 0) {
                offset += wrote
                retries = 0
                continue
            }
            retries++
            if (retries >= 5) {
                throw IllegalStateException("USB transfer failed during $label (written=$offset/$length)")
            }
        }
    }

    private fun readSingleFrameOrThrow(stage: String): FastbootFrame {
        val raw = readRawWithRetry(stage, retries = 60, timeoutMs = 1200)
            ?: throw IllegalStateException("No response from device during $stage")
        return parseFrame(raw)
    }

    private fun readFramesUntilTerminalOrThrow(stage: String): List<FastbootFrame> {
        val frames = mutableListOf<FastbootFrame>()
        for (index in 0 until 96) {
            val raw = readRawWithRetry(stage, retries = if (index == 0) 60 else 20, timeoutMs = 1200) ?: break
            val frame = parseFrame(raw)
            frames.add(frame)
            if (frame.type == "OKAY" || frame.type == "FAIL") break
        }
        if (frames.isEmpty()) throw IllegalStateException("No fastboot response during $stage")
        return frames
    }

    private fun readRawWithRetry(stage: String, retries: Int, timeoutMs: Int): String? {
        val connection = usbConnection ?: throw IllegalStateException("USB connection is not open")
        val endpoint = inEndpoint ?: throw IllegalStateException("Fastboot IN endpoint is missing")
        val buffer = ByteArray(4096)

        repeat(retries) {
            val read = connection.bulkTransfer(endpoint, buffer, buffer.size, timeoutMs)
            if (read > 0) {
                return String(buffer, 0, read, Charsets.US_ASCII).trimEnd('\u0000', '\r', '\n')
            }
            if (read < 0) {
                // Negative is common timeout on some stacks; retry before failing.
                return@repeat
            }
        }
        return null
    }

    private fun parseFrame(raw: String): FastbootFrame {
        return if (raw.length >= 4) FastbootFrame(raw.take(4), raw.drop(4)) else FastbootFrame("TEXT", raw)
    }

    private fun findFastbootInterface(device: UsbDevice) = (0 until device.interfaceCount)
        .map { device.getInterface(it) }
        .firstOrNull { it.interfaceClass == 0xff && it.interfaceSubclass == 0x42 && it.interfaceProtocol == 0x03 }

    private fun now(): String = dateFormat.format(Date())
    private fun logInfo(msg: String) = LogLine(now(), msg, LogType.INFO)
    private fun logWarn(msg: String) = LogLine(now(), msg, LogType.WARN)
    private fun logOk(msg: String) = LogLine(now(), msg, LogType.OK)
}
