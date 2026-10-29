package com.example.hardware

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets

data class NfcBeamPayload(
    val peerIp: String,
    val peerPort: Int,
    val deviceName: String,
    val p2pMacAddress: String? = null
)

sealed class NfcDiscoveryEvent {
    data class CompatibleDeviceTapped(
        val payload: NfcBeamPayload?,
        val techList: List<String>,
        val tagIdHex: String
    ) : NfcDiscoveryEvent()

    data class AdapterStateChanged(val isEnabled: Boolean) : NfcDiscoveryEvent()
}

class NfcBeamHelper(private val context: Context) {
    private val tag = "NfcBeamHelper"

    val nfcAdapter: NfcAdapter? by lazy {
        try {
            NfcAdapter.getDefaultAdapter(context)
        } catch (e: Exception) {
            null
        }
    }

    val isNfcSupported: Boolean
        get() = nfcAdapter != null

    val isNfcEnabled: Boolean
        get() = nfcAdapter?.isEnabled == true

    private val _adapterEnabledFlow = MutableStateFlow(isNfcEnabled)
    val adapterEnabledFlow: StateFlow<Boolean> = _adapterEnabledFlow.asStateFlow()

    private val _discoveryEvents = MutableSharedFlow<NfcDiscoveryEvent>(extraBufferCapacity = 8)
    val discoveryEvents: SharedFlow<NfcDiscoveryEvent> = _discoveryEvents.asSharedFlow()

    private var adapterStateReceiverRegistered = false
    private var localPayloadProvider: (() -> NfcBeamPayload)? = null

