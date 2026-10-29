package com.example.ui

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import android.os.Environment
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.model.TransferRecord
import com.example.model.BeamTab
import com.example.model.SharableItem
import com.example.model.TransferState
import com.example.ui.components.AuraPathCanvasOverlay
import com.example.ui.components.BeamInfoDialog
import com.example.ui.components.BeamTopBar
import com.example.ui.components.DiscreetTransferProgressIndicator
import com.example.ui.components.FluidAuroraRipple
import com.example.ui.components.MediaCategoryBar
import com.example.ui.components.NearbyRadarView
import com.example.ui.components.ReceiveModeModal
import com.example.ui.components.SharableMediaGrid
import com.example.ui.components.TouchSimulatorControls
import com.example.ui.components.TransferHistoryView
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CardNavy
import com.example.ui.theme.CyberBlack
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.TextSecondary
import java.io.File

@Composable
fun BeamDropScreen(
    viewModel: BeamDropViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentTab by viewModel.currentTab.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val mediaItems by viewModel.mediaItems.collectAsState()
    val nearbyPeers by viewModel.nearbyPeers.collectAsState()
    val transferState by viewModel.transferState.collectAsState()
    val auraIntensity by viewModel.auraIntensity.collectAsState()
    val isContactLocked by viewModel.isContactLocked.collectAsState()
    val showInfoDialog by viewModel.showInfoDialog.collectAsState()
    val transferHistory by viewModel.transferHistory.collectAsState()
    val totalBytesTransferred by viewModel.totalBytesTransferred.collectAsState()
    val localIpAddress by viewModel.localIpAddress.collectAsState()
    val lastReceivedFile by viewModel.lastReceivedFile.collectAsState()
    val wifiDirectState by viewModel.wifiDirectState.collectAsState()
    val transferQueue by viewModel.transferQueue.collectAsState()
    val isQueueExpanded by viewModel.isQueueExpanded.collectAsState()
    val auraPathState by viewModel.auraPathState.collectAsState()

    val selectedItems = mediaItems.filter { it.isSelected }
    val selectedCount = selectedItems.size
    val selectedSizeFormatted = SharableItem.formatFileSize(selectedItems.sumOf { it.sizeBytes })

    // Zero-permission modern Android Photo & Video Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            try {
                var fileName = "Photo_${System.currentTimeMillis()}.jpg"
                var fileSize = 1024L * 1024L * 4L
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIdx != -1) fileName = cursor.getString(nameIdx) ?: fileName
                        if (sizeIdx != -1) fileSize = cursor.getLong(sizeIdx).coerceAtLeast(1024L)
                    }
                }
                val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                viewModel.addCustomFile(fileName, fileSize, mimeType, uri.toString())
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    // System File/Document Picker contract for any file
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            try {
                var fileName = "Document_${System.currentTimeMillis()}"
                var fileSize = 1024L * 1024L
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: fileName
                        if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex).coerceAtLeast(1024L)
                    }
                }
                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                viewModel.addCustomFile(fileName, fileSize, mimeType, uri.toString())
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    var showReceiveModal by remember { mutableStateOf(false) }

    fun openTransferRecord(record: TransferRecord) {
        try {
            val file = record.localUri?.let { File(it) } ?: File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
                "BeamDrop/${record.fileName}"
            )
            if (file.exists()) {
                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(contentUri, "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, "Open file with"))
            } else if (record.localUri?.startsWith("content://") == true) {
                val uri = Uri.parse(record.localUri)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, "Open file with"))
            } else {
                Toast.makeText(context, "File location: ${record.fileName}", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareTransferRecord(record: TransferRecord) {
        try {
            val file = record.localUri?.let { File(it) } ?: File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
                "BeamDrop/${record.fileName}"
            )
            val shareUri = if (file.exists()) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            } else if (record.localUri?.startsWith("content://") == true) {
                Uri.parse(record.localUri)
            } else null

            if (shareUri != null) {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    type = "*/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(sendIntent, "Share ${record.fileName}"))
            } else {
                Toast.makeText(context, "File unavailable for sharing", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error sharing file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun openReceivedFile(file: File?) {
        if (file == null || !file.exists()) {
            Toast.makeText(context, "File saved to Downloads/BeamDrop", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Open file with"))
        } catch (e: Exception) {
            Toast.makeText(context, "Saved to ${file.absolutePath}", Toast.LENGTH_LONG).show()
        }
    }

    BackHandler(enabled = currentTab != BeamTab.FILES) {
        viewModel.selectTab(BeamTab.FILES)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CyberBlack)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                ) {
                    BeamTopBar(
                        deviceName = viewModel.deviceName,
                        isNfcReady = viewModel.isNfcReady,
                        isProximityActive = viewModel.isProximityActive,
                        isP2pReady = wifiDirectState.isConnected,
                        onReceiveClick = { showReceiveModal = true },
                        onInfoClick = { viewModel.toggleInfoDialog(true) }
                    )

                    if (currentTab == BeamTab.FILES) {
                        MediaCategoryBar(
                            selectedCategory = selectedCategory,
                            onCategorySelected = { viewModel.selectCategory(it) },
                            selectedCount = selectedCount,
                            selectedTotalSizeFormatted = selectedSizeFormatted,
                            onClearSelection = { viewModel.clearSelection() }
                        )
                    }
                }
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    // Navigation Bar
                    NavigationBar(
                        containerColor = CardNavy,
                        tonalElevation = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(width = 1.dp, color = BorderSubtle)
                    ) {
                        NavigationBarItem(
                            selected = currentTab == BeamTab.FILES,
                            onClick = { viewModel.selectTab(BeamTab.FILES) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = "Files",
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            label = { Text("Files", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = CyberBlack,
                                indicatorColor = ElectricCyan,
                                unselectedIconColor = TextSecondary,
                                selectedTextColor = ElectricCyan,
                                unselectedTextColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("nav_files")
                        )

                        NavigationBarItem(
                            selected = currentTab == BeamTab.RADAR,
                            onClick = { viewModel.selectTab(BeamTab.RADAR) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Radar,
                                    contentDescription = "Radar",
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            label = { Text("Radar", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = CyberBlack,
                                indicatorColor = ElectricCyan,
                                unselectedIconColor = TextSecondary,
                                selectedTextColor = ElectricCyan,
                                unselectedTextColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("nav_radar")
                        )

                        NavigationBarItem(
                            selected = currentTab == BeamTab.HISTORY,
                            onClick = { viewModel.selectTab(BeamTab.HISTORY) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = "History",
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            label = { Text("History", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = CyberBlack,
                                indicatorColor = ElectricCyan,
                                unselectedIconColor = TextSecondary,
                                selectedTextColor = ElectricCyan,
                                unselectedTextColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("nav_history")
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Tab Content
                Crossfade(
                    targetState = currentTab,
                    label = "tab_crossfade"
                ) { tab ->
                    when (tab) {
                        BeamTab.FILES -> {
                            val itemsForCategory = mediaItems.filter { it.category == selectedCategory && !it.id.startsWith("browsed_") }
                            SharableMediaGrid(
                                items = itemsForCategory,
                                stagedItems = selectedItems,
                                nearbyPeers = nearbyPeers,
                                onToggleSelect = { viewModel.toggleItemSelection(it) },
                                onPickFromStorage = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                    )
                                },
                                onPickDocument = {
                                    filePickerLauncher.launch(arrayOf("*/*"))
                                },
                                onAddTextSnippet = { text ->
                                    viewModel.addTextSnippet(text)
                                },
                                onClearSelection = {
                                    viewModel.clearSelection()
                                },
                                onSelectPeerToSend = { peer ->
                                    if (selectedItems.isEmpty()) {
                                        filePickerLauncher.launch(arrayOf("*/*"))
                                    } else {
                                        viewModel.startPeerTransfer(peer)
                                    }
                                },
                                onScanSubnet = {
                                    viewModel.scanSubnetNow()
                                },
                                onAddManualIp = { ip ->
                                    viewModel.addManualPeerIp(ip)
                                },
                                onSourceButtonPositioned = { offset ->
                                    viewModel.updateSourceButtonCoordinates(offset)
                                },
                                onTargetPeerPositioned = { peerId, offset ->
                                    viewModel.updateTargetDeviceCoordinates(peerId, offset)
                                }
                            )
                        }
                        BeamTab.RADAR -> {
                            NearbyRadarView(
                                peers = nearbyPeers,
                                localIpAddress = localIpAddress,
                                deviceName = viewModel.deviceName,
                                p2pState = wifiDirectState,
                                onSelectPeer = { peer ->
                                    viewModel.startPeerTransfer(peer)
                                },
                                onAddManualIp = { ip ->
                                    viewModel.addManualPeerIp(ip)
                                },
                                onCreateP2pGroup = {
                                    viewModel.wifiDirectManager.createAutonomousGroup { }
                                },
                                onScanSubnet = {
                                    viewModel.scanSubnetNow()
                                }
                            )
                        }
                        BeamTab.HISTORY -> {
                            TransferHistoryView(
                                transfers = transferHistory,
                                totalBytesTransferred = totalBytesTransferred ?: 0L,
                                onDeleteRecord = { viewModel.deleteHistoryRecord(it) },
                                onClearAll = { viewModel.clearAllHistory() },
                                onOpenFile = { record -> openTransferRecord(record) },
                                onShareFile = { record -> shareTransferRecord(record) },
                                onRetryTransfer = { record -> viewModel.retryHistoryRecord(record) }
                            )
                        }
                    }
                }

                // Discreet, Self-Dismissing Progress Indicator (Replaces notification queue and clears immediately on completion)
                DiscreetTransferProgressIndicator(
                    transferState = transferState,
                    onCancelTransfer = { viewModel.cancelTransfer() },
                    onDismissCompleted = { viewModel.dismissCompleted() },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        // Fullscreen Fluid Aurora Light Ripple Overlay
        FluidAuroraRipple(
            intensity = auraIntensity,
            isContactLocked = isContactLocked,
            modifier = Modifier.fillMaxSize()
        )

        // Shared Cross-Platform Canvas 'Aura Path' Payload Migration Overlay (Synchronized with Windows)
        AuraPathCanvasOverlay(
            auraPathState = auraPathState,
            modifier = Modifier.fillMaxSize()
        )

        // Info Modal
        if (showInfoDialog) {
            BeamInfoDialog(
                onDismiss = { viewModel.toggleInfoDialog(false) }
            )
        }

        // Receive Mode Modal
        if (showReceiveModal) {
            ReceiveModeModal(
                deviceName = viewModel.deviceName,
                localIp = localIpAddress,
                onDismiss = { showReceiveModal = false }
            )
        }
    }
}
