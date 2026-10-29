package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.QueueStatus
import com.example.model.QueuedTransfer
import com.example.model.TransferDirection
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CardNavy
import com.example.ui.theme.CyberBlack
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonTeal
import com.example.ui.theme.RadiantPurple
import com.example.ui.theme.SolarAmber
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun PersistentTransferQueueHUD(
    queue: List<QueuedTransfer>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onCancelTransfer: (String) -> Unit,
    onCancelAll: () -> Unit,
    onClearCompleted: () -> Unit,
    onOpenFile: (QueuedTransfer) -> Unit,
    onRetryTransfer: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val activeTransfers = queue.filter { it.isActive }
    val activeCount = activeTransfers.size
    val firstActive = activeTransfers.firstOrNull()

    // Immediately purge any finished items so the indicator clears upon completion
    androidx.compose.runtime.LaunchedEffect(queue) {
        if (queue.any { it.isFinished }) {
            onClearCompleted()
        }
    }

    // Filter selection state for expanded view
    var selectedFilter by remember { mutableStateOf("ACTIVE") }

    val displayedQueue = activeTransfers

    AnimatedVisibility(
        visible = activeTransfers.isNotEmpty(),
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .animateContentSize()
        ) {
            if (true) {
                // ==========================================
                // Mini Dock Capsule (Persistent bottom pill)
                // ==========================================
                Surface(
                    onClick = onToggleExpand,
                    shape = RoundedCornerShape(20.dp),
                    color = SurfaceElevated,
                    tonalElevation = 8.dp,
                    shadowElevation = 10.dp,
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.2.dp,
                        brush = Brush.horizontalGradient(listOf(ElectricCyan, RadiantPurple))
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transfer_queue_mini_dock")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            // Direction Indicator Icon
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (firstActive?.direction == TransferDirection.UPLOAD) {
                                            ElectricCyan.copy(alpha = 0.2f)
                                        } else {
                                            SolarAmber.copy(alpha = 0.2f)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (firstActive?.direction == TransferDirection.UPLOAD) {
                                        Icons.Default.ArrowUpward
                                    } else {
                                        Icons.Default.ArrowDownward
                                    },
                                    contentDescription = null,
                                    tint = if (firstActive?.direction == TransferDirection.UPLOAD) ElectricCyan else SolarAmber,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (activeCount > 0) {
                                            "$activeCount Active Transfer${if (activeCount > 1) "s" else ""}"
                                        } else {
                                            "Transfers Completed (${queue.size})"
                                        },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    if (firstActive != null && firstActive.speedMbps > 0) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "• ${firstActive.formattedSpeed}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = ElectricCyan
                                        )
                                    }
                                }

                                if (firstActive != null) {
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "${firstActive.fileName} (${firstActive.progressPercent}%)",
                                        fontSize = 11.sp,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { firstActive.progress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = ElectricCyan,
                                        trackColor = BorderSubtle,
                                        strokeCap = StrokeCap.Round
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (firstActive != null) {
                                IconButton(
                                    onClick = { onCancelTransfer(firstActive.id) },
                                    modifier = Modifier
                                        .size(30.dp)
                                        .testTag("quick_cancel_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cancel Transfer",
                                        tint = SolarAmber,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = onToggleExpand,
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("expand_queue_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ExpandLess,
                                    contentDescription = "Expand Queue",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                // ==========================================
                // Expanded Full Queue Management Drawer
                // ==========================================
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(SurfaceElevated)
                        .border(
                            width = 1.5.dp,
                            brush = Brush.linearGradient(listOf(ElectricCyan, RadiantPurple)),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(16.dp)
                        .testTag("transfer_queue_expanded_sheet")
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.SwapVert,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Transfer Queue",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (activeCount > 0) ElectricCyan.copy(alpha = 0.2f) else CardNavy)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "$activeCount active",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (activeCount > 0) ElectricCyan else TextSecondary
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (activeCount > 0) {
                                    TextButton(
                                        onClick = onCancelAll,
                                        modifier = Modifier.testTag("cancel_all_queue_btn")
                                    ) {
                                        Text(
                                            text = "Cancel All",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SolarAmber
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = onToggleExpand,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("collapse_queue_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ExpandMore,
                                        contentDescription = "Collapse",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Filter Chips Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            QueueFilterChip(
                                label = "All (${queue.size})",
                                isSelected = selectedFilter == "ALL",
                                onClick = { selectedFilter = "ALL" }
                            )
                            QueueFilterChip(
                                label = "Active ($activeCount)",
                                isSelected = selectedFilter == "ACTIVE",
                                onClick = { selectedFilter = "ACTIVE" }
                            )
                            QueueFilterChip(
                                label = "Finished (${queue.count { it.isFinished }})",
                                isSelected = selectedFilter == "FINISHED",
                                onClick = { selectedFilter = "FINISHED" }
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            if (queue.any { it.isFinished }) {
                                IconButton(
                                    onClick = onClearCompleted,
                                    modifier = Modifier.size(28.dp).testTag("clear_completed_queue")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteSweep,
                                        contentDescription = "Clear finished",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Queue items list
                        if (displayedQueue.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No transfers in this view",
                                    fontSize = 13.sp,
                                    color = TextSecondary
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((displayedQueue.size * 88).coerceIn(100, 320).dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(displayedQueue, key = { it.id }) { item ->
                                    QueuedTransferCard(
                                        transfer = item,
                                        onCancel = { onCancelTransfer(item.id) },
                                        onRetry = { onRetryTransfer(item.id) },
                                        onOpenFile = { onOpenFile(item) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QueueFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) ElectricCyan else CardNavy)
            .border(1.dp, if (isSelected) ElectricCyan else BorderSubtle, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) CyberBlack else TextSecondary
        )
    }
}

@Composable
fun QueuedTransferCard(
    transfer: QueuedTransfer,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOpenFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardNavy)
            .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
            .padding(12.dp)
            .testTag("queued_transfer_card_${transfer.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Direction & Status Badge Circle
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                when (transfer.status) {
                                    QueueStatus.COMPLETED -> NeonTeal.copy(alpha = 0.2f)
                                    QueueStatus.FAILED, QueueStatus.CANCELLED -> SolarAmber.copy(alpha = 0.2f)
                                    else -> if (transfer.direction == TransferDirection.UPLOAD) {
                                        ElectricCyan.copy(alpha = 0.2f)
                                    } else {
                                        SolarAmber.copy(alpha = 0.2f)
                                    }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (transfer.status) {
                                QueueStatus.COMPLETED -> Icons.Default.CheckCircle
                                QueueStatus.FAILED, QueueStatus.CANCELLED -> Icons.Default.Close
                                else -> if (transfer.direction == TransferDirection.UPLOAD) {
                                    Icons.Default.ArrowUpward
                                } else {
                                    Icons.Default.ArrowDownward
                                }
                            },
                            contentDescription = null,
                            tint = when (transfer.status) {
                                QueueStatus.COMPLETED -> NeonTeal
                                QueueStatus.FAILED, QueueStatus.CANCELLED -> SolarAmber
                                else -> if (transfer.direction == TransferDirection.UPLOAD) ElectricCyan else SolarAmber
                            },
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = transfer.fileName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (transfer.direction == TransferDirection.UPLOAD) {
                                    "To ${transfer.peerName}"
                                } else {
                                    "From ${transfer.peerName}"
                                },
                                fontSize = 11.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "• ${transfer.formattedSize}",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }

                // Actions (Cancel / Retry / Open)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (transfer.status) {
                        QueueStatus.IN_PROGRESS, QueueStatus.QUEUED -> {
                            IconButton(
                                onClick = onCancel,
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("cancel_item_${transfer.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel item",
                                    tint = SolarAmber,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        QueueStatus.COMPLETED -> {
                            if (transfer.direction == TransferDirection.DOWNLOAD) {
                                IconButton(
                                    onClick = onOpenFile,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("open_item_${transfer.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = "Open file",
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                        QueueStatus.FAILED, QueueStatus.CANCELLED -> {
                            IconButton(
                                onClick = onRetry,
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("retry_item_${transfer.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay,
                                    contentDescription = "Retry item",
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        else -> {}
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress Bar
            LinearProgressIndicator(
                progress = {
                    when (transfer.status) {
                        QueueStatus.COMPLETED -> 1f
                        QueueStatus.FAILED, QueueStatus.CANCELLED -> 0f
                        else -> transfer.progress
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = when (transfer.status) {
                    QueueStatus.COMPLETED -> NeonTeal
                    QueueStatus.FAILED, QueueStatus.CANCELLED -> SolarAmber
                    else -> ElectricCyan
                },
                trackColor = BorderSubtle,
                strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Stats Sub-Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (transfer.status) {
                        QueueStatus.COMPLETED -> "Completed"
                        QueueStatus.CANCELLED -> "Cancelled"
                        QueueStatus.FAILED -> transfer.errorReason ?: "Failed"
                        QueueStatus.QUEUED -> "Queued..."
                        QueueStatus.PAUSED -> "Paused"
                        QueueStatus.IN_PROGRESS -> "${transfer.formattedTransferred} of ${transfer.formattedSize} (${transfer.progressPercent}%)"
                    },
                    fontSize = 11.sp,
                    color = when (transfer.status) {
                        QueueStatus.COMPLETED -> NeonTeal
                        QueueStatus.FAILED, QueueStatus.CANCELLED -> SolarAmber
                        else -> TextSecondary
                    },
                    fontWeight = FontWeight.Medium
                )

                if (transfer.status == QueueStatus.IN_PROGRESS && transfer.speedMbps > 0) {
                    Text(
                        text = "${transfer.formattedSpeed} • ETA: ${transfer.etaSeconds}s",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ElectricCyan
                    )
                }
            }
        }
    }
}
