package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.TabletAndroid
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
import com.example.ui.theme.SolarAmber
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

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
    val infiniteTransition = rememberInfiniteTransition(label = "receive_hub_rotation")
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_rotation"
    )
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_radius"
    )
    val sonarWave by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sonar_wave"
    )

    var quickSaveMode by remember { mutableStateOf("On") } // "Off", "Favorites", "On"
    var showManualIpInput by remember { mutableStateOf(false) }
    var manualIpInput by remember { mutableStateOf("") }
    val favoriteIps = remember { mutableStateListOf<String>() }

    val ipOctetTag = remember(localIpAddress) {
        val last = localIpAddress.substringAfterLast(".", "0")
        "#$last"
    }

    val MintTeal = Color(0xFF5EEAD4)
    val DeepTealCard = Color(0xFF1E3A3A)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("radar_lazy_column"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Center Receive Emblem + Device Identity + #Octet (Matches User's Screenshot 1 & 2)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(190.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val maxR = size.minDimension / 2f

                        // Expanding subtle continuity wave
                        drawCircle(
                            color = MintTeal.copy(alpha = (1f - sonarWave) * 0.28f),
                            radius = maxR * (0.55f + sonarWave * 0.45f),
                            center = center,
                            style = Stroke(width = 2.5f)
                        )

                        // Rotating 8-segment dashed outer ring (Exact visual signature)
                        val ringRadius = maxR * 0.64f
                        val arcDiameter = ringRadius * 2f
                        val topLeft = Offset(center.x - ringRadius, center.y - ringRadius)

                        rotate(degrees = ringRotation, pivot = center) {
                            val segmentCount = 8
                            val sweepPerSegment = 26f
                            val stepAngle = 360f / segmentCount
                            for (i in 0 until segmentCount) {
                                drawArc(
                                    color = MintTeal,
                                    startAngle = i * stepAngle,
                                    sweepAngle = sweepPerSegment,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = Size(arcDiameter, arcDiameter),
                                    style = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                        }

                        // Solid glowing inner circle
                        drawCircle(
                            color = MintTeal,
                            radius = maxR * 0.38f * pulseRadius,
                            center = center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = deviceName.uppercase(),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    letterSpacing = 0.8.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = ipOctetTag,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "IP: $localIpAddress • Port: 8988",
                    fontSize = 12.sp,
                    color = MintTeal.copy(alpha = 0.85f)
                )
            }
        }

        // 2. Quick Save Segmented Pill Selector (Off | Favorites | On)
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Quick Save",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(CardNavy)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(50))
                        .padding(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("Off", "Favorites", "On").forEach { mode ->
                        val selected = quickSaveMode == mode
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) DeepTealCard else Color.Transparent)
                                .clickable { quickSaveMode = mode }
                                .padding(horizontal = 20.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) MintTeal else TextSecondary
                            )
                        }
                    }
                }
            }
        }

        // 3. Nearby Devices Header + Action Icons (Refresh, Manual IP, Wi-Fi Direct)
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Nearby devices",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onScanSubnet,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("rescan_wifi_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Scan Subnet",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = { showManualIpInput = !showManualIpInput },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddLink,
                            contentDescription = "Direct IP Connect",
                            tint = if (showManualIpInput) MintTeal else TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onCreateP2pGroup,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiTethering,
                            contentDescription = "Wi-Fi Direct P2P",
                            tint = if (p2pState.isConnected) SolarAmber else TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Optional Direct IP Box (Toggled by link icon or always accessible via testTag)
        item {
            if (showManualIpInput || peers.isEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = manualIpInput,
                        onValueChange = { manualIpInput = it },
                        placeholder = {
                            Text(
                                "Enter Peer IP or #Octet (e.g. 192.168.1.109)",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("manual_ip_input"),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MintTeal,
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
                                val resolvedIp = if (!raw.contains(".") && localIpAddress.contains(".")) {
                                    localIpAddress.substringBeforeLast(".") + "." + raw
                                } else {
                                    raw
                                }
                                onAddManualIp(resolvedIp)
                                manualIpInput = ""
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MintTeal,
                            contentColor = CyberBlack
                        ),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("connect_manual_ip_btn")
                    ) {
                        Text(text = "Connect", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 4. Discovered Peer Cards (Matches Screenshot 4: Phone/PC icon + Name + #167 + Platform Tag + Heart)
        if (peers.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(CardNavy.copy(alpha = 0.7f))
                        .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp))
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No nearby devices detected yet",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Ensure the target Android phone or Windows PC is on the same Wi-Fi network.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
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

@Composable
fun NearbyPeerRow(
    peer: BeamPeer,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onPositioned: (Offset) -> Unit = {},
    onSendClick: () -> Unit
) {
    val isComputer = peer.deviceType == DeviceType.COMPUTER
    val peerOctet = "#" + peer.ipAddress.substringAfterLast(".", "0")
    val platformBadge = when {
        isComputer -> "Windows"
        peer.name.contains(" ", ignoreCase = true) -> peer.name.substringBefore(" ")
        else -> "Android"
    }
    val CardTealSurface = Color(0xFF233936)
    val BadgeTealSurface = Color(0xFF344E4A)
    val MintTeal = Color(0xFF5EEAD4)

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
            .clip(RoundedCornerShape(18.dp))
            .background(CardTealSurface)
            .border(1.dp, MintTeal.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .clickable { onSendClick() }
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .testTag("peer_row_${peer.id}"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = when (peer.deviceType) {
                    DeviceType.PHONE -> Icons.Default.PhoneAndroid
                    DeviceType.TABLET -> Icons.Default.TabletAndroid
                    else -> Icons.Default.Computer
                },
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(34.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peer.name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(BadgeTealSurface)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = peerOctet,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary.copy(alpha = 0.9f)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(BadgeTealSurface)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = platformBadge,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorite) MintTeal else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Button(
                onClick = onSendClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MintTeal,
                    contentColor = CyberBlack
                ),
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
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Send", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
