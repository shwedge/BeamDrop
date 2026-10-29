package com.example.hardware

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import java.nio.charset.StandardCharsets

/**
 * Host-based Card Emulation (HCE) service that enables direct phone-to-phone NFC discovery
 * on Android 10+ when a compatible NFC device in ReaderMode taps this phone.
 */
class BeamHostApduService : HostApduService() {
    private val tag = "BeamHostApduService"

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null || commandApdu.size < 4) {
            return STATUS_FAILED
        }

        // 1. Check if this is the ISO-7816 SELECT AID command: 00 A4 04 00 07 F0 42 45 41 4D 44 52
        if (isSelectAidApdu(commandApdu)) {
            Log.d(tag, "NFC SELECT AID received from compatible peer device")
            val localPayload = NfcBeamHelper.activeLocalPayload
            val responseText = if (localPayload != null) {
                val macPart = localPayload.p2pMacAddress ?: ""
                "BEAMDROP|${localPayload.peerIp}|${localPayload.peerPort}|${localPayload.deviceName}|$macPart"
            } else {
                "BEAMDROP|127.0.0.1|8988|Android"
            }
            val payloadBytes = responseText.toByteArray(StandardCharsets.UTF_8)
            return payloadBytes + STATUS_SUCCESS
        }

        // 2. Check if this is the Peer Handshake Exchange APDU: 80 CA 00 00 <len> <utf8_payload>
        if (commandApdu[0] == 0x80.toByte() && commandApdu[1] == 0xCA.toByte() && commandApdu.size > 5) {
            val dataLen = commandApdu[4].toInt() and 0xFF
            if (commandApdu.size >= 5 + dataLen) {
                val rawString = String(commandApdu, 5, dataLen, StandardCharsets.UTF_8)
                val parsed = NfcBeamHelper.parseRawHandshakeText(rawString)
                Log.d(tag, "NFC HCE received peer handshake payload: $parsed")
                NfcBeamHelper.notifyHcePeerTapped(parsed)
                return STATUS_SUCCESS
            }
        }

        return STATUS_FAILED
    }

    override fun onDeactivated(reason: Int) {
        Log.d(tag, "HCE link deactivated (reason=$reason)")
    }

    private fun isSelectAidApdu(apdu: ByteArray): Boolean {
        if (apdu.size < 5 + AID_BYTES.size) return false
        if (apdu[0] != 0x00.toByte() || apdu[1] != 0xA4.toByte() || apdu[2] != 0x04.toByte()) {
            return false
        }
        for (i in AID_BYTES.indices) {
            if (apdu[5 + i] != AID_BYTES[i]) return false
        }
        return true
    }

    companion object {
        // AID: F0 42 45 41 4D 44 52 ("BEAMDR")
        val AID_BYTES = byteArrayOf(
            0xF0.toByte(),
            0x42.toByte(),
            0x45.toByte(),
            0x41.toByte(),
            0x4D.toByte(),
            0x44.toByte(),
            0x52.toByte()
        )

        val STATUS_SUCCESS = byteArrayOf(0x90.toByte(), 0x00.toByte())
        val STATUS_FAILED = byteArrayOf(0x6F.toByte(), 0x00.toByte())

        fun buildSelectAidApdu(): ByteArray {
            val header = byteArrayOf(
                0x00.toByte(), // CLA
                0xA4.toByte(), // INS (SELECT)
                0x04.toByte(), // P1 (By Name)
                0x00.toByte(), // P2
                AID_BYTES.size.toByte() // Lc
            )
            return header + AID_BYTES + byteArrayOf(0x00.toByte())
        }

        fun buildPeerExchangeApdu(payload: NfcBeamPayload): ByteArray {
            val macPart = payload.p2pMacAddress ?: ""
            val raw = "BEAMDROP|${payload.peerIp}|${payload.peerPort}|${payload.deviceName}|$macPart"
                .toByteArray(StandardCharsets.UTF_8)
            val capped = if (raw.size > 240) raw.copyOf(240) else raw
            val header = byteArrayOf(
                0x80.toByte(),
                0xCA.toByte(),
                0x00.toByte(),
                0x00.toByte(),
                capped.size.toByte()
            )
            return header + capped
        }
    }
}
