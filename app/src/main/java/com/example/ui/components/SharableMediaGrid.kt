package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import com.example.model.DeviceType
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BeamPeer
import com.example.model.SharableItem
import com.example.model.ShareCategory
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CardNavy
import com.example.ui.theme.CyberBlack
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.DeepViolet
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonTeal
import com.example.ui.theme.RadiantPurple
import com.example.ui.theme.SolarAmber
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VividBlue

@Composable
fun SharableMediaGrid(
    items: List<SharableItem>,
    stagedItems: List<SharableItem> = emptyList(),
    nearbyPeers: List<BeamPeer> = emptyList(),
    saveFolderDisplayName: String = "Downloads/BeamDrop",
    onChooseSaveFolder: () -> Unit = {},
    onToggleSelect: (SharableItem) -> Unit,
    onPickFromStorage: () -> Unit,
    onPickDocument: () -> Unit = onPickFromStorage,
    onPickFolder: () -> Unit = onPickDocument,
    onAddTextSnippet: (String) -> Unit = {},
    onClearSelection: () -> Unit = {},
    onSelectPeerToSend: (BeamPeer) -> Unit = {},
    onSelectPeerToSendWithDirection: (BeamPeer, String) -> Unit = { peer, _ -> onSelectPeerToSend(peer) },
    onSwipePreviewPeer: (BeamPeer, String) -> Unit = { _, _ -> },
    onSwipePreviewDirection: (String) -> Unit = {},
    onAutoBeamBySwipe: (String) -> Unit = {},
    onScanSubnet: () -> Unit = {},
    onAddManualIp: (String) -> Unit = {},
    onSourceButtonPositioned: (Offset) -> Unit = {},
    onTargetPeerPositioned: (String, Offset) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    var showTextDialog by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }
    var showManualIpDialog by remember { mutableStateOf(false) }
    var manualIpText by remember { mutableStateOf("") }
    val favoriteIps = remember { mutableStateListOf<String>() }
    var targetedPeerId by remember { mutableStateOf<String?>(null) }
    val activeTargetPeer = nearbyPeers.firstOrNull { it.id == targetedPeerId } ?: nearbyPeers.firstOrNull()

    val MintTeal = Color(0xFF7DD3FC)
    val SelectionCardBg = Color(0xFF0E296B)

    if (showTextDialog) {
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            containerColor = CardNavy,
            title = {
                Text("Send Plain Text (Pop-up on Receiver)", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("Paste or type plain text to pop up on receiver...", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MintTeal,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            onAddTextSnippet(textInput.trim())
                            textInput = ""
                        }
                        showTextDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MintTeal, contentColor = CyberBlack)
                ) {
                    Text("Confirm", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTextDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    if (showManualIpDialog) {
        AlertDialog(
            onDismissRequest = { showManualIpDialog = false },
            containerColor = CardNavy,
            title = {
                Text("Connect by IP or #Number", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                OutlinedTextField(
                    value = manualIpText,
                    onValueChange = { manualIpText = it },
                    placeholder = { Text("e.g. 192.168.1.109 or #109", color = TextSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MintTeal,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleaned = manualIpText.trim().removePrefix("#")
                        if (cleaned.isNotBlank()) {
                            onAddManualIp(cleaned)
                            manualIpText = ""
                        }
                        showManualIpDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MintTeal, contentColor = CyberBlack)
                ) {
                    Text("Connect", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualIpDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. Selection Section at the Top (File | Media | Text | Paste — with Glass Liquid Icon Orbs)
        item {
            var lastReportedOffset by remember { mutableStateOf(Offset.Unspecified) }
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Selection",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SelectionActionTile(
                        icon = Icons.Default.Description,
                        label = "File",
                        bgColor = SelectionCardBg,
                        tintColor = ElectricCyan,
                        onClick = onPickDocument,
                        modifier = Modifier
                            .weight(1f)
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
                                    onSourceButtonPositioned(center)
                                }
                            }
                            .testTag("pick_from_device_card")
                    )
                    SelectionActionTile(
                        icon = Icons.Default.Folder,
                        label = "Folder",
                        bgColor = SelectionCardBg,
                        tintColor = MintTeal,
                        onClick = onPickFolder,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_folder_button")
                    )
                    SelectionActionTile(
                        icon = Icons.Default.PhotoLibrary,
                        label = "Media",
                        bgColor = SelectionCardBg,
                        tintColor = VividBlue,
                        onClick = onPickFromStorage,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("file_transfer_aura_button")
                    )
                    SelectionActionTile(
                        icon = Icons.Default.ContentPaste,
                        label = "Paste",
                        bgColor = SelectionCardBg,
                        tintColor = RadiantPurple,
                        onClick = {
                            val clip = clipboardManager.getText()?.text
                            if (!clip.isNullOrBlank()) {
                                onAddTextSnippet(clip)
                            } else {
                                showTextDialog = true
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 2. Active Staged Files Box (iOS-Style Draggable / Swipable Liquid Glass Card with >70% Auto-Beam)
        if (stagedItems.isNotEmpty()) {
            item {
                StagedFolderSwipeCard(
                    stagedItems = stagedItems,
                    nearbyPeers = nearbyPeers,
                    selectedTargetPeer = activeTargetPeer,
                    onSelectTargetPeer = { peer -> targetedPeerId = peer.id },
                    onClearSelection = onClearSelection,
                    onOpenPlainTextDialog = { showTextDialog = true },
                    onSwipePreview = { peer, direction ->
                        if (peer != null) {
                            onSwipePreviewPeer(peer, direction)
                        } else {
                            onSwipePreviewDirection(direction)
                        }
                    },
                    onAutoBeamSwipe = { direction ->
                        if (activeTargetPeer != null) {
                            onSelectPeerToSendWithDirection(activeTargetPeer, direction)
                        } else {
                            onAutoBeamBySwipe(direction)
                        }
                    },
                    onBeamToPeer = { peer, direction ->
                        onSelectPeerToSendWithDirection(peer, direction)
                    }
                )
            }
        }

        // 3. Nearby Devices Section (Clean Glass Liquid Device Cards with Name & #Number)
        item {
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlassLiquidIconOrb(
                        size = 36.dp,
                        cornerRadius = 11.dp,
                        tintColor = MintTeal
                    ) {
                        IconButton(
                            onClick = onScanSubnet,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Scan Nearby",
                                tint = TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    GlassLiquidIconOrb(
                        size = 36.dp,
                        cornerRadius = 11.dp,
                        tintColor = ElectricCyan
                    ) {
                        IconButton(
                            onClick = { showManualIpDialog = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddLink,
                                contentDescription = "Manual IP",
                                tint = TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        if (nearbyPeers.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Troubleshoot",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MintTeal,
                        modifier = Modifier
                            .clickable { onScanSubnet() }
                            .padding(bottom = 10.dp)
                    )
                    Text(
                        text = "Please ensure that the desired target is also on the same Wi-Fi network.",
                        fontSize = 12.5.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(nearbyPeers, key = { it.id }) { peer ->
                val isFav = favoriteIps.contains(peer.ipAddress)
                val isTarget = (activeTargetPeer?.id == peer.id && stagedItems.isNotEmpty())
                NearbyPeerRow(
                    peer = peer,
                    isFavorite = isFav,
                    isTargeted = isTarget,
                    onToggleFavorite = {
                        if (isFav) favoriteIps.remove(peer.ipAddress) else favoriteIps.add(peer.ipAddress)
                    },
                    onPositioned = { offset ->
                        onTargetPeerPositioned(peer.id, offset)
                    },
                    onSwipePreview = { direction ->
                        onSwipePreviewPeer(peer, direction)
                    },
                    onSendWithDirection = { direction ->
                        onSelectPeerToSendWithDirection(peer, direction)
                    },
                    onSendClick = {
                        if (stagedItems.isNotEmpty()) {
                            onSelectPeerToSendWithDirection(peer, "TOP")
                        } else {
                            targetedPeerId = peer.id
                            onSelectPeerToSend(peer)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SelectionActionTile(
    icon: ImageVector,
    label: String,
    bgColor: Color,
    tintColor: Color = ElectricCyan,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .glassLiquidCard(
                cornerRadius = 18.dp,
                tintColor = tintColor,
                secondaryTint = Color(0xFF38BDF8),
                baseColor = bgColor,
                baseAlpha = 0.38f
            )
            .clickable { onClick() }
            .padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        GlassLiquidIconOrb(
            size = 46.dp,
            cornerRadius = 14.dp,
            tintColor = tintColor,
            secondaryColor = Color(0xFF38BDF8)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            maxLines = 1
        )
    }
}

@Composable
fun MediaCard(
    item: SharableItem,
    onToggleSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (item.isSelected) ElectricCyan else BorderSubtle
    val borderWidth = if (item.isSelected) 2.dp else 1.dp

    Box(
        modifier = modifier
            .aspectRatio(1.1f)
            .clip(RoundedCornerShape(16.dp))
            .background(if (item.isSelected) SurfaceElevated else CardNavy)
            .border(borderWidth, borderColor, RoundedCornerShape(16.dp))
            .clickable { onToggleSelect() }
            .testTag("media_card_${item.id}")
    ) {
        CardArtwork(item = item)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            CyberBlack.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(CyberBlack.copy(alpha = 0.65f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = item.subtitle,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = when (item.category) {
                        ShareCategory.PHOTOS -> ElectricCyan
                        ShareCategory.VIDEOS -> SolarAmber
                        ShareCategory.DOCUMENTS -> VividBlue
                        ShareCategory.AUDIO -> RadiantPurple
                        ShareCategory.APPS -> NeonTeal
                    }
                )
            }

            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (item.isSelected) ElectricCyan else CardNavy.copy(alpha = 0.7f))
                    .border(1.dp, if (item.isSelected) ElectricCyan else TextSecondary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (item.isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = CyberBlack,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
        ) {
            Text(
                text = item.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.formattedSize,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )
                Text(
                    text = item.dateAdded,
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun CardArtwork(item: SharableItem) {
    val gradient = when (item.category) {
        ShareCategory.PHOTOS -> Brush.linearGradient(listOf(DeepNavy, DeepViolet.copy(alpha = 0.6f)))
        ShareCategory.VIDEOS -> Brush.linearGradient(listOf(CyberBlack, SolarAmber.copy(alpha = 0.35f)))
        ShareCategory.DOCUMENTS -> Brush.linearGradient(listOf(DeepNavy, VividBlue.copy(alpha = 0.4f)))
        ShareCategory.AUDIO -> Brush.linearGradient(listOf(CardNavy, RadiantPurple.copy(alpha = 0.5f)))
        ShareCategory.APPS -> Brush.linearGradient(listOf(DeepNavy, NeonTeal.copy(alpha = 0.4f)))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient),
        contentAlignment = Alignment.Center
    ) {
        when (item.category) {
            ShareCategory.PHOTOS -> {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = ElectricCyan.copy(alpha = 0.35f),
                    modifier = Modifier.size(48.dp)
                )
            }
            ShareCategory.VIDEOS -> {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(CyberBlack.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = SolarAmber,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            ShareCategory.DOCUMENTS -> {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = VividBlue.copy(alpha = 0.45f),
                    modifier = Modifier.size(46.dp)
                )
            }
            ShareCategory.AUDIO -> {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = RadiantPurple.copy(alpha = 0.45f),
                    modifier = Modifier.size(48.dp)
                )
            }
            ShareCategory.APPS -> {
                Icon(
                    imageVector = Icons.Default.Widgets,
                    contentDescription = null,
                    tint = NeonTeal.copy(alpha = 0.45f),
                    modifier = Modifier.size(46.dp)
                )
            }
        }
    }
}

/**
 * iOS-inspired interactive horizontal swipable card for staged folders, files, and plain text.
 * Swiping left or right past 70% automatically beams the staged items without requiring the user
 * to touch or select a receiver card first!
 */
@Composable
private fun StagedFolderSwipeCard(
    stagedItems: List<SharableItem>,
    nearbyPeers: List<BeamPeer>,
    selectedTargetPeer: BeamPeer?,
    onSelectTargetPeer: (BeamPeer) -> Unit,
    onClearSelection: () -> Unit,
    onOpenPlainTextDialog: () -> Unit = {},
    onSwipePreview: (BeamPeer?, String) -> Unit = { _, _ -> },
    onAutoBeamSwipe: (String) -> Unit = {},
    onBeamToPeer: (BeamPeer, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val dragXAnim = remember { Animatable(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var hasAutoTriggeredInCurrentGesture by remember { mutableStateOf(false) }
    var cardWidthPx by remember { mutableStateOf(0f) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    val activePeer = selectedTargetPeer ?: nearbyPeers.firstOrNull()
    val dragX = dragXAnim.value

    val fallbackWidthPx = remember(density) { with(density) { 300.dp.toPx() } }
    val effectiveWidthPx = if (cardWidthPx > 80f) cardWidthPx else fallbackWidthPx
    // Full swipe span is 45% of card width so 70% of swipe span is easily reachable with one thumb swipe
    val fullSwipeSpanPx = (effectiveWidthPx * 0.45f).coerceAtLeast(110f)
    val swipeRatio = (kotlin.math.abs(dragX) / fullSwipeSpanPx).coerceIn(0f, 1f)
    val swipePercentDisplay = (swipeRatio * 100f).toInt().coerceIn(0, 100)
    val isPastSeventyPercent = swipeRatio >= 0.70f

    val previewThresholdPx = remember(density) { with(density) { 12.dp.toPx() } }
    val determinedDirection = if (dragX >= 0f) "RIGHT" else "LEFT"

    val MintTeal = Color(0xFF5EEAD4)
    val SelectionCardBg = Color(0xFF1F3835)
    val hasPlainTextItem = stagedItems.any { it.mimeType.contains("beamdrop-raw") || it.uri?.startsWith("beamtext://") == true }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { size ->
                if (size.width > 0) {
                    cardWidthPx = size.width.toFloat()
                }
            }
            .graphicsLayer {
                translationX = dragX
                rotationZ = (dragX * 0.035f).coerceIn(-10f, 10f)
                val scale = if (isDragging) 1.03f else 1f
                scaleX = scale
                scaleY = scale
            }
            .glassLiquidCard(
                cornerRadius = 20.dp,
                tintColor = if (isPastSeventyPercent) ElectricCyan else MintTeal,
                secondaryTint = if (isPastSeventyPercent) RadiantPurple else ElectricCyan,
                baseColor = SelectionCardBg,
                baseAlpha = if (isDragging) 0.34f else 0.24f
            )
            .pointerInput(stagedItems.size) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        isDragging = true
                        hasAutoTriggeredInCurrentGesture = false
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        if (hasAutoTriggeredInCurrentGesture) return@detectHorizontalDragGestures
                        coroutineScope.launch {
                            val nextX = dragXAnim.value + dragAmount
                            dragXAnim.snapTo(nextX)
                            val liveDir = if (nextX >= 0f) "RIGHT" else "LEFT"
                            if (kotlin.math.abs(nextX) >= previewThresholdPx) {
                                onSwipePreview(activePeer, liveDir)
                            }
                            val liveRatio = (kotlin.math.abs(nextX) / fullSwipeSpanPx).coerceIn(0f, 1f)
                            // Immediately fire auto-send as soon as user swipes past 70% without needing to touch receiver!
                            if (liveRatio >= 0.70f && !hasAutoTriggeredInCurrentGesture) {
                                hasAutoTriggeredInCurrentGesture = true
                                isDragging = false
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSwipePreview(activePeer, liveDir)
                                val flingTargetX = if (liveDir == "RIGHT") effectiveWidthPx * 0.95f else -effectiveWidthPx * 0.95f
                                dragXAnim.animateTo(
                                    targetValue = flingTargetX,
                                    animationSpec = tween(durationMillis = 190, easing = FastOutSlowInEasing)
                                )
                                onAutoBeamSwipe(liveDir)
                                dragXAnim.snapTo(0f)
                            }
                        }
                    },
                    onDragEnd = {
                        isDragging = false
                        if (!hasAutoTriggeredInCurrentGesture) {
                            val endRatio = (kotlin.math.abs(dragXAnim.value) / fullSwipeSpanPx).coerceIn(0f, 1f)
                            val endDir = if (dragXAnim.value >= 0f) "RIGHT" else "LEFT"
                            if (endRatio >= 0.70f) {
                                hasAutoTriggeredInCurrentGesture = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                coroutineScope.launch {
                                    onSwipePreview(activePeer, endDir)
                                    val flingTargetX = if (endDir == "RIGHT") effectiveWidthPx * 0.95f else -effectiveWidthPx * 0.95f
                                    dragXAnim.animateTo(
                                        targetValue = flingTargetX,
                                        animationSpec = tween(durationMillis = 190, easing = FastOutSlowInEasing)
                                    )
                                    onAutoBeamSwipe(endDir)
                                    dragXAnim.snapTo(0f)
                                }
                            } else {
                                coroutineScope.launch {
                                    dragXAnim.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            }
                        }
                    },
                    onDragCancel = {
                        isDragging = false
                        hasAutoTriggeredInCurrentGesture = false
                        coroutineScope.launch {
                            dragXAnim.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                        }
                    }
                )
            }
            .padding(16.dp)
            .testTag("staged_folder_swipe_card")
    ) {
        // Top header: Selection summary + Clear icon
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (hasPlainTextItem && stagedItems.size == 1) {
                        "Plain Text Staged • Ready to Beam"
                    } else {
                        "Selected (${stagedItems.size}) • ${SharableItem.formatFileSize(stagedItems.sumOf { it.sizeBytes })}"
                    },
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MintTeal
                )
                Text(
                    text = "Swipe card >70% left/right to send immediately without selecting receiver",
                    fontSize = 11.5.sp,
                    color = TextPrimary.copy(alpha = 0.88f)
                )
            }

            IconButton(
                onClick = onClearSelection,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("clear_staged_selection_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear Selection",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Target Device Pill (auto-targeted if available)
        if (activePeer != null) {
            val isComputer = activePeer.deviceType == DeviceType.COMPUTER ||
                activePeer.name.contains("VICTUS", ignoreCase = true) ||
                activePeer.name.contains("Windows", ignoreCase = true) ||
                activePeer.name.contains("PC", ignoreCase = true)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassLiquidPill(cornerRadius = 10.dp, tintColor = ElectricCyan)
                    .clickable {
                        if (nearbyPeers.size > 1) {
                            val currIdx = nearbyPeers.indexOfFirst { it.id == activePeer.id }
                            val nextIdx = (currIdx + 1) % nearbyPeers.size
                            onSelectTargetPeer(nearbyPeers[nextIdx])
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isComputer) Icons.Default.Computer else Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Auto-Receiver: ${activePeer.name}",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }

                if (nearbyPeers.size > 1) {
                    Text(
                        text = "Tap to switch (${nearbyPeers.size})",
                        fontSize = 11.sp,
                        color = MintTeal
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }

        // Staged Items List (Showing Plain Text / Folders / Files)
        stagedItems.take(3).forEach { staged ->
            val isPlainText = staged.mimeType.contains("beamdrop-raw") || staged.uri?.startsWith("beamtext://") == true
            val isDir = !isPlainText && (
                staged.mimeType.contains("directory", ignoreCase = true) ||
                    staged.title.contains("folder", ignoreCase = true)
                )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when {
                        isPlainText -> Icons.Default.Notes
                        isDir -> Icons.Default.Folder
                        else -> Icons.Default.Description
                    },
                    contentDescription = null,
                    tint = when {
                        isPlainText -> RadiantPurple
                        isDir -> MintTeal
                        else -> ElectricCyan
                    },
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = staged.title,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isPlainText) {
                        Text(
                            text = "Plain Text • Opens Copy Pop-up on Receiver",
                            fontSize = 10.5.sp,
                            color = MintTeal
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                if (isPlainText) {
                    Text(
                        text = "Edit",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan,
                        modifier = Modifier
                            .clickable { onOpenPlainTextDialog() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                } else {
                    Text(
                        text = staged.formattedSize,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        }
        if (stagedItems.size > 3) {
            Text(
                text = "+ ${stagedItems.size - 3} more item(s)",
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Dynamic iOS >70% Swipe Progress Bar & Action Bar
        if (isDragging) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassLiquidPill(
                        cornerRadius = 12.dp,
                        tintColor = if (isPastSeventyPercent) RadiantPurple else ElectricCyan,
                        baseColor = if (isPastSeventyPercent) Color(0xFF261935) else Color(0xFF132A26)
                    )
                    .padding(vertical = 10.dp, horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (determinedDirection == "RIGHT") Icons.Default.ArrowForward else Icons.Default.ArrowBack,
                        contentDescription = null,
                        tint = if (isPastSeventyPercent) MintTeal else TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPastSeventyPercent) {
                            "70%+ Reached — Beaming Now!"
                        } else {
                            "Swiping $swipePercentDisplay% (70% auto-sends) ➔"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPastSeventyPercent) MintTeal else TextPrimary
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.NearMe,
                        contentDescription = null,
                        tint = MintTeal,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Swipe >70% left/right to auto-beam ➔",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary.copy(alpha = 0.90f)
                    )
                }

                Button(
                    onClick = {
                        if (activePeer != null) {
                            onBeamToPeer(activePeer, "RIGHT")
                        } else {
                            onAutoBeamSwipe("RIGHT")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = Color(0xFF0F1E1B)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("beam_staged_now_btn")
                ) {
                    Text(
                        text = "Beam ➔",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
