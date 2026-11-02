package com.example

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.tech.NfcA
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.hardware.NfcBeamPayload
import com.example.ui.BeamDropScreen
import com.example.ui.BeamDropViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val viewModel: BeamDropViewModel by viewModels()
    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var intentFiltersArray: Array<IntentFilter>? = null
    private var techListsArray: Array<Array<String>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setupNfcForegroundDispatch()
        handleNfcIntent(intent)

        setContent {
            MyApplicationTheme {
                BeamDropScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    private fun setupNfcForegroundDispatch() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) return

        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        pendingIntent = PendingIntent.getActivity(this, 0, intent, flags)

        val ndefFilter = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
            try {
                addDataType("application/vnd.com.example.beamdrop")
            } catch (e: Exception) {
                // Ignore
            }
        }
        val wildcardFilter = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
            try {
                addDataType("*/*")
            } catch (e: Exception) {
                // Ignore
            }
        }
        val tagFilter = IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        val techFilter = IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)

        intentFiltersArray = arrayOf(ndefFilter, wildcardFilter, tagFilter, techFilter)
        techListsArray = arrayOf(
            arrayOf(android.nfc.tech.IsoDep::class.java.name),
            arrayOf(Ndef::class.java.name),
            arrayOf(NfcA::class.java.name),
            arrayOf(NdefFormatable::class.java.name)
        )
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppForegrounded()
    }

    override fun onResume() {
        super.onResume()
        if (nfcAdapter?.isEnabled == true && pendingIntent != null) {
            // Passive Foreground Dispatch listens for physical NFC taps with zero idle RF coil heat
            try {
                nfcAdapter?.enableForegroundDispatch(
                    this,
                    pendingIntent,
                    intentFiltersArray,
                    techListsArray
                )
            } catch (_: Exception) {
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (nfcAdapter?.isEnabled == true) {
            try {
                nfcAdapter?.disableForegroundDispatch(this)
            } catch (_: Exception) {
            }
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.onAppBackgrounded()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNfcIntent(intent)
    }

    private fun handleNfcIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return

        if (action == NfcAdapter.ACTION_NDEF_DISCOVERED ||
            action == NfcAdapter.ACTION_TECH_DISCOVERED ||
            action == NfcAdapter.ACTION_TAG_DISCOVERED
        ) {
            var payload: NfcBeamPayload? = null

            // 1. Try reading from NDEF messages extra
            val rawMessages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            }

            if (!rawMessages.isNullOrEmpty()) {
                val msg = rawMessages[0] as? NdefMessage
                if (msg != null) {
                    payload = viewModel.nfcHelper.parseHandshakePayload(msg)
                }
            }

            // 2. If no NDEF extra, inspect the Tag via NfcBeamHelper (supports IsoDep HCE + NDEF + NFC-A/B/F/V)
            val tagObj = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            }

            if (payload != null) {
                Toast.makeText(this, "BeamDrop NFC Contact Detected!", Toast.LENGTH_SHORT).show()
                viewModel.onNfcBeamDispatched(payload)
            } else if (tagObj != null) {
                Toast.makeText(this, "BeamDrop NFC Contact Detected!", Toast.LENGTH_SHORT).show()
                // handleDiscoveredTag exchanges HCE/NDEF payloads and emits CompatibleDeviceTapped -> triggers Aura Ripple
                viewModel.nfcHelper.handleDiscoveredTag(tagObj)
            } else {
                viewModel.onNfcBeamDispatched(null)
            }
        }
    }
}
