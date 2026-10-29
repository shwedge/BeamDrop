package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BeamPeer
import com.example.model.DeviceType
import com.example.p2p.WifiDirectConnectionState
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CardNavy
import com.example.ui.theme.CyberBlack
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonTeal
import com.example.ui.theme.RadiantPurple
import com.example.ui.theme.SolarAmber
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Clean, Apple AirDrop & OneTransfer-style fluid radar discovery screen.
 * Displays an organic pulsating radar sweep with floating nearby device cards.
 * Strips all raw developer IP/port logs and test controls from the user interface.
 */
@Composable
fun NearbyRadarView(
    peers: List<BeamPeer>,
    localIpAddress: String,
    deviceName: String = "Android Phone",
    p2pState: WifiDirectConnectionState = WifiDirectConnectionState(),
    onSelectPeer: (BeamPeer) -> Unit,
    onAddManualIp: (String) -> Unit,
    onCreateP2pGroup: () -> Unit = {},
    onScanSubnet: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "airdrop_radar_transition")

    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_sweep"
    )

    val ringPulse1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_pulse_1"
    )

    val ringPulse2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, delayMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_pulse_2"
    )

    val coreGlow by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "core_glow"
    )

    var showManualIpInput by remember { mutableStateOf(false) }
    var manualIpInput by remember { mutableStateOf("") }
    val favoriteIps = remember { mutableStateListOf<String>() }

    val MintTeal = Color(0xFF00F5D4)
    val VioletAura = Color(0xFF9D4EDD)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("radar_lazy_column"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Top Header Actions (Rescan & Discreet Manual IP)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Nearby Devices",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onScanSubnet,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("rescan_wifi_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Scan Subnet",
                            tint = ElectricCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = { showManualIpInput = !showManualIpInput },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddLink,
                            contentDescription = "Direct Connect",
                            tint = if (showManualIpInput) ElectricCyan else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Optional discreet Manual IP connect dialog/bar
        item {
            AnimatedVisibility(
                visible = showManualIpInput,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = manualIpInput,
                        onValueChange = { manualIpInput = it },
                        placeholder = {
                            Text("Enter IP address (e.g. 192.168.1.50)", fontSize = 12.sp, color = TextSecondary)
                        },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("manual_ip_input"),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = CardNavy,
                            unfocusedContainerColor = CardNavy
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            val raw = manualIpInput.trim().removePrefix("#")
                            if (raw.isNotEmpty()) {
                                onAddManualIp(raw)
                                manualIpInput = ""
                                showManualIpInput = false
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = CyberBlack
                        ),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("connect_manual_ip_btn")
                    ) {
                        Text("Connect", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 2. Apple AirDrop Style Liquid Radar Canvas
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxR = size.minDimension / 2f - 14.dp.toPx()

                    // Concentric static subtle orbit guides
                    listOf(0.32f, 0.55f, 0.78f, 1.0f).forEach { frac ->
                        drawCircle(
                            color = Color(0xFF1E293B).copy(alpha = 0.55f),
                            radius = maxR * frac,
                            center = center,
                            style = Stroke(width = 1.2f)
                        )
                    }

                    // Dynamic expanding fluid ripple wave 1
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.Transparent,
                                MintTeal.copy(alpha = (1f - ringPulse1) * 0.45f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = maxR * ringPulse1
                        ),
                        radius = maxR * ringPulse1,
                        center = center
                    )
                    drawCircle(
                        color = MintTeal.copy(alpha = (1f - ringPulse1) * 0.5f),
                        radius = maxR * ringPulse1,
                        center = center,
                        style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Dynamic expanding fluid ripple wave 2
                    drawCircle(
                        color = VioletAura.copy(alpha = (1f - ringPulse2) * 0.4f),
                        radius = maxR * ringPulse2,
                        center = center,
                        style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Rotating luminous sweep radar beam
                    rotate(degrees = sweepAngle, pivot = center) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MintTeal.copy(alpha = 0.02f),
                                    MintTeal.copy(alpha = 0.22f)
                                ),
                                center = center
                            ),
                            startAngle = 0f,
                            sweepAngle = 75f,
                            useCenter = true,
                            topLeft = Offset(center.x - maxR, center.y - maxR),
                            size = Size(maxR * 2, maxR * 2)
                        )
                    }

                    // Center glowing halo of your device
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MintTeal.copy(alpha = 0.35f * coreGlow),
                                VioletAura.copy(alpha = 0.15f * coreGlow),
                                Color.Transparent
                            ),
                            center = center,
                            radius = 48.dp.toPx() * coreGlow
                        ),
                        radius = 48.dp.toPx() * coreGlow,
                        center = center
                    )
                }

                // Center Phone Avatar representing THIS device
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(ElectricCyan, RadiantPurple)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = "My Device",
                            tint = CyberBlack,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = deviceName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Text(
                        text = if (peers.isNotEmpty()) "${peers.size} device(s) found" else "Scanning nearby...",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (peers.isNotEmpty()) ElectricCyan else TextSecondary
                    )
                }
            }
        }

        // 3. Discovered Devices (Apple AirDrop Style Peer Cards)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Devices (${peers.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                if (peers.isNotEmpty()) {
                    Text(
                        text = "Tap any device to beam",
                        fontSize = 12.sp,
                        color = ElectricCyan,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        if (peers.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(CardNavy.copy(alpha = 0.65f))
                        .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
                        .padding(vertical = 28.dp, horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = "Scanning",
                            tint = ElectricCyan.copy(alpha = 0.65f),
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "Looking for nearby devices...",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Make sure your Windows laptop or other phone has BeamDrop open and is connected to the same Wi-Fi.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        } else {
            items(peers, key = { it.ipAddress }) { peer ->
                val isFav = favoriteIps.contains(peer.ipAddress)
                NearbyPeerRow(
                    peer = peer,
                    isFavorite = isFav,
                    onToggleFavorite = {
                        if (isFav) favoriteIps.remove(peer.ipAddress) else favoriteIps.add(peer.ipAddress)
                    },
                    onSendClick = { onSelectPeer(peer) }
                )
            }
        }
    }
}

