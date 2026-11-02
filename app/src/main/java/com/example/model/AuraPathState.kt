package com.example.model

import androidx.compose.ui.geometry.Offset
import java.util.Locale

/**
 * Shared cross-platform animation state between Android and Windows that drives the
 * Glass Liquid animation representing the file payload flowing from the source
 * device to the target device (Top edge on Android, Left/Right/Top edge on Windows).
 */
data class AuraPathState(
    val isActive: Boolean = false,
    val sessionId: String = "",
    val fileName: String = "",
    val fileSizeBytes: Long = 0L,
    val migrationProgress: Float = 0f,
    val speedMbps: Float = 0f,
    val isOutgoing: Boolean = true,
    val sourceDeviceName: String = "",
    val targetDeviceName: String = "",
    val targetPeerId: String = "",
    val flowDirection: String = "TOP", // "TOP", "LEFT", or "RIGHT"
    val stateToken: String = "IDLE",   // "START", "MIGRATING", "COMPLETED", "CANCELLED", "IDLE"
    val sourceButtonOffset: Offset? = null,
    val targetDeviceOffset: Offset? = null,
    val timestampMs: Long = 0L
) {
    /**
     * Serializes this state into a compact, cross-platform UDP wire packet understood
     * by both Android's NetworkDiscoveryManager and Windows' BeamDropWindows.cs.
     * Format: BEAMDROP_AURA_PATH|<sessionId>|<sourceDevice>|<targetDevice>|<fileName>|<progress>|<speedMbps>|<state>|<flowDirection>
     */
    fun toWirePacket(tokenOverride: String = if (isActive) "MIGRATING" else "COMPLETED"): String {
        val safeSrc = sourceDeviceName.replace("|", "_").ifBlank { "BeamDrop_Peer" }
        val safeDst = targetDeviceName.replace("|", "_").ifBlank { "BeamDrop_Target" }
        val safeFile = fileName.replace("|", "_").ifBlank { "Payload.bin" }
        val safeSession = sessionId.ifBlank { "aura_${timestampMs % 100000}" }
        val clampedProgress = migrationProgress.coerceIn(0f, 1f)
        val safeDir = when (flowDirection.uppercase(Locale.US)) {
            "LEFT" -> "LEFT"
            "RIGHT" -> "RIGHT"
            else -> "TOP"
        }
        return String.format(
            Locale.US,
            "BEAMDROP_AURA_PATH|%s|%s|%s|%s|%.4f|%.2f|%s|%s",
            safeSession,
            safeSrc,
            safeDst,
            safeFile,
            clampedProgress,
            speedMbps,
            tokenOverride,
            safeDir
        )
    }

    companion object {
        fun fromWirePacket(raw: String, localDeviceName: String): AuraPathState? {
            if (!raw.startsWith("BEAMDROP_AURA_PATH|")) return null
            val parts = raw.split("|")
            if (parts.size < 8) return null
            val sessionId = parts[1]
            val srcDevice = parts[2]
            val dstDevice = parts[3]
            val fileName = parts[4]
            val progress = parts[5].toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f
            val speed = parts[6].toFloatOrNull() ?: 0f
            val token = parts[7].uppercase(Locale.US)
            val direction = parts.getOrNull(8)?.uppercase(Locale.US)?.takeIf {
                it == "LEFT" || it == "RIGHT" || it == "TOP"
            } ?: "TOP"
            val active = token != "COMPLETED" && token != "CANCELLED" && progress < 1.0f
            val outgoing = srcDevice.equals(localDeviceName, ignoreCase = true)

            return AuraPathState(
                isActive = active,
                sessionId = sessionId,
                fileName = fileName,
                migrationProgress = progress,
                speedMbps = speed,
                isOutgoing = outgoing,
                sourceDeviceName = srcDevice,
                targetDeviceName = dstDevice,
                flowDirection = direction,
                stateToken = token,
                timestampMs = System.currentTimeMillis()
            )
        }
    }
}
