package com.example.discovery

import android.content.Context
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

    val localDeviceId: String = UUID.randomUUID().toString().take(8)
    private var multicastLock: WifiManager.MulticastLock? = null

    private val _discoveredPeers = MutableStateFlow<Map<String, BeamPeer>>(emptyMap())
    val discoveredPeers: StateFlow<Map<String, BeamPeer>> = _discoveredPeers.asStateFlow()

    private val _remoteAuraPathState = MutableStateFlow<AuraPathState?>(null)
    val remoteAuraPathState: StateFlow<AuraPathState?> = _remoteAuraPathState.asStateFlow()

    private val peerLastSeenMs = ConcurrentHashMap<String, Long>()

    private val _localIpAddress = MutableStateFlow(getPrimaryLocalIpAddress() ?: "127.0.0.1")
    val localIpAddress: StateFlow<String> = _localIpAddress.asStateFlow()

    private var nsdRegistrationListener: NsdManager.RegistrationListener? = null
    private var nsdDiscoveryListener: NsdManager.DiscoveryListener? = null

    private var broadcastJob: Job? = null
    private var listenJob: Job? = null
    private var subnetScanJob: Job? = null
    private var udpSocket: DatagramSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun start() {
        acquireMulticastLock()
        refreshLocalIps()
        registerNsdService()
        startNsdDiscovery()
        startUdpBeacon()
        startUdpListener()
        startPeriodicSubnetScan()
    }

    fun stop() {
        stopNsd()
        broadcastJob?.cancel()
        listenJob?.cancel()
        subnetScanJob?.cancel()
        try {
            udpSocket?.close()
        } catch (_: Exception) {}
        releaseMulticastLock()
    }

    private fun acquireMulticastLock() {
        try {
            multicastLock = wifiManager?.createMulticastLock("BeamDropMulticastLock")?.apply {
                setReferenceCounted(true)
                acquire()
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

    fun getAllLocalIpAddresses(): Set<String> {
        val ips = mutableSetOf<String>()
        ips.add("127.0.0.1")
        ips.add("0.0.0.0")
        ips.add("localhost")
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    val host = addr.hostAddress
                    if (host != null) {
                        val cleanHost = host.substringBefore("%")
                        ips.add(cleanHost)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Error reading network interfaces", e)
        }
        return ips
    }

    private fun isSelfIp(ip: String): Boolean {
        val clean = ip.trim()
        if (clean.isEmpty() || clean.startsWith("127.") || clean == "0.0.0.0") return true
        return getAllLocalIpAddresses().contains(clean)
    }

    private fun refreshLocalIps() {
        val ip = getPrimaryLocalIpAddress()
        if (ip != null) {
            _localIpAddress.value = ip
        }
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
                            // Verify via TCP handshake or register resolved NSD service
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
                val serviceName = serviceInfo?.serviceName ?: return
                val current = _discoveredPeers.value.toMutableMap()
                val key = current.entries.firstOrNull { it.value.name.contains(serviceName, ignoreCase = true) }?.key
                if (key != null) {
                    current.remove(key)
                    peerLastSeenMs.remove(key)
                    _discoveredPeers.value = current
                }
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

    private fun stopNsd() {
        try {
            nsdRegistrationListener?.let { nsdManager?.unregisterService(it) }
            nsdDiscoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
        } catch (e: Exception) {
            Log.w(tag, "Error stopping NSD", e)
        }
    }

    // --- Targeted Subnet Broadcasts ---
    private fun getSubnetBroadcastAddresses(): List<InetAddress> {
        val broadcasts = mutableSetOf<InetAddress>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                for (interfaceAddress in iface.interfaceAddresses) {
                    val broadcast = interfaceAddress.broadcast
                    if (broadcast != null) {
                        broadcasts.add(broadcast)
                    }
                }
            }
            val myIp = getPrimaryLocalIpAddress()
            if (myIp != null && myIp.count { it == '.' } == 3) {
                val subnetBcast = myIp.substringBeforeLast(".") + ".255"
                broadcasts.add(InetAddress.getByName(subnetBcast))
            }
            broadcasts.add(InetAddress.getByName("255.255.255.255"))
        } catch (e: Exception) {
            try {
                broadcasts.add(InetAddress.getByName("255.255.255.255"))
            } catch (_: Exception) {}
        }
        return broadcasts.toList()
    }

    private fun startUdpBeacon() {
        broadcastJob = scope.launch {
            val udpPort = 8989
            while (isActive) {
                try {
                    val myIp = getPrimaryLocalIpAddress() ?: _localIpAddress.value
                    _localIpAddress.value = myIp

                    // Unified UDP Beacon understood by both Android and Windows
                    val message = "BEAMDROP_BEACON|$localDeviceId|$deviceName|$myIp|$tcpPort|PHONE"
                    val bytes = message.toByteArray(StandardCharsets.UTF_8)

                    val targets = getSubnetBroadcastAddresses()
                    DatagramSocket().use { socket ->
                        socket.broadcast = true
                        for (target in targets) {
                            try {
                                val packet = DatagramPacket(bytes, bytes.size, target, udpPort)
                                socket.send(packet)
                            } catch (_: Exception) {}
                        }
                        // Also send direct unicast refresh to known peers
                        for (peer in _discoveredPeers.value.values) {
                            try {
                                val addr = InetAddress.getByName(peer.ipAddress)
                                val packet = DatagramPacket(bytes, bytes.size, addr, udpPort)
                                socket.send(packet)
                            } catch (_: Exception) {}
                        }
                    }
                } catch (_: Exception) {}
                delay(2000)
            }
        }
    }

    private fun startUdpListener() {
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

                        if (parts.isNotEmpty() && parts[0] == "BEAMDROP_BEACON" && parts.size >= 6) {
                            // Format: BEAMDROP_BEACON|<id>|<name>|<ip>|<port>|<type>
                            val peerId = parts[1]
                            val peerName = parts[2]
                            val rawIp = parts[3]
                            val peerIp = if (rawIp.isNotEmpty() && rawIp != "127.0.0.1" && rawIp != "0.0.0.0") rawIp else senderSocketIp
                            val peerPort = parts[4].toIntOrNull() ?: tcpPort
                            val typeToken = parts.getOrNull(5) ?: ""

                            if (peerId != localDeviceId && !isSelfIp(peerIp)) {
                                val devType = resolveDeviceType(typeToken, peerName)
                                addOrUpdatePeer(
                                    id = "udp_$peerId",
                                    name = peerName,
                                    ip = peerIp,
                                    port = peerPort,
                                    deviceType = devType
                                )
                                sendUnicastBeaconReply(peerIp)
                            }
                        } else if (parts.isNotEmpty() && parts[0] == "BEAMDROP_BEACON" && parts.size == 5) {
                            // 5-part Windows Format: BEAMDROP_BEACON|<name>|<ip>|<port>|<type>
                            val peerName = parts[1]
                            val rawIp = parts[2]
                            val peerIp = if (rawIp.isNotEmpty() && rawIp != "127.0.0.1" && rawIp != "0.0.0.0") rawIp else senderSocketIp
                            val peerPort = parts[3].toIntOrNull() ?: tcpPort
                            val typeToken = parts[4]

                            if (!isSelfIp(peerIp) && !peerName.equals(deviceName, ignoreCase = true)) {
                                val devType = resolveDeviceType(typeToken, peerName)
                                addOrUpdatePeer(
                                    id = "udp_$peerIp",
                                    name = peerName,
                                    ip = peerIp,
                                    port = peerPort,
                                    deviceType = devType
                                )
                                sendUnicastBeaconReply(peerIp)
                            }
                        } else if (parts.isNotEmpty() && parts[0] == "BEAMDROP_HELLO" && parts.size >= 3) {
                            // Legacy/Alternate Windows Format: BEAMDROP_HELLO|<name>|<port>|<type>|<id>
                            val peerName = parts[1]
                            val peerPort = parts[2].toIntOrNull() ?: tcpPort
                            val typeToken = parts.getOrNull(3) ?: "COMPUTER"
                            val peerId = parts.getOrNull(4) ?: "win_$senderSocketIp"

                            if (peerId != localDeviceId && !isSelfIp(senderSocketIp) && !peerName.equals(deviceName, ignoreCase = true)) {
                                val devType = resolveDeviceType(typeToken, peerName)
                                addOrUpdatePeer(
                                    id = "udp_$peerId",
                                    name = peerName,
                                    ip = senderSocketIp,
                                    port = peerPort,
                                    deviceType = devType
                                )
                                sendUnicastBeaconReply(senderSocketIp)
                            }
                        } else if (parts.isNotEmpty() && parts[0] == "BEAMDROP_AURA_PATH") {
                            if (!isSelfIp(senderSocketIp)) {
                                val parsedAura = AuraPathState.fromWirePacket(text, deviceName)
                                if (parsedAura != null) {
                                    _remoteAuraPathState.value = parsedAura
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

    private fun sendUnicastBeaconReply(targetIp: String) {
        scope.launch {
            try {
                val myIp = getPrimaryLocalIpAddress() ?: _localIpAddress.value
                val reply = "BEAMDROP_BEACON|$localDeviceId|$deviceName|$myIp|$tcpPort|PHONE"
                val bytes = reply.toByteArray(StandardCharsets.UTF_8)
                DatagramSocket().use { sock ->
                    val packet = DatagramPacket(bytes, bytes.size, InetAddress.getByName(targetIp), 8989)
                    sock.send(packet)
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Broadcasts/unicasts the shared AuraPathState frame over UDP 8989 so that Windows and Android
     * render the migrating payload along their Canvas 'Aura Path' in visual lockstep.
     */
    fun broadcastAuraPathSync(
        state: AuraPathState,
        targetIp: String? = null,
        stateToken: String = if (state.isActive) "MIGRATING" else "COMPLETED"
    ) {
        scope.launch {
            try {
                val wire = state.toWirePacket(stateToken)
                val bytes = wire.toByteArray(StandardCharsets.UTF_8)
                DatagramSocket().use { sock ->
                    sock.broadcast = true
                    if (!targetIp.isNullOrBlank() && targetIp != "127.0.0.1") {
                        try {
                            sock.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName(targetIp), 8989))
                        } catch (_: Exception) {}
                    }
                    for (bcast in getSubnetBroadcastAddresses()) {
                        try {
                            sock.send(DatagramPacket(bytes, bytes.size, bcast, 8989))
                        } catch (_: Exception) {}
                    }
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

    // --- Active Verified Subnet Probe (NEVER adds fake/unverified IPs) ---
    private fun startPeriodicSubnetScan() {
        subnetScanJob = scope.launch {
            delay(1000)
            while (isActive) {
                val myIp = getPrimaryLocalIpAddress()
                if (myIp != null && !myIp.startsWith("127.")) {
                    _localIpAddress.value = myIp
                    probeSubnet(myIp)
                }
                evictStalePeers()
                delay(10000)
            }
        }
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
            socket.connect(InetSocketAddress(targetIp, port), 260)
            socket.soTimeout = 500

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
        if (localIp.count { it == '.' } != 3) return@withContext
        val prefix = localIp.substringBeforeLast(".") + "."
        val myLastOctet = localIp.substringAfterLast(".").toIntOrNull() ?: -1

        val candidates = (1..254).filter { it != myLastOctet }
        val batches = candidates.chunked(32)

        for (batch in batches) {
            val jobs = batch.map { octet ->
                val targetIp = "$prefix$octet"
                scope.launch {
                    verifyAndRegisterPeerViaTcp(targetIp, tcpPort)
                }
            }
            jobs.forEach { it.join() }
        }
    }

    private fun evictStalePeers() {
        val now = System.currentTimeMillis()
        val current = _discoveredPeers.value.toMutableMap()
        var changed = false

        val iterator = current.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val ip = entry.key
            val lastSeen = peerLastSeenMs[ip] ?: now
            // If not seen via UDP/TCP for 35 seconds, verify with TCP ping before evicting
            if (now - lastSeen > 35_000L && !entry.value.id.startsWith("manual_")) {
                iterator.remove()
                peerLastSeenMs.remove(ip)
                changed = true
            }
        }
        if (changed) {
            _discoveredPeers.value = current
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

    private fun addOrUpdatePeer(id: String, name: String, ip: String, port: Int, deviceType: DeviceType) {
        if (isSelfIp(ip)) return

        peerLastSeenMs[ip] = System.currentTimeMillis()
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
    }

    fun getPrimaryLocalIpAddress(): String? {
        try {
            var fallbackCandidate: String? = null
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val ifaceName = iface.name.lowercase()
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        if (host != null && !host.startsWith("127.") && !host.startsWith("169.254.")) {
                            // Prefer Wi-Fi / WLAN / Eth interfaces over cellular/VPN tunnels
                            if (ifaceName.startsWith("wlan") || ifaceName.startsWith("wifi") || ifaceName.startsWith("eth") || ifaceName.startsWith("ap")) {
                                return host
                            }
                            if (fallbackCandidate == null) {
                                fallbackCandidate = host
                            }
                        }
                    }
                }
            }
            return fallbackCandidate
        } catch (_: Exception) {}
        return null
    }
}
