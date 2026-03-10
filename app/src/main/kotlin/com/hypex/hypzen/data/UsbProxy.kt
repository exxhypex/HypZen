package com.hypex.hypzen.data

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.util.Log
import kotlinx.coroutines.*
import java.net.ServerSocket
import java.net.Socket

class UsbProxy(
    private val usbConnection: UsbDeviceConnection,
    private val inEndpoint: UsbEndpoint,
    private val outEndpoint: UsbEndpoint
) {
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var proxyJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun start(): Int = withContext(Dispatchers.IO) {
        val server = ServerSocket(0)
        serverSocket = server
        val port = server.localPort

        Log.d("UsbProxy", "Listening on port $port")

        proxyJob = scope.launch {
            try {
                while (isActive) {
                    val client = server.accept()
                    clientSocket = client
                    client.tcpNoDelay = true
                    Log.d("UsbProxy", "Client connected")

                    try {
                        val input = client.getInputStream()
                        val output = client.getOutputStream()

                        coroutineScope {
                            val usbToSocket = launch {
                                val buffer = ByteArray(maxOf(4096, inEndpoint.maxPacketSize * 32))
                                try {
                                    while (isActive && !client.isClosed) {
                                        val read = usbConnection.bulkTransfer(inEndpoint, buffer, buffer.size, 250)
                                        if (read > 0) {
                                            output.write(buffer, 0, read)
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("UsbProxy", "USB to Socket error: ${e.message}")
                                }
                            }

                            val socketToUsb = launch {
                                val socketBuffer = ByteArray(64 * 1024)
                                try {
                                    while (isActive && !client.isClosed) {
                                        val read = input.read(socketBuffer)
                                        if (read <= 0) break

                                        var offset = 0
                                        var retries = 0
                                        while (offset < read) {
                                            val wrote = usbConnection.bulkTransfer(
                                                outEndpoint,
                                                socketBuffer,
                                                offset,
                                                read - offset,
                                                2500
                                            )
                                            if (wrote > 0) {
                                                offset += wrote
                                                retries = 0
                                            } else {
                                                retries++
                                                if (retries >= 10) {
                                                    throw IllegalStateException("bulkTransfer write failed (wrote=$offset/$read)")
                                                }
                                                delay(2)
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("UsbProxy", "Socket to USB error: ${e.message}")
                                }
                            }

                            socketToUsb.invokeOnCompletion { usbToSocket.cancel() }
                            usbToSocket.invokeOnCompletion { socketToUsb.cancel() }
                        }
                    } catch (e: Exception) {
                        Log.e("UsbProxy", "Client handler error: ${e.message}")
                    } finally {
                        runCatching { client.close() }
                        clientSocket = null
                        Log.d("UsbProxy", "Client disconnected")
                    }
                }
            } catch (e: Exception) {
                Log.e("UsbProxy", "Proxy error: ${e.message}")
            }
        }

        port
    }

    fun stop() {
        proxyJob?.cancel()
        try {
            clientSocket?.close()
            serverSocket?.close()
            usbConnection.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
