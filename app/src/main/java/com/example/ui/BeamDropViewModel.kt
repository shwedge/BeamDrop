package com.example.ui

import android.app.Application
import android.net.Uri
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
import com.example.ui.theme.ActiveBeamThemeState
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ReceivedTextPayload(
    val senderName: String,
    val textContent: String,
    val timestampMs: Long = System.currentTimeMillis()
)

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

    private val prefs = application.getSharedPreferences("beamdrop_user_settings", android.content.Context.MODE_PRIVATE)

    val defaultDeviceName: String = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"

    private val _customDeviceName = MutableStateFlow(
        prefs.getString("custom_device_name", defaultDeviceName)?.takeIf { it.isNotBlank() } ?: defaultDeviceName
    )
    val customDeviceName: StateFlow<String> = _customDeviceName.asStateFlow()

    val deviceName: String
        get() = _customDeviceName.value

    // Settings State Flows (persisted in SharedPreferences)
    private val _themeMode = MutableStateFlow(prefs.getString("theme_mode", "System") ?: "System")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _colorTheme = MutableStateFlow(prefs.getString("color_theme", "LocalSend") ?: "LocalSend")
    val colorTheme: StateFlow<String> = _colorTheme.asStateFlow()

    private val _languageSetting = MutableStateFlow(prefs.getString("language_setting", "System") ?: "System")
    val languageSetting: StateFlow<String> = _languageSetting.asStateFlow()

    private val _minimizeToTray = MutableStateFlow(prefs.getBoolean("minimize_to_tray", false))
    val minimizeToTray: StateFlow<Boolean> = _minimizeToTray.asStateFlow()

    private val _autostartAfterLogin = MutableStateFlow(prefs.getBoolean("autostart_after_login", false))
    val autostartAfterLogin: StateFlow<Boolean> = _autostartAfterLogin.asStateFlow()

    private val _animationsEnabled = MutableStateFlow(prefs.getBoolean("animations_enabled", true))
    val animationsEnabled: StateFlow<Boolean> = _animationsEnabled.asStateFlow()

    private val _quickSaveEnabled = MutableStateFlow(prefs.getBoolean("quick_save", false))
    val quickSaveEnabled: StateFlow<Boolean> = _quickSaveEnabled.asStateFlow()

    private val _quickSaveFavorites = MutableStateFlow(prefs.getBoolean("quick_save_favorites", true))
    val quickSaveFavorites: StateFlow<Boolean> = _quickSaveFavorites.asStateFlow()

    private val _requirePinEnabled = MutableStateFlow(prefs.getBoolean("require_pin", false))
    val requirePinEnabled: StateFlow<Boolean> = _requirePinEnabled.asStateFlow()

    private val _autoFinishEnabled = MutableStateFlow(prefs.getBoolean("auto_finish", true))
    val autoFinishEnabled: StateFlow<Boolean> = _autoFinishEnabled.asStateFlow()

    private val _saveToHistoryEnabled = MutableStateFlow(prefs.getBoolean("save_to_history", true))
    val saveToHistoryEnabled: StateFlow<Boolean> = _saveToHistoryEnabled.asStateFlow()

    private val _advancedSettingsEnabled = MutableStateFlow(prefs.getBoolean("advanced_settings", false))
    val advancedSettingsEnabled: StateFlow<Boolean> = _advancedSettingsEnabled.asStateFlow()

    private val _isServerRunning = MutableStateFlow(true)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

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

                if (_saveToHistoryEnabled.value) {
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
                }

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

    private val _isNfcScanning = MutableStateFlow(false)
    val isNfcScanning: StateFlow<Boolean> = _isNfcScanning.asStateFlow()

    private val _showInfoDialog = MutableStateFlow(false)
    val showInfoDialog: StateFlow<Boolean> = _showInfoDialog.asStateFlow()

    private val _lastReceivedFile = MutableStateFlow<File?>(null)
    val lastReceivedFile: StateFlow<File?> = _lastReceivedFile.asStateFlow()

    private val _receivedTextPopup = MutableStateFlow<ReceivedTextPayload?>(null)
    val receivedTextPopup: StateFlow<ReceivedTextPayload?> = _receivedTextPopup.asStateFlow()

    private val _saveFolderDisplayName = MutableStateFlow(RealTransferEngine.getSaveFolderDisplayName(application))
    val saveFolderDisplayName: StateFlow<String> = _saveFolderDisplayName.asStateFlow()

    // Shared cross-platform Canvas 'Aura Path' animation state synchronized with Windows
    private val _auraPathState = MutableStateFlow(AuraPathState())
    val auraPathState: StateFlow<AuraPathState> = _auraPathState.asStateFlow()

    private var measuredSourceButtonOffset: Offset? = null
    private val measuredTargetPeerOffsets = ConcurrentHashMap<String, Offset>()
    private var pendingTargetPeer: BeamPeer? = null
    private var pendingFlowDirection: String = "TOP"
    private var activePeerIp: String? = null

    val isNfcReady: Boolean
        get() = nfcHelper.isNfcSupported

    val isProximityActive: Boolean = false

    private var transferJob: Job? = null
    private var nfcScanJob: Job? = null
    private var directionalWavePreviewJob: Job? = null

    init {
        ActiveBeamThemeState.update(_themeMode.value, _colorTheme.value)
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
                    if (remoteState.stateToken == "CANCELLED") {
                        transferEngine.cancelActiveTransfers()
                        transferJob?.cancel()
                        transferJob = null
                        activeTransferJobs.values.forEach { it.cancel() }
                        activeTransferJobs.clear()
                        dismissCompleted()
                        return@collect
                    }
                    val targetOffset = measuredTargetPeerOffsets.values.firstOrNull()
                    _auraPathState.value = remoteState.copy(
                        sourceButtonOffset = measuredSourceButtonOffset,
                        targetDeviceOffset = targetOffset
                    )
                    if (!remoteState.isActive && _transferState.value is TransferState.Idle) {
                        _auraIntensity.value = 0f
                    }
                }
            }
        }
    }

    fun onAppForegrounded() {
        discoveryManager.resumeBackgroundActivity()
    }

    fun onAppBackgrounded() {
        discoveryManager.pauseBackgroundActivity()
    }

    fun updateSourceButtonCoordinates(offset: Offset) {
        val prev = measuredSourceButtonOffset
        if (prev != null && kotlin.math.abs(prev.x - offset.x) < 8f && kotlin.math.abs(prev.y - offset.y) < 8f) {
            return
        }
        measuredSourceButtonOffset = offset
        if (_auraPathState.value.isActive) {
            _auraPathState.value = _auraPathState.value.copy(sourceButtonOffset = offset)
        }
    }

    fun updateTargetDeviceCoordinates(peerId: String, offset: Offset) {
        val prev = measuredTargetPeerOffsets[peerId]
        if (prev != null && kotlin.math.abs(prev.x - offset.x) < 8f && kotlin.math.abs(prev.y - offset.y) < 8f) {
            return
        }
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
                is IncomingTransferEvent.PeerLeft -> {
                    discoveryManager.removePeerByIdOrIp(
                        peerId = event.peerId,
                        peerIp = event.peerIp,
                        peerName = event.peerName
                    )
                }
                is IncomingTransferEvent.Started -> {
                    discoveryManager.setTransferActive(true)
                    activePeerIp = event.senderIp
                    _isContactLocked.value = false
                    _auraIntensity.value = 0f
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
                    _transferQueue.value = listOf(initialTransfer)

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
                        flowDirection = event.flowDirection,
                        stateToken = "START",
                        sourceButtonOffset = null,
                        targetDeviceOffset = measuredSourceButtonOffset,
                        timestampMs = System.currentTimeMillis()
                    )
                }
                is IncomingTransferEvent.Progress -> {
                    val activePeerName = _auraPathState.value.sourceDeviceName.ifEmpty { "Connected Device" }
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
                        ipAddress = activePeerIp.orEmpty(),
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
                        stateToken = "MIGRATING",
                        targetDeviceOffset = measuredSourceButtonOffset,
                        timestampMs = System.currentTimeMillis()
                    )
                }
                is IncomingTransferEvent.TextReceived -> {
                    discoveryManager.setTransferActive(false)
                    haptics.onTransferCompleted()
                    currentIncomingTransferId = null
                    _receivedTextPopup.value = ReceivedTextPayload(
                        senderName = event.senderName,
                        textContent = event.textContent
                    )
                    if (_saveToHistoryEnabled.value) {
                        viewModelScope.launch {
                            val snippetPreview = event.textContent.trim().replace("\n", " ").take(36).ifEmpty { "Plain Text" }
                            transferRepository.logTransfer(
                                TransferRecord(
                                    fileName = "Text: $snippetPreview",
                                    fileType = "PLAIN_TEXT",
                                    fileSize = event.textContent.toByteArray(Charsets.UTF_8).size.toLong().coerceAtLeast(1L),
                                    direction = "RECEIVED",
                                    peerDeviceName = event.senderName,
                                    durationMs = 150L,
                                    transferRateMbps = 65f,
                                    localUri = "beamtext://${event.textContent}",
                                    status = "COMPLETED"
                                )
                            )
                        }
                    }
                    dismissCompleted()
                }
                is IncomingTransferEvent.Completed -> {
                    discoveryManager.setTransferActive(false)
                    haptics.onTransferCompleted()
                    if (event.savedFiles.isNotEmpty()) {
                        _lastReceivedFile.value = event.savedFiles.firstOrNull()
                    }
                    currentIncomingTransferId = null

                    // Save to Room DB if Save to History is enabled
                    if (_saveToHistoryEnabled.value && event.savedFiles.isNotEmpty()) {
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
                    }

                    // Immediately clear active progress indicator and queue upon completion
                    dismissCompleted()
                }
                is IncomingTransferEvent.Cancelled -> {
                    discoveryManager.setTransferActive(false)
                    currentIncomingTransferId = null
                    dismissCompleted()
                }
                is IncomingTransferEvent.Failed -> {
                    discoveryManager.setTransferActive(false)
                    _isContactLocked.value = false
                    _auraIntensity.value = 0f
                    currentIncomingTransferId = null
                    _transferQueue.value = emptyList()

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
                val previous = _nearbyPeers.value
                val list = peerMap.values.toList()
                val currentP2p = previous.filter { it.id.startsWith("p2p_") }
                val merged = list + currentP2p
                if (previous != merged) {
                    _nearbyPeers.value = merged
                    if (merged.isNotEmpty() && previous.isEmpty()) {
                        haptics.onApproachTick()
                    }
                }
            }
        }
    }

    private fun observeWifiDirectPeers() {
        viewModelScope.launch {
            wifiDirectManager.servicePeers.collect { servicePeerMap ->
                val previous = _nearbyPeers.value
                val p2pPeers = servicePeerMap.values.toList()
                val networkPeers = previous.filter { !it.id.startsWith("p2p_") }
                val merged = networkPeers + p2pPeers
                if (previous != merged) {
                    _nearbyPeers.value = merged
                    if (merged.isNotEmpty() && previous.isEmpty()) {
                        haptics.onApproachTick()
                    }
                }
            }
        }
    }

    private fun loadDeviceMedia() {
        // Keep _mediaItems strictly empty on startup; only files explicitly chosen from storage by the user appear
        _mediaItems.value = emptyList()
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
    }

    fun clearSelection() {
        pendingTargetPeer = null
        _mediaItems.value = emptyList()
        if (_transferState.value is TransferState.Idle) {
            _auraIntensity.value = 0f
        }
    }

    fun toggleNfcScan() {
        if (_isNfcScanning.value) {
            dismissNfcScan()
        } else {
            _isNfcScanning.value = true
            haptics.onApproachTick()
            scanSubnetNow()
            nfcScanJob?.cancel()
            nfcScanJob = viewModelScope.launch {
                delay(6500L)
                _isNfcScanning.value = false
            }
        }
    }

    fun dismissNfcScan() {
        nfcScanJob?.cancel()
        nfcScanJob = null
        _isNfcScanning.value = false
    }

    private var lastSwipePreviewTimeMs: Long = 0L
    private var lastSwipeDirection: String = ""

    /**
     * Immediately triggers the directional Liquid Glass wave animation on BOTH the Android phone
     * and the connected Windows app (via UDP BEAMDROP_AURA_PATH) when the user swipes right or left on a peer.
     */
    fun triggerDirectionalGlassWave(peer: BeamPeer, flowDirection: String, isLiveDrag: Boolean = false) {
        val normalizedDirection = when (flowDirection.uppercase()) {
            "LEFT" -> "LEFT"
            "RIGHT" -> "RIGHT"
            else -> "TOP"
        }
        val now = System.currentTimeMillis()
        if (isLiveDrag && normalizedDirection == lastSwipeDirection && now - lastSwipePreviewTimeMs < 140L) {
            return
        }
        if (!isLiveDrag || normalizedDirection != lastSwipeDirection) {
            haptics.onApproachTick()
        }
        lastSwipePreviewTimeMs = now
        lastSwipeDirection = normalizedDirection

        val currentSessionId = if (_auraPathState.value.isActive && _auraPathState.value.stateToken == "SWIPE") {
            _auraPathState.value.sessionId
        } else {
            "swipe_${UUID.randomUUID().toString().take(6)}"
        }

        val waveState = AuraPathState(
            isActive = true,
            sessionId = currentSessionId,
            fileName = _mediaItems.value.firstOrNull { it.isSelected }?.title ?: "Liquid Glass Beam",
            fileSizeBytes = _mediaItems.value.filter { it.isSelected }.sumOf { it.sizeBytes },
            migrationProgress = if (isLiveDrag) 0.42f else 0.68f,
            speedMbps = 0f,
            isOutgoing = true,
            sourceDeviceName = deviceName,
            targetDeviceName = peer.name,
            targetPeerId = peer.id,
            flowDirection = normalizedDirection,
            stateToken = "SWIPE",
            sourceButtonOffset = measuredSourceButtonOffset,
            targetDeviceOffset = measuredTargetPeerOffsets[peer.id] ?: measuredTargetPeerOffsets.values.firstOrNull(),
            timestampMs = now
        )
        _auraPathState.value = waveState
        discoveryManager.broadcastAuraPathSync(waveState, peer.ipAddress, "SWIPE")

        directionalWavePreviewJob?.cancel()
        directionalWavePreviewJob = viewModelScope.launch {
            delay(2500L)
            if (_transferState.value is TransferState.Idle && _auraPathState.value.stateToken == "SWIPE") {
                _auraPathState.value = AuraPathState(isActive = false)
            }
        }
    }

    fun setPendingPeerForPicker(peer: BeamPeer?, flowDirection: String = "TOP") {
        pendingTargetPeer = peer
        pendingFlowDirection = flowDirection
        if (peer != null) {
            triggerDirectionalGlassWave(peer, flowDirection)
        }
    }

    fun triggerPendingTransferIfReady() {
        val target = pendingTargetPeer ?: return
        val direction = pendingFlowDirection
        val selectedFiles = _mediaItems.value.filter { it.isSelected }
        if (selectedFiles.isNotEmpty()) {
            pendingTargetPeer = null
            pendingFlowDirection = "TOP"
            executeTransfer(target, selectedFiles, direction)
        }
    }

    fun startPeerTransfer(peer: BeamPeer, flowDirection: String = "TOP") {
        triggerDirectionalGlassWave(peer, flowDirection)
        val selectedFiles = _mediaItems.value.filter { it.isSelected }
        val targetFiles = if (selectedFiles.isNotEmpty()) {
            selectedFiles
        } else {
            val first = _mediaItems.value.firstOrNull() ?: return
            listOf(first)
        }
        executeTransfer(peer, targetFiles, flowDirection)
    }

    /**
     * Triggers the live directional Liquid Glass wave even if no receiver was manually selected yet.
     */
    fun previewSwipeOnStagedCard(flowDirection: String) {
        val peer = pendingTargetPeer ?: _nearbyPeers.value.firstOrNull()
            ?: BeamPeer(
                id = "auto_receiver",
                name = "Nearby Receiver",
                ipAddress = "",
                deviceType = DeviceType.COMPUTER
            )
        triggerDirectionalGlassWave(peer, flowDirection, isLiveDrag = true)
    }

    /**
     * Automatically beams the staged file/folder/text when the user swipes >70% on the staged card,
     * without requiring the user to touch or select a receiver card first.
     */
    fun swipeToSendStagedItems(flowDirection: String = "RIGHT") {
        val selectedFiles = _mediaItems.value.filter { it.isSelected }
        val targetFiles = if (selectedFiles.isNotEmpty()) {
            selectedFiles
        } else {
            val first = _mediaItems.value.firstOrNull() ?: return
            listOf(first)
        }

        val immediatePeer = pendingTargetPeer ?: _nearbyPeers.value.firstOrNull()
        if (immediatePeer != null) {
            triggerDirectionalGlassWave(immediatePeer, flowDirection)
            executeTransfer(immediatePeer, targetFiles, flowDirection)
            return
        }

        // If no peer is in the list yet, ignite the directional swipe animation, probe the subnet immediately,
        // and auto-beam as soon as a receiver responds on the network!
        val placeholderPeer = BeamPeer(
            id = "auto_scan_peer",
            name = "Scanning Receiver...",
            ipAddress = "",
            deviceType = DeviceType.COMPUTER
        )
        triggerDirectionalGlassWave(placeholderPeer, flowDirection)
        scanSubnetNow()
        viewModelScope.launch {
            repeat(8) {
                delay(260L)
                val foundPeer = _nearbyPeers.value.firstOrNull()
                if (foundPeer != null) {
                    executeTransfer(foundPeer, targetFiles, flowDirection)
                    return@launch
                }
            }
        }
    }

    private fun executeTransfer(
        peer: BeamPeer,
        items: List<SharableItem>,
        flowDirection: String = "TOP"
    ) {
        transferJob?.cancel()
        directionalWavePreviewJob?.cancel()
        dismissNfcScan()
        discoveryManager.setTransferActive(true)
        activePeerIp = peer.ipAddress
        _isContactLocked.value = false
        _auraIntensity.value = 0f

        val normalizedDirection = when (flowDirection.uppercase()) {
            "LEFT" -> "LEFT"
            "RIGHT" -> "RIGHT"
            else -> "TOP"
        }

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
            flowDirection = normalizedDirection,
            stateToken = "START",
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
                status = QueueStatus.IN_PROGRESS,
                mimeType = item.mimeType,
                localUri = item.uri
            )
            qId to queued
        }

        _transferQueue.value = batchTransferPairs.map { it.second }

        transferJob = viewModelScope.launch {
            val thisJob = coroutineContext[Job]!!
            batchTransferPairs.forEach { (id, _) ->
                activeTransferJobs[id] = thisJob
            }

            val startTime = System.currentTimeMillis()
            var lastSpeed = 48.0f

            try {
                transferEngine.sendFiles(peer, items, deviceName, normalizedDirection).collect { progress ->
                    lastSpeed = progress.currentSpeedMbps

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
                        flowDirection = normalizedDirection,
                        stateToken = "MIGRATING",
                        sourceButtonOffset = measuredSourceButtonOffset,
                        targetDeviceOffset = measuredTargetPeerOffsets[peer.id] ?: measuredTargetPeerOffsets.values.firstOrNull(),
                        timestampMs = System.currentTimeMillis()
                    )
                    _auraPathState.value = updatedAura
                    discoveryManager.broadcastAuraPathSync(updatedAura, peer.ipAddress, "MIGRATING")
                }

                val durationMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1)

                if (_saveToHistoryEnabled.value) {
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
                }

                haptics.onTransferCompleted()
                discoveryManager.broadcastAuraPathSync(
                    _auraPathState.value.copy(isActive = false, migrationProgress = 1.0f, stateToken = "COMPLETED"),
                    peer.ipAddress,
                    "COMPLETED"
                )

                // Immediately remove browsed/staged files, clear queue, and self-dismiss progress indicator
                clearSelection()
                dismissCompleted()
            } catch (e: Exception) {
                Log.e(tag, "Failed to send files: ${e.message}", e)
                val isCancelled = e is kotlinx.coroutines.CancellationException
                val reasonStr = if (isCancelled) "Cancelled by user" else (e.message ?: "Connection failed to ${peer.name} (${peer.ipAddress})")

                _transferQueue.value = emptyList()
                _isContactLocked.value = false
                _auraIntensity.value = 0f
                _auraPathState.value = AuraPathState(isActive = false)
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
                discoveryManager.setTransferActive(false)
                batchTransferPairs.forEach { (id, _) -> activeTransferJobs.remove(id) }
            }
        }
    }

    fun addManualPeerIp(ip: String) {
        val cleaned = ip.trim().removePrefix("#")
        if (cleaned.isEmpty()) return
        val fullIp = if (!cleaned.contains(".") && localIpAddress.value.contains(".")) {
            localIpAddress.value.substringBeforeLast(".") + "." + cleaned
        } else {
            cleaned
        }
        discoveryManager.addManualPeer(fullIp, 8988, "Manual ($fullIp)")
    }

    /**
     * Connects to a peer via a scanned QR code payload, beamdrop:// URI, http:// web bridge URL, or IP/#Octet.
     * Supports both same-network direct TCP connection and cross-network Wi-Fi Hotspot / Web Bridge pairing.
     */
    fun connectViaQrCode(rawPayload: String): String {
        val trimmed = rawPayload.trim()
        if (trimmed.isEmpty()) return ""

        var extractedIp = ""
        var extractedPort = 8988
        var extractedName = "QR Connected Device"

        try {
            if (trimmed.startsWith("beamdrop://", ignoreCase = true) || trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                val uri = Uri.parse(trimmed)
                uri.getQueryParameter("ip")?.takeIf { it.isNotBlank() }?.let { extractedIp = it }
                if (extractedIp.isEmpty() && !uri.host.isNullOrBlank() && uri.host != "connect") {
                    extractedIp = uri.host!!
                }
                uri.getQueryParameter("port")?.toIntOrNull()?.let { extractedPort = it }
                uri.getQueryParameter("tcp")?.toIntOrNull()?.let { extractedPort = it }
                uri.getQueryParameter("name")?.takeIf { it.isNotBlank() }?.let { extractedName = it }
                uri.getQueryParameter("peer")?.takeIf { it.isNotBlank() }?.let { extractedName = it }
            } else if (trimmed.contains("|")) {
                // BEAMDROP_QR|ip|port|name
                val parts = trimmed.split("|")
                extractedIp = parts.firstOrNull { it.count { c -> c == '.' } == 3 }.orEmpty()
                extractedName = parts.lastOrNull { it.isNotBlank() && !it.all { c -> c.isDigit() } } ?: extractedName
            } else {
                val clean = trimmed.removePrefix("#")
                extractedIp = if (!clean.contains(".") && localIpAddress.value.contains(".")) {
                    localIpAddress.value.substringBeforeLast(".") + "." + clean
                } else {
                    clean
                }
            }
        } catch (_: Exception) {
            extractedIp = trimmed.removePrefix("#")
        }

        if (extractedIp.isBlank()) return ""

        val devType = if (extractedName.contains("Windows", ignoreCase = true) ||
            extractedName.contains("PC", ignoreCase = true) ||
            extractedName.contains("VICTUS", ignoreCase = true)
        ) {
            DeviceType.COMPUTER
        } else {
            DeviceType.PHONE
        }

        discoveryManager.registerHandshakePeer(
            peerId = "qr_$extractedIp",
            peerName = extractedName,
            peerIp = extractedIp,
            port = extractedPort,
            deviceType = devType
        )
        discoveryManager.addManualPeer(extractedIp, extractedPort, extractedName)

        val qrPeer = BeamPeer(
            id = "qr_$extractedIp",
            name = extractedName,
            ipAddress = extractedIp,
            port = extractedPort,
            deviceType = devType
        )
        triggerDirectionalGlassWave(qrPeer, "TOP")

        val staged = _mediaItems.value.filter { it.isSelected }
        if (staged.isNotEmpty()) {
            executeTransfer(qrPeer, staged, "TOP")
        }
        return extractedIp
    }

    fun setCustomSaveFolder(treeUri: String?, folderDisplayName: String) {
        RealTransferEngine.setCustomSaveFolder(getApplication(), treeUri, folderDisplayName)
        _saveFolderDisplayName.value = RealTransferEngine.getSaveFolderDisplayName(getApplication())
    }

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        ActiveBeamThemeState.update(mode, _colorTheme.value)
        prefs.edit().putString("theme_mode", mode).apply()
    }

    fun setColorTheme(color: String) {
        _colorTheme.value = color
        ActiveBeamThemeState.update(_themeMode.value, color)
        prefs.edit().putString("color_theme", color).apply()
    }

    fun setLanguageSetting(lang: String) {
        _languageSetting.value = lang
        prefs.edit().putString("language_setting", lang).apply()
    }

    fun setMinimizeToTray(enabled: Boolean) {
        _minimizeToTray.value = enabled
        prefs.edit().putBoolean("minimize_to_tray", enabled).apply()
    }

    fun setAutostartAfterLogin(enabled: Boolean) {
        _autostartAfterLogin.value = enabled
        prefs.edit().putBoolean("autostart_after_login", enabled).apply()
    }

    fun setAnimationsEnabled(enabled: Boolean) {
        _animationsEnabled.value = enabled
        prefs.edit().putBoolean("animations_enabled", enabled).apply()
        if (!enabled) {
            _auraIntensity.value = 0f
            _isContactLocked.value = false
            _auraPathState.value = AuraPathState(isActive = false)
        }
    }

    fun setQuickSaveEnabled(enabled: Boolean) {
        _quickSaveEnabled.value = enabled
        prefs.edit().putBoolean("quick_save", enabled).apply()
    }

    fun setQuickSaveFavorites(enabled: Boolean) {
        _quickSaveFavorites.value = enabled
        prefs.edit().putBoolean("quick_save_favorites", enabled).apply()
    }

    fun setRequirePinEnabled(enabled: Boolean) {
        _requirePinEnabled.value = enabled
        prefs.edit().putBoolean("require_pin", enabled).apply()
    }

    fun setAutoFinishEnabled(enabled: Boolean) {
        _autoFinishEnabled.value = enabled
        prefs.edit().putBoolean("auto_finish", enabled).apply()
    }

    fun setSaveToHistoryEnabled(enabled: Boolean) {
        _saveToHistoryEnabled.value = enabled
        prefs.edit().putBoolean("save_to_history", enabled).apply()
    }

    fun setAdvancedSettingsEnabled(enabled: Boolean) {
        _advancedSettingsEnabled.value = enabled
        prefs.edit().putBoolean("advanced_settings", enabled).apply()
    }

    fun updateCustomDeviceName(newName: String) {
        val cleaned = newName.trim().ifEmpty { defaultDeviceName }
        _customDeviceName.value = cleaned
        prefs.edit().putString("custom_device_name", cleaned).apply()
        restartNetworkAndServer()
    }

    fun restartNetworkAndServer() {
        viewModelScope.launch {
            haptics.onApproachTick()
            transferEngine.stopServer()
            webShareServer.stop()
            discoveryManager.stop()
            delay(180L)
            discoveryManager.start()
            startRealTransferServer()
            webShareServer.start()
            _isServerRunning.value = true
            scanSubnetNow()
        }
    }

    fun stopReceiveServer() {
        haptics.onApproachTick()
        transferEngine.stopServer()
        webShareServer.stop()
        _isServerRunning.value = false
    }

    fun dismissLastReceivedFile() {
        _lastReceivedFile.value = null
    }

    fun dismissReceivedTextPopup() {
        _receivedTextPopup.value = null
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
        transferEngine.cancelActiveTransfers()
        webShareServer.cancelActiveWebTransfers()
        val peerIp = activePeerIp
        discoveryManager.broadcastAuraPathSync(
            _auraPathState.value.copy(isActive = false, stateToken = "CANCELLED"),
            peerIp,
            "CANCELLED"
        )
        if (!peerIp.isNullOrBlank()) {
            transferEngine.sendCancelSignalToPeer(peerIp, 8988, deviceName)
        }
        _transferQueue.value = _transferQueue.value.filter { it.id != transferId && it.isActive }
        if (_transferQueue.value.isEmpty()) {
            dismissCompleted()
        }
    }

    fun cancelAllActiveTransfers() {
        val peerIp = activePeerIp ?: when (val st = _transferState.value) {
            is TransferState.Transferring -> st.peer.ipAddress
            is TransferState.HandshakeReady -> st.peer.ipAddress
            else -> null
        }
        val cancelledAura = _auraPathState.value.copy(isActive = false, stateToken = "CANCELLED")
        discoveryManager.broadcastAuraPathSync(cancelledAura, peerIp, "CANCELLED")
        if (!peerIp.isNullOrBlank()) {
            transferEngine.sendCancelSignalToPeer(peerIp, 8988, deviceName)
        }
        transferEngine.cancelActiveTransfers()
        webShareServer.cancelActiveWebTransfers()
        activeTransferJobs.values.forEach { it.cancel() }
        activeTransferJobs.clear()
        transferJob?.cancel()
        transferJob = null
        currentIncomingTransferId = null
        activePeerIp = null
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
            flowDirection = _auraPathState.value.flowDirection.ifBlank { "TOP" },
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
    }

    fun addTextSnippet(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val previewLine = trimmed.replace("\n", " ").take(48).ifEmpty { "Plain Text" }
        val utf8Bytes = trimmed.toByteArray(Charsets.UTF_8)
        val newItem = SharableItem(
            id = "browsed_text_${UUID.randomUUID()}",
            title = previewLine,
            subtitle = "Plain Text • Receiver Copy Pop-up",
            sizeBytes = utf8Bytes.size.toLong().coerceAtLeast(1L),
            mimeType = "text/plain;beamdrop-raw",
            category = ShareCategory.DOCUMENTS,
            uri = "beamtext://$trimmed",
            isSelected = true,
            dateAdded = "Plain Text"
        )
        _mediaItems.value = listOf(newItem) + _mediaItems.value
        _selectedCategory.value = ShareCategory.DOCUMENTS
        triggerPendingTransferIfReady()
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
