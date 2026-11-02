package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CardNavy
import com.example.ui.theme.CyberBlack
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonTeal
import com.example.ui.theme.RadiantPurple
import com.example.ui.theme.SolarAmber
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun BeamInfoDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = CyberBlack
                )
            ) {
                Text(text = "Got It", fontWeight = FontWeight.Bold)
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassLiquidIconOrb(
                    size = 38.dp,
                    cornerRadius = 13.dp,
                    tintColor = ElectricCyan,
                    secondaryColor = RadiantPurple
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "BeamDrop Magic",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                InfoFeatureRow(
                    icon = Icons.Default.Devices,
                    title = "Universal Across Android",
                    description = "Works seamlessly across Pixel, Samsung, Xiaomi, OnePlus, Motorola, and any Android device.",
                    tint = ElectricCyan
                )

                InfoFeatureRow(
                    icon = Icons.Default.WifiTethering,
                    title = "Top-Touch & NFC Gesture",
                    description = "Simply touch the tops of two phones together. Proximity sensors and NFC pair devices in under 150ms.",
                    tint = RadiantPurple
                )

                InfoFeatureRow(
                    icon = Icons.Default.Speed,
                    title = "Local Wi-Fi Direct (50+ MB/s)",
                    description = "Files beam directly between devices with zero cloud servers, zero compression, and zero mobile data.",
                    tint = NeonTeal
                )

                InfoFeatureRow(
                    icon = Icons.Default.Bolt,
                    title = "Glass Liquid Wave & Self-Dismissing HUD",
                    description = "Translucent glass liquid waves flow directionally across devices while a discreet progress capsule self-dismisses upon completion.",
                    tint = SolarAmber
                )

                InfoFeatureRow(
                    icon = Icons.Default.Devices,
                    title = "Windows 10/11 Native App (BeamDrop.exe)",
                    description = "Located in /windows/BeamDrop.exe (and BeamDropWindows.cs / Run-BeamDrop.bat), or downloadable wirelessly from your phone at port 8990.",
                    tint = ElectricCyan
                )

                InfoFeatureRow(
                    icon = Icons.Default.Bolt,
                    title = "Developed by Sajjad Hossain Mridul",
                    description = "Created & architected by Sajjad Hossain Mridul and the BeamDrop Staff for seamless Android & Windows local continuity.",
                    tint = NeonTeal
                )
            }
        },
        containerColor = DeepNavy,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun InfoFeatureRow(
    icon: ImageVector,
    title: String,
    description: String,
    tint: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        GlassLiquidIconOrb(
            size = 34.dp,
            cornerRadius = 11.dp,
            tintColor = tint,
            secondaryColor = ElectricCyan
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = description,
                fontSize = 11.sp,
                color = TextSecondary,
                lineHeight = 15.sp
            )
        }
    }
}
