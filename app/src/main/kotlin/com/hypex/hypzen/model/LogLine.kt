package com.hypex.hypzen.model

enum class LogType {
    CMD, OK, WARN, ERROR, INFO, DIM
}

data class LogLine(
    val timestamp: String,
    val text: String,
    val type: LogType
)

data class FlashOperation(
    val command: String = "flash",
    val partition: String,
    val fileName: String? = null,
    var fileUri: android.net.Uri? = null,
    var status: OperationStatus = OperationStatus.PENDING
)

enum class OperationStatus {
    PENDING, MISSING, READY, FLASHING, DONE, ERROR
}
