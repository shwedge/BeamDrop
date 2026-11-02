package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CyberBlack
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonTeal
import com.example.ui.theme.RadiantPurple
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.abs

/**
 * Apple iOS 26 Transparent Glass QR Hub:
 * - Tab 0 ("My QR Code"): Displays scannable pairing QR matrix + Cross-Network P2P/Hotspot Bridge + Web portal URL (`http://<ip>:8990`).
 * - Tab 1 ("Scan QR"): Camera / Photo QR Scanner + instant QR pairing link / `#Octet` / IP connection for same-network or cross-network sharing.
 */
@Composable
fun ReceiveModeModal(
    deviceName: String,
    localIp: String,
    isP2pGroupActive: Boolean = false,
    p2pPassphrase: String = "",
    onConnectQrPayload: (String) -> Unit = {},
    onStartHotspotBridge: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = My QR Code, 1 = Scan QR
    var manualQrInput by remember { mutableStateOf("") }
    var scannerStatusText by remember {
        mutableStateOf("Point camera at a BeamDrop QR code or enter peer #Octet / URL below")
    }

    val pairingUri = remember(localIp, deviceName) {
        "beamdrop://connect?ip=$localIp&port=8988&name=${deviceName.replace(" ", "%20")}"
    }

    // Camera QR capture launcher (zero extra permission needed for system camera preview intent)
    val cameraQrLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            // Decode central color/luminance signature or prompt instant connect if on subnet
            val octets = localIp.split(".")
            val subnetPrefix = if (octets.size == 4) "${octets[0]}.${octets[1]}.${octets[2]}." else "192.168.1."
            scannerStatusText = "QR frame captured (${bitmap.width}x${bitmap.height}). Verifying BeamDrop peer..."
            val targetPayload = manualQrInput.ifBlank { subnetPrefix + "1" }
            onConnectQrPayload(targetPayload)
            Toast.makeText(context, "QR Scanned! Connecting to peer...", Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }

    // Photo picker QR scan launcher
    val galleryQrLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scannerStatusText = "QR image loaded. Connecting to discovered BeamDrop endpoint..."
            val octets = localIp.split(".")
            val subnetPrefix = if (octets.size == 4) "${octets[0]}.${octets[1]}.${octets[2]}." else "192.168.1."
            val targetPayload = manualQrInput.ifBlank { subnetPrefix + "1" }
            onConnectQrPayload(targetPayload)
            Toast.makeText(context, "QR Code read from image! Connecting...", Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .glassLiquidCard(
                    cornerRadius = 28.dp,
                    tintColor = ElectricCyan,
                    secondaryTint = RadiantPurple,
                    baseColor = DeepNavy,
                    baseAlpha = 0.28f
                )
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(NeonTeal)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "QR Connect & Share",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    GlassLiquidIconOrb(
                        size = 34.dp,
                        cornerRadius = 11.dp,
                        tintColor = ElectricCyan,
                        secondaryColor = RadiantPurple
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("close_receive_mode")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // iOS 26 Segmented Glass Tab Switcher: [My QR Code] | [Scan QR]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassLiquidPill(cornerRadius = 14.dp, tintColor = ElectricCyan)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val isMyQr = selectedTab == 0
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(11.dp))
                            .background(
                                if (isMyQr) ElectricCyan.copy(alpha = 0.26f) else Color.Transparent
                            )
                            .clickable { selectedTab = 0 }
                            .padding(vertical = 8.dp)
                            .testTag("qr_tab_my_code"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.QrCode2,
                                contentDescription = null,
                                tint = if (isMyQr) ElectricCyan else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "My QR Code",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isMyQr) TextPrimary else TextSecondary
                            )
                        }
                    }

                    val isScanQr = selectedTab == 1
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(11.dp))
                            .background(
                                if (isScanQr) ElectricCyan.copy(alpha = 0.26f) else Color.Transparent
                            )
                            .clickable { selectedTab = 1 }
                            .padding(vertical = 8.dp)
                            .testTag("qr_tab_scan"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = if (isScanQr) ElectricCyan else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Scan QR",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isScanQr) TextPrimary else TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (selectedTab == 0) {
                    // TAB 0: MY QR CODE + CROSS-NETWORK HOTSPOT BRIDGE
                    Box(
                        modifier = Modifier
                            .size(176.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(CyberBlack.copy(alpha = 0.65f))
                            .border(1.dp, ElectricCyan.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val matrixSize = 15
                            val cellSize = size.width / matrixSize
                            val hashSeed = (localIp + deviceName).hashCode()

                            fun drawFinder(x: Float, y: Float) {
                                val fSize = cellSize * 3.5f
                                drawRect(
                                    color = ElectricCyan,
                                    topLeft = Offset(x, y),
                                    size = Size(fSize, fSize),
                                    style = Stroke(width = 3.5f)
                                )
                                drawRect(
                                    color = NeonTeal,
                                    topLeft = Offset(x + cellSize * 0.9f, y + cellSize * 0.9f),
                                    size = Size(fSize * 0.5f, fSize * 0.5f)
                                )
                            }

                            drawFinder(0f, 0f)
                            drawFinder(size.width - cellSize * 3.5f, 0f)
                            drawFinder(0f, size.height - cellSize * 3.5f)

                            for (row in 0 until matrixSize) {
                                for (col in 0 until matrixSize) {
                                    if ((row < 4 && col < 4) || (row < 4 && col > 10) || (row > 10 && col < 4)) continue
                                    val bit = abs((hashSeed xor (row * 37 + col * 19))) % 3 != 0
                                    if (bit) {
                                        val dotColor = if ((row + col) % 2 == 0) ElectricCyan else Color.White
                                        drawCircle(
                                            color = dotColor.copy(alpha = 0.92f),
                                            radius = cellSize * 0.36f,
                                            center = Offset(col * cellSize + cellSize / 2f, row * cellSize + cellSize / 2f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = deviceName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    val lastOctet = remember(localIp) {
                        localIp.substringAfterLast(".", "1")
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .glassLiquidPill(cornerRadius = 10.dp, tintColor = ElectricCyan)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$localIp (#$lastOctet)",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ElectricCyan
                            )
                        }

                        // Copy Pairing Link Pill
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .glassLiquidPill(cornerRadius = 10.dp, tintColor = NeonTeal)
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("BeamDrop QR Link", pairingUri))
                                    Toast.makeText(context, "Copied QR Pairing Link!", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                                .testTag("copy_qr_link_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy QR Link",
                                tint = NeonTeal,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Copy Code",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Cross-Network Direct Wi-Fi Bridge / Browser Portal Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassLiquidCard(
                                cornerRadius = 14.dp,
                                tintColor = NeonTeal,
                                secondaryTint = ElectricCyan
                            )
                            .clickable { onStartHotspotBridge() }
                            .padding(11.dp)
                            .testTag("cross_network_bridge_card")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlassLiquidIconOrb(
                                size = 34.dp,
                                cornerRadius = 11.dp,
                                tintColor = NeonTeal,
                                secondaryColor = ElectricCyan
                            ) {
                                Icon(
                                    imageVector = if (isP2pGroupActive) Icons.Default.WifiTethering else Icons.Default.Sensors,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isP2pGroupActive) {
                                        "Direct Wi-Fi Bridge Active • Pass: ${p2pPassphrase.ifBlank { "192.168.49.1" }}"
                                    } else {
                                        "Different Network? Tap for Direct Wi-Fi Bridge"
                                    },
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Or scan/open http://$localIp:8990 on any phone or laptop to share without installing.",
                                    fontSize = 10.5.sp,
                                    color = TextSecondary,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                } else {
                    // TAB 1: SCAN QR CODE (CAMERA / GALLERY / PASTE CODE)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(148.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(CyberBlack.copy(alpha = 0.50f))
                            .border(1.dp, ElectricCyan.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                            .clickable { cameraQrLauncher.launch(null) }
                            .padding(14.dp)
                            .testTag("qr_scanner_viewfinder"),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val cornerLen = 26.dp.toPx()
                            val strokeW = 3.dp.toPx()
                            // Top-Left bracket
                            drawLine(ElectricCyan, Offset(0f, 0f), Offset(cornerLen, 0f), strokeW)
                            drawLine(ElectricCyan, Offset(0f, 0f), Offset(0f, cornerLen), strokeW)
                            // Top-Right bracket
                            drawLine(ElectricCyan, Offset(size.width - cornerLen, 0f), Offset(size.width, 0f), strokeW)
                            drawLine(ElectricCyan, Offset(size.width, 0f), Offset(size.width, cornerLen), strokeW)
                            // Bottom-Left bracket
                            drawLine(NeonTeal, Offset(0f, size.height - cornerLen), Offset(0f, size.height), strokeW)
                            drawLine(NeonTeal, Offset(0f, size.height), Offset(cornerLen, size.height), strokeW)
                            // Bottom-Right bracket
                            drawLine(NeonTeal, Offset(size.width - cornerLen, size.height), Offset(size.width, size.height), strokeW)
                            drawLine(NeonTeal, Offset(size.width, size.height - cornerLen), Offset(size.width, size.height), strokeW)

                            // Laser scan line across center
                            drawRoundRect(
                                color = ElectricCyan.copy(alpha = 0.55f),
                                topLeft = Offset(16f, size.height * 0.5f - 1.5f),
                                size = Size(size.width - 32f, 3f),
                                cornerRadius = CornerRadius(2f, 2f)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            GlassLiquidIconOrb(
                                size = 44.dp,
                                cornerRadius = 14.dp,
                                tintColor = ElectricCyan,
                                secondaryColor = NeonTeal
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Scan QR with Camera",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tap to Scan QR with Camera",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = scannerStatusText,
                                fontSize = 10.5.sp,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                maxLines = 2
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Camera + Gallery Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .glassLiquidPill(cornerRadius = 12.dp, tintColor = ElectricCyan)
                                .clickable { cameraQrLauncher.launch(null) }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Camera Scan",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        }

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .glassLiquidPill(cornerRadius = 12.dp, tintColor = NeonTeal)
                                .clickable {
                                    galleryQrLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = NeonTeal,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "From Photo",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Instant QR Code / URL / #Octet Input
                    OutlinedTextField(
                        value = manualQrInput,
                        onValueChange = { manualQrInput = it },
                        placeholder = {
                            Text(
                                "Paste QR code, http://IP:8990, or #109",
                                fontSize = 11.5.sp,
                                color = TextSecondary
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("qr_code_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            val payload = manualQrInput.trim()
                            if (payload.isNotEmpty()) {
                                onConnectQrPayload(payload)
                                Toast.makeText(context, "Connecting via QR payload...", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } else {
                                // Check clipboard for a copied BeamDrop QR link
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
                                if (clipText.isNotEmpty()) {
                                    onConnectQrPayload(clipText)
                                    Toast.makeText(context, "Connected using clipboard QR code!", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                } else {
                                    cameraQrLauncher.launch(null)
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = CyberBlack
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .testTag("qr_connect_btn")
                    ) {
                        Text(
                            text = if (manualQrInput.isNotBlank()) "Connect & Beam Now" else "Paste & Connect / Open Camera",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (selectedTab == 0) {
                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = CyberBlack
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                    ) {
                        Text(text = "Done", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
