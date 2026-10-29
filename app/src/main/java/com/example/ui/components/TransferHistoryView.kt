package com.example.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Widgets
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
import androidx.compose.ui.graphics.Brush
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
    ALL("All Files", "history_filter_all"),
    COMPLETED("Completed", "history_filter_completed"),
    FAILED("Other", "history_filter_failed"),
    SENT("Sent", "history_filter_sent"),
    RECEIVED("Received", "history_filter_received")
}

/**
 * Pure History Gallery showcasing transferred files and media items.
 * Removes all developer log traces, technical error traces, and diagnostic readouts.
 */
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
                    record.peerDeviceName.contains(searchQuery, ignoreCase = true)
            }
            matchesFilter && matchesQuery
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("transfer_history_screen")
    ) {
        // 1. Elegant Header Banner (Total Transferred Files & Storage)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(CardNavy)
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(listOf(ElectricCyan.copy(alpha = 0.5f), RadiantPurple.copy(alpha = 0.4f))),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(vertical = 16.dp, horizontal = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Transferred Files",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${transfers.size} items • ${SharableItem.formatFileSize(totalBytesTransferred)} total",
                    fontSize = 12.sp,
                    color = ElectricCyan,
                    fontWeight = FontWeight.Medium
                )
            }

            if (transfers.isNotEmpty()) {
                IconButton(
                    onClick = onClearAll,
                    modifier = Modifier.testTag("clear_all_history_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Clear All",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Search Bar for File Names
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(text = "Search photos, videos, documents...", fontSize = 12.sp, color = TextSecondary)
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
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

        // 3. Clean Filter Chips (All Files | Received | Sent)
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
                    HistoryFilter.COMPLETED -> transfers.count { it.isSuccess }
                    HistoryFilter.FAILED -> transfers.count { it.isFailed || it.isCancelled }
                    HistoryFilter.SENT -> transfers.count { it.direction.equals("SENT", ignoreCase = true) }
                    HistoryFilter.RECEIVED -> transfers.count { it.direction.equals("RECEIVED", ignoreCase = true) }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (isSelected) ElectricCyan.copy(alpha = 0.18f)
                            else CardNavy
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) ElectricCyan else BorderSubtle,
                            shape = RoundedCornerShape(18.dp)
                        )
                        .clickable { selectedFilter = filter }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                        .testTag(filter.tag),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${filter.label} ($count)",
                        fontSize = 11.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) ElectricCyan else TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 4. File Elements List
        if (filteredTransfers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(CardNavy),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = ElectricCyan.copy(alpha = 0.5f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Text(
                        text = "No files transferred yet",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Transferred photos, 4K videos, documents, and files will appear here.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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

/**
 * Pure File Document / Media Item Card in History.
 */
@Composable
fun HistoryRecordRow(
    record: TransferRecord,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRetry: () -> Unit = {},
    onDelete: () -> Unit
) {
    val isSent = record.direction.equals("SENT", ignoreCase = true)
    val relativeTime = formatRelativeTime(record.timestamp)

    val lowerName = record.fileName.lowercase()
    val (fileIcon, iconBg, iconColor) = when {
        lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".png") || lowerName.endsWith(".webp") || record.fileType == "PHOTOS" ->
            Triple(Icons.Default.Image, ElectricCyan.copy(alpha = 0.16f), ElectricCyan)
        lowerName.endsWith(".mp4") || lowerName.endsWith(".mov") || lowerName.endsWith(".mkv") || record.fileType == "VIDEOS" ->
            Triple(Icons.Default.PlayArrow, RadiantPurple.copy(alpha = 0.18f), RadiantPurple)
        lowerName.endsWith(".mp3") || lowerName.endsWith(".wav") || lowerName.endsWith(".m4a") || record.fileType == "AUDIO" ->
            Triple(Icons.Default.GraphicEq, SolarAmber.copy(alpha = 0.18f), SolarAmber)
        lowerName.endsWith(".apk") || record.fileType == "APPS" ->
            Triple(Icons.Default.Widgets, NeonTeal.copy(alpha = 0.18f), NeonTeal)
        else ->
            Triple(Icons.Default.Description, Color(0xFF38BDF8).copy(alpha = 0.18f), Color(0xFF38BDF8))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardNavy)
            .border(width = 1.dp, color = BorderSubtle, shape = RoundedCornerShape(18.dp))
            .clickable { onOpen() }
            .padding(horizontal = 14.dp, vertical = 13.dp)
            .animateContentSize()
            .testTag("history_row_${record.id}"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            // File type thumbnail icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = fileIcon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.fileName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (isSent) "Sent to ${record.peerDeviceName}" else "From ${record.peerDeviceName}",
                        fontSize = 11.5.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(text = "•", fontSize = 10.sp, color = TextSecondary)
                    Text(
                        text = SharableItem.formatFileSize(record.fileSize),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ElectricCyan
                    )
                    Text(text = "•", fontSize = 10.sp, color = TextSecondary)
                    Text(
                        text = relativeTime,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Open & Share actions
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onOpen,
                modifier = Modifier
                    .size(34.dp)
                    .testTag("open_file_${record.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = "Open file",
                    tint = ElectricCyan,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onShare,
                modifier = Modifier
                    .size(34.dp)
                    .testTag("share_file_${record.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share",
                    tint = TextSecondary,
                    modifier = Modifier.size(17.dp)
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("delete_record_${record.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete",
                    tint = TextSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val diffMs = System.currentTimeMillis() - timestamp
    val seconds = diffMs / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        seconds < 60 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}
