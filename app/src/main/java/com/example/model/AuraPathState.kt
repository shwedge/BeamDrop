package com.example.model

import androidx.compose.ui.geometry.Offset

/**
 * Shared cross-platform animation state between Android and Windows that drives a
 * Canvas-based 'Aura Path' visually representing the file payload migrating from the
 * source device's transfer button to the target device, synchronized in real time over UDP/TCP.
 */
data class AuraPathState(
    val isActive: Boolean = false,
    val sessionId: String = "",
    val fileName: String = "",
    val fileSizeBytes: Long = 0L,
    val migrationProgress: Float = 0f, // 0.0f (at source button) -> 1.0f (at target device)
    val speedMbps: Float = 0f,
    val isOutgoing: Boolean = true,
    val sourceDeviceName: String = "",
    val targetDeviceName: String = "",
    val targetPeerId: String = "",
    val sourceButtonOffset: Offset? = null,
    val targetDeviceOffset: Offset? = null,
    val timestampMs: Long = 0L
) {
    /**
     * Serializes the shared animation state into a compact wire frame for real-time
     * UDP/TCP synchronization between Android and Windows.
     * Format: BEAMDROP_AURA_PATH|<sessionId>|<sourceDevice>|<targetDevice>|<fileName>|<progress>|<speedMbps>|<state>
     */
    fun toWirePacket(stateToken: String = if (isActive) "MIGRATING" else "COMPLETED"): String {
        val safeSource = sourceDeviceName.replace("|", "_").ifBlank { "BeamDrop_Source" }
        val safeTarget = targetDeviceName.replace("|", "_").ifBlank { "BeamDrop_Target" }
        val safeFile = fileName.replace("|", "_").ifBlank { "Payload.bin" }
        val clampedProgress = migrationProgress.coerceIn(0f, 1f)
        return "BEAMDROP_AURA_PATH|$sessionId|$safeSource|$safeTarget|$safeFile|$clampedProgress|$speedMbps|$stateToken"
    }

    companion object {
        fun fromWirePacket(packet: String, localDeviceName: String): AuraPathState? {
            if (!packet.startsWith("BEAMDROP_AURA_PATH|")) return null
            val parts = packet.trim().split("|")
            if (parts.size < 8) return null

            val sessionId = parts[1]
            val sourceDevice = parts[2]
            val targetDevice = parts[3]
            val fileName = parts[4]
            val progress = parts[5].toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f
            val speed = parts[6].toFloatOrNull() ?: 0f
            val stateToken = parts[7]

            val isActive = stateToken != "COMPLETED" && stateToken != "CANCELLED" && progress < 1.0f
            val isOutgoing = sourceDevice.equals(localDeviceName, ignoreCase = true)

            return AuraPathState(
                isActive = isActive,
                sessionId = sessionId,
                fileName = fileName,
                migrationProgress = if (stateToken == "COMPLETED") 1.0f else progress,
                speedMbps = speed,
                isOutgoing = isOutgoing,
                sourceDeviceName = sourceDevice,
                targetDeviceName = targetDevice,
                timestampMs = System.currentTimeMillis()
            )
        }
    }
}
