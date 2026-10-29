package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.TransferRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("BeamDrop", appName)
    }

    @Test
    fun `transfer record stores timestamps file names and statuses accurately`() {
        val now = 1761666000000L
        val completedRecord = TransferRecord(
            id = 1L,
            fileName = "Keynote_Demo_4K.mp4",
            fileType = "VIDEOS",
            fileSize = 52428800L,
            direction = "SENT",
            peerDeviceName = "Windows PC (Desktop)",
            timestamp = now,
            durationMs = 950L,
            transferRateMbps = 55.2f,
            status = "COMPLETED"
        )
        assertTrue(completedRecord.isSuccess)
        assertFalse(completedRecord.isFailed)
        assertEquals("Keynote_Demo_4K.mp4", completedRecord.fileName)
        assertEquals(now, completedRecord.timestamp)

        val failedRecord = TransferRecord(
            id = 2L,
            fileName = "Archive_Backup.zip",
            fileType = "DOCUMENTS",
            fileSize = 104857600L,
            direction = "RECEIVED",
            peerDeviceName = "Honor Phone",
            timestamp = now + 5000L,
            status = "FAILED",
            errorMessage = "Socket connection reset by peer"
        )
        assertTrue(failedRecord.isFailed)
        assertFalse(failedRecord.isSuccess)
        assertEquals("Socket connection reset by peer", failedRecord.errorMessage)
    }

    @Test
    fun `nfc adapter discovery helper parses beamdrop handshake and validates compatible techs`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = com.example.hardware.NfcBeamHelper(context)

        val ndefMessage = helper.createBeamHandshakeMessage(
            localIp = "192.168.1.167",
            port = 8988,
            deviceName = "HONOR Win RT",
            p2pMac = "02:11:22:33:44:55"
        )
        val parsed = helper.parseHandshakePayload(ndefMessage)
        assertEquals("192.168.1.167", parsed?.peerIp)
        assertEquals(8988, parsed?.peerPort)
        assertEquals("HONOR Win RT", parsed?.deviceName)
        assertEquals("02:11:22:33:44:55", parsed?.p2pMacAddress)

        assertTrue(helper.isCompatibleNfcDeviceOrTag(listOf(android.nfc.tech.IsoDep::class.java.name)))
        assertTrue(helper.isCompatibleNfcDeviceOrTag(listOf(android.nfc.tech.Ndef::class.java.name)))
    }

    @Test
    fun `discreet progress indicator updates during active transfer and clears immediately on completion`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val viewModel = com.example.ui.BeamDropViewModel(app)

        // Stage a custom browsed file
        viewModel.addCustomFile("Presentation_Final.pdf", 10 * 1024 * 1024L, "application/pdf", "/tmp/Presentation_Final.pdf")
        assertTrue(viewModel.mediaItems.value.any { it.title == "Presentation_Final.pdf" && it.isSelected })

        val peer = com.example.model.BeamPeer(
            id = "peer_win",
            name = "SAJJAD-VICTUS",
            ipAddress = "192.168.1.109",
            deviceType = com.example.model.DeviceType.COMPUTER
        )
        val activeItem = viewModel.mediaItems.value.first { it.title == "Presentation_Final.pdf" }

        // Simulate 55% active transfer progress
        viewModel.updateActiveTransferProgress(peer, activeItem, progress = 0.55f, speedMbps = 64.5f)
        assertTrue(viewModel.transferState.value is com.example.model.TransferState.Transferring)
        assertEquals(1, viewModel.transferQueue.value.size)

        // Simulate 100% completion -> must immediately self-dismiss and clear queue & staged browsed file
        viewModel.updateActiveTransferProgress(peer, activeItem, progress = 1.0f, speedMbps = 64.5f)
        assertTrue(viewModel.transferState.value is com.example.model.TransferState.Idle)
        assertTrue(viewModel.transferQueue.value.isEmpty())
        assertFalse(viewModel.mediaItems.value.any { it.id.startsWith("browsed_") })

        viewModel.onCleared()
    }

    @Test
    fun `wifiDirectManager handles broadcast receiver actions and dns sd service discovery`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val wifiDirectManager = com.example.p2p.WifiDirectManager(context, "HONOR Win RT", 8988)

        val stateEnabledIntent = android.content.Intent(android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION).apply {
            putExtra(
                android.net.wifi.p2p.WifiP2pManager.EXTRA_WIFI_STATE,
                android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_STATE_ENABLED
            )
        }
        wifiDirectManager.receiver.onReceive(context, stateEnabledIntent)
        assertTrue(wifiDirectManager.isP2pEnabled.value)

        // Simulate DNS-SD TXT & Service discovery response from a peer on the local network
        wifiDirectManager.onDnsSdServiceDiscovered(
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            deviceName = "SAJJAD-VICTUS",
            txtRecord = mapOf(
                "device_name" to "SAJJAD-VICTUS",
                "listen_port" to "8988",
                "ip_address" to "192.168.1.109",
                "device_type" to "COMPUTER"
            )
        )

        val discovered = wifiDirectManager.servicePeers.value["p2p_AA:BB:CC:DD:EE:FF"]
        assertEquals("SAJJAD-VICTUS", discovered?.name)
        assertEquals("192.168.1.109", discovered?.ipAddress)
        assertEquals(8988, discovered?.port)
        assertEquals(com.example.model.DeviceType.COMPUTER, discovered?.deviceType)
    }
}

