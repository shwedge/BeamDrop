package com.example.p2p

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.os.Build
import android.util.Log
import com.example.model.BeamPeer
import com.example.model.DeviceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

data class WifiDirectConnectionState(
    val isConnected: Boolean = false,
    val isGroupOwner: Boolean = false,
    val groupOwnerAddress: String? = null,
    val localDeviceName: String? = null,
    val localDeviceAddress: String? = null,
    val isServiceDiscoveryActive: Boolean = false
)

class WifiDirectManager(
    private val context: Context,
    private val localDeviceName: String = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
    private val transferPort: Int = 8988
) {
    companion object {
        const val SERVICE_INSTANCE = "_beamdrop"
        const val SERVICE_REG_TYPE = "_beamdrop._tcp"
        const val DEFAULT_GO_IP = "192.168.49.1"
    }

    private val tag = "WifiDirectManager"
    private val manager: WifiP2pManager? = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null
    private var isReceiverRegistered = false
    private var isDnsSdListenersConfigured = false

    private val _isP2pEnabled = MutableStateFlow(false)
    val isP2pEnabled: StateFlow<Boolean> = _isP2pEnabled.asStateFlow()

    private val _p2pPeers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val p2pPeers: StateFlow<List<WifiP2pDevice>> = _p2pPeers.asStateFlow()

    // Real-time peers discovered via Wi-Fi Direct DNS-SD service discovery and peer broadcasts
    private val _servicePeers = MutableStateFlow<Map<String, BeamPeer>>(emptyMap())
    val servicePeers: StateFlow<Map<String, BeamPeer>> = _servicePeers.asStateFlow()

    private val _connectionState = MutableStateFlow(WifiDirectConnectionState())
    val connectionState: StateFlow<WifiDirectConnectionState> = _connectionState.asStateFlow()

    private var onConnectionInfoListener: ((WifiP2pInfo) -> Unit)? = null

    // Cache of TXT records received via DnsSdTxtRecordListener keyed by device MAC address
    private val peerTxtRecords = ConcurrentHashMap<String, Map<String, String>>()

    val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent != null) {
                handleBroadcastIntent(intent)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun handleBroadcastIntent(intent: Intent) {
        val action = intent.action ?: return
        when (action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                val enabled = (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED)
                _isP2pEnabled.value = enabled
                Log.d(tag, "Wi-Fi P2P state changed: enabled=$enabled")
                if (!enabled) {
                    _p2pPeers.value = emptyList()
                    _servicePeers.value = emptyMap()
                    _connectionState.value = _connectionState.value.copy(
                        isConnected = false,
                        groupOwnerAddress = null,
                        isServiceDiscoveryActive = false
                    )
                }
            }

            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                val inlineList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(WifiP2pManager.EXTRA_P2P_DEVICE_LIST, WifiP2pDeviceList::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(WifiP2pManager.EXTRA_P2P_DEVICE_LIST)
                }
                if (inlineList != null) {
                    updatePeerList(inlineList.deviceList.toList())
                }
                try {
                    if (manager != null && channel != null) {
                        manager.requestPeers(channel) { peerList: WifiP2pDeviceList? ->
                            val peers = peerList?.deviceList?.toList() ?: emptyList()
                            updatePeerList(peers)
                            Log.d(tag, "Wi-Fi P2P peers discovered: ${peers.size}")
                        }
                    }
                } catch (e: SecurityException) {
                    Log.w(tag, "Missing permission to request P2P peers: ${e.message}")
                }
            }

            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                val networkInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO, NetworkInfo::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)
                }

                val p2pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO, WifiP2pInfo::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO)
                }

                if (p2pInfo != null && p2pInfo.groupFormed) {
                    val host = p2pInfo.groupOwnerAddress?.hostAddress ?: DEFAULT_GO_IP
                    _connectionState.value = _connectionState.value.copy(
                        isConnected = true,
                        isGroupOwner = p2pInfo.isGroupOwner,
                        groupOwnerAddress = host
                    )
                    onConnectionInfoListener?.invoke(p2pInfo)
                } else if (networkInfo?.isConnected == true) {
                    manager?.requestConnectionInfo(channel) { info: WifiP2pInfo? ->
                        if (info != null && info.groupFormed) {
                            val host = info.groupOwnerAddress?.hostAddress ?: DEFAULT_GO_IP
                            _connectionState.value = _connectionState.value.copy(
                                isConnected = true,
                                isGroupOwner = info.isGroupOwner,
                                groupOwnerAddress = host
                            )
                            Log.d(tag, "Wi-Fi Direct connected! GO IP: $host, isGO: ${info.isGroupOwner}")
                            onConnectionInfoListener?.invoke(info)
                        }
                    }
                } else {
                    _connectionState.value = _connectionState.value.copy(
                        isConnected = false,
                        groupOwnerAddress = null
                    )
                }
            }

            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE, WifiP2pDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                }
                if (device != null) {
                    _connectionState.value = _connectionState.value.copy(
                        localDeviceName = device.deviceName,
                        localDeviceAddress = device.deviceAddress
                    )
                }
            }
        }
    }

    private fun updatePeerList(peers: List<WifiP2pDevice>) {
        _p2pPeers.value = peers
        val updatedMap = _servicePeers.value.toMutableMap()
        peers.forEach { device ->
            val addr = device.deviceAddress ?: return@forEach
            val key = "p2p_$addr"
            val txt = peerTxtRecords[addr]
            val resolvedName = txt?.get("device_name")?.takeIf { it.isNotBlank() }
                ?: device.deviceName?.takeIf { it.isNotBlank() }
                ?: "Wi-Fi Direct Peer"
            val resolvedPort = txt?.get("listen_port")?.toIntOrNull() ?: transferPort
            val typeStr = txt?.get("device_type") ?: ""
            val devType = when {
                typeStr.equals("COMPUTER", ignoreCase = true) ||
                    resolvedName.contains("Windows", ignoreCase = true) ||
                    resolvedName.contains("PC", ignoreCase = true) -> DeviceType.COMPUTER
                typeStr.equals("TABLET", ignoreCase = true) -> DeviceType.TABLET
                else -> DeviceType.PHONE
            }

            updatedMap[key] = BeamPeer(
                id = key,
                name = if (resolvedName.contains("Wi-Fi Direct")) resolvedName else "$resolvedName (Wi-Fi Direct)",
                ipAddress = _connectionState.value.groupOwnerAddress ?: DEFAULT_GO_IP,
                port = resolvedPort,
                deviceType = devType,
                signalStrengthDbm = -32
            )
        }
        _servicePeers.value = updatedMap
    }

    /**
     * Processes a discovered DNS-SD service & TXT record from a nearby BeamDrop peer.
     */
    fun onDnsSdServiceDiscovered(
        deviceAddress: String,
        deviceName: String?,
        txtRecord: Map<String, String> = emptyMap()
    ) {
        if (deviceAddress.isBlank()) return
        if (txtRecord.isNotEmpty()) {
            peerTxtRecords[deviceAddress] = txtRecord
        }
        val cachedTxt = peerTxtRecords[deviceAddress] ?: txtRecord
        val resolvedName = cachedTxt["device_name"]?.takeIf { it.isNotBlank() }
            ?: deviceName?.takeIf { it.isNotBlank() }
            ?: "BeamDrop Peer"
        val resolvedPort = cachedTxt["listen_port"]?.toIntOrNull() ?: transferPort
        val resolvedIp = cachedTxt["ip_address"]?.takeIf { it.isNotBlank() }
            ?: _connectionState.value.groupOwnerAddress
            ?: DEFAULT_GO_IP
        val typeStr = cachedTxt["device_type"] ?: ""
        val devType = when {
            typeStr.equals("COMPUTER", ignoreCase = true) ||
                resolvedName.contains("Windows", ignoreCase = true) ||
                resolvedName.contains("PC", ignoreCase = true) -> DeviceType.COMPUTER
            typeStr.equals("TABLET", ignoreCase = true) -> DeviceType.TABLET
            else -> DeviceType.PHONE
        }

        val key = "p2p_$deviceAddress"
        val peer = BeamPeer(
            id = key,
            name = resolvedName,
            ipAddress = resolvedIp,
            port = resolvedPort,
            deviceType = devType,
            signalStrengthDbm = -28
        )
        val updated = _servicePeers.value.toMutableMap()
        updated[key] = peer
        _servicePeers.value = updated
    }

    fun start() {
        if (manager != null) {
            try {
                channel = manager.initialize(context, context.mainLooper) {
                    Log.w(tag, "Wi-Fi P2P Channel disconnected")
                }
            } catch (e: Exception) {
                Log.w(tag, "Wi-Fi P2P initialize unavailable: ${e.message}")
            }
        }

        if (!isReceiverRegistered) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(receiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    context.registerReceiver(receiver, intentFilter)
                }
                isReceiverRegistered = true
            } catch (e: Exception) {
                Log.w(tag, "Failed to register Wi-Fi Direct receiver: ${e.message}")
            }
        }

        setupServiceDiscoveryListeners()
    }

    fun stop() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
            isReceiverRegistered = false
        }
        try {
            if (manager != null && channel != null) {
                manager.clearLocalServices(channel, null)
                manager.clearServiceRequests(channel, null)
            }
        } catch (_: Exception) {}
        removeGroup()
    }

    /**
     * Registers BeamDrop's local DNS-SD service (_beamdrop._tcp) over Wi-Fi Direct so nearby
     * devices can discover this device's name, TCP port, and protocol metadata in real time.
     */
    @SuppressLint("MissingPermission")
    fun registerLocalService(customIp: String? = null) {
        val mgr = manager ?: return
        val ch = channel ?: return
        try {
            val record = mutableMapOf(
                "device_name" to localDeviceName,
                "listen_port" to transferPort.toString(),
                "device_type" to "PHONE",
                "protocol" to "BEAMDROP_V1"
            )
            if (!customIp.isNullOrBlank() && customIp != "127.0.0.1") {
                record["ip_address"] = customIp
            }
            val serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(
                SERVICE_INSTANCE,
                SERVICE_REG_TYPE,
                record
            )
            mgr.clearLocalServices(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    try {
                        mgr.addLocalService(ch, serviceInfo, object : WifiP2pManager.ActionListener {
                            override fun onSuccess() {
                                Log.d(tag, "Wi-Fi Direct DNS-SD local service registered ($SERVICE_REG_TYPE)")
                            }
                            override fun onFailure(reason: Int) {
                                Log.w(tag, "Failed to add Wi-Fi Direct local service: $reason")
                            }
                        })
                    } catch (e: SecurityException) {
                        Log.w(tag, "Missing permission for addLocalService: ${e.message}")
                    }
                }
                override fun onFailure(reason: Int) {}
            })
        } catch (e: Exception) {
            Log.w(tag, "registerLocalService unavailable: ${e.message}")
        }
    }

    /**
     * Configures DNS-SD response listeners (Service + TXT record) on WifiP2pManager.
     */
    private fun setupServiceDiscoveryListeners() {
        val mgr = manager ?: return
        val ch = channel ?: return
        if (isDnsSdListenersConfigured) return
        try {
            val servListener = WifiP2pManager.DnsSdServiceResponseListener { instanceName, registrationType, srcDevice ->
                Log.d(tag, "DNS-SD Service discovered: $instanceName ($registrationType) from ${srcDevice.deviceName}")
                if (instanceName.contains("beamdrop", ignoreCase = true) ||
                    registrationType.contains("_beamdrop", ignoreCase = true)
                ) {
                    onDnsSdServiceDiscovered(
                        deviceAddress = srcDevice.deviceAddress ?: "",
                        deviceName = srcDevice.deviceName
                    )
                }
            }

            val txtListener = WifiP2pManager.DnsSdTxtRecordListener { fullDomain, record, device ->
                Log.d(tag, "DNS-SD TXT record from ${device.deviceName} ($fullDomain): $record")
                if (fullDomain.contains("beamdrop", ignoreCase = true) || record.containsKey("device_name")) {
                    onDnsSdServiceDiscovered(
                        deviceAddress = device.deviceAddress ?: "",
                        deviceName = device.deviceName,
                        txtRecord = record
                    )
                }
            }

            mgr.setDnsSdResponseListeners(ch, servListener, txtListener)
            isDnsSdListenersConfigured = true
        } catch (e: Exception) {
            Log.w(tag, "setupServiceDiscoveryListeners failed: ${e.message}")
        }
    }

    /**
     * Initiates real-time Wi-Fi Direct DNS-SD service discovery on the local wireless medium.
     */
    @SuppressLint("MissingPermission")
    fun discoverServices() {
        val mgr = manager ?: return
        val ch = channel ?: return
        setupServiceDiscoveryListeners()
        try {
            val serviceRequest = WifiP2pDnsSdServiceRequest.newInstance()
            mgr.clearServiceRequests(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    mgr.addServiceRequest(ch, serviceRequest, object : WifiP2pManager.ActionListener {
                        override fun onSuccess() {
                            try {
                                mgr.discoverServices(ch, object : WifiP2pManager.ActionListener {
                                    override fun onSuccess() {
                                        _connectionState.value = _connectionState.value.copy(
                                            isServiceDiscoveryActive = true
                                        )
                                        Log.d(tag, "Wi-Fi Direct DNS-SD service discovery started")
                                    }
                                    override fun onFailure(reason: Int) {
                                        Log.w(tag, "Wi-Fi Direct discoverServices failed: $reason")
                                    }
                                })
                            } catch (e: SecurityException) {
                                Log.w(tag, "Missing permission for discoverServices: ${e.message}")
                            }
                        }
                        override fun onFailure(reason: Int) {
                            Log.w(tag, "addServiceRequest failed: $reason")
                        }
                    })
                }
                override fun onFailure(reason: Int) {}
            })
        } catch (e: Exception) {
            Log.w(tag, "discoverServices unavailable: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun discoverPeers() {
        if (manager == null || channel == null) return
        try {
            manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(tag, "Wi-Fi Direct peer discovery initiated")
                }
                override fun onFailure(reason: Int) {
                    Log.w(tag, "Wi-Fi Direct peer discovery failed: $reason")
                }
            })
        } catch (e: Exception) {
            Log.w(tag, "discoverPeers unavailable: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(deviceAddress: String, onConnected: (String) -> Unit) {
        if (manager == null || channel == null) return
        val config = WifiP2pConfig().apply {
            this.deviceAddress = deviceAddress
        }

        onConnectionInfoListener = { info ->
            val ip = info.groupOwnerAddress?.hostAddress ?: DEFAULT_GO_IP
            onConnected(ip)
        }

        try {
            manager.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(tag, "Wi-Fi Direct connection initiated to $deviceAddress")
                }
                override fun onFailure(reason: Int) {
                    Log.e(tag, "Wi-Fi Direct connection failed: $reason")
                }
            })
        } catch (e: Exception) {
            Log.e(tag, "Error connecting P2P", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun createAutonomousGroup(onGroupCreated: (String) -> Unit) {
        if (manager == null || channel == null) return

        onConnectionInfoListener = { info ->
            val ip = info.groupOwnerAddress?.hostAddress ?: DEFAULT_GO_IP
            onGroupCreated(ip)
        }

        try {
            manager.createGroup(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(tag, "Wi-Fi Direct autonomous group created successfully")
                }
                override fun onFailure(reason: Int) {
                    Log.w(tag, "Failed to create Wi-Fi Direct group: $reason")
                }
            })
        } catch (e: Exception) {
            Log.e(tag, "Error creating P2P group", e)
        }
    }

    fun removeGroup() {
        if (manager == null || channel == null) return
        try {
            manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(tag, "Wi-Fi Direct group removed")
                }
                override fun onFailure(reason: Int) {}
            })
        } catch (_: Exception) {}
    }
}
