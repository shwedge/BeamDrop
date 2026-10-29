package com.example

import androidx.compose.ui.geometry.Offset
import com.example.model.AuraPathState
import com.example.model.BeamPeer
import com.example.model.DeviceType
import com.example.model.SharableItem
import com.example.model.ShareCategory
import com.example.model.TransferState
import com.example.ui.components.evaluateCubicBezier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testFileSizeFormatting() {
        assertEquals("0 B", SharableItem.formatFileSize(0))
        assertEquals("500.0 B", SharableItem.formatFileSize(500))
        assertEquals("1.0 KB", SharableItem.formatFileSize(1024))
        assertEquals("42.0 MB", SharableItem.formatFileSize(42 * 1024 * 1024L))
        assertEquals("1.5 GB", SharableItem.formatFileSize((1.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun testBeamPeerSignalPercentCalculation() {
        val strongPeer = BeamPeer(
            id = "test_1",
            name = "Pixel 9 Pro",
            deviceType = DeviceType.PHONE,
            signalStrengthDbm = -40
        )
        // (-40 + 100) * 1.5 = 90%
        assertEquals(90, strongPeer.signalPercent)

        val weakPeer = BeamPeer(
            id = "test_2",
            name = "OnePlus 12",
            deviceType = DeviceType.PHONE,
            signalStrengthDbm = -95
        )
        assertTrue(weakPeer.signalPercent in 10..15)
    }

    @Test
    fun testShareCategoryLabels() {
        assertEquals("Photos", ShareCategory.PHOTOS.label)
        assertEquals("Videos", ShareCategory.VIDEOS.label)
        assertEquals("Documents", ShareCategory.DOCUMENTS.label)
        assertEquals("Audio", ShareCategory.AUDIO.label)
        assertEquals("Apps", ShareCategory.APPS.label)
    }

    @Test
    fun testAuraPathStateWireProtocolSerialization() {
        val state = AuraPathState(
            isActive = true,
            sessionId = "session_99",
            sourceDeviceName = "HONOR Win RT",
            targetDeviceName = "SAJJAD-VICTUS",
            fileName = "Vacation_4K.mp4",
            migrationProgress = 0.642f,
            speedMbps = 48.5f,
            isOutgoing = true
        )
        val packet = state.toWirePacket()
        assertTrue(packet.startsWith("BEAMDROP_AURA_PATH|"))

        val parsed = AuraPathState.fromWirePacket(packet, "HONOR Win RT")
        assertNotNull(parsed)
        assertEquals("session_99", parsed!!.sessionId)
        assertEquals("HONOR Win RT", parsed.sourceDeviceName)
        assertEquals("SAJJAD-VICTUS", parsed.targetDeviceName)
        assertEquals("Vacation_4K.mp4", parsed.fileName)
        assertEquals(0.642f, parsed.migrationProgress, 0.002f)
        assertEquals(48.5f, parsed.speedMbps, 0.2f)
        assertTrue(parsed.isOutgoing)
        assertTrue(parsed.isActive)
    }

    @Test
    fun testCubicBezierAuraPathEvaluation() {
        val p0 = Offset(100f, 800f)
        val p1 = Offset(200f, 400f)
        val p2 = Offset(400f, 300f)
        val p3 = Offset(500f, 200f)

        val start = evaluateCubicBezier(p0, p1, p2, p3, 0f)
        assertEquals(100f, start.x, 0.01f)
        assertEquals(800f, start.y, 0.01f)

        val end = evaluateCubicBezier(p0, p1, p2, p3, 1f)
        assertEquals(500f, end.x, 0.01f)
        assertEquals(200f, end.y, 0.01f)

        val mid = evaluateCubicBezier(p0, p1, p2, p3, 0.5f)
        assertTrue(mid.x in 250f..350f)
        assertTrue(mid.y in 350f..500f)
    }
}
