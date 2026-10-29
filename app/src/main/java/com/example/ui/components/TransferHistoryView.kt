package com.example.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TransferRecord
import com.example.model.SharableItem
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CardNavy
import com.example.ui.theme.CoralRed
import com.example.ui.theme.CyberBlack
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonTeal
import com.example.ui.theme.RadiantPurple
import com.example.ui.theme.SolarAmber
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class HistoryFilter(val label: String, val tag: String) {
    ALL("All Logs", "history_filter_all"),
    COMPLETED("Success", "history_filter_completed"),
    FAILED("Failed / Cancelled", "history_filter_failed"),
    SENT("Sent", "history_filter_sent"),
    RECEIVED("Received", "history_filter_received")
}

@Composable
fun TransferHistoryView(
    transfers: List<TransferRecord>,
    totalBytesTransferred: Long,
    onDeleteRecord: (Long) -> Unit,
    onClearAll: () -> Unit,
    onOpenFile: (TransferRecord) -> Unit = {},
    onShareFile: (TransferRecord) -> Unit = {},
    onRetryTransfer: (TransferRecord) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf(HistoryFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }

    val completedCount = transfers.count { it.isSuccess }
    val failedCount = transfers.count { it.isFailed || it.isCancelled }
    val peakSpeedMbps = transfers.maxOfOrNull { it.transferRateMbps } ?: 0f

    val filteredTransfers = remember(transfers, selectedFilter, searchQuery) {
        transfers.filter { record ->
            val matchesFilter = when (selectedFilter) {
                HistoryFilter.ALL -> true
                HistoryFilter.COMPLETED -> record.isSuccess
                HistoryFilter.FAILED -> record.isFailed || record.isCancelled
                HistoryFilter.SENT -> record.direction.equals("SENT", ignoreCase = true)
                HistoryFilter.RECEIVED -> record.direction.equals("RECEIVED", ignoreCase = true)
            }
            val matchesQuery = if (searchQuery.isBlank()) {
                true
            } else {
                record.fileName.contains(searchQuery, ignoreCase = true) ||
                    record.peerDeviceName.contains(searchQuery, ignoreCase = true) ||
                    record.status.contains(searchQuery, ignoreCase = true)
            }
            matchesFilter && matchesQuery
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("transfer_history_screen")
    ) {
        // Summary Metrics Dashboard Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceElevated)
                .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
                .padding(vertical = 14.dp, horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MetricColumn(
                label = "Total Logs",
                value = transfers.size.toString(),
                valueColor = TextPrimary
            )

            VerticalDividerBar()

            MetricColumn(
                label = "Success / Fail",
                value = "$completedCount / $failedCount",
                valueColor = if (failedCount == 0) NeonTeal else SolarAmber
            )

            VerticalDividerBar()

            MetricColumn(
                label = "Data Beamed",
                value = SharableItem.formatFileSize(totalBytesTransferred),
                valueColor = ElectricCyan
            )

            VerticalDividerBar()

            MetricColumn(
                label = "Peak Speed",
                value = if (peakSpeedMbps > 0f) String.format(Locale.US, "%.1f MB/s", peakSpeedMbps) else "0.0 MB/s",
                valueColor = SolarAmber
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Input Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = "Search file names, devices, or status...",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search transfer history",
                    tint = ElectricCyan,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CardNavy,
                unfocusedContainerColor = CardNavy,
                focusedBorderColor = ElectricCyan,
                unfocusedBorderColor = BorderSubtle,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = ElectricCyan
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("history_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Filter Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HistoryFilter.entries.forEach { filter ->
                val isSelected = selectedFilter == filter
                val count = when (filter) {
                    HistoryFilter.ALL -> transfers.size
                    HistoryFilter.COMPLETED -> completedCount
                    HistoryFilter.FAILED -> failedCount
                    HistoryFilter.SENT -> transfers.count { it.direction.equals("SENT", ignoreCase = true) }
                    HistoryFilter.RECEIVED -> transfers.count { it.direction.equals("RECEIVED", ignoreCase = true) }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isSelected) ElectricCyan.copy(alpha = 0.18f)
                            else CardNavy
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) ElectricCyan else BorderSubtle,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable { selectedFilter = filter }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag(filter.tag)
                ) {
                    Text(
                        text = "${filter.label} ($count)",
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) ElectricCyan else TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // History list header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TRANSFER AUDIT LOG (${filteredTransfers.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.sp
            )

            if (transfers.isNotEmpty()) {
                TextButton(
                    onClick = onClearAll,
                    modifier = Modifier.testTag("clear_history_btn")
                ) {
                    Text(
                        text = "Clear All Logs",
                        fontSize = 12.sp,
                        color = CoralRed,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (filteredTransfers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = TextSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (transfers.isEmpty()) "No transfer logs recorded yet" else "No matching transfer logs",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (transfers.isEmpty()) {
                            "Past uploads, downloads, timestamps, and success/failure statuses will appear here automatically."
                        } else {
                            "Try switching the status filter or clearing your search query."
                        },
                        fontSize = 12.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("history_list")
            ) {
                items(filteredTransfers, key = { it.id }) { record ->
                    HistoryRecordRow(
                        record = record,
                        onOpen = { onOpenFile(record) },
                        onShare = { onShareFile(record) },
                        onRetry = { onRetryTransfer(record) },
                        onDelete = { onDeleteRecord(record.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricColumn(
    label: String,
    value: String,
    valueColor: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}

@Composable
private fun VerticalDividerBar() {
    Box(
        modifier = Modifier
            .height(28.dp)
            .width(1.dp)
            .background(BorderSubtle)
    )
}

@Composable
fun HistoryRecordRow(
    record: TransferRecord,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRetry: () -> Unit = {},
    onDelete: () -> Unit
) {
    val isSent = record.direction.equals("SENT", ignoreCase = true)
    val timeFormat = remember { SimpleDateFormat("MMM d, yyyy • hh:mm:ss a", Locale.getDefault()) }
    val dateString = timeFormat.format(Date(record.timestamp))
    val relativeTime = formatRelativeTime(record.timestamp)

    val (statusLabel, statusColor, statusIcon) = when {
        record.isSuccess -> Triple("SUCCESS", NeonTeal, Icons.Default.CheckCircle)
        record.isCancelled -> Triple("CANCELLED", SolarAmber, Icons.Default.Cancel)
        else -> Triple("FAILED", CoralRed, Icons.Default.ErrorOutline)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardNavy)
            .border(
                width = 1.dp,
                color = when {
                    record.isFailed -> CoralRed.copy(alpha = 0.4f)
                    record.isCancelled -> SolarAmber.copy(alpha = 0.35f)
                    else -> BorderSubtle
                },
                shape = RoundedCornerShape(16.dp)
            )
            .padding(14.dp)
            .animateContentSize()
            .testTag("history_row_${record.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Direction badge circle
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                record.isFailed -> CoralRed.copy(alpha = 0.15f)
                                isSent -> RadiantPurple.copy(alpha = 0.15f)
                                else -> ElectricCyan.copy(alpha = 0.15f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSent) Icons.Default.CallMade else Icons.Default.CallReceived,
                        contentDescription = if (isSent) "Sent transfer" else "Received transfer",
                        tint = when {
                            record.isFailed -> CoralRed
                            isSent -> RadiantPurple
                            else -> ElectricCyan
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = record.fileName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        // Status Pill Badge
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(statusColor.copy(alpha = 0.15f))
                                .border(0.8.dp, statusColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                .testTag("history_status_${record.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = statusLabel,
                                tint = statusColor,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = statusLabel,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = statusColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Peer & File Metadata Row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isSent) "To ${record.peerDeviceName}" else "From ${record.peerDeviceName}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary
                        )
                        Text(text = " • ", fontSize = 11.sp, color = TextSecondary)
                        Text(
                            text = SharableItem.formatFileSize(record.fileSize),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ElectricCyan
                        )
                        if (record.transferRateMbps > 0f && record.isSuccess) {
                            Text(text = " • ", fontSize = 11.sp, color = TextSecondary)
                            Text(
                                text = String.format(Locale.US, "%.1f MB/s", record.transferRateMbps),
                                fontSize = 11.sp,
                                color = NeonTeal
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Timestamp Row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "Timestamp",
                            tint = TextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$dateString ($relativeTime)",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            // Action Buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (record.isFailed || record.isCancelled) {
                    IconButton(
                        onClick = onRetry,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("history_retry_${record.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay,
                            contentDescription = "Retry transfer",
                            tint = SolarAmber,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (record.isSuccess && !record.localUri.isNullOrEmpty()) {
                    IconButton(
                        onClick = onOpen,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("history_open_${record.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Open file",
                            tint = ElectricCyan,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    IconButton(
                        onClick = onShare,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("history_share_${record.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share file",
                            tint = NeonTeal,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("history_delete_${record.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete log entry",
                        tint = TextSecondary,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        // Error or Cancellation detail banner if present
        if (!record.errorMessage.isNullOrBlank() && (record.isFailed || record.isCancelled)) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(CyberBlack)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Reason: ${record.errorMessage}",
                    fontSize = 11.sp,
                    color = if (record.isFailed) CoralRed else SolarAmber,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun formatRelativeTime(timestampMs: Long): String {
    val diffMs = (System.currentTimeMillis() - timestampMs).coerceAtLeast(0L)
    val seconds = diffMs / 1000L
    return when {
        seconds < 45 -> "Just now"
        seconds < 3600 -> "${seconds / 60}m ago"
        seconds < 86400 -> "${seconds / 3600}h ago"
        else -> "${seconds / 86400}d ago"
    }
}