/**
 * Modern, Apple AirDrop style peer device row.
 */
@Composable
fun NearbyPeerRow(
    peer: BeamPeer,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onPositioned: (Offset) -> Unit = {},
    onSendClick: () -> Unit
) {
    val isComputer = peer.deviceType == DeviceType.COMPUTER
    val platformLabel = if (isComputer) "Windows PC" else "Android"
    val isReady = true

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                val pos = coords.positionInRoot()
                val center = Offset(
                    x = pos.x + coords.size.width / 2f,
                    y = pos.y + coords.size.height / 2f
                )
                onPositioned(center)
            }
            .clip(RoundedCornerShape(20.dp))
            .background(CardNavy)
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        ElectricCyan.copy(alpha = 0.5f),
                        RadiantPurple.copy(alpha = 0.35f)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onSendClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("peer_row_${peer.id}"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            // Glowing round device avatar
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                ElectricCyan.copy(alpha = 0.22f),
                                RadiantPurple.copy(alpha = 0.22f)
                            )
                        )
                    )
                    .border(
                        1.5.dp,
                        Brush.linearGradient(listOf(ElectricCyan, RadiantPurple)),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        isComputer -> Icons.Default.Computer
                        peer.deviceType == DeviceType.TABLET -> Icons.Default.TabletAndroid
                        else -> Icons.Default.PhoneAndroid
                    },
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peer.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(ElectricCyan)
                    )
                    Text(
                        text = "$platformLabel • Ready to Beam",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorite) SolarAmber else TextSecondary.copy(alpha = 0.6f),
                    modifier = Modifier.size(19.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Button(
                onClick = onSendClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = CyberBlack
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier
                    .height(38.dp)
                    .fluidAuraRippleButton(
                        isPeerDiscovered = true,
                        cornerRadius = 12.dp
                    )
                    .testTag("peer_send_btn_${peer.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Beam", fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}
