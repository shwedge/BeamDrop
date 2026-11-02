package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ActiveBeamThemeState
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonTeal
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun BeamTopBar(
    deviceName: String,
    isNfcReady: Boolean,
    isNfcScanning: Boolean = false,
    isProximityActive: Boolean = false,
    isP2pReady: Boolean = false,
    activeQueueCount: Int = 0,
    onNfcScanClick: () -> Unit = {},
    onQueueClick: () -> Unit = {},
    onReceiveClick: () -> Unit = {},
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeTheme = ActiveBeamThemeState.current
    val primaryAccent = activeTheme.primaryAccent
    val secondaryAccent = activeTheme.secondaryAccent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: App Emblem + Title + Developer Sajjad Hossain Mridul
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            WwdcLiquidGlassLocateEmblem(
                size = 44.dp,
                cornerRadius = 13.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "BeamDrop",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(primaryAccent)
                    )
                }
                Text(
                    text = "$deviceName • Sajjad Hossain Mridul",
                    fontSize = 11.5.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Top Bar Right-Aligned Controls: NFC, QR, and Info (History & Settings are in the bottom navigation bar)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Interactive NFC Scan Pill
            Row(
                modifier = Modifier
                    .glassLiquidPill(
                        cornerRadius = 18.dp,
                        tintColor = if (isNfcScanning) primaryAccent else secondaryAccent,
                        baseColor = if (isNfcScanning) Color(0xFF1F4B46) else activeTheme.pillBaseColor
                    )
                    .clickable { onNfcScanClick() }
                    .padding(horizontal = 11.dp, vertical = 8.dp)
                    .testTag("nfc_scan_button"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Nfc,
                    contentDescription = "NFC Scan",
                    tint = if (isNfcScanning) primaryAccent else Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isNfcScanning) "Scanning" else "NFC",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isNfcScanning) primaryAccent else Color.White,
                    maxLines = 1
                )
            }

            // QR Code Connect / Receive Button
            GlassLiquidIconOrb(
                size = 38.dp,
                cornerRadius = 12.dp,
                tintColor = secondaryAccent,
                secondaryColor = primaryAccent
            ) {
                IconButton(
                    onClick = onReceiveClick,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("receive_mode_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = "Receive Mode & QR",
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            // Info Button aligned at top-right
            GlassLiquidIconOrb(
                size = 38.dp,
                cornerRadius = 12.dp,
                tintColor = secondaryAccent,
                secondaryColor = primaryAccent
            ) {
                IconButton(
                    onClick = onInfoClick,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("info_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "How It Works",
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}
