package com.example.ui

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.model.TransferRecord
import com.example.engine.RealTransferEngine
import com.example.model.BeamTab
import com.example.ui.components.AuraPathCanvasOverlay
import com.example.ui.components.BeamInfoDialog
import com.example.ui.components.BeamSettingsView
import com.example.ui.components.BeamTopBar
import com.example.ui.components.DiscreetTransferProgressIndicator
import com.example.ui.components.FluidAuroraRipple
import com.example.ui.components.GlassLiquidIconOrb
import com.example.ui.components.NearbyRadarView
import com.example.ui.components.NfcLiquidScanOverlay
import com.example.ui.components.ReceiveModeModal
import com.example.ui.components.SharableMediaGrid
import com.example.ui.components.TransferHistoryView
import com.example.ui.components.glassLiquidCard
import com.example.ui.components.glassLiquidPill
import com.example.ui.theme.ActiveBeamThemeState
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CardNavy
import com.example.ui.theme.CyberBlack
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonTeal
import com.example.ui.theme.RadiantPurple
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun BeamDropScreen(
    viewModel: BeamDropViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val currentTab by viewModel.currentTab.collectAsState()
    val showInfoDialog by viewModel.showInfoDialog.collectAsState()
    val lastReceivedFile by viewModel.lastReceivedFile.collectAsState()
    val receivedTextPopup by viewModel.receivedTextPopup.collectAsState()

    // 1. Choose Default Save Folder Launcher (OpenDocumentTree) for received files
    val saveFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(treeUri, flags)
            } catch (_: Exception) {}

            var folderLabel = "Custom Folder"
            try {
                val docId = DocumentsContract.getTreeDocumentId(treeUri)
                val clean = docId.substringAfterLast(":").substringAfterLast("/")
                if (clean.isNotBlank()) folderLabel = clean
            } catch (_: Exception) {}

            viewModel.setCustomSaveFolder(treeUri.toString(), folderLabel)

            // If there is a freshly received file, also copy it immediately into the newly chosen folder
            val recent = lastReceivedFile
            if (recent != null && recent.exists()) {
                coroutineScope.launch(Dispatchers.IO) {
                    RealTransferEngine.persistReceivedFileToDeviceStorage(context, recent)
                }
            }
            Toast.makeText(context, "Save location set to: $folderLabel", Toast.LENGTH_SHORT).show()
        }
    }

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
        if (uris.isNotEmpty()) {
            viewModel.triggerPendingTransferIfReady()
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
        if (uris.isNotEmpty()) {
            viewModel.triggerPendingTransferIfReady()
        }
    }

    // System Folder Picker contract (OpenDocumentTree) for sending a folder
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
            } catch (_: Exception) {}

            var folderName = "Folder_${System.currentTimeMillis()}"
            try {
                val docId = DocumentsContract.getTreeDocumentId(treeUri)
                val clean = docId.substringAfterLast(":").substringAfterLast("/")
                if (clean.isNotBlank()) {
                    folderName = clean
                }
            } catch (_: Exception) {
                val lastSeg = treeUri.lastPathSegment
                if (!lastSeg.isNullOrBlank()) {
                    folderName = lastSeg.substringAfterLast(":").substringAfterLast("/")
                }
            }

            viewModel.addCustomFile(
                name = folderName,
                sizeBytes = 1024L * 1024L * 24L,
                mimeType = "vnd.android.document/directory",
                uri = treeUri.toString()
            )
            viewModel.triggerPendingTransferIfReady()
        }
    }

    var showReceiveModal by remember { mutableStateOf(false) }

    fun openTransferRecord(record: TransferRecord) {
        try {
            if (record.localUri?.startsWith("beamtext://") == true) {
                val raw = record.localUri.removePrefix("beamtext://")
                clipboardManager.setText(AnnotatedString(raw))
                Toast.makeText(context, "Copied text to clipboard!", Toast.LENGTH_SHORT).show()
                return
            }
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
                Toast.makeText(context, "Saved in Downloads/BeamDrop/${record.fileName}", Toast.LENGTH_SHORT).show()
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

    BackHandler(enabled = currentTab != BeamTab.FILES) {
        viewModel.selectTab(BeamTab.FILES)
    }

    val colorTheme by viewModel.colorTheme.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    ActiveBeamThemeState.update(themeMode, colorTheme)
    val activeTheme = ActiveBeamThemeState.current

    // Root container with iOS 26 Control Center / Clear Glass atmospheric backdrop that adapts to selected Theme & Color
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(activeTheme.backdropStops.lastOrNull() ?: CyberBlack)
            .drawWithCache {
                val w = size.width
                val h = size.height

                val baseBackdrop = Brush.linearGradient(
                    colors = activeTheme.backdropStops,
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                )

                val topLeftIceOrb = Brush.radialGradient(
                    colors = listOf(
                        activeTheme.primaryAccent.copy(alpha = if (activeTheme.isClearGlass) 0.42f else 0.26f),
                        activeTheme.secondaryAccent.copy(alpha = if (activeTheme.isClearGlass) 0.28f else 0.16f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.16f, h * 0.12f),
                    radius = w * 0.88f
                )

                val bottomRightOrb = Brush.radialGradient(
                    colors = listOf(
                        activeTheme.secondaryAccent.copy(alpha = if (activeTheme.isClearGlass) 0.36f else 0.22f),
                        activeTheme.primaryAccent.copy(alpha = if (activeTheme.isClearGlass) 0.22f else 0.12f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.84f, h * 0.84f),
                    radius = w * 0.85f
                )

                val clearFrostSheen = Brush.verticalGradient(
                    colors = if (activeTheme.isClearGlass) {
                        listOf(
                            Color.White.copy(alpha = 0.22f),
                            Color(0xFFBAE6FD).copy(alpha = 0.10f),
                            Color.White.copy(alpha = 0.14f)
                        )
                    } else {
                        listOf(Color.Transparent, Color.Transparent)
                    }
                )

                onDrawBehind {
                    drawRect(brush = baseBackdrop)
                    drawRect(brush = topLeftIceOrb)
                    drawRect(brush = bottomRightOrb)
                    if (activeTheme.isClearGlass) {
                        drawRect(brush = clearFrostSheen)
                    }
                }
            }
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
                    BeamTopBarHost(
                        viewModel = viewModel,
                        onReceiveClick = { showReceiveModal = true },
                        onInfoClick = { viewModel.toggleInfoDialog(true) }
                    )
                    // Inline horizontal line progress strip right below the top bar so it never overlaps device cards
                    TransferProgressIndicatorHost(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .glassLiquidCard(
                                cornerRadius = 24.dp,
                                tintColor = ElectricCyan,
                                secondaryTint = Color(0xFF5EEAD4),
                                baseColor = CardNavy,
                                baseAlpha = 0.26f
                            )
                    ) {
                        NavigationBarItem(
                            selected = currentTab == BeamTab.FILES,
                            onClick = { viewModel.selectTab(BeamTab.FILES) },
                            icon = {
                                GlassLiquidIconOrb(
                                    size = 36.dp,
                                    cornerRadius = 12.dp,
                                    tintColor = if (currentTab == BeamTab.FILES) ElectricCyan else BorderSubtle,
                                    secondaryColor = if (currentTab == BeamTab.FILES) Color(0xFF5EEAD4) else CardNavy
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = "Files",
                                        tint = if (currentTab == BeamTab.FILES) Color.White else TextSecondary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            },
                            label = { Text("Files", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ElectricCyan,
                                indicatorColor = Color.Transparent,
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
                                GlassLiquidIconOrb(
                                    size = 36.dp,
                                    cornerRadius = 12.dp,
                                    tintColor = if (currentTab == BeamTab.RADAR) ElectricCyan else BorderSubtle,
                                    secondaryColor = if (currentTab == BeamTab.RADAR) Color(0xFF5EEAD4) else CardNavy
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Radar,
                                        contentDescription = "Radar",
                                        tint = if (currentTab == BeamTab.RADAR) Color.White else TextSecondary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            },
                            label = { Text("Radar", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ElectricCyan,
                                indicatorColor = Color.Transparent,
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
                                GlassLiquidIconOrb(
                                    size = 36.dp,
                                    cornerRadius = 12.dp,
                                    tintColor = if (currentTab == BeamTab.HISTORY) ElectricCyan else BorderSubtle,
                                    secondaryColor = if (currentTab == BeamTab.HISTORY) Color(0xFF5EEAD4) else CardNavy
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = "History",
                                        tint = if (currentTab == BeamTab.HISTORY) Color.White else TextSecondary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            },
                            label = { Text("History", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ElectricCyan,
                                indicatorColor = Color.Transparent,
                                unselectedIconColor = TextSecondary,
                                selectedTextColor = ElectricCyan,
                                unselectedTextColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("nav_history")
                        )

                        NavigationBarItem(
                            selected = currentTab == BeamTab.SETTINGS,
                            onClick = { viewModel.selectTab(BeamTab.SETTINGS) },
                            icon = {
                                GlassLiquidIconOrb(
                                    size = 36.dp,
                                    cornerRadius = 12.dp,
                                    tintColor = if (currentTab == BeamTab.SETTINGS) ElectricCyan else BorderSubtle,
                                    secondaryColor = if (currentTab == BeamTab.SETTINGS) Color(0xFF5EEAD4) else CardNavy
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Settings",
                                        tint = if (currentTab == BeamTab.SETTINGS) Color.White else TextSecondary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            },
                            label = { Text("Settings", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ElectricCyan,
                                indicatorColor = Color.Transparent,
                                unselectedIconColor = TextSecondary,
                                selectedTextColor = ElectricCyan,
                                unselectedTextColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("nav_settings")
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
                Crossfade(
                    targetState = currentTab,
                    label = "tab_crossfade"
                ) { tab ->
                    when (tab) {
                        BeamTab.FILES -> {
                            FilesTabHost(
                                viewModel = viewModel,
                                onPickFromStorage = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                    )
                                },
                                onPickDocument = {
                                    filePickerLauncher.launch(arrayOf("*/*"))
                                },
                                onPickFolder = {
                                    folderPickerLauncher.launch(null)
                                },
                                onChooseSaveFolder = {
                                    saveFolderPickerLauncher.launch(null)
                                }
                            )
                        }
                        BeamTab.RADAR -> {
                            RadarTabHost(viewModel = viewModel)
                        }
                        BeamTab.HISTORY -> {
                            HistoryTabHost(
                                viewModel = viewModel,
                                onOpenFile = { record -> openTransferRecord(record) },
                                onShareFile = { record -> shareTransferRecord(record) }
                            )
                        }
                        BeamTab.SETTINGS -> {
                            SettingsTabHost(
                                viewModel = viewModel,
                                onChooseSaveFolder = {
                                    saveFolderPickerLauncher.launch(null)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Isolated leaf composable for full-screen continuity light bloom & Aura Path
        TransferAuraOverlaysHost(
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize()
        )

        if (showInfoDialog) {
            BeamInfoDialog(
                onDismiss = { viewModel.toggleInfoDialog(false) }
            )
        }

        if (showReceiveModal) {
            ReceiveModeModalHost(
                viewModel = viewModel,
                onDismiss = { showReceiveModal = false }
            )
        }

        // Pop-up Receiver Dialog when Plain Text is received from Sender (no .txt file created)
        receivedTextPopup?.let { textPayload ->
            LaunchedEffect(textPayload) {
                clipboardManager.setText(AnnotatedString(textPayload.textContent))
            }
            ReceivedPlainTextPopupDialog(
                payload = textPayload,
                onCopyText = {
                    clipboardManager.setText(AnnotatedString(textPayload.textContent))
                    Toast.makeText(context, "Plain text copied to clipboard!", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { viewModel.dismissReceivedTextPopup() }
            )
        }
    }
}

@Composable
private fun ReceivedPlainTextPopupDialog(
    payload: ReceivedTextPayload,
    onCopyText: () -> Unit,
    onDismiss: () -> Unit
) {
    val activeTheme = ActiveBeamThemeState.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = activeTheme.cardBaseColor.copy(alpha = 0.96f),
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassLiquidIconOrb(
                    size = 38.dp,
                    cornerRadius = 12.dp,
                    tintColor = activeTheme.primaryAccent,
                    secondaryColor = activeTheme.secondaryAccent
                ) {
                    Icon(
                        imageVector = Icons.Default.Notes,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Plain Text Received",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "From ${payload.senderName}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = activeTheme.primaryAccent
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Copy the plain text sent by ${payload.senderName}:",
                    fontSize = 12.5.sp,
                    color = TextSecondary
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .glassLiquidCard(
                            cornerRadius = 14.dp,
                            tintColor = activeTheme.primaryAccent,
                            secondaryTint = activeTheme.secondaryAccent,
                            baseColor = DeepNavy,
                            baseAlpha = 0.65f
                        )
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                        .testTag("received_plain_text_box")
                ) {
                    Text(
                        text = payload.textContent,
                        fontSize = 13.5.sp,
                        color = TextPrimary,
                        lineHeight = 19.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCopyText()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = activeTheme.primaryAccent,
                    contentColor = CyberBlack
                ),
                modifier = Modifier.testTag("copy_received_text_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy Text", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dismiss_received_text_btn")
            ) {
                Text("Close", color = TextSecondary)
            }
        }
    )
}

@Composable
private fun BeamTopBarHost(
    viewModel: BeamDropViewModel,
    onReceiveClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    val wifiDirectState by viewModel.wifiDirectState.collectAsState()
    val isNfcScanning by viewModel.isNfcScanning.collectAsState()
    val customDeviceName by viewModel.customDeviceName.collectAsState()
    BeamTopBar(
        deviceName = customDeviceName,
        isNfcReady = viewModel.isNfcReady,
        isNfcScanning = isNfcScanning,
        isProximityActive = viewModel.isProximityActive,
        isP2pReady = wifiDirectState.isConnected,
        onNfcScanClick = { viewModel.toggleNfcScan() },
        onReceiveClick = onReceiveClick,
        onInfoClick = onInfoClick
    )
}

@Composable
private fun FilesTabHost(
    viewModel: BeamDropViewModel,
    onPickFromStorage: () -> Unit,
    onPickDocument: () -> Unit,
    onPickFolder: () -> Unit = onPickDocument,
    onChooseSaveFolder: () -> Unit = {}
) {
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val mediaItems by viewModel.mediaItems.collectAsState()
    val nearbyPeers by viewModel.nearbyPeers.collectAsState()
    val saveFolderDisplayName by viewModel.saveFolderDisplayName.collectAsState()

    val selectedItems = remember(mediaItems) {
        mediaItems.filter { it.isSelected }
    }
    val itemsForCategory = remember(mediaItems, selectedCategory) {
        mediaItems.filter { it.category == selectedCategory && !it.id.startsWith("browsed_") }
    }

    SharableMediaGrid(
        items = itemsForCategory,
        stagedItems = selectedItems,
        nearbyPeers = nearbyPeers,
        saveFolderDisplayName = saveFolderDisplayName,
        onChooseSaveFolder = onChooseSaveFolder,
        onToggleSelect = { viewModel.toggleItemSelection(it) },
        onPickFromStorage = onPickFromStorage,
        onPickDocument = onPickDocument,
        onPickFolder = onPickFolder,
        onAddTextSnippet = { text -> viewModel.addTextSnippet(text) },
        onClearSelection = { viewModel.clearSelection() },
        onSelectPeerToSend = { peer ->
            if (selectedItems.isEmpty()) {
                viewModel.setPendingPeerForPicker(peer, "TOP")
                onPickDocument()
            } else {
                viewModel.startPeerTransfer(peer, "TOP")
            }
        },
        onSelectPeerToSendWithDirection = { peer, direction ->
            if (selectedItems.isEmpty()) {
                viewModel.setPendingPeerForPicker(peer, direction)
                onPickDocument()
            } else {
                viewModel.startPeerTransfer(peer, direction)
            }
        },
        onSwipePreviewPeer = { peer, direction ->
            viewModel.triggerDirectionalGlassWave(peer, direction, isLiveDrag = true)
        },
        onSwipePreviewDirection = { direction ->
            viewModel.previewSwipeOnStagedCard(direction)
        },
        onAutoBeamBySwipe = { direction ->
            viewModel.swipeToSendStagedItems(direction)
        },
        onScanSubnet = { viewModel.scanSubnetNow() },
        onAddManualIp = { ip -> viewModel.addManualPeerIp(ip) },
        onSourceButtonPositioned = { offset -> viewModel.updateSourceButtonCoordinates(offset) },
        onTargetPeerPositioned = { peerId, offset -> viewModel.updateTargetDeviceCoordinates(peerId, offset) }
    )
}

@Composable
private fun RadarTabHost(viewModel: BeamDropViewModel) {
    val nearbyPeers by viewModel.nearbyPeers.collectAsState()
    val localIpAddress by viewModel.localIpAddress.collectAsState()
    val wifiDirectState by viewModel.wifiDirectState.collectAsState()

    NearbyRadarView(
        peers = nearbyPeers,
        localIpAddress = localIpAddress,
        deviceName = viewModel.deviceName,
        p2pState = wifiDirectState,
        onSelectPeer = { peer -> viewModel.startPeerTransfer(peer, "TOP") },
        onSelectPeerWithDirection = { peer, direction -> viewModel.startPeerTransfer(peer, direction) },
        onSwipePreviewPeer = { peer, direction ->
            viewModel.triggerDirectionalGlassWave(peer, direction, isLiveDrag = true)
        },
        onAddManualIp = { ip -> viewModel.addManualPeerIp(ip) },
        onCreateP2pGroup = { viewModel.wifiDirectManager.createAutonomousGroup { } },
        onScanSubnet = { viewModel.scanSubnetNow() }
    )
}

@Composable
private fun HistoryTabHost(
    viewModel: BeamDropViewModel,
    onOpenFile: (TransferRecord) -> Unit,
    onShareFile: (TransferRecord) -> Unit
) {
    val transferHistory by viewModel.transferHistory.collectAsState()
    val totalBytesTransferred by viewModel.totalBytesTransferred.collectAsState()

    TransferHistoryView(
        transfers = transferHistory,
        totalBytesTransferred = totalBytesTransferred ?: 0L,
        onDeleteRecord = { viewModel.deleteHistoryRecord(it) },
        onClearAll = { viewModel.clearAllHistory() },
        onOpenFile = onOpenFile,
        onShareFile = onShareFile,
        onRetryTransfer = { record -> viewModel.retryHistoryRecord(record) }
    )
}

@Composable
private fun ReceiveModeModalHost(
    viewModel: BeamDropViewModel,
    onDismiss: () -> Unit
) {
    val localIpAddress by viewModel.localIpAddress.collectAsState()
    val wifiDirectState by viewModel.wifiDirectState.collectAsState()
    ReceiveModeModal(
        deviceName = viewModel.deviceName,
        localIp = localIpAddress,
        isP2pGroupActive = wifiDirectState.isGroupOwner || wifiDirectState.isConnected,
        p2pPassphrase = wifiDirectState.groupOwnerAddress ?: "192.168.49.1",
        onConnectQrPayload = { payload -> viewModel.connectViaQrCode(payload) },
        onStartHotspotBridge = { viewModel.wifiDirectManager.createAutonomousGroup { } },
        onDismiss = onDismiss
    )
}

@Composable
private fun TransferProgressIndicatorHost(
    viewModel: BeamDropViewModel,
    modifier: Modifier = Modifier
) {
    val transferState by viewModel.transferState.collectAsState()
    DiscreetTransferProgressIndicator(
        transferState = transferState,
        onCancelTransfer = { viewModel.cancelTransfer() },
        onDismissCompleted = { viewModel.dismissCompleted() },
        modifier = modifier
    )
}

@Composable
private fun SettingsTabHost(
    viewModel: BeamDropViewModel,
    onChooseSaveFolder: () -> Unit
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val colorTheme by viewModel.colorTheme.collectAsState()
    val languageSetting by viewModel.languageSetting.collectAsState()
    val minimizeToTray by viewModel.minimizeToTray.collectAsState()
    val autostartAfterLogin by viewModel.autostartAfterLogin.collectAsState()
    val animationsEnabled by viewModel.animationsEnabled.collectAsState()
    val quickSaveEnabled by viewModel.quickSaveEnabled.collectAsState()
    val quickSaveFavorites by viewModel.quickSaveFavorites.collectAsState()
    val requirePinEnabled by viewModel.requirePinEnabled.collectAsState()
    val saveFolderDisplayName by viewModel.saveFolderDisplayName.collectAsState()
    val autoFinishEnabled by viewModel.autoFinishEnabled.collectAsState()
    val saveToHistoryEnabled by viewModel.saveToHistoryEnabled.collectAsState()
    val isServerRunning by viewModel.isServerRunning.collectAsState()
    val customDeviceName by viewModel.customDeviceName.collectAsState()
    val localIpAddress by viewModel.localIpAddress.collectAsState()
    val advancedSettingsEnabled by viewModel.advancedSettingsEnabled.collectAsState()

    BeamSettingsView(
        themeMode = themeMode,
        colorTheme = colorTheme,
        languageSetting = languageSetting,
        minimizeToTray = minimizeToTray,
        autostartAfterLogin = autostartAfterLogin,
        animationsEnabled = animationsEnabled,
        quickSaveEnabled = quickSaveEnabled,
        quickSaveFavorites = quickSaveFavorites,
        requirePinEnabled = requirePinEnabled,
        saveFolderDisplayName = saveFolderDisplayName,
        autoFinishEnabled = autoFinishEnabled,
        saveToHistoryEnabled = saveToHistoryEnabled,
        isServerRunning = isServerRunning,
        deviceName = customDeviceName,
        localIpAddress = localIpAddress,
        advancedSettingsEnabled = advancedSettingsEnabled,
        onThemeModeChange = { viewModel.setThemeMode(it) },
        onColorThemeChange = { viewModel.setColorTheme(it) },
        onLanguageChange = { viewModel.setLanguageSetting(it) },
        onMinimizeToTrayChange = { viewModel.setMinimizeToTray(it) },
        onAutostartChange = { viewModel.setAutostartAfterLogin(it) },
        onAnimationsChange = { viewModel.setAnimationsEnabled(it) },
        onQuickSaveChange = { viewModel.setQuickSaveEnabled(it) },
        onQuickSaveFavoritesChange = { viewModel.setQuickSaveFavorites(it) },
        onRequirePinChange = { viewModel.setRequirePinEnabled(it) },
        onChooseSaveFolder = onChooseSaveFolder,
        onAutoFinishChange = { viewModel.setAutoFinishEnabled(it) },
        onSaveToHistoryChange = { viewModel.setSaveToHistoryEnabled(it) },
        onRestartNetworkAndServer = { viewModel.restartNetworkAndServer() },
        onStopServer = { viewModel.stopReceiveServer() },
        onUpdateDeviceName = { viewModel.updateCustomDeviceName(it) },
        onAdvancedSettingsChange = { viewModel.setAdvancedSettingsEnabled(it) }
    )
}

@Composable
private fun TransferAuraOverlaysHost(
    viewModel: BeamDropViewModel,
    modifier: Modifier = Modifier
) {
    val animationsEnabled by viewModel.animationsEnabled.collectAsState()
    val auraIntensity by viewModel.auraIntensity.collectAsState()
    val isContactLocked by viewModel.isContactLocked.collectAsState()
    val auraPathState by viewModel.auraPathState.collectAsState()
    val isNfcScanning by viewModel.isNfcScanning.collectAsState()

    if (animationsEnabled) {
        FluidAuroraRipple(
            intensity = auraIntensity,
            isContactLocked = isContactLocked,
            modifier = modifier
        )

        AuraPathCanvasOverlay(
            auraPathState = auraPathState,
            modifier = modifier
        )
    }

    NfcLiquidScanOverlay(
        isScanning = isNfcScanning,
        onDismiss = { viewModel.dismissNfcScan() },
        modifier = modifier
    )
}
