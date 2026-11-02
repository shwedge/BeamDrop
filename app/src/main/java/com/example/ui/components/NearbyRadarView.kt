package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
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
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

/**
 * Clean, Glass Liquid radar discovery screen.
 * Displays refractive concentric glass rings with floating nearby device cards.
 */
@Composable
fun NearbyRadarView(
    peers: List<BeamPeer>,
    localIpAddress: String,
    deviceName: String = "Android Phone",
    p2pState: WifiDirectConnectionState = WifiDirectConnectionState(),
    onSelectPeer: (BeamPeer) -> Unit,
    onSelectPeerWithDirection: (BeamPeer, String) -> Unit = { peer, _ -> onSelectPeer(peer) },
    onSwipePreviewPeer: (BeamPeer, String) -> Unit = { _, _ -> },
    onAddManualIp: (String) -> Unit,
    onCreateP2pGroup: () -> Unit = {},
    onScanSubnet: () -> Unit = {},
    modifier: Modifier = Modifier
) {
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlassLiquidIconOrb(
                        size = 38.dp,
                        cornerRadius = 12.dp,
                        tintColor = ElectricCyan
                    ) {
                        IconButton(
                            onClick = onScanSubnet,
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("rescan_wifi_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Scan Subnet",
                                tint = TextPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    GlassLiquidIconOrb(
                        size = 38.dp,
                        cornerRadius = 12.dp,
                        tintColor = if (showManualIpInput) ElectricCyan else NeonTeal
                    ) {
                        IconButton(
                            onClick = { showManualIpInput = !showManualIpInput },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddLink,
                                contentDescription = "Direct Connect",
                                tint = if (showManualIpInput) ElectricCyan else TextPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
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

        // 2. Apple WWDC25 Liquid Glass Locate Centerpiece Card & Orbiting Peer Nodes
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(304.dp)
                    .glassLiquidCard(
                        cornerRadius = 26.dp,
                        tintColor = ElectricCyan,
                        secondaryTint = RadiantPurple,
                        baseColor = Color(0xFF0E296B),
                        baseAlpha = 0.44f
                    )
                    .padding(12.dp)
                    .testTag("radar_glass_container"),
                contentAlignment = Alignment.Center
            ) {
                // Top Specular Metadata Chips matching WWDC25 Icon Composer header (34° • 50% • Liquid Glass)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp, end = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .glassLiquidPill(cornerRadius = 10.dp, tintColor = ElectricCyan)
                            .padding(horizontal = 8.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            text = "34° Specular",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .glassLiquidPill(cornerRadius = 10.dp, tintColor = NeonTeal)
                            .padding(horizontal = 8.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            text = "50% Glass",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f - 8.dp.toPx())
                    val maxR = size.minDimension / 2f - 10.dp.toPx()

                    // Refractive WWDC25 frosted white-ice concentric glass locate rings
                    listOf(0.44f, 0.68f, 0.92f).forEachIndexed { idx, frac ->
                        val r = maxR * frac
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.10f - idx * 0.025f),
                                    Color(0xFFBAE6FD).copy(alpha = 0.08f - idx * 0.02f),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = r
                            ),
                            radius = r,
                            center = center
                        )
                        drawCircle(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = if (idx == 0) 0.62f else 0.36f),
                                    Color(0xFFBAE6FD).copy(alpha = 0.42f),
                                    Color.White.copy(alpha = 0.22f)
                                ),
                                start = Offset(center.x - r, center.y - r),
                                end = Offset(center.x + r, center.y + r)
                            ),
                            radius = r,
                            center = center,
                            style = Stroke(width = 1.4.dp.toPx())
                        )
                    }
                }

                // Orbiting Glass Liquid Peer Nodes on the Radar
                val orbitAngles = remember { listOf(-45.0, 35.0, 145.0, -135.0, -90.0, 90.0) }
                peers.take(6).forEachIndexed { index, peer ->
                    val angleRad = Math.toRadians(orbitAngles[index % orbitAngles.size])
                    val orbitRadiusDp = if (index % 2 == 0) 96.dp else 112.dp
                    val offsetX = (orbitRadiusDp.value * kotlin.math.cos(angleRad)).dp
                    val offsetY = (orbitRadiusDp.value * kotlin.math.sin(angleRad)).dp
                    val isPeerComputer = peer.deviceType == DeviceType.COMPUTER ||
                        peer.name.contains("VICTUS", ignoreCase = true) ||
                        peer.name.contains("Windows", ignoreCase = true) ||
                        peer.name.contains("PC", ignoreCase = true)
                    val peerOctet = peer.ipAddress.substringAfterLast(".", "109")

                    Column(
                        modifier = Modifier
                            .offset(x = offsetX, y = offsetY)
                            .clickable { onSelectPeerWithDirection(peer, "TOP") }
                            .testTag("radar_orbit_node_${peer.id}"),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        GlassLiquidIconOrb(
                            size = 40.dp,
                            cornerRadius = 13.dp,
                            tintColor = ElectricCyan,
                            secondaryColor = NeonTeal
                        ) {
                            Icon(
                                imageVector = if (isPeerComputer) Icons.Default.Computer else Icons.Default.PhoneAndroid,
                                contentDescription = peer.name,
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Box(
                            modifier = Modifier
                                .glassLiquidPill(
                                    cornerRadius = 6.dp,
                                    tintColor = ElectricCyan
                                )
                                .padding(horizontal = 6.dp, vertical = 1.5.dp)
                        ) {
                            Text(
                                text = "#$peerOctet",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }
                }

                // Center Apple WWDC25 3D Pillowed Sapphire Liquid Glass Locate Squircle Emblem
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    WwdcLiquidGlassLocateEmblem(
                        size = 112.dp,
                        cornerRadius = 28.dp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = deviceName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Box(
                        modifier = Modifier
                            .glassLiquidPill(
                                cornerRadius = 8.dp,
                                tintColor = if (peers.isNotEmpty()) ElectricCyan else RadiantPurple
                            )
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (peers.isNotEmpty()) "${peers.size} device(s) located" else "Glass Locate Active...",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        // 3. Discovered Devices
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
                        text = "Tap or swipe left/right to beam",
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
                        .glassLiquidCard(cornerRadius = 20.dp, tintColor = ElectricCyan)
                        .padding(vertical = 28.dp, horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GlassLiquidIconOrb(
                            size = 48.dp,
                            cornerRadius = 24.dp,
                            tintColor = ElectricCyan
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sensors,
                                contentDescription = "Scanning",
                                tint = ElectricCyan,
                                modifier = Modifier.size(24.dp)
                            )
                        }
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
                    onSwipePreview = { direction -> onSwipePreviewPeer(peer, direction) },
                    onSendClick = { onSelectPeerWithDirection(peer, "TOP") },
                    onSendWithDirection = { direction -> onSelectPeerWithDirection(peer, direction) }
                )
            }
        }
    }
}

/**
 * Glass Liquid nearby device card showing the device type icon inside a refractive glass orb,
 * full device name, #number tag, and device brand/OS tag.
 * Supports both tapping (top flow) and swiping left or right (directional left/right glass liquid flow to Windows).
 */
@Composable
fun NearbyPeerRow(
    peer: BeamPeer,
    isFavorite: Boolean = false,
    isTargeted: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onPositioned: (Offset) -> Unit = {},
    onSwipePreview: (String) -> Unit = {},
    onSendWithDirection: (String) -> Unit = {},
    onSendClick: () -> Unit
) {
    val isComputer = peer.deviceType == DeviceType.COMPUTER ||
        peer.name.contains("VICTUS", ignoreCase = true) ||
        peer.name.contains("Windows", ignoreCase = true) ||
        peer.name.contains("PC", ignoreCase = true) ||
        peer.name.contains("Laptop", ignoreCase = true) ||
        peer.name.contains("Desktop", ignoreCase = true)

    val octetTag = remember(peer.ipAddress) {
        val last = peer.ipAddress.substringAfterLast(".", "")
        if (last.isNotEmpty() && last.all { it.isDigit() }) "#$last" else "#109"
    }

    val deviceTypeTag = remember(peer.name, isComputer) {
        if (isComputer) {
            "Windows"
        } else {
            val firstWord = peer.name.split(" ", "-", "_").firstOrNull().orEmpty()
            if (firstWord.length >= 2) {
                firstWord.lowercase().replaceFirstChar { it.uppercase() }
            } else {
                "Android"
            }
        }
    }

    val density = LocalDensity.current
    val maxDragPx = remember(density) { with(density) { 120.dp.toPx() } }
    val thresholdPx = remember(density) { with(density) { 24.dp.toPx() } }
    val previewThresholdPx = remember(density) { with(density) { 12.dp.toPx() } }

    var dragOffsetX by remember(peer.id) { mutableFloatStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(
        targetValue = dragOffsetX,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "peer_card_swipe_offset"
    )

    var lastReportedOffset by remember(peer.id) { mutableStateOf(Offset.Unspecified) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
            .onGloballyPositioned { coords ->
                val pos = coords.positionInRoot()
                val center = Offset(
                    x = pos.x + coords.size.width / 2f,
                    y = pos.y + coords.size.height / 2f
                )
                if (lastReportedOffset == Offset.Unspecified ||
                    kotlin.math.abs(lastReportedOffset.x - center.x) >= 8f ||
                    kotlin.math.abs(lastReportedOffset.y - center.y) >= 8f
                ) {
                    lastReportedOffset = center
                    onPositioned(center)
                }
            }
            .glassLiquidCard(
                cornerRadius = 20.dp,
                tintColor = if (kotlin.math.abs(dragOffsetX) >= previewThresholdPx || isTargeted) ElectricCyan else Color(0xFF5EEAD4),
                secondaryTint = if (kotlin.math.abs(dragOffsetX) >= previewThresholdPx || isTargeted) RadiantPurple else ElectricCyan,
                baseColor = Color(0xFF0E2229),
                baseAlpha = if (isTargeted) 0.26f else 0.20f
            )
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    dragOffsetX = (dragOffsetX + delta).coerceIn(-maxDragPx, maxDragPx)
                    if (dragOffsetX >= previewThresholdPx) {
                        onSwipePreview("RIGHT")
                    } else if (dragOffsetX <= -previewThresholdPx) {
                        onSwipePreview("LEFT")
                    }
                },
                onDragStopped = { velocity ->
                    val finalOffset = dragOffsetX
                    dragOffsetX = 0f
                    when {
                        finalOffset <= -thresholdPx || velocity < -260f -> {
                            onSwipePreview("LEFT")
                            onSendWithDirection("LEFT")
                        }
                        finalOffset >= thresholdPx || velocity > 260f -> {
                            onSwipePreview("RIGHT")
                            onSendWithDirection("RIGHT")
                        }
                        else -> {}
                    }
                }
            )
            .clickable {
                onSendClick()
            }
            .padding(horizontal = 16.dp, vertical = 15.dp)
            .testTag("peer_row_${peer.id}"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            GlassLiquidIconOrb(
                size = 48.dp,
                cornerRadius = 15.dp,
                tintColor = Color(0xFF5EEAD4),
                secondaryColor = ElectricCyan
            ) {
                Icon(
                    imageVector = when {
                        isComputer -> Icons.Default.Computer
                        peer.deviceType == DeviceType.TABLET -> Icons.Default.TabletAndroid
                        else -> Icons.Default.PhoneAndroid
                    },
                    contentDescription = deviceTypeTag,
                    tint = TextPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peer.name,
                    fontSize = 17.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .glassLiquidPill(
                                cornerRadius = 7.dp,
                                tintColor = Color(0xFF5EEAD4)
                            )
                            .padding(horizontal = 8.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            text = octetTag,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary.copy(alpha = 0.94f),
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Box(
                        modifier = Modifier
                            .glassLiquidPill(
                                cornerRadius = 7.dp,
                                tintColor = ElectricCyan
                            )
                            .padding(horizontal = 8.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            text = deviceTypeTag,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary.copy(alpha = 0.94f),
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    if (isTargeted) {
                        Box(
                            modifier = Modifier
                                .glassLiquidPill(
                                    cornerRadius = 7.dp,
                                    tintColor = ElectricCyan,
                                    baseColor = Color(0xFF142B28)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.5.dp)
                        ) {
                            Text(
                                text = "Target Device",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElectricCyan,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        GlassLiquidIconOrb(
            size = 38.dp,
            cornerRadius = 12.dp,
            tintColor = if (isFavorite) Color(0xFF5EEAD4) else ElectricCyan,
            secondaryColor = RadiantPurple
        ) {
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .size(38.dp)
                    .testTag("peer_send_btn_${peer.id}")
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorite) Color(0xFF5EEAD4) else TextPrimary.copy(alpha = 0.85f),
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}
