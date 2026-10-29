package com.example.ui

import android.app.Application
import android.nfc.Tag
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DeviceMediaLoader
import com.example.data.database.BeamDropDatabase
import com.example.data.model.TransferRecord
import com.example.data.repository.TransferRepository
import com.example.discovery.NetworkDiscoveryManager
import com.example.engine.IncomingTransferEvent
import com.example.engine.RealTransferEngine
import com.example.hardware.HapticFeedbackHelper
import com.example.hardware.NfcBeamHelper
import com.example.hardware.NfcBeamPayload
import com.example.hardware.NfcDiscoveryEvent
import com.example.hardware.TouchGestureEvent
import com.example.hardware.TouchSensorManager
import androidx.compose.ui.geometry.Offset
import com.example.model.AuraPathState
import com.example.model.BeamPeer
import com.example.model.DeviceType
import com.example.model.SharableItem
import com.example.model.ShareCategory
import com.example.model.QueuedTransfer
import com.example.model.QueueStatus
import com.example.model.TransferDirection
import com.example.model.TransferState
import com.example.model.BeamTab
import com.example.p2p.WifiDirectConnectionState
import com.example.p2p.WifiDirectManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class BeamDropViewModel(application: Application) : AndroidViewModel(application) {
    private val tag = "BeamDropViewModel"
    private val database = BeamDropDatabase.getDatabase(application)
    private val transferRepository = TransferRepository(database.transferDao())
    private val haptics = HapticFeedbackHelper(application)
    val nfcHelper = NfcBeamHelper(application)
    private val sensorManager = TouchSensorManager(application)
    private val transferEngine = RealTransferEngine(application)
    private val mediaLoader = DeviceMediaLoader(application)
    val wifiDirectManager = WifiDirectManager(application)

    val deviceName: String = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"

    // Persistent Transfer Queue State
    private val _transferQueue = MutableStateFlow<List<QueuedTransfer>>(emptyList())
    val transferQueue: StateFlow<List<QueuedTransfer>> = _transferQueue.asStateFlow()

    private val _isQueueExpanded = MutableStateFlow(false)
    val isQueueExpanded: StateFlow<Boolean> = _isQueueExpanded.asStateFlow()

    private val activeTransferJobs = ConcurrentHashMap<String, Job>()
    private var currentIncomingTransferId: String? = null

    // Real Wi-Fi Network Discovery
    private val discoveryManager = NetworkDiscoveryManager(
        context = application,
        deviceName = deviceName,
        tcpPort = 8988
    )

    val localIpAddress: StateFlow<String> = discoveryManager.localIpAddress

    val transferHistory: StateFlow<List<TransferRecord>> = transferRepository.allTransfers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalBytesTransferred: StateFlow<Long?> = transferRepository.totalBytesTransferred
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val webShareServer = com.example.engine.WebShareServer(
        context = application,
        port = 8990,
        deviceName = deviceName,
        getLocalIp = { localIpAddress.value },
        onFileReceived = { file, size, senderInfo ->
            viewModelScope.launch {
                _lastReceivedFile.value = file
                haptics.onTransferCompleted()

                transferRepository.logTransfer(
                    TransferRecord(
                        fileName = file.name,
                        fileType = "DOCUMENTS",
                        fileSize = size,
                        direction = "RECEIVED",
                        peerDeviceName = senderInfo,
                        durationMs = 1200L,
                        transferRateMbps = 55.4f,
                        localUri = file.absolutePath,
                        status = "COMPLETED"
                    )
                )

                // Immediately clear queue and self-dismiss progress state upon completion
                dismissCompleted()
            }
        },
        getStagedFiles = {
            _mediaItems.value.filter { it.isSelected }
        },
        getTransferHistory = {
            transferHistory.value
        }
    )

    val wifiDirectState: StateFlow<WifiDirectConnectionState> = wifiDirectManager.connectionState

    // State Holders
    private val _currentTab = MutableStateFlow(BeamTab.FILES)
    val currentTab: StateFlow<BeamTab> = _currentTab.asStateFlow()

    private val _selectedCategory = MutableStateFlow(ShareCategory.PHOTOS)
    val selectedCategory: StateFlow<ShareCategory> = _selectedCategory.asStateFlow()

    private val _mediaItems = MutableStateFlow<List<SharableItem>>(emptyList())
    val mediaItems: StateFlow<List<SharableItem>> = _mediaItems.asStateFlow()

    // Real discovered peers on local Wi-Fi / NSD
    private val _nearbyPeers = MutableStateFlow<List<BeamPeer>>(emptyList())
    val nearbyPeers: StateFlow<List<BeamPeer>> = _nearbyPeers.asStateFlow()

    private val _transferState = MutableStateFlow<TransferState>(TransferState.Idle)
    val transferState: StateFlow<TransferState> = _transferState.asStateFlow()

    private val _auraIntensity = MutableStateFlow(0f)
    val auraIntensity: StateFlow<Float> = _auraIntensity.asStateFlow()

    private val _isContactLocked = MutableStateFlow(false)
    val isContactLocked: StateFlow<Boolean> = _isContactLocked.asStateFlow()

    private val _showInfoDialog = MutableStateFlow(false)
    val showInfoDialog: StateFlow<Boolean> = _showInfoDialog.asStateFlow()

    private val _lastReceivedFile = MutableStateFlow<File?>(null)
    val lastReceivedFile: StateFlow<File?> = _lastReceivedFile.asStateFlow()

    // Shared cross-platform Canvas 'Aura Path' animation state synchronized with Windows
    private val _auraPathState = MutableStateFlow(AuraPathState())
    val auraPathState: StateFlow<AuraPathState> = _auraPathState.asStateFlow()

    private var measuredSourceButtonOffset: Offset? = null
    private val measuredTargetPeerOffsets = ConcurrentHashMap<String, Offset>()

    val isNfcReady: Boolean
        get() = nfcHelper.isNfcSupported

    val isProximityActive: Boolean = true

    private var transferJob: Job? = null

    init {
        // Start Real Wi-Fi discovery & Wi-Fi Direct P2P
        discoveryManager.start()
        wifiDirectManager.start()
        startRealTransferServer()
        webShareServer.start()
        observeNetworkPeers()
        observeWifiDirectPeers()
        observeNfcDiscoveryEvents()
        observeRemoteAuraPathSync()

        // Load real media and sensors
        loadDeviceMedia()
        startSensorMonitoring()
    }

    private fun observeRemoteAuraPathSync() {
        viewModelScope.launch {
            discoveryManager.remoteAuraPathState.collect { remoteState ->
                if (remoteState != null) {
                    val targetOffset = measuredTargetPeerOffsets.values.firstOrNull()
                    _auraPathState.value = remoteState.copy(
                        sourceButtonOffset = measuredSourceButtonOffset,
                        targetDeviceOffset = targetOffset
                    )
                    if (remoteState.isActive) {
                        _auraIntensity.value = 0.8f
                    } else if (_transferState.value is TransferState.Idle) {
                        _auraIntensity.value = 0f
                    }
                }
            }
        }
    }

    fun updateSourceButtonCoordinates(offset: Offset) {
        measuredSourceButtonOffset = offset
        if (_auraPathState.value.isActive) {
            _auraPathState.value = _auraPathState.value.copy(sourceButtonOffset = offset)
        }
    }

    fun updateTargetDeviceCoordinates(peerId: String, offset: Offset) {
        measuredTargetPeerOffsets[peerId] = offset
        if (_auraPathState.value.isActive && (_auraPathState.value.targetPeerId == peerId || _auraPathState.value.targetDeviceOffset == null)) {
            _auraPathState.value = _auraPathState.value.copy(targetDeviceOffset = offset)
        }
    }

    private fun startRealTransferServer() {
        transferEngine.startServer(
            port = 8988,
            localDeviceId = discoveryManager.localDeviceId,
            myDeviceName = deviceName
        ) { event ->
            when (event) {
                is IncomingTransferEvent.PeerHandshake -> {
                    discoveryManager.registerHandshakePeer(
                        peerId = event.peerId,
                        peerName = event.peerName,
                        peerIp = event.peerIp,
                        port = 8988,
                        deviceType = event.deviceType
                    )
                }
                is IncomingTransferEvent.Started -> {
                    _isContactLocked.value = true
                    _auraIntensity.value = 0.8f
                    haptics.onTopContactLocked()

                    val rxId = "rx_${UUID.randomUUID()}"
                    currentIncomingTransferId = rxId
                    val initialTransfer = QueuedTransfer(
                        id = rxId,
                        fileName = "Receiving ${event.fileCount} item(s)",
                        fileSize = event.totalBytes,
                        bytesTransferred = 0L,
                        progress = 0f,
                        speedMbps = 0f,
                        direction = TransferDirection.DOWNLOAD,
                        peerName = event.senderName,
                        status = QueueStatus.IN_PROGRESS
                    )
                    _transferQueue.value = listOf(initialTransfer) + _transferQueue.value

                    val devType = if (event.senderName.contains("Windows", ignoreCase = true) ||
                        event.senderName.contains("PC", ignoreCase = true) ||
                        event.senderName.contains("Mac", ignoreCase = true)
                    ) {
                        DeviceType.COMPUTER
                    } else {
                        DeviceType.PHONE
                    }
                    val peer = BeamPeer(
                        id = "sender_${event.senderIp.ifEmpty { event.senderName }}",
                        name = event.senderName,
                        ipAddress = event.senderIp,
                        deviceType = devType
                    )
                    _transferState.value = TransferState.HandshakeReady(peer, emptyList())
                    _auraPathState.value = AuraPathState(
                        isActive = true,
                        sessionId = rxId,
                        fileName = "Incoming (${event.fileCount})",
                        fileSizeBytes = event.totalBytes,
                        migrationProgress = 0.04f,
                        speedMbps = 0f,
                        isOutgoing = false,
                        sourceDeviceName = event.senderName,
                        targetDeviceName = deviceName,
                        targetPeerId = peer.id,
                        sourceButtonOffset = null,
                        targetDeviceOffset = measuredSourceButtonOffset,
                        timestampMs = System.currentTimeMillis()
                    )
                }
                is IncomingTransferEvent.Progress -> {
                    _auraIntensity.value = 0.7f
                    var activePeerName = "Connected Device"
                    currentIncomingTransferId?.let { id ->
                        _transferQueue.value = _transferQueue.value.map { item ->
                            if (item.id == id) {
                                activePeerName = item.peerName
                                item.copy(
                                    fileName = event.fileName,
                                    fileSize = event.totalBytes,
                                    bytesTransferred = event.bytesTransferred,
                                    progress = event.progress,
                                    speedMbps = event.speedMbps,
                                    status = QueueStatus.IN_PROGRESS
                                )
                            } else item
                        }
                    }

                    val dummyItem = SharableItem(
                        id = "rx_${event.fileIndex}",
                        title = event.fileName,
                        subtitle = "Incoming Beam",
                        sizeBytes = event.totalBytes,
                        mimeType = "*/*",
                        category = ShareCategory.DOCUMENTS
                    )
                    val devType = if (activePeerName.contains("Windows", ignoreCase = true) ||
                        activePeerName.contains("PC", ignoreCase = true) ||
                        activePeerName.contains("Mac", ignoreCase = true)
                    ) {
                        DeviceType.COMPUTER
                    } else {
                        DeviceType.PHONE
                    }
                    val peer = BeamPeer(
                        id = "incoming_sender",
                        name = activePeerName,
                        deviceType = devType
                    )
                    _transferState.value = TransferState.Transferring(
                        peer = peer,
                        currentItem = dummyItem,
                        currentItemIndex = event.fileIndex,
                        totalItemCount = event.totalFiles,
                        progress = event.progress,
                        speedMbps = event.speedMbps,
                        bytesTransferred = event.bytesTransferred,
                        totalBytes = event.totalBytes,
                        etaSeconds = 0
                    )
                    _auraPathState.value = _auraPathState.value.copy(
                        isActive = true,
                        fileName = event.fileName,
                        fileSizeBytes = event.totalBytes,
                        migrationProgress = event.progress.coerceIn(0f, 1f),
                        speedMbps = event.speedMbps,
                        isOutgoing = false,
                        sourceDeviceName = activePeerName,
                        targetDeviceName = deviceName,
                        targetDeviceOffset = measuredSourceButtonOffset,
                        timestampMs = System.currentTimeMillis()
                    )
                    haptics.onTransferChunkTick()
                }
                is IncomingTransferEvent.Completed -> {
                    haptics.onTransferCompleted()
                    _lastReceivedFile.value = event.savedFiles.firstOrNull()
                    currentIncomingTransferId = null

                    // Save to Room DB
                    viewModelScope.launch {
                        event.savedFiles.forEach { file ->
                            transferRepository.logTransfer(
                                TransferRecord(
                                    fileName = file.name,
                                    fileType = "RECEIVED_FILE",
                                    fileSize = file.length(),
                                    direction = "RECEIVED",
                                    peerDeviceName = event.senderName,
                                    durationMs = event.durationMs,
                                    transferRateMbps = if (event.durationMs > 0) {
                                        (event.totalBytes.toFloat() / (event.durationMs / 1000f)) / (1024f * 1024f)
                                    } else 48f,
                                    localUri = file.absolutePath,
                                    status = "COMPLETED"
                                )
                            )
                        }
                    }

                    // Immediately clear active progress indicator and queue upon completion
                    dismissCompleted()
                }
                is IncomingTransferEvent.Failed -> {
                    _isContactLocked.value = false
                    _auraIntensity.value = 0f
                    var failedFileName = "Incoming Beam"
                    var failedFileSize = 0L
                    var failedPeerName = "Nearby Device"

                    currentIncomingTransferId?.let { id ->
                        val matching = _transferQueue.value.find { it.id == id }
                        if (matching != null) {
                            failedFileName = matching.fileName
                            failedFileSize = matching.fileSize
                            failedPeerName = matching.peerName
                        }
                        _transferQueue.value = _transferQueue.value.filter { it.id != id && it.isActive }
                        currentIncomingTransferId = null
                    }

                    viewModelScope.launch {
                        transferRepository.logTransfer(
                            TransferRecord(
                                fileName = failedFileName,
                                fileType = "RECEIVED_FILE",
                                fileSize = failedFileSize,
                                direction = "RECEIVED",
                                peerDeviceName = failedPeerName,
                                durationMs = 0L,
                                transferRateMbps = 0f,
                                status = "FAILED",
                                errorMessage = event.error
                            )
                        )
                    }
                    _transferState.value = TransferState.Failed(null, event.error)
                    viewModelScope.launch {
                        delay(2200)
                        if (_transferState.value is TransferState.Failed) {
                            dismissCompleted()
                        }
                    }
                }
            }
        }
    }

    private fun observeNetworkPeers() {
        viewModelScope.launch {
            discoveryManager.discoveredPeers.collect { peerMap ->
                val previousCount = _nearbyPeers.value.size
                val list = peerMap.values.toList()
                val currentP2p = _nearbyPeers.value.filter { it.id.startsWith("p2p_") }
                val merged = list + currentP2p
                _nearbyPeers.value = merged
                if (merged.isNotEmpty() && previousCount == 0) {
                    if (_auraIntensity.value < 0.35f) {
                        _auraIntensity.value = 0.38f
                    }
                    haptics.onApproachTick()
                }
            }
        }
    }

    private fun observeWifiDirectPeers() {
        viewModelScope.launch {
            wifiDirectManager.servicePeers.collect { servicePeerMap ->
                val previousCount = _nearbyPeers.value.size
                val p2pPeers = servicePeerMap.values.toList()
                val networkPeers = _nearbyPeers.value.filter { !it.id.startsWith("p2p_") }
                val merged = networkPeers + p2pPeers
                _nearbyPeers.value = merged
                if (merged.isNotEmpty() && previousCount == 0) {
                    if (_auraIntensity.value < 0.35f) {
                        _auraIntensity.value = 0.38f
                    }
                    haptics.onApproachTick()
                }
            }
        }
    }

    private fun loadDeviceMedia() {
        viewModelScope.launch {
            val realMedia = mediaLoader.loadDeviceMedia()
            if (realMedia.isNotEmpty()) {
                _mediaItems.value = realMedia
            } else {
                // If on fresh emulator without photos yet, provide an initial curated set that can be shared or replaced
                loadInitialMedia()
            }
        }
    }

    private fun startSensorMonitoring() {
        sensorManager.startListening()
        viewModelScope.launch {
            sensorManager.events.collect { event ->
                when (event) {
                    is TouchGestureEvent.Approaching -> {
                        _auraIntensity.value = 0.45f
                        haptics.onApproachTick()
                    }
                    is TouchGestureEvent.ContactTriggered -> {
                        handleTopTouchContact()
                    }
                    is TouchGestureEvent.Cleared -> {
                        if (_transferState.value is TransferState.TouchGestureActive) {
                            _auraIntensity.value = 0f
                            _isContactLocked.value = false
                            _transferState.value = TransferState.Idle
                        }
                    }
                }
            }
        }
    }

    private fun handleTopTouchContact() {
        if (_transferState.value is TransferState.Transferring) return

        val selectedFiles = _mediaItems.value.filter { it.isSelected }
        val targetFiles = if (selectedFiles.isNotEmpty()) {
            selectedFiles
        } else {
            val firstItem = _mediaItems.value.firstOrNull() ?: return
            listOf(firstItem)
        }

        _isContactLocked.value = true
        _auraIntensity.value = 1.0f
        haptics.onTopContactLocked()

        // Find a genuine remote peer on Wi-Fi or Wi-Fi Direct
        val peer = _nearbyPeers.value.firstOrNull()
        if (peer == null) {
            _transferState.value = TransferState.Failed(
                peer = null,
                reason = "No other BeamDrop device detected. Please open BeamDrop on the other phone on this Wi-Fi or tap tops together with NFC."
            )
            viewModelScope.launch {
                delay(3500)
                if (_transferState.value is TransferState.Failed) {
                    _transferState.value = TransferState.Idle
                    _auraIntensity.value = 0f
                    _isContactLocked.value = false
                }
            }
            return
        }

        _transferState.value = TransferState.TouchGestureActive(
            peerName = peer.name,
            auraIntensity = 1.0f
        )

        viewModelScope.launch {
            delay(400)
            _transferState.value = TransferState.HandshakeReady(peer, targetFiles)
            delay(300)
            executeTransfer(peer, targetFiles)
        }
    }

    private fun observeNfcDiscoveryEvents() {
        viewModelScope.launch {
            localIpAddress.collect { ip ->
                NfcBeamHelper.activeLocalPayload = NfcBeamPayload(
                    peerIp = ip,
                    peerPort = 8988,
                    deviceName = deviceName,
                    p2pMacAddress = wifiDirectState.value.localDeviceAddress
                )
                wifiDirectManager.registerLocalService(ip)
            }
        }
        viewModelScope.launch {
            nfcHelper.discoveryEvents.collect { event ->
                when (event) {
                    is NfcDiscoveryEvent.CompatibleDeviceTapped -> {
                        onNfcBeamDispatched(event.payload)
                    }
                    is NfcDiscoveryEvent.AdapterStateChanged -> {
                        Log.d(tag, "NFC adapter state changed: enabled=${event.isEnabled}")
                    }
                }
            }
        }
    }

    fun getCurrentNfcPayload(): NfcBeamPayload {
        return NfcBeamPayload(
            peerIp = localIpAddress.value,
            peerPort = 8988,
            deviceName = deviceName,
            p2pMacAddress = wifiDirectState.value.localDeviceAddress
        )
    }

    /**
     * Called when a compatible NFC device or tag is tapped (via ReaderMode, HCE, or Foreground Dispatch).
     * Immediately triggers the FluidAuroraRipple animation and initiates peer connection/transfer.
     */
    fun onNfcBeamDispatched(payload: NfcBeamPayload?) {
        // Immediately lock contact and ignite the full FluidAuroraRipple animation
        _isContactLocked.value = true
        _auraIntensity.value = 1.0f
        haptics.onTopContactLocked()

        val localIps = discoveryManager.getAllLocalIpAddresses()
        val targetPeer: BeamPeer?
        if (payload != null && payload.peerIp.isNotEmpty() && payload.peerIp != "127.0.0.1" && !localIps.contains(payload.peerIp)) {
            targetPeer = BeamPeer(
                id = "nfc_${payload.peerIp}",
                name = payload.deviceName.ifEmpty { "NFC Tapped Device" },
                ipAddress = payload.peerIp,
                port = payload.peerPort,
                deviceType = DeviceType.PHONE
            )
            discoveryManager.addManualPeer(payload.peerIp, payload.peerPort, payload.deviceName)
        } else {
            targetPeer = _nearbyPeers.value.firstOrNull()
        }

        if (targetPeer == null) {
            // Keep the FluidAuroraRipple animation active while probing the subnet for the tapped device
            _transferState.value = TransferState.TouchGestureActive(
                peerName = payload?.deviceName?.takeIf { it.isNotBlank() } ?: "NFC Device Tapped",
                auraIntensity = 1.0f
            )
            scanSubnetNow()
            viewModelScope.launch {
                delay(1800)
                val discoveredPeer = _nearbyPeers.value.firstOrNull()
                if (discoveredPeer != null) {
                    val selectedFiles = _mediaItems.value.filter { it.isSelected }
                    val targetFiles = if (selectedFiles.isNotEmpty()) selectedFiles else _mediaItems.value.take(1)
                    if (targetFiles.isNotEmpty()) {
                        _transferState.value = TransferState.HandshakeReady(discoveredPeer, targetFiles)
                        delay(300)
                        executeTransfer(discoveredPeer, targetFiles)
                    } else {
                        _transferState.value = TransferState.Idle
                        _isContactLocked.value = false
                        _auraIntensity.value = 0.4f
                    }
                } else {
                    _transferState.value = TransferState.Idle
                    _isContactLocked.value = false
                    _auraIntensity.value = 0f
                }
            }
            return
        }

        val selectedFiles = _mediaItems.value.filter { it.isSelected }
        val targetFiles = if (selectedFiles.isNotEmpty()) selectedFiles else _mediaItems.value.take(1)

        _transferState.value = TransferState.TouchGestureActive(
            peerName = targetPeer.name,
            auraIntensity = 1.0f
        )

        if (targetFiles.isEmpty()) {
            viewModelScope.launch {
                delay(1500)
                _transferState.value = TransferState.Idle
                _isContactLocked.value = false
                _auraIntensity.value = 0.4f
            }
            return
        }

        if (payload != null && !payload.p2pMacAddress.isNullOrEmpty()) {
            // Wi-Fi Direct connection requested by NFC payload!
            wifiDirectManager.connectToDevice(payload.p2pMacAddress) { p2pIp ->
                val p2pPeer = targetPeer.copy(ipAddress = p2pIp)
                viewModelScope.launch {
                    delay(300)
                    _transferState.value = TransferState.HandshakeReady(p2pPeer, targetFiles)
                    executeTransfer(p2pPeer, targetFiles)
                }
            }
        } else {
            viewModelScope.launch {
                delay(400)
                _transferState.value = TransferState.HandshakeReady(targetPeer, targetFiles)
                delay(300)
                executeTransfer(targetPeer, targetFiles)
            }
        }
    }

    fun writeBeamPayloadToTag(tagObj: Tag): Boolean {
        val myIp = localIpAddress.value
        val p2pMac = wifiDirectState.value.localDeviceAddress
        return nfcHelper.writeBeamPayloadToTag(tagObj, myIp, 8988, deviceName, p2pMac)
    }

    fun simulateTopTouch() {
        sensorManager.triggerSimulatedTopTouch()
    }

    fun selectTab(tab: BeamTab) {
        _currentTab.value = tab
    }

    fun selectCategory(category: ShareCategory) {
        _selectedCategory.value = category
    }

    fun toggleItemSelection(item: SharableItem) {
        _mediaItems.value = _mediaItems.value.map {
            if (it.id == item.id) it.copy(isSelected = !it.isSelected) else it
        }
        val anySelected = _mediaItems.value.any { it.isSelected }
        _auraIntensity.value = if (anySelected) 0.35f else 0f
    }

    fun clearSelection() {
        _mediaItems.value = _mediaItems.value
            .filterNot { it.id.startsWith("browsed_") }
            .map { it.copy(isSelected = false) }
        _auraIntensity.value = 0f
    }

    fun startPeerTransfer(peer: BeamPeer) {
        val selectedFiles = _mediaItems.value.filter { it.isSelected }
        val targetFiles = if (selectedFiles.isNotEmpty()) {
            selectedFiles
        } else {
            listOf(_mediaItems.value.first())
        }
        executeTransfer(peer, targetFiles)
    }

    private fun executeTransfer(peer: BeamPeer, items: List<SharableItem>) {
        transferJob?.cancel()
        _isContactLocked.value = true
        _auraIntensity.value = 0.8f

        val sessionId = "aura_${UUID.randomUUID().toString().take(8)}"
        val targetOffset = measuredTargetPeerOffsets[peer.id] ?: measuredTargetPeerOffsets.values.firstOrNull()
        val initialAuraState = AuraPathState(
            isActive = true,
            sessionId = sessionId,
            fileName = items.firstOrNull()?.title ?: "Payload",
            fileSizeBytes = items.sumOf { it.sizeBytes },
            migrationProgress = 0.03f,
            speedMbps = 0f,
            isOutgoing = true,
            sourceDeviceName = deviceName,
            targetDeviceName = peer.name,
            targetPeerId = peer.id,
            sourceButtonOffset = measuredSourceButtonOffset,
            targetDeviceOffset = targetOffset,
            timestampMs = System.currentTimeMillis()
        )
        _auraPathState.value = initialAuraState
        discoveryManager.broadcastAuraPathSync(initialAuraState, peer.ipAddress, "START")

        val batchTransferPairs = items.map { item ->
            val qId = "tx_${UUID.randomUUID()}"
            val queued = QueuedTransfer(
                id = qId,
                fileName = item.title,
                fileSize = item.sizeBytes,
                bytesTransferred = 0L,
                progress = 0f,
                speedMbps = 0f,
                direction = TransferDirection.UPLOAD,
                peerName = peer.name,
                status = QueueStatus.QUEUED,
                mimeType = item.mimeType,
                localUri = item.uri
            )
            qId to queued
        }

        _transferQueue.value = batchTransferPairs.map { it.second } + _transferQueue.value

        transferJob = viewModelScope.launch {
            val thisJob = coroutineContext[Job]!!
            batchTransferPairs.forEach { (id, _) ->
                activeTransferJobs[id] = thisJob
            }

            val startTime = System.currentTimeMillis()
            var lastSpeed = 48.0f

            try {
                transferEngine.sendFiles(peer, items, deviceName).collect { progress ->
                    lastSpeed = progress.currentSpeedMbps
                    val currentIdx = progress.currentItemIndex

                    _transferQueue.value = _transferQueue.value.mapNotNull { queueItem ->
                        val itemIndex = batchTransferPairs.indexOfFirst { it.first == queueItem.id }
                        if (itemIndex != -1) {
                            when {
                                itemIndex < currentIdx -> {
                                    // Immediately remove already-completed items from the active queue
                                    null
                                }
                                itemIndex == currentIdx -> {
                                    queueItem.copy(
                                        bytesTransferred = progress.bytesTransferred,
                                        progress = progress.overallProgress,
                                        speedMbps = progress.currentSpeedMbps,
                                        etaSeconds = progress.etaSeconds,
                                        status = QueueStatus.IN_PROGRESS
                                    )
                                }
                                else -> {
                                    queueItem.copy(status = QueueStatus.QUEUED)
                                }
                            }
                        } else queueItem
                    }

                    _transferState.value = TransferState.Transferring(
                        peer = peer,
                        currentItem = progress.currentItem,
                        currentItemIndex = progress.currentItemIndex,
                        totalItemCount = progress.totalItems,
                        progress = progress.overallProgress,
                        speedMbps = progress.currentSpeedMbps,
                        bytesTransferred = progress.bytesTransferred,
                        totalBytes = progress.totalBytes,
                        etaSeconds = progress.etaSeconds
                    )

                    val updatedAura = _auraPathState.value.copy(
                        isActive = true,
                        fileName = progress.currentItem.title,
                        fileSizeBytes = progress.totalBytes,
                        migrationProgress = progress.overallProgress.coerceIn(0f, 1f),
                        speedMbps = progress.currentSpeedMbps,
                        isOutgoing = true,
                        sourceButtonOffset = measuredSourceButtonOffset,
                        targetDeviceOffset = measuredTargetPeerOffsets[peer.id] ?: measuredTargetPeerOffsets.values.firstOrNull(),
                        timestampMs = System.currentTimeMillis()
                    )
                    _auraPathState.value = updatedAura
                    discoveryManager.broadcastAuraPathSync(updatedAura, peer.ipAddress, "MIGRATING")

                    haptics.onTransferChunkTick()
                }

                val durationMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1)

                items.forEach { item ->
                    transferRepository.logTransfer(
                        TransferRecord(
                            fileName = item.title,
                            fileType = item.category.name,
                            fileSize = item.sizeBytes,
                            direction = "SENT",
                            peerDeviceName = peer.name,
                            durationMs = durationMs / items.size,
                            transferRateMbps = lastSpeed,
                            localUri = item.uri,
                            status = "COMPLETED"
                        )
                    )
                }

                haptics.onTransferCompleted()
                discoveryManager.broadcastAuraPathSync(
                    _auraPathState.value.copy(isActive = false, migrationProgress = 1.0f),
                    peer.ipAddress,
                    "COMPLETED"
                )

                // Immediately remove browsed/staged files, clear queue, and self-dismiss progress indicator
                clearSelection()
                dismissCompleted()
            } catch (e: Exception) {
                Log.e(tag, "Failed to send files: ${e.message}", e)
                val isCancelled = e is kotlinx.coroutines.CancellationException
                val statusStr = if (isCancelled) "CANCELLED" else "FAILED"
                val reasonStr = if (isCancelled) "Cancelled by user" else (e.message ?: "Connection failed to ${peer.name} (${peer.ipAddress})")

                val batchIds = batchTransferPairs.map { it.first }.toSet()
                _transferQueue.value = _transferQueue.value.filter { it.id !in batchIds && it.isActive }

                // Persist failed or cancelled transfer logs to History DB
                items.forEach { item ->
                    transferRepository.logTransfer(
                        TransferRecord(
                            fileName = item.title,
                            fileType = item.category.name,
                            fileSize = item.sizeBytes,
                            direction = "SENT",
                            peerDeviceName = peer.name,
                            durationMs = (System.currentTimeMillis() - startTime).coerceAtLeast(0),
                            transferRateMbps = 0f,
                            localUri = item.uri,
                            status = statusStr,
                            errorMessage = reasonStr
                        )
                    )
                }

                _isContactLocked.value = false
                _auraIntensity.value = 0f
                if (!isCancelled) {
                    _transferState.value = TransferState.Failed(peer, reasonStr)
                    viewModelScope.launch {
                        delay(2200)
                        if (_transferState.value is TransferState.Failed) {
                            dismissCompleted()
                        }
                    }
                } else {
                    dismissCompleted()
                }
            } finally {
                batchTransferPairs.forEach { (id, _) -> activeTransferJobs.remove(id) }
            }
        }
    }

    fun addManualPeerIp(ip: String) {
        discoveryManager.addManualPeer(ip, 8988, "Manual ($ip)")
    }

    fun scanSubnetNow() {
        wifiDirectManager.discoverPeers()
        wifiDirectManager.discoverServices()
        viewModelScope.launch {
            val ip = localIpAddress.value
            if (ip != "127.0.0.1") {
                discoveryManager.probeSubnet(ip)
            }
        }
    }

    fun cancelTransfer() {
        cancelAllActiveTransfers()
    }

    // Queue Management Methods
    fun toggleQueueExpanded(expanded: Boolean? = null) {
        _isQueueExpanded.value = expanded ?: !_isQueueExpanded.value
    }

    fun cancelQueuedTransfer(transferId: String) {
        val job = activeTransferJobs.remove(transferId)
        job?.cancel()
        _transferQueue.value = _transferQueue.value.filter { it.id != transferId && it.isActive }
        if (_transferQueue.value.isEmpty()) {
            dismissCompleted()
        }
    }

    fun cancelAllActiveTransfers() {
        activeTransferJobs.values.forEach { it.cancel() }
        activeTransferJobs.clear()
        transferJob?.cancel()
        transferJob = null
        dismissCompleted()
    }

    fun clearCompletedTransfers() {
        _transferQueue.value = _transferQueue.value.filter { it.isActive }
        if (_transferQueue.value.isEmpty() && _transferState.value is TransferState.Completed) {
            dismissCompleted()
        }
    }

    fun retryQueuedTransfer(transferId: String) {
        val item = _transferQueue.value.find { it.id == transferId } ?: return
        if (item.direction == TransferDirection.UPLOAD) {
            val peer = _nearbyPeers.value.find { it.name == item.peerName }
                ?: BeamPeer(id = "peer_${item.peerName}", name = item.peerName)
            val mediaItem = _mediaItems.value.find { it.title == item.fileName }
                ?: SharableItem(
                    id = item.id,
                    title = item.fileName,
                    subtitle = "Queued retry",
                    sizeBytes = item.fileSize,
                    mimeType = item.mimeType,
                    category = ShareCategory.DOCUMENTS,
                    uri = item.localUri
                )
            _transferQueue.value = _transferQueue.value.filter { it.id != transferId }
            executeTransfer(peer, listOf(mediaItem))
        }
    }

    fun retryHistoryRecord(record: TransferRecord) {
        val peer = _nearbyPeers.value.find { it.name.equals(record.peerDeviceName, ignoreCase = true) }
            ?: _nearbyPeers.value.firstOrNull()
            ?: BeamPeer(id = "peer_${record.peerDeviceName}", name = record.peerDeviceName)
        val mediaItem = _mediaItems.value.find { it.title == record.fileName }
            ?: SharableItem(
                id = "retry_${record.id}",
                title = record.fileName,
                subtitle = "Retry from History",
                sizeBytes = record.fileSize.coerceAtLeast(1024L),
                mimeType = "*/*",
                category = ShareCategory.DOCUMENTS,
                uri = record.localUri
            )
        executeTransfer(peer, listOf(mediaItem))
    }

    fun dismissCompleted() {
        _transferQueue.value = emptyList()
        _isQueueExpanded.value = false
        _transferState.value = TransferState.Idle
        _auraPathState.value = AuraPathState(isActive = false)
        _auraIntensity.value = 0f
        _isContactLocked.value = false
    }

    /**
     * Updates the discreet active progress indicator state and shared AuraPathState,
     * immediately clearing if progress >= 1f.
     */
    fun updateActiveTransferProgress(
        peer: BeamPeer,
        item: SharableItem,
        progress: Float,
        speedMbps: Float = 52.0f
    ) {
        val clamped = progress.coerceIn(0f, 1f)
        if (clamped >= 1f) {
            discoveryManager.broadcastAuraPathSync(
                _auraPathState.value.copy(isActive = false, migrationProgress = 1.0f),
                peer.ipAddress,
                "COMPLETED"
            )
            clearSelection()
            dismissCompleted()
            return
        }
        val bytesDone = (item.sizeBytes * clamped).toLong()
        _transferQueue.value = listOf(
            QueuedTransfer(
                id = "active_${item.id}",
                fileName = item.title,
                fileSize = item.sizeBytes,
                bytesTransferred = bytesDone,
                progress = clamped,
                speedMbps = speedMbps,
                direction = TransferDirection.UPLOAD,
                peerName = peer.name,
                status = QueueStatus.IN_PROGRESS
            )
        )
        _transferState.value = TransferState.Transferring(
            peer = peer,
            currentItem = item,
            currentItemIndex = 0,
            totalItemCount = 1,
            progress = clamped,
            speedMbps = speedMbps,
            bytesTransferred = bytesDone,
            totalBytes = item.sizeBytes,
            etaSeconds = 1
        )
        val syncState = AuraPathState(
            isActive = true,
            sessionId = "active_${item.id}",
            fileName = item.title,
            fileSizeBytes = item.sizeBytes,
            migrationProgress = clamped,
            speedMbps = speedMbps,
            isOutgoing = true,
            sourceDeviceName = deviceName,
            targetDeviceName = peer.name,
            targetPeerId = peer.id,
            sourceButtonOffset = measuredSourceButtonOffset,
            targetDeviceOffset = measuredTargetPeerOffsets[peer.id] ?: measuredTargetPeerOffsets.values.firstOrNull(),
            timestampMs = System.currentTimeMillis()
        )
        _auraPathState.value = syncState
        discoveryManager.broadcastAuraPathSync(syncState, peer.ipAddress, "MIGRATING")
    }

    fun deleteHistoryRecord(id: Long) {
        viewModelScope.launch {
            transferRepository.deleteRecord(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            transferRepository.clearAllHistory()
        }
    }

    fun toggleInfoDialog(show: Boolean) {
        _showInfoDialog.value = show
    }

    fun addCustomFile(name: String, sizeBytes: Long, mimeType: String, uri: String? = null) {
        val category = when {
            mimeType.startsWith("image/") -> ShareCategory.PHOTOS
            mimeType.startsWith("video/") -> ShareCategory.VIDEOS
            mimeType.startsWith("audio/") -> ShareCategory.AUDIO
            mimeType.contains("android.package-archive") -> ShareCategory.APPS
            else -> ShareCategory.DOCUMENTS
        }

        val newItem = SharableItem(
            id = "browsed_${UUID.randomUUID()}",
            title = name,
            subtitle = "Selected for Send",
            sizeBytes = sizeBytes.coerceAtLeast(1L),
            mimeType = mimeType,
            category = category,
            uri = uri,
            isSelected = true,
            dateAdded = "Staged"
        )

        _mediaItems.value = listOf(newItem) + _mediaItems.value
        _selectedCategory.value = category
        _auraIntensity.value = 0.45f
    }

    fun addTextSnippet(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        try {
            val file = File(getApplication<Application>().cacheDir, "Text_${System.currentTimeMillis() % 100000}.txt")
            file.writeText(trimmed)
            addCustomFile(
                name = file.name,
                sizeBytes = file.length().coerceAtLeast(1L),
                mimeType = "text/plain",
                uri = file.absolutePath
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to stage text snippet: ${e.message}")
        }
    }

    private fun loadInitialMedia() {
        _mediaItems.value = listOf(
            SharableItem(
                id = "p1",
                title = "Camera_Capture_Pro.jpg",
                subtitle = "Device Photo",
                sizeBytes = 8 * 1024 * 1024L,
                mimeType = "image/jpeg",
                category = ShareCategory.PHOTOS,
                isSelected = false
            ),
            SharableItem(
                id = "v1",
                title = "Recorded_Video_4K.mp4",
                subtitle = "Device Video",
                sizeBytes = 48 * 1024 * 1024L,
                mimeType = "video/mp4",
                category = ShareCategory.VIDEOS
            ),
            SharableItem(
                id = "d1",
                title = "Document_Presentation.pdf",
                subtitle = "Device PDF",
                sizeBytes = 14 * 1024 * 1024L,
                mimeType = "application/pdf",
                category = ShareCategory.DOCUMENTS
            ),
            SharableItem(
                id = "a1",
                title = "Audio_Recording_Master.wav",
                subtitle = "Device Audio",
                sizeBytes = 28 * 1024 * 1024L,
                mimeType = "audio/wav",
                category = ShareCategory.AUDIO
            )
        )
    }

    public override fun onCleared() {
        super.onCleared()
        discoveryManager.stop()
        wifiDirectManager.stop()
        transferEngine.stopServer()
        webShareServer.stop()
        sensorManager.stopListening()
        transferJob?.cancel()
    }
}
