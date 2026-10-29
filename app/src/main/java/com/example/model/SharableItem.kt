package com.example.model

enum class ShareCategory(val label: String) {
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    DOCUMENTS("Documents"),
    AUDIO("Audio"),
    APPS("Apps")
}

data class SharableItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val sizeBytes: Long,
    val mimeType: String,
    val category: ShareCategory,
    val uri: String? = null,
    val previewDrawable: String? = null,
    val isSelected: Boolean = false,
    val dateAdded: String = "Today"
) {
    val formattedSize: String
        get() = formatFileSize(sizeBytes)

    companion object {
        fun formatFileSize(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
            val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
            return String.format(java.util.Locale.US, "%.1f %s", value, units[digitGroups.coerceAtMost(units.size - 1)])
        }
    }
}