    private val adapterStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == NfcAdapter.ACTION_ADAPTER_STATE_CHANGED) {
                val state = intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, NfcAdapter.STATE_OFF)
                val enabled = state == NfcAdapter.STATE_ON
                _adapterEnabledFlow.value = enabled
                _discoveryEvents.tryEmit(NfcDiscoveryEvent.AdapterStateChanged(enabled))
            }
        }
    }

    private val readerCallback = NfcAdapter.ReaderCallback { discoveredTag ->
        handleDiscoveredTag(discoveredTag)
    }

    init {
        registerHceListener()
        registerAdapterStateReceiver()
    }

    private fun registerHceListener() {
        hceTapCallback = { payload ->
            _discoveryEvents.tryEmit(
                NfcDiscoveryEvent.CompatibleDeviceTapped(
                    payload = payload,
                    techList = listOf(IsoDep::class.java.name),
                    tagIdHex = "HCE_PEER"
                )
            )
        }
    }

    private fun registerAdapterStateReceiver() {
        if (adapterStateReceiverRegistered) return
        try {
            val filter = IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
            context.applicationContext.registerReceiver(adapterStateReceiver, filter)
            adapterStateReceiverRegistered = true
        } catch (e: Exception) {
            Log.w(tag, "Unable to register NFC state receiver: ${e.message}")
        }
    }

    /**
     * Enables NFC Adapter ReaderMode discovery for instant peer-to-peer HCE and NDEF tag detection
     * when a compatible device is tapped.
     */
    fun enableReaderModeDiscovery(
        activity: Activity,
        payloadProvider: () -> NfcBeamPayload
    ) {
        localPayloadProvider = payloadProvider
        activeLocalPayload = payloadProvider()
        val adapter = nfcAdapter ?: return
        if (!adapter.isEnabled) return

        try {
            val flags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V
            val extras = Bundle().apply {
                putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250)
            }
            adapter.enableReaderMode(activity, readerCallback, flags, extras)
            Log.d(tag, "NFC ReaderMode discovery enabled for peer tap detection")
        } catch (e: Exception) {
            Log.w(tag, "Failed to enable NFC ReaderMode: ${e.message}")
        }
    }

    /**
     * Disables NFC Adapter ReaderMode when the activity pauses.
     */
    fun disableReaderModeDiscovery(activity: Activity) {
        val adapter = nfcAdapter ?: return
        try {
            adapter.disableReaderMode(activity)
        } catch (e: Exception) {
            Log.w(tag, "Failed to disable NFC ReaderMode: ${e.message}")
        }
    }

    /**
     * Processes a discovered NFC Tag or HCE peer device from ReaderMode or Intent dispatch,
     * verifying compatibility, exchanging BeamDrop handshakes, and emitting CompatibleDeviceTapped
     * to trigger the Aura Ripple animation.
     */
    fun handleDiscoveredTag(discoveredTag: Tag): NfcBeamPayload? {
        val techs = discoveredTag.techList?.toList().orEmpty()
        val tagIdHex = discoveredTag.id?.joinToString("") { "%02X".format(it) } ?: "NFC_TAP"
        Log.d(tag, "NFC Tag/Device tapped: id=$tagIdHex, techs=$techs")

        if (!isCompatibleNfcDeviceOrTag(techs)) {
            Log.d(tag, "Ignored incompatible NFC tag: $techs")
            return null
        }

        val currentLocal = localPayloadProvider?.invoke()?.also {
            activeLocalPayload = it
        }

        // 1. Try ISO-DEP Host Card Emulation (HCE) peer-to-peer handshake with another Android phone
        var resolvedPayload: NfcBeamPayload? = exchangeWithHcePeer(discoveredTag, currentLocal)

        // 2. Fallback to NDEF Tag read / write if not an HCE peer
        if (resolvedPayload == null) {
            resolvedPayload = readFromTag(discoveredTag)
            if (resolvedPayload == null && currentLocal != null) {
                writeBeamPayloadToTag(
                    tagObj = discoveredTag,
                    localIp = currentLocal.peerIp,
                    port = currentLocal.peerPort,
                    deviceName = currentLocal.deviceName,
                    p2pMac = currentLocal.p2pMacAddress
                )
            }
        }

        _discoveryEvents.tryEmit(
            NfcDiscoveryEvent.CompatibleDeviceTapped(
                payload = resolvedPayload,
                techList = techs,
                tagIdHex = tagIdHex
            )
        )
        return resolvedPayload
    }

    /**
     * Exchanges BeamDrop peer metadata over ISO-DEP with a tapped Android phone running BeamHostApduService.
     */
    private fun exchangeWithHcePeer(tagObj: Tag, localPayload: NfcBeamPayload?): NfcBeamPayload? {
        val isoDep = IsoDep.get(tagObj) ?: return null
        return try {
            isoDep.connect()
            isoDep.timeout = 1200

            // Step A: Select BeamDrop AID (F04245414D4452) to receive remote device's payload
            val selectResponse = isoDep.transceive(BeamHostApduService.buildSelectAidApdu())
            var remotePayload: NfcBeamPayload? = null
            if (isApduSuccess(selectResponse) && selectResponse.size > 2) {
                val body = selectResponse.copyOfRange(0, selectResponse.size - 2)
                val responseStr = String(body, StandardCharsets.UTF_8)
                remotePayload = parseRawHandshakeText(responseStr)
            }

            // Step B: Push our own local payload to the tapped phone so both devices trigger Aura Ripple & connect
            if (localPayload != null) {
                try {
                    isoDep.transceive(BeamHostApduService.buildPeerExchangeApdu(localPayload))
                } catch (_: Exception) {
                }
            }

            isoDep.close()
            remotePayload
        } catch (e: Exception) {
            Log.d(tag, "IsoDep HCE exchange not supported on tapped target: ${e.message}")
            try {
                isoDep.close()
            } catch (_: Exception) {
            }
            null
        }
    }

    private fun isApduSuccess(response: ByteArray?): Boolean {
        if (response == null || response.size < 2) return false
        val sw1 = response[response.size - 2]
        val sw2 = response[response.size - 1]
        return sw1 == 0x90.toByte() && sw2 == 0x00.toByte()
    }

    fun isCompatibleNfcDeviceOrTag(techList: List<String>): Boolean {
        if (techList.isEmpty()) return true
        val compatibleTechs = setOf(
            IsoDep::class.java.name,
            Ndef::class.java.name,
            NdefFormatable::class.java.name,
            NfcA::class.java.name,
            NfcB::class.java.name,
            NfcF::class.java.name,
            NfcV::class.java.name
        )
        return techList.any { it in compatibleTechs }
    }

    /**
     * Creates an NDEF message carrying the BeamDrop IP address, port, device name, and Wi-Fi Direct MAC
     */
    fun createBeamHandshakeMessage(localIp: String, port: Int, deviceName: String, p2pMac: String? = null): NdefMessage {
        val macPart = p2pMac ?: ""
        val payload = "BEAMDROP|$localIp|$port|$deviceName|$macPart".toByteArray(StandardCharsets.UTF_8)
        val mimeRecord = NdefRecord.createMime("application/vnd.com.example.beamdrop", payload)
        return NdefMessage(arrayOf(mimeRecord))
    }

    /**
     * Parses an incoming BeamDrop NDEF payload
     */
    fun parseHandshakePayload(ndefMessage: NdefMessage): NfcBeamPayload? {
        return try {
            val record = ndefMessage.records.firstOrNull() ?: return null
            val text = String(record.payload, StandardCharsets.UTF_8)
            parseRawHandshakeText(text)
        } catch (e: Exception) {
            Log.w(tag, "Failed to parse NDEF handshake", e)
            null
        }
    }

    /**
     * Reads NDEF messages from a scanned Tag
     */
    fun readFromTag(tagObj: Tag): NfcBeamPayload? {
        try {
            val ndef = Ndef.get(tagObj) ?: return null
            ndef.connect()
            val msg = ndef.ndefMessage
            ndef.close()
            if (msg != null) {
                return parseHandshakePayload(msg)
            }
        } catch (e: Exception) {
            Log.w(tag, "Error reading from tag: ${e.message}")
        }
        return null
    }

    /**
     * Writes current device's BeamDrop pairing payload onto a writable tag
     */
    fun writeBeamPayloadToTag(tagObj: Tag, localIp: String, port: Int, deviceName: String, p2pMac: String? = null): Boolean {
        try {
            val message = createBeamHandshakeMessage(localIp, port, deviceName, p2pMac)
            val ndef = Ndef.get(tagObj)
            if (ndef != null) {
                ndef.connect()
                if (!ndef.isWritable) {
                    ndef.close()
                    return false
                }
                ndef.writeNdefMessage(message)
                ndef.close()
                return true
            } else {
                val formatable = NdefFormatable.get(tagObj)
                if (formatable != null) {
                    formatable.connect()
                    formatable.format(message)
                    formatable.close()
                    return true
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Error writing NDEF message to tag: ${e.message}")
        }
        return false
    }

    companion object {
        @Volatile
        var activeLocalPayload: NfcBeamPayload? = null

        @Volatile
        private var hceTapCallback: ((NfcBeamPayload?) -> Unit)? = null

        fun notifyHcePeerTapped(payload: NfcBeamPayload?) {
            hceTapCallback?.invoke(payload)
        }

        fun parseRawHandshakeText(text: String): NfcBeamPayload? {
            val trimmed = text.trim()
            val parts = trimmed.split("|")
            return if (parts.size >= 4 && parts[0] == "BEAMDROP") {
                val mac = if (parts.size >= 5 && parts[4].isNotEmpty()) parts[4] else null
                NfcBeamPayload(
                    peerIp = parts[1],
                    peerPort = parts[2].toIntOrNull() ?: 8988,
                    deviceName = parts[3],
                    p2pMacAddress = mac
                )
            } else {
                null
            }
        }
    }
}
