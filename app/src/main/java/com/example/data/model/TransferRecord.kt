package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transfers")
data class TransferRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileName: String,
    val fileType: String, // PHOTOS, VIDEOS, AUDIO, DOCUMENTS, APPS
    val fileSize: Long,
    val direction: String, // SENT, RECEIVED
    val peerDeviceName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val durationMs: Long = 0,
    val transferRateMbps: Float = 0f,
    val localUri: String? = null,
    val status: String = "COMPLETED", // COMPLETED, FAILED, CANCELLED
    val errorMessage: String? = null
) {
    val isSuccess: Boolean
        get() = status.equals("COMPLETED", ignoreCase = true)

    val isFailed: Boolean
        get() = status.equals("FAILED", ignoreCase = true)

    val isCancelled: Boolean
        get() = status.equals("CANCELLED", ignoreCase = true)
}
