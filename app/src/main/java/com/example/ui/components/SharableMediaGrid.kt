package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
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
    onToggleSelect: (SharableItem) -> Unit,
    onPickFromStorage: () -> Unit,
    onPickDocument: () -> Unit = onPickFromStorage,
    onAddTextSnippet: (String) -> Unit = {},
    onClearSelection: () -> Unit = {},
    onSelectPeerToSend: (BeamPeer) -> Unit = {},
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

    val MintTeal = Color(0xFF5EEAD4)
    val SelectionCardBg = Color(0xFF28423F)

    if (showTextDialog) {
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            containerColor = CardNavy,
            title = {
                Text("Send Text Message", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("Enter text or link to share...", color = TextSecondary) },
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
                    Text("Stage Text", fontWeight = FontWeight.Bold)
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
                Text("Connect by IP Address", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                OutlinedTextField(
                    value = manualIpText,
                    onValueChange = { manualIpText = it },
                    placeholder = { Text("e.g. 192.168.1.109", color = TextSecondary) },
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
                        if (manualIpText.isNotBlank()) {
                            onAddManualIp(manualIpText.trim())
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

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Selection Section (File | Media | Text | Paste — Matches Screenshot 4)
        item(span = { GridItemSpan(2) }) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Selection",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SelectionActionTile(
                        icon = Icons.Default.Description,
                        label = "File",
                        bgColor = SelectionCardBg,
                        onClick = onPickDocument,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_from_device_card")
                    )
                    SelectionActionTile(
                        icon = Icons.Default.Folder,
                        label = "Media",
                        bgColor = SelectionCardBg,
                        onClick = onPickFromStorage,
                        modifier = Modifier.weight(1f)
                    )
                    SelectionActionTile(
                        icon = Icons.Default.Notes,
                        label = "Text",
                        bgColor = SelectionCardBg,
                        onClick = { showTextDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                    SelectionActionTile(
                        icon = Icons.Default.ContentPaste,
                        label = "Paste",
                        bgColor = SelectionCardBg,
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

        // 2. Active Staged Files Box (Automatically cleared when transfer completes!)
        if (stagedItems.isNotEmpty()) {
            item(span = { GridItemSpan(2) }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SelectionCardBg.copy(alpha = 0.75f))
                        .border(1.dp, MintTeal.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${stagedItems.size} file(s) ready to send",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MintTeal
                            )
                            Text(
                                text = "Total size: ${SharableItem.formatFileSize(stagedItems.sumOf { it.sizeBytes })} • Tap a device below to send",
                                fontSize = 11.sp,
                                color = TextPrimary.copy(alpha = 0.85f)
                            )
                        }

                        IconButton(
                            onClick = onClearSelection,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear Selection",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    stagedItems.take(3).forEach { staged ->
                        Text(
                            text = "• ${staged.title} (${staged.formattedSize})",
                            fontSize = 12.sp,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                    if (stagedItems.size > 3) {
                        Text(
                            text = "+ ${stagedItems.size - 3} more file(s)",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // 3. Nearby Devices Section right on the Send screen (Matches Screenshot 4)
        item(span = { GridItemSpan(2) }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Nearby devices",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onScanSubnet,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Scan Nearby",
                                tint = TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = { showManualIpDialog = true },
                            modifier = Modifier.size(34.dp)
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

                Spacer(modifier = Modifier.height(8.dp))

                if (nearbyPeers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(CardNavy.copy(alpha = 0.6f))
                            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                            .padding(vertical = 16.dp, horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Please ensure that the desired target is also on the same Wi-Fi network.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    val primaryPeer = nearbyPeers.first()
                    Button(
                        onClick = { onSelectPeerToSend(primaryPeer) },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MintTeal,
                            contentColor = CyberBlack
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .height(52.dp)
                            .onGloballyPositioned { coords ->
                                val pos = coords.positionInRoot()
                                val center = Offset(
                                    x = pos.x + coords.size.width / 2f,
                                    y = pos.y + coords.size.height / 2f
                                )
                                onSourceButtonPositioned(center)
                            }
                            .fluidAuraRippleButton(
                                isPeerDiscovered = nearbyPeers.isNotEmpty(),
                                cornerRadius = 16.dp
                            )
                            .testTag("file_transfer_aura_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Transfer Files",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (stagedItems.isNotEmpty()) {
                                "Transfer ${stagedItems.size} File(s) to ${primaryPeer.name}"
                            } else {
                                "Beam Files to ${primaryPeer.name}"
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        nearbyPeers.forEach { peer ->
                            NearbyPeerRow(
                                peer = peer,
                                onPositioned = { offset ->
                                    onTargetPeerPositioned(peer.id, offset)
                                },
                                onSendClick = { onSelectPeerToSend(peer) }
                            )
                        }
                    }
                }
            }
        }

        // 4. Optional On-Device Gallery Section Header
        if (items.isNotEmpty()) {
            item(span = { GridItemSpan(2) }) {
                Text(
                    text = "Device Library",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            items(items, key = { it.id }) { item ->
                MediaCard(
                    item = item,
                    onToggleSelect = { onToggleSelect(item) }
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.dp, Color(0xFF5EEAD4).copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = TextPrimary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
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
