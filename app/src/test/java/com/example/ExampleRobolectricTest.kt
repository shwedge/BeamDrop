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

    @Test
    fun `mainActivity launches and networkDiscoveryManager initializes without crash`() {
        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        assertFalse(activity.isFinishing)

        val discoveryManager = com.example.discovery.NetworkDiscoveryManager(
            context = activity,
            deviceName = "HONOR Test",
            tcpPort = 8988
        )
        discoveryManager.start()
        val localIp = discoveryManager.getPrimaryLocalIpAddress() ?: discoveryManager.localIpAddress.value
        assertTrue(localIp.isNotBlank())

        // Verify peer appears immediately when joining Wi-Fi / BeamDrop
        discoveryManager.registerHandshakePeer(
            peerId = "win_victus",
            peerName = "SAJJAD-VICTUS",
            peerIp = "192.168.1.109",
            port = 8988,
            deviceType = com.example.model.DeviceType.COMPUTER
        )
        assertTrue(discoveryManager.discoveredPeers.value.containsKey("192.168.1.109"))

        // Verify peer disappears immediately when leaving BeamDrop
        val removed = discoveryManager.removePeerByIdOrIp(peerId = "win_victus", peerIp = "192.168.1.109")
        assertTrue(removed)
        assertFalse(discoveryManager.discoveredPeers.value.containsKey("192.168.1.109"))

        discoveryManager.pauseBackgroundActivity()
        discoveryManager.resumeBackgroundActivity()
        discoveryManager.stop()

        controller.pause().stop().destroy()
    }

    @Test
    fun `auraPathState serializes directional flow and both sender and receiver can cancel active transfer`() {
        val leftAura = com.example.model.AuraPathState(
            isActive = true,
            sessionId = "aura_dir_1",
            sourceDeviceName = "HONOR Phone",
            targetDeviceName = "SAJJAD-VICTUS",
            fileName = "Design_Assets.zip",
            migrationProgress = 0.42f,
            speedMbps = 78.4f,
            flowDirection = "LEFT"
        )
        val wireLeft = leftAura.toWirePacket("MIGRATING")
        val parsedLeft = com.example.model.AuraPathState.fromWirePacket(wireLeft, "SAJJAD-VICTUS")
        assertEquals("LEFT", parsedLeft?.flowDirection)
        assertEquals("MIGRATING", parsedLeft?.stateToken)
        assertEquals(true, parsedLeft?.isActive)

        val cancelledWire = leftAura.copy(flowDirection = "RIGHT").toWirePacket("CANCELLED")
        val parsedCancelled = com.example.model.AuraPathState.fromWirePacket(cancelledWire, "SAJJAD-VICTUS")
        assertEquals("RIGHT", parsedCancelled?.flowDirection)
        assertEquals("CANCELLED", parsedCancelled?.stateToken)
        assertEquals(false, parsedCancelled?.isActive)

        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val viewModel = com.example.ui.BeamDropViewModel(app)
        viewModel.addCustomFile("Design_Assets.zip", 15 * 1024 * 1024L, "application/zip", "/tmp/Design_Assets.zip")
        val peer = com.example.model.BeamPeer(
            id = "peer_win",
            name = "SAJJAD-VICTUS",
            ipAddress = "192.168.1.109",
            deviceType = com.example.model.DeviceType.COMPUTER
        )
        val item = viewModel.mediaItems.value.first { it.title == "Design_Assets.zip" }

        // Simulate active transfer (receiver or sender) and cancel mid-flight
        viewModel.updateActiveTransferProgress(peer, item, progress = 0.35f, speedMbps = 52.0f)
        assertTrue(viewModel.transferState.value is com.example.model.TransferState.Transferring)

        viewModel.cancelAllActiveTransfers()
        assertTrue(viewModel.transferState.value is com.example.model.TransferState.Idle)
        assertTrue(viewModel.transferQueue.value.isEmpty())
        assertFalse(viewModel.auraPathState.value.isActive)

        viewModel.onCleared()
    }

    @Test
    fun `plain text staging avoids txt file creation and theme state updates for iOS 26 Clear`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val viewModel = com.example.ui.BeamDropViewModel(app)

        viewModel.addTextSnippet("Hello from Sajjad Hossain Mridul")
        val stagedText = viewModel.mediaItems.value.firstOrNull()
        assertEquals("text/plain;beamdrop-raw", stagedText?.mimeType)
        assertEquals("beamtext://Hello from Sajjad Hossain Mridul", stagedText?.uri)
        assertEquals(true, stagedText?.isSelected)

        // Verify iOS 26 Clear theme mode updates ActiveBeamThemeState
        viewModel.setThemeMode("iOS 26 Clear")
        assertTrue(com.example.ui.theme.ActiveBeamThemeState.current.isClearGlass)

        // Verify swipeToSendStagedItems triggers directional wave even without manually selecting a receiver
        viewModel.swipeToSendStagedItems("RIGHT")
        assertTrue(viewModel.auraPathState.value.isActive)
        assertEquals("RIGHT", viewModel.auraPathState.value.flowDirection)

        viewModel.onCleared()
    }
}
