package com.example.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import com.example.model.AuraPathState
import com.example.model.BeamPeer
import com.example.model.DeviceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class NetworkDiscoveryManager(
    private val context: Context,
    private val deviceName: String,
    val tcpPort: Int = 8988
) {
    private val tag = "BeamDropDiscovery"
    private val serviceType = "_beamdrop._tcp."
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    val localDeviceId: String = UUID.randomUUID().toString().take(8)
    private var multicastLock: WifiManager.MulticastLock? = null

    private val _discoveredPeers = MutableStateFlow<Map<String, BeamPeer>>(emptyMap())
    val discoveredPeers: StateFlow<Map<String, BeamPeer>> = _discoveredPeers.asStateFlow()

    private val _remoteAuraPathState = MutableStateFlow<AuraPathState?>(null)
    val remoteAuraPathState: StateFlow<AuraPathState?> = _remoteAuraPathState.asStateFlow()

    private val peerLastSeenMs = ConcurrentHashMap<String, Long>()
    private val lastUnicastReplyMs = ConcurrentHashMap<String, Long>()

    // Cached network topology to prevent expensive JNI NetworkInterface enumeration syscalls in loops
    @Volatile
    private var lastNetworkCacheMs: Long = 0L
    @Volatile
    private var cachedLocalIps: Set<String> = setOf("127.0.0.1", "0.0.0.0", "localhost")
    @Volatile
    private var cachedPrimaryIp: String? = null
    @Volatile
    private var cachedBroadcastAddresses: List<InetAddress> = emptyList()

    @Volatile
    private var isTransferInProgress: Boolean = false
    @Volatile
    private var isBackgrounded: Boolean = false
    @Volatile
    private var lastAuraSyncSentMs: Long = 0L

    private val _localIpAddress = MutableStateFlow("127.0.0.1")
    val localIpAddress: StateFlow<String> = _localIpAddress.asStateFlow()

    private var nsdRegistrationListener: NsdManager.RegistrationListener? = null
    private var nsdDiscoveryListener: NsdManager.DiscoveryListener? = null
    private var wifiNetworkCallback: ConnectivityManager.NetworkCallback? = null

    private var broadcastJob: Job? = null
    private var listenJob: Job? = null
    private var subnetScanJob: Job? = null
    private var udpSocket: DatagramSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun start() {
        isBackgrounded = false
        scope.launch {
            refreshNetworkCache(force = true)
            sendImmediateBeaconBurst()
        }
        registerWifiNetworkCallback()
        registerNsdService()
        startNsdDiscovery()
        startUdpListener()
        startUdpBeacon()
        startPeriodicMaintenance()
    }

    fun stop() {
        isBackgrounded = true
        sendDepartureByeAsync()
        unregisterWifiNetworkCallback()
        stopNsd()
        broadcastJob?.cancel()
        listenJob?.cancel()
        subnetScanJob?.cancel()
        try {
            udpSocket?.close()
        } catch (_: Exception) {}
        udpSocket = null
        releaseMulticastLock()
    }

    fun pauseBackgroundActivity() {
        isBackgrounded = true
        sendDepartureByeAsync()
        broadcastJob?.cancel()
        broadcastJob = null
        stopNsdDiscoveryOnly()
        releaseMulticastLock()
    }

    fun resumeBackgroundActivity() {
        if (!isBackgrounded) return
        isBackgrounded = false
        scope.launch {
            refreshNetworkCache(force = true)
            sendImmediateBeaconBurst()
        }
        startNsdDiscovery()
        if (broadcastJob?.isActive != true) {
            startUdpBeacon()
        }
    }

    fun setTransferActive(active: Boolean) {
        isTransferInProgress = active
    }

    private fun registerWifiNetworkCallback() {
        if (wifiNetworkCallback != null) return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scope.launch {
                    refreshNetworkCache(force = true)
                    if (!isBackgrounded) {
                        sendImmediateBeaconBurst()
                    }
                }
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                scope.launch {
                    refreshNetworkCache(force = true)
                    if (!isBackgrounded) {
                        sendImmediateBeaconBurst()
                    }
                }
            }

            override fun onLost(network: Network) {
                scope.launch {
                    cachedPrimaryIp = null
                    refreshNetworkCache(force = true)
                    if (cachedPrimaryIp == null || cachedPrimaryIp == "127.0.0.1") {
                        peerLastSeenMs.clear()
                        if (_discoveredPeers.value.isNotEmpty()) {
                            _discoveredPeers.value = emptyMap()
                        }
                    }
                }
            }
        }
        try {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            connectivityManager?.registerNetworkCallback(request, callback)
            wifiNetworkCallback = callback
        } catch (e: Exception) {
            Log.w(tag, "Unable to register Wi-Fi NetworkCallback: ${e.message}")
        }
    }

    private fun unregisterWifiNetworkCallback() {
        try {
            wifiNetworkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
        } catch (_: Exception) {}
        wifiNetworkCallback = null
    }

    private fun acquireMulticastLockBriefly() {
        try {
            if (multicastLock == null) {
                multicastLock = wifiManager?.createMulticastLock("BeamDropMulticastLock")?.apply {
                    setReferenceCounted(false)
                }
            }
            if (multicastLock?.isHeld != true) {
                multicastLock?.acquire()
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to acquire multicast lock: ${e.message}")
        }
    }

    private fun releaseMulticastLock() {
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (_: Exception) {}
    }

    @Synchronized
    private fun refreshNetworkCache(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastNetworkCacheMs < 30_000L && cachedPrimaryIp != null) {
            return
        }
        lastNetworkCacheMs = now

        val ips = mutableSetOf("127.0.0.1", "0.0.0.0", "localhost")
        val broadcasts = mutableSetOf<InetAddress>()
        var preferredIp: String? = null
        var fallbackIp: String? = null

        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces != null && interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp) continue
                val isLoopback = iface.isLoopback
                val ifaceName = iface.name.lowercase()

                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    val host = addr.hostAddress
                    if (host != null) {
                        val cleanHost = host.substringBefore("%")
                        ips.add(cleanHost)
                        if (!isLoopback && addr is Inet4Address && !cleanHost.startsWith("127.") && !cleanHost.startsWith("169.254.")) {
                            if (ifaceName.startsWith("wlan") || ifaceName.startsWith("wifi") || ifaceName.startsWith("eth") || ifaceName.startsWith("ap")) {
                                if (preferredIp == null) preferredIp = cleanHost
                            } else if (fallbackIp == null) {
                                fallbackIp = cleanHost
                            }
                        }
                    }
                }

                if (!isLoopback) {
                    for (interfaceAddress in iface.interfaceAddresses) {
                        val broadcast = interfaceAddress.broadcast
                        if (broadcast != null) {
                            broadcasts.add(broadcast)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Error refreshing network interfaces: ${e.message}")
        }

        val resolvedPrimary = preferredIp ?: fallbackIp
        if (resolvedPrimary != null) {
            cachedPrimaryIp = resolvedPrimary
            val ipFlow = _localIpAddress as MutableStateFlow<String>?
            if (ipFlow != null && ipFlow.value != resolvedPrimary) {
                ipFlow.value = resolvedPrimary
            }
            val octets = resolvedPrimary.split(".")
            if (octets.size == 4) {
                try {
                    val b0 = octets[0].toInt().toByte()
                    val b1 = octets[1].toInt().toByte()
                    val b2 = octets[2].toInt().toByte()
                    broadcasts.add(InetAddress.getByAddress(byteArrayOf(b0, b1, b2, 255.toByte())))
                } catch (_: Exception) {}
            }
        }
        try {
            broadcasts.add(
                InetAddress.getByAddress(
                    byteArrayOf(255.toByte(), 255.toByte(), 255.toByte(), 255.toByte())
                )
            )
        } catch (_: Exception) {}

        cachedLocalIps = ips
        cachedBroadcastAddresses = broadcasts.toList()
    }

    fun getAllLocalIpAddresses(): Set<String> {
        refreshNetworkCache(force = false)
        return cachedLocalIps
    }

    private fun isSelfIp(ip: String): Boolean {
        val clean = ip.trim()
        if (clean.isEmpty() || clean.startsWith("127.") || clean == "0.0.0.0") return true
        return getAllLocalIpAddresses().contains(clean)
    }

    // --- Android NSD (mDNS) Registration & Discovery ---
    private fun registerNsdService() {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "BeamDrop-${deviceName.replace(" ", "_")}-$localDeviceId"
            this.serviceType = this@NetworkDiscoveryManager.serviceType
            port = tcpPort
            setAttribute("deviceName", deviceName)
            setAttribute("deviceType", "PHONE")
            setAttribute("id", localDeviceId)
            setAttribute("port", tcpPort.toString())
        }

        nsdRegistrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo?) {
                Log.d(tag, "NSD service registered: ${info?.serviceName}")
            }
            override fun onRegistrationFailed(info: NsdServiceInfo?, errorCode: Int) {
                Log.w(tag, "NSD registration failed: $errorCode")
            }
            override fun onServiceUnregistered(info: NsdServiceInfo?) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo?, errorCode: Int) {}
        }

        try {
            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, nsdRegistrationListener)
        } catch (e: Exception) {
            Log.e(tag, "Error registering NSD service", e)
        }
    }

    private fun startNsdDiscovery() {
        if (nsdDiscoveryListener != null) return
        nsdDiscoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String?) {
                Log.d(tag, "NSD discovery started: $regType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                if (serviceInfo == null) return
                val sName = serviceInfo.serviceName ?: ""

                // STRICT SELF-FILTER: Ignore services created by this device
                if (sName.contains(localDeviceId)) {
                    return
                }

                try {
                    nsdManager?.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo?, errorCode: Int) {
                            Log.w(tag, "NSD resolve failed: $errorCode")
                        }

                        override fun onServiceResolved(resolvedInfo: NsdServiceInfo?) {
                            if (resolvedInfo == null) return
                            val hostIp = resolvedInfo.host?.hostAddress?.substringBefore("%") ?: return

                            if (isSelfIp(hostIp)) return

                            val resolvedPort = resolvedInfo.port.takeIf { it > 0 } ?: tcpPort
                            scope.launch {
                                val verified = verifyAndRegisterPeerViaTcp(hostIp, resolvedPort)
                                if (!verified) {
                                    val rawName = resolvedInfo.serviceName ?: ""
                                    val isComputer = rawName.contains("Windows", ignoreCase = true) ||
                                        rawName.contains("PC", ignoreCase = true) ||
                                        rawName.contains("Mac", ignoreCase = true) ||
                                        resolvedInfo.attributes?.get("deviceType")?.let {
                                            String(it, StandardCharsets.UTF_8).contains("COMPUTER", ignoreCase = true)
                                        } == true

                                    val cleanName = rawName
                                        .removePrefix("BeamDrop-")
                                        .replace("_", " ")
                                        .substringBeforeLast("-")
                                        .trim()

                                    addOrUpdatePeer(
                                        id = "nsd_$hostIp",
                                        name = cleanName.ifEmpty { if (isComputer) "Windows PC ($hostIp)" else "BeamDrop Device ($hostIp)" },
                                        ip = hostIp,
                                        port = resolvedPort,
                                        deviceType = if (isComputer) DeviceType.COMPUTER else DeviceType.PHONE
                                    )
                                }
                            }
                        }
                    })
                } catch (e: Exception) {
                    Log.w(tag, "Error initiating resolveService", e)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
                val rawServiceName = serviceInfo?.serviceName ?: return
                val cleanName = rawServiceName
                    .removePrefix("BeamDrop-")
                    .replace("_", " ")
                    .substringBeforeLast("-")
                    .trim()
                val trailingId = rawServiceName.substringAfterLast("-", "")
                removePeerByIdOrIp(
                    peerId = trailingId.takeIf { it.isNotBlank() },
                    peerIp = serviceInfo.host?.hostAddress?.substringBefore("%"),
                    peerName = cleanName.takeIf { it.isNotBlank() } ?: rawServiceName
                )
            }

            override fun onDiscoveryStopped(serviceType: String?) {}
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}
        }

        try {
            nsdManager?.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, nsdDiscoveryListener)
        } catch (e: Exception) {
            Log.e(tag, "Error starting NSD discovery", e)
        }
    }

    private fun stopNsdDiscoveryOnly() {
        try {
            nsdDiscoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
        } catch (_: Exception) {}
        nsdDiscoveryListener = null
    }

    private fun stopNsd() {
        try {
            nsdRegistrationListener?.let { nsdManager?.unregisterService(it) }
            nsdDiscoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
        } catch (e: Exception) {
            Log.w(tag, "Error stopping NSD", e)
        }
        nsdRegistrationListener = null
        nsdDiscoveryListener = null
    }

    // --- Targeted Subnet Broadcasts ---
    private fun getSubnetBroadcastAddresses(): List<InetAddress> {
        refreshNetworkCache(force = false)
        return cachedBroadcastAddresses
    }

    private fun sendUdpPacket(bytes: ByteArray, address: InetAddress, port: Int = 8989) {
        try {
            val activeSock = udpSocket
            if (activeSock != null && !activeSock.isClosed) {
                activeSock.send(DatagramPacket(bytes, bytes.size, address, port))
            } else {
                DatagramSocket().use { sock ->
                    sock.broadcast = true
                    sock.send(DatagramPacket(bytes, bytes.size, address, port))
                }
            }
        } catch (_: Exception) {}
    }

    private fun sendSingleDiscoveryBeacon() {
        try {
            val myIp = getPrimaryLocalIpAddress() ?: _localIpAddress.value
            val message = "BEAMDROP_BEACON|$localDeviceId|$deviceName|$myIp|$tcpPort|PHONE"
            val bytes = message.toByteArray(StandardCharsets.UTF_8)

            val targets = getSubnetBroadcastAddresses()
            for (target in targets) {
                sendUdpPacket(bytes, target, 8989)
            }
            for (peer in _discoveredPeers.value.values) {
                try {
                    sendUdpPacket(bytes, InetAddress.getByName(peer.ipAddress), 8989)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    private suspend fun sendImmediateBeaconBurst() {
        if (isBackgrounded || isTransferInProgress) return
        sendSingleDiscoveryBeacon()
        delay(550L)
        if (!isBackgrounded && !isTransferInProgress) {
            sendSingleDiscoveryBeacon()
        }
    }

    private fun sendDepartureByeAsync() {
        val knownPeers = _discoveredPeers.value.values.toList()
        val myIp = cachedPrimaryIp ?: _localIpAddress.value
        val byeMessage = "BEAMDROP_BYE|$localDeviceId|$myIp|$deviceName"
        val bytes = byeMessage.toByteArray(StandardCharsets.UTF_8)
        val targets = cachedBroadcastAddresses

        scope.launch {
            try {
                for (target in targets) {
                    sendUdpPacket(bytes, target, 8989)
                }
                for (peer in knownPeers) {
                    try {
                        sendUdpPacket(bytes, InetAddress.getByName(peer.ipAddress), 8989)
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }

    private fun startUdpBeacon() {
        broadcastJob?.cancel()
        broadcastJob = scope.launch {
            // Bounded 3-packet announcement burst (0s, 1.8s, 4.0s) then 0% idle radio/CPU usage.
            // Passive UDP listener + NSD callbacks handle all subsequent peer arrivals/departures.
            val delays = longArrayOf(0L, 1800L, 2200L)
            for (waitMs in delays) {
                if (!isActive || isBackgrounded) break
                if (waitMs > 0L) delay(waitMs)
                if (!isBackgrounded && !isTransferInProgress) {
                    sendSingleDiscoveryBeacon()
                }
            }
        }
    }

    private fun startUdpListener() {
        listenJob?.cancel()
        listenJob = scope.launch {
            try {
                udpSocket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(8989))
                    broadcast = true
                }

                val buffer = ByteArray(2048)
                while (isActive) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        udpSocket?.receive(packet)
                        val senderSocketIp = packet.address?.hostAddress?.substringBefore("%") ?: ""
                        val text = String(packet.data, 0, packet.length, StandardCharsets.UTF_8).trim()
                        val parts = text.split("|")
                        val command = parts.firstOrNull().orEmpty()

                        if (command == "BEAMDROP_BYE" || command == "BEAMDROP_OFFLINE" || command == "BEAMDROP_EXIT") {
                            // Format: BEAMDROP_BYE|<id>|<ip>|<name> or BEAMDROP_BYE|<name>|<ip>
                            val token1 = parts.getOrNull(1)
                            val token2 = parts.getOrNull(2)
                            val token3 = parts.getOrNull(3)
                            val byeIp = when {
                                !token2.isNullOrBlank() && token2.count { it == '.' } == 3 -> token2
                                !token1.isNullOrBlank() && token1.count { it == '.' } == 3 -> token1
                                else -> senderSocketIp
                            }
                            if (token1 != localDeviceId && !isSelfIp(byeIp)) {
                                removePeerByIdOrIp(
                                    peerId = token1,
                                    peerIp = byeIp.ifEmpty { senderSocketIp },
                                    peerName = token3 ?: token1
                                )
                            }
                        } else if (command == "BEAMDROP_BEACON" && parts.size >= 6) {
                            // Format: BEAMDROP_BEACON|<id>|<name>|<ip>|<port>|<type>
                            val peerId = parts[1]
                            val peerName = parts[2]
                            val rawIp = parts[3]
                            val peerIp = if (rawIp.isNotEmpty() && rawIp != "127.0.0.1" && rawIp != "0.0.0.0") rawIp else senderSocketIp
                            val peerPort = parts[4].toIntOrNull() ?: tcpPort
                            val typeToken = parts.getOrNull(5) ?: ""

                            if (peerId != localDeviceId && !isSelfIp(peerIp)) {
                                val devType = resolveDeviceType(typeToken, peerName)
                                val isNewPeer = addOrUpdatePeer(
                                    id = "udp_$peerId",
                                    name = peerName,
                                    ip = peerIp,
                                    port = peerPort,
                                    deviceType = devType
                                )
                                sendUnicastBeaconReplyIfNeeded(peerIp, force = isNewPeer)
                            }
                        } else if (command == "BEAMDROP_BEACON" && parts.size == 5) {
                            // 5-part Windows Format: BEAMDROP_BEACON|<name>|<ip>|<port>|<type>
                            val peerName = parts[1]
                            val rawIp = parts[2]
                            val peerIp = if (rawIp.isNotEmpty() && rawIp != "127.0.0.1" && rawIp != "0.0.0.0") rawIp else senderSocketIp
                            val peerPort = parts[3].toIntOrNull() ?: tcpPort
                            val typeToken = parts[4]

                            if (!isSelfIp(peerIp) && !peerName.equals(deviceName, ignoreCase = true)) {
                                val devType = resolveDeviceType(typeToken, peerName)
                                val isNewPeer = addOrUpdatePeer(
                                    id = "udp_$peerIp",
                                    name = peerName,
                                    ip = peerIp,
                                    port = peerPort,
                                    deviceType = devType
                                )
                                sendUnicastBeaconReplyIfNeeded(peerIp, force = isNewPeer)
                            }
                        } else if (command == "BEAMDROP_HELLO" && parts.size >= 3) {
                            // Legacy/Alternate Windows Format: BEAMDROP_HELLO|<name>|<port>|<type>|<id>
                            val peerName = parts[1]
                            val peerPort = parts[2].toIntOrNull() ?: tcpPort
                            val typeToken = parts.getOrNull(3) ?: "COMPUTER"
                            val peerId = parts.getOrNull(4) ?: "win_$senderSocketIp"

                            if (peerId != localDeviceId && !isSelfIp(senderSocketIp) && !peerName.equals(deviceName, ignoreCase = true)) {
                                val devType = resolveDeviceType(typeToken, peerName)
                                val isNewPeer = addOrUpdatePeer(
                                    id = "udp_$peerId",
                                    name = peerName,
                                    ip = senderSocketIp,
                                    port = peerPort,
                                    deviceType = devType
                                )
                                sendUnicastBeaconReplyIfNeeded(senderSocketIp, force = isNewPeer)
                            }
                        } else if (command == "BEAMDROP_CANCEL") {
                            if (!isSelfIp(senderSocketIp)) {
                                val senderName = parts.getOrNull(1).orEmpty().ifEmpty { "Remote Peer" }
                                _remoteAuraPathState.value = AuraPathState(
                                    isActive = false,
                                    sessionId = "cancel_${System.currentTimeMillis()}",
                                    sourceDeviceName = senderName,
                                    targetDeviceName = deviceName,
                                    stateToken = "CANCELLED",
                                    timestampMs = System.currentTimeMillis()
                                )
                            }
                        } else if (command == "BEAMDROP_AURA_PATH") {
                            if (!isSelfIp(senderSocketIp)) {
                                val parsedAura = AuraPathState.fromWirePacket(text, deviceName)
                                if (parsedAura != null) {
                                    val prev = _remoteAuraPathState.value
                                    if (prev == null ||
                                        parsedAura.stateToken == "CANCELLED" ||
                                        parsedAura.stateToken == "COMPLETED" ||
                                        prev.isActive != parsedAura.isActive ||
                                        prev.flowDirection != parsedAura.flowDirection ||
                                        kotlin.math.abs(prev.migrationProgress - parsedAura.migrationProgress) >= 0.02f
                                    ) {
                                        _remoteAuraPathState.value = parsedAura
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        if (!isActive) break
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "UDP Listener stopped: ${e.message}")
            }
        }
    }

    private fun sendUnicastBeaconReplyIfNeeded(targetIp: String, force: Boolean = false) {
        val now = System.currentTimeMillis()
        val lastSent = lastUnicastReplyMs[targetIp] ?: 0L
        if (!force && now - lastSent < 8_000L) return
        lastUnicastReplyMs[targetIp] = now

        scope.launch {
            try {
                val myIp = getPrimaryLocalIpAddress() ?: _localIpAddress.value
                val reply = "BEAMDROP_BEACON|$localDeviceId|$deviceName|$myIp|$tcpPort|PHONE"
                val bytes = reply.toByteArray(StandardCharsets.UTF_8)
                sendUdpPacket(bytes, InetAddress.getByName(targetIp), 8989)
            } catch (_: Exception) {}
        }
    }

    /**
     * Unicasts/broadcasts the shared AuraPathState frame over UDP 8989 with rate-limiting
     * so Windows and Android stay synchronized without flooding the Wi-Fi radio.
     */
    fun broadcastAuraPathSync(
        state: AuraPathState,
        targetIp: String? = null,
        stateToken: String = if (state.isActive) "MIGRATING" else "COMPLETED"
    ) {
        val now = System.currentTimeMillis()
        val isBoundaryEvent = stateToken == "START" || stateToken == "SWIPE" || stateToken == "COMPLETED" || stateToken == "CANCELLED" || !state.isActive
        if (!isBoundaryEvent && now - lastAuraSyncSentMs < 120L) {
            return
        }
        lastAuraSyncSentMs = now

        scope.launch {
            try {
                val wire = state.toWirePacket(stateToken)
                val bytes = wire.toByteArray(StandardCharsets.UTF_8)
                val knownPeerIps = _discoveredPeers.value.keys.toSet()

                fun dispatchOnce() {
                    if (!targetIp.isNullOrBlank() && targetIp != "127.0.0.1") {
                        try {
                            sendUdpPacket(bytes, InetAddress.getByName(targetIp), 8989)
                        } catch (_: Exception) {}
                    }
                    for (peerIp in knownPeerIps) {
                        if (peerIp != targetIp && peerIp.isNotBlank() && peerIp != "127.0.0.1") {
                            try {
                                sendUdpPacket(bytes, InetAddress.getByName(peerIp), 8989)
                            } catch (_: Exception) {}
                        }
                    }
                    if (isBoundaryEvent || targetIp.isNullOrBlank() || targetIp == "127.0.0.1") {
                        for (bcast in getSubnetBroadcastAddresses()) {
                            sendUdpPacket(bytes, bcast, 8989)
                        }
                    }
                }

                dispatchOnce()
                if (isBoundaryEvent) {
                    delay(45L)
                    dispatchOnce()
                }
            } catch (_: Exception) {}
        }
    }

    private fun resolveDeviceType(typeToken: String, peerName: String): DeviceType {
        return if (
            typeToken.equals("COMPUTER", ignoreCase = true) ||
            peerName.contains("Windows", ignoreCase = true) ||
            peerName.contains("PC", ignoreCase = true) ||
            peerName.contains("Desktop", ignoreCase = true) ||
            peerName.contains("Laptop", ignoreCase = true) ||
            peerName.contains("Mac", ignoreCase = true)
        ) {
            DeviceType.COMPUTER
        } else {
            DeviceType.PHONE
        }
    }

    /**
     * Event-Driven Active-Peer Liveness Check:
     * Performs a single verification pass after startup/network change rather than an infinite loop,
     * keeping idle CPU and Wi-Fi radio usage at 0%.
     */
    private fun startPeriodicMaintenance() {
        subnetScanJob?.cancel()
        subnetScanJob = scope.launch {
            delay(5000L)
            if (isActive && !isBackgrounded && !isTransferInProgress) {
                verifyActivePeersLiveness()
            }
        }
    }

    private suspend fun verifyActivePeersLiveness() {
        val snapshot = _discoveredPeers.value
        if (snapshot.isEmpty()) return

        val now = System.currentTimeMillis()
        for ((ip, peer) in snapshot) {
            if (isTransferInProgress || isBackgrounded) break
            val lastSeen = peerLastSeenMs[ip] ?: 0L
            val silenceMs = now - lastSeen
            if (silenceMs > 4500L) {
                val isStillOnline = checkKnownPeerAlive(ip, peer.port)
                if (isStillOnline) {
                    peerLastSeenMs[ip] = System.currentTimeMillis()
                } else {
                    removePeerByIdOrIp(peerId = peer.id, peerIp = ip)
                }
            }
        }
    }

    private suspend fun checkKnownPeerAlive(targetIp: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        val socket = Socket()
        try {
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(targetIp, port), 350)
            socket.soTimeout = 350
            val pingMsg = "BEAMDROP_PING|$localDeviceId|$deviceName|PHONE\n"
            val out = socket.getOutputStream()
            out.write(pingMsg.toByteArray(StandardCharsets.UTF_8))
            out.flush()
            return@withContext true
        } catch (_: Exception) {
            return@withContext false
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Immediately removes a peer by IP address, ID, or name when it exits BeamDrop or leaves Wi-Fi.
     */
    fun removePeerByIdOrIp(peerId: String? = null, peerIp: String? = null, peerName: String? = null): Boolean {
        val current = _discoveredPeers.value
        if (current.isEmpty()) return false

        val updated = current.toMutableMap()
        var removed = false

        if (!peerIp.isNullOrBlank() && updated.remove(peerIp) != null) {
            peerLastSeenMs.remove(peerIp)
            lastUnicastReplyMs.remove(peerIp)
            removed = true
        }

        val iterator = updated.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val candidate = entry.value
            val matchId = !peerId.isNullOrBlank() && (
                candidate.id.equals(peerId, ignoreCase = true) ||
                    candidate.id.endsWith("_$peerId", ignoreCase = true)
                )
            val matchName = !peerName.isNullOrBlank() && (
                candidate.name.equals(peerName, ignoreCase = true) ||
                    candidate.name.contains(peerName, ignoreCase = true)
                )
            if (matchId || matchName) {
                peerLastSeenMs.remove(entry.key)
                lastUnicastReplyMs.remove(entry.key)
                iterator.remove()
                removed = true
            }
        }

        if (removed) {
            _discoveredPeers.value = updated
            Log.d(tag, "Removed departed peer (id=$peerId, ip=$peerIp, name=$peerName)")
        }
        return removed
    }

    /**
     * Connects to targetIp:port and performs a strict BEAMDROP_PING -> BEAMDROP_PONG handshake.
     * Returns true ONLY if the remote host is a genuine BeamDrop instance (Android or Windows).
     */
    suspend fun verifyAndRegisterPeerViaTcp(targetIp: String, port: Int = tcpPort): Boolean = withContext(Dispatchers.IO) {
        if (isSelfIp(targetIp)) return@withContext false
        val socket = Socket()
        try {
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(targetIp, port), 240)
            socket.soTimeout = 450

            val pingMsg = "BEAMDROP_PING|$localDeviceId|$deviceName|PHONE\n"
            val out = socket.getOutputStream()
            out.write(pingMsg.toByteArray(StandardCharsets.UTF_8))
            out.flush()

            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val response = reader.readLine()?.trim() ?: ""
            if (response.startsWith("BEAMDROP_PONG|")) {
                val parts = response.split("|")
                val peerId = parts.getOrNull(1)?.ifEmpty { "peer_$targetIp" } ?: "peer_$targetIp"
                val peerName = parts.getOrNull(2)?.ifEmpty { "BeamDrop Device ($targetIp)" } ?: "BeamDrop Device ($targetIp)"
                val typeStr = parts.getOrNull(3) ?: "PHONE"

                if (peerId != localDeviceId) {
                    val devType = resolveDeviceType(typeStr, peerName)
                    addOrUpdatePeer(
                        id = "verified_$peerId",
                        name = peerName,
                        ip = targetIp,
                        port = port,
                        deviceType = devType
                    )
                    return@withContext true
                }
            }
        } catch (_: Exception) {
            // Host offline or not running BeamDrop
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {}
        }
        return@withContext false
    }

    suspend fun probeSubnet(localIp: String) = withContext(Dispatchers.IO) {
        refreshNetworkCache(force = true)
        acquireMulticastLockBriefly()
        try {
            // First send an immediate UDP broadcast burst so active peers reply in <10ms
            val myIp = getPrimaryLocalIpAddress() ?: localIp
            val message = "BEAMDROP_BEACON|$localDeviceId|$deviceName|$myIp|$tcpPort|PHONE"
            val bytes = message.toByteArray(StandardCharsets.UTF_8)
            for (target in getSubnetBroadcastAddresses()) {
                sendUdpPacket(bytes, target, 8989)
            }
            delay(350)
            // Verify liveness of any existing peers first
            verifyActivePeersLiveness()
            // Only perform TCP subnet sweep if no peers answered UDP broadcast
            if (_discoveredPeers.value.isNotEmpty()) return@withContext

            if (localIp.count { it == '.' } != 3) return@withContext
            val prefix = localIp.substringBeforeLast(".") + "."
            val myLastOctet = localIp.substringAfterLast(".").toIntOrNull() ?: -1

            val candidates = (1..254).filter { it != myLastOctet }
            val batches = candidates.chunked(24)

            for (batch in batches) {
                if (_discoveredPeers.value.isNotEmpty()) break
                val jobs = batch.map { octet ->
                    val targetIp = "$prefix$octet"
                    scope.launch {
                        verifyAndRegisterPeerViaTcp(targetIp, tcpPort)
                    }
                }
                jobs.forEach { it.join() }
            }
        } finally {
            releaseMulticastLock()
        }
    }

    fun registerHandshakePeer(peerId: String, peerName: String, peerIp: String, port: Int = tcpPort, deviceType: DeviceType) {
        if (!isSelfIp(peerIp) && peerId != localDeviceId) {
            addOrUpdatePeer(
                id = "handshake_$peerId",
                name = peerName,
                ip = peerIp,
                port = port,
                deviceType = deviceType
            )
        }
    }

    fun addManualPeer(ip: String, port: Int = tcpPort, fallbackName: String = "Wi-Fi Peer") {
        val cleanIp = ip.trim()
        if (cleanIp.isEmpty() || isSelfIp(cleanIp)) return
        scope.launch {
            val verified = verifyAndRegisterPeerViaTcp(cleanIp, port)
            if (!verified) {
                addOrUpdatePeer("manual_$cleanIp", "$fallbackName ($cleanIp)", cleanIp, port, DeviceType.PHONE)
            }
        }
    }

    /**
     * Adds or updates a discovered peer. Returns true if the peer was newly added or changed,
     * and avoids emitting duplicate StateFlow maps when nothing changed.
     */
    private fun addOrUpdatePeer(id: String, name: String, ip: String, port: Int, deviceType: DeviceType): Boolean {
        if (isSelfIp(ip)) return false

        peerLastSeenMs[ip] = System.currentTimeMillis()
        val existing = _discoveredPeers.value[ip]
        if (existing != null &&
            existing.name == name &&
            existing.port == port &&
            existing.deviceType == deviceType
        ) {
            return false
        }

        val current = _discoveredPeers.value.toMutableMap()
        current[ip] = BeamPeer(
            id = id,
            name = name,
            ipAddress = ip,
            port = port,
            deviceType = deviceType,
            signalStrengthDbm = -36,
            avatarTintHex = if (deviceType == DeviceType.COMPUTER) 0xFF9D4EDD else 0xFF00F5D4
        )
        _discoveredPeers.value = current
        Log.d(tag, "Registered verified peer: $name ($deviceType) at $ip:$port")
        return true
    }

    fun getPrimaryLocalIpAddress(): String? {
        refreshNetworkCache(force = false)
        return cachedPrimaryIp
    }
}
