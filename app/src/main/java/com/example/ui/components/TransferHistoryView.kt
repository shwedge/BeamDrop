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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TransferRecord
import com.example.model.SharableItem
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonTeal
import com.example.ui.theme.RadiantPurple
import com.example.ui.theme.SolarAmber
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VividBlue

/**
 * Glass Liquid Transfer History View.
 * Stores and highlights what was transferred recently (files & folders as the primary title),
 * with a sleek device & direction bar directly below the file/folder title.
 */
@Composable
fun TransferHistoryView(
    transfers: List<TransferRecord>,
    totalBytesTransferred: Long,
    onDeleteRecord: (Long) -> Unit,
    onClearAll: () -> Unit,
    onOpenFile: (TransferRecord) -> Unit = {},
    onShareFile: (TransferRecord) -> Unit = {},
    onSaveFileAs: (TransferRecord) -> Unit = {},
    onRetryTransfer: (TransferRecord) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val MintTeal = Color(0xFF5EEAD4)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
            .testTag("transfer_history_screen")
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Top Header: "Recent transfers" + Clear All action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Recent transfers",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                if (transfers.isNotEmpty() && totalBytesTransferred > 0L) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .glassLiquidPill(
                                cornerRadius = 7.dp,
                                tintColor = MintTeal
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${transfers.size} item(s) • ${SharableItem.formatFileSize(totalBytesTransferred)} total",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MintTeal
                        )
                    }
                }
            }

            if (transfers.isNotEmpty()) {
                GlassLiquidIconOrb(
                    size = 38.dp,
                    cornerRadius = 12.dp,
                    tintColor = MintTeal,
                    secondaryColor = ElectricCyan
                ) {
                    IconButton(
                        onClick = onClearAll,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("clear_all_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear History",
                            tint = MintTeal,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (transfers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassLiquidCard(cornerRadius = 22.dp, tintColor = MintTeal)
                        .padding(horizontal = 24.dp, vertical = 36.dp)
                ) {
                    GlassLiquidIconOrb(
                        size = 54.dp,
                        cornerRadius = 18.dp,
                        tintColor = MintTeal,
                        secondaryColor = ElectricCyan
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MintTeal,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "No recent transfers yet",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Files, media, and folders you send or receive will appear here.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 100.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(transfers, key = { it.id }) { record ->
                    RecentTransferItemCard(
                        record = record,
                        onDelete = { onDeleteRecord(record.id) },
                        onOpen = { onOpenFile(record) },
                        onShare = { onShareFile(record) },
                        onSaveAs = { onSaveFileAs(record) },
                        onRetry = { onRetryTransfer(record) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentTransferItemCard(
    record: TransferRecord,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onSaveAs: () -> Unit,
    onRetry: () -> Unit
) {
    val cleanDeviceName = remember(record.peerDeviceName) {
        record.peerDeviceName
            .substringBefore(" (")
            .ifBlank { "BeamDrop Peer" }
    }

    val isComputer = remember(record.peerDeviceName) {
        record.peerDeviceName.contains("VICTUS", ignoreCase = true) ||
            record.peerDeviceName.contains("Windows", ignoreCase = true) ||
            record.peerDeviceName.contains("PC", ignoreCase = true) ||
            record.peerDeviceName.contains("Laptop", ignoreCase = true) ||
            record.peerDeviceName.contains("Desktop", ignoreCase = true)
    }

    val isSent = record.direction.equals("SENT", ignoreCase = true)

    val (fileIcon, iconTint) = remember(record.fileName, record.fileType) {
        resolveFileIconAndTint(record.fileName, record.fileType)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassLiquidCard(
                cornerRadius = 18.dp,
                tintColor = iconTint,
                secondaryTint = Color(0xFF5EEAD4)
            )
            .clickable {
                if (!isSent) onOpen() else onRetry()
            }
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("history_row_${record.id}"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            // Glass Liquid File/Folder Icon Orb
            GlassLiquidIconOrb(
                size = 48.dp,
                cornerRadius = 15.dp,
                tintColor = iconTint,
                secondaryColor = ElectricCyan
            ) {
                Icon(
                    imageVector = fileIcon,
                    contentDescription = record.fileName,
                    tint = TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                // 1. Primary Title: Transferred File or Folder Name
                Text(
                    text = record.fileName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 2. Subtitle Bar directly below the file/folder title: Glass Liquid Device Badge + Size
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Direction + Peer Device Glass Liquid Pill
                    Row(
                        modifier = Modifier
                            .glassLiquidPill(
                                cornerRadius = 7.dp,
                                tintColor = if (isSent) ElectricCyan else NeonTeal
                            )
                            .padding(horizontal = 8.dp, vertical = 2.5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isSent) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = if (isSent) "Sent" else "Received",
                            tint = if (isSent) ElectricCyan else NeonTeal,
                            modifier = Modifier.size(11.dp)
                        )
                        Icon(
                            imageVector = if (isComputer) Icons.Default.Computer else Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = TextPrimary.copy(alpha = 0.88f),
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = cleanDeviceName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary.copy(alpha = 0.94f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // File Size Glass Liquid Pill
                    Box(
                        modifier = Modifier
                            .glassLiquidPill(
                                cornerRadius = 7.dp,
                                tintColor = iconTint
                            )
                            .padding(horizontal = 7.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            text = SharableItem.formatFileSize(record.fileSize),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary.copy(alpha = 0.85f),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        // Right Actions: Glass Liquid Save As, Share & Delete Orbs
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (!isSent) {
                GlassLiquidIconOrb(
                    size = 34.dp,
                    cornerRadius = 11.dp,
                    tintColor = NeonTeal,
                    secondaryColor = ElectricCyan
                ) {
                    IconButton(
                        onClick = onSaveAs,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("save_as_history_${record.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SaveAlt,
                            contentDescription = "Save File To Folder",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            GlassLiquidIconOrb(
                size = 34.dp,
                cornerRadius = 11.dp,
                tintColor = ElectricCyan,
                secondaryColor = NeonTeal
            ) {
                IconButton(
                    onClick = onShare,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share File",
                        tint = TextPrimary.copy(alpha = 0.90f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            GlassLiquidIconOrb(
                size = 34.dp,
                cornerRadius = 11.dp,
                tintColor = RadiantPurple,
                secondaryColor = ElectricCyan
            ) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove",
                        tint = TextPrimary.copy(alpha = 0.80f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

private fun resolveFileIconAndTint(fileName: String, fileType: String): Pair<ImageVector, Color> {
    val lower = fileName.lowercase()
    return when {
        fileType.contains("PHOTO", ignoreCase = true) ||
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
            lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".heic") -> {
            Icons.Default.Image to ElectricCyan
        }
        fileType.contains("VIDEO", ignoreCase = true) ||
            lower.endsWith(".mp4") || lower.endsWith(".mkv") ||
            lower.endsWith(".mov") || lower.endsWith(".webm") -> {
            Icons.Default.PlayArrow to SolarAmber
        }
        fileType.contains("AUDIO", ignoreCase = true) ||
            lower.endsWith(".mp3") || lower.endsWith(".wav") ||
            lower.endsWith(".flac") || lower.endsWith(".m4a") -> {
            Icons.Default.GraphicEq to RadiantPurple
        }
        lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z") ||
            fileType.contains("FOLDER", ignoreCase = true) -> {
            Icons.Default.Folder to NeonTeal
        }
        else -> {
            Icons.Default.Description to VividBlue
        }
    }
}
