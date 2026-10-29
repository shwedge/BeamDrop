package com.example.model

enum class TransferDirection {
    UPLOAD,
    DOWNLOAD
}

enum class QueueStatus {
    QUEUED,
    IN_PROGRESS,
    PAUSED,
    COMPLETED,
    CANCELLED,
    FAILED
}

data class QueuedTransfer(
    val id: String,
    val fileName: String,
    val fileSize: Long,
    val bytesTransferred: Long = 0L,
    val progress: Float = 0f,
    val speedMbps: Float = 0f,
    val direction: TransferDirection,
    val peerName: String,
    val status: QueueStatus = QueueStatus.QUEUED,
    val mimeType: String = "*/*",
    val localUri: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val errorReason: String? = null,
    val etaSeconds: Int = 0
) {
    val isFinished: Boolean
        get() = status == QueueStatus.COMPLETED || status == QueueStatus.CANCELLED || status == QueueStatus.FAILED

    val isActive: Boolean
        get() = status == QueueStatus.IN_PROGRESS || status == QueueStatus.QUEUED

    val progressPercent: Int
        get() = (progress * 100).toInt().coerceIn(0, 100)

    val formattedSize: String
        get() = SharableItem.formatFileSize(fileSize)

    val formattedTransferred: String
        get() = SharableItem.formatFileSize(bytesTransferred)

    val formattedSpeed: String
        get() = "%.1f MB/s".format(speedMbps)
}
