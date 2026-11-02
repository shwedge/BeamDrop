package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ActiveBeamThemeState
import com.example.ui.theme.CyberBlack
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonTeal
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

// Exact pill & switch styling from the user's Settings reference screenshots (image_1177e953.png, image_a812a424.png, image_2fdad807.png)
private val SettingsCardSurface = Color(0xFF141E1D)
private val SettingsPillBg = Color(0xFF2B4643)
private val SettingsToggleActiveTrack = Color(0xFF67E8D8)
private val SettingsToggleActiveThumb = Color(0xFF0A2E2A)
private val SettingsToggleInactiveTrack = Color(0xFF1A2524)
private val SettingsToggleInactiveBorder = Color(0xFF8CA3A0)
private val SettingsToggleInactiveThumb = Color(0xFF94A8A5)

@Composable
fun BeamSettingsView(
    themeMode: String,
    colorTheme: String,
    languageSetting: String,
    minimizeToTray: Boolean,
    autostartAfterLogin: Boolean,
    animationsEnabled: Boolean,
    quickSaveEnabled: Boolean,
    quickSaveFavorites: Boolean,
    requirePinEnabled: Boolean,
    saveFolderDisplayName: String,
    autoFinishEnabled: Boolean,
    saveToHistoryEnabled: Boolean,
    isServerRunning: Boolean,
    deviceName: String,
    localIpAddress: String,
    advancedSettingsEnabled: Boolean,
    onThemeModeChange: (String) -> Unit,
    onColorThemeChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
    onMinimizeToTrayChange: (Boolean) -> Unit,
    onAutostartChange: (Boolean) -> Unit,
    onAnimationsChange: (Boolean) -> Unit,
    onQuickSaveChange: (Boolean) -> Unit,
    onQuickSaveFavoritesChange: (Boolean) -> Unit,
    onRequirePinChange: (Boolean) -> Unit,
    onChooseSaveFolder: () -> Unit,
    onAutoFinishChange: (Boolean) -> Unit,
    onSaveToHistoryChange: (Boolean) -> Unit,
    onRestartNetworkAndServer: () -> Unit,
    onStopServer: () -> Unit,
    onUpdateDeviceName: (String) -> Unit,
    onAdvancedSettingsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showRenameDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showSupportDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showChangelogDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_view_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Screen Header
        item {
            Text(
                text = "Settings",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
        }

        // ============================================================
        // CARD 1: General (matches image_1177e953.png)
        // ============================================================
        item {
            SettingsSectionCard(title = "General") {
                SettingsDropdownRow(
                    label = "Theme",
                    selectedValue = themeMode,
                    options = listOf("System", "iOS 26 Clear", "Dark", "Midnight"),
                    onSelect = onThemeModeChange,
                    testTag = "settings_theme_dropdown"
                )

                SettingsDropdownRow(
                    label = "Color",
                    selectedValue = colorTheme,
                    options = listOf(
                        "LocalSend",
                        "iOS 26 Clear",
                        "Sapphire Blue",
                        "Royal Violet",
                        "Emerald Glass",
                        "Sunset Rose",
                        "Cyber Gold",
                        "Crimson Ruby",
                        "Arctic Cyan"
                    ),
                    onSelect = onColorThemeChange,
                    testTag = "settings_color_dropdown"
                )

                SettingsDropdownRow(
                    label = "Language",
                    selectedValue = languageSetting,
                    options = listOf("System", "English", "বাংলা (Bengali)", "Español", "Deutsch"),
                    onSelect = onLanguageChange,
                    showDropdownArrow = false,
                    testTag = "settings_language_pill"
                )

                SettingsToggleRow(
                    label = "Minimize to the System Tray/Menu Bar when closing",
                    checked = minimizeToTray,
                    onCheckedChange = onMinimizeToTrayChange,
                    testTag = "settings_minimize_toggle"
                )

                SettingsToggleRow(
                    label = "Autostart after login",
                    checked = autostartAfterLogin,
                    onCheckedChange = onAutostartChange,
                    testTag = "settings_autostart_toggle"
                )

                SettingsToggleRow(
                    label = "Animations",
                    checked = animationsEnabled,
                    onCheckedChange = onAnimationsChange,
                    testTag = "settings_animations_toggle"
                )
            }
        }

        // ============================================================
        // CARD 2: Receive (matches image_1177e953.png & image_a812a424.png)
        // ============================================================
        item {
            SettingsSectionCard(title = "Receive") {
                SettingsToggleRow(
                    label = "Quick Save",
                    checked = quickSaveEnabled,
                    onCheckedChange = onQuickSaveChange,
                    testTag = "settings_quick_save_toggle"
                )

                SettingsToggleRow(
                    label = "Quick Save for \"Favorites\"",
                    checked = quickSaveFavorites,
                    onCheckedChange = onQuickSaveFavoritesChange,
                    testTag = "settings_quick_save_favorites_toggle"
                )

                SettingsToggleRow(
                    label = "Require PIN",
                    checked = requirePinEnabled,
                    onCheckedChange = onRequirePinChange,
                    testTag = "settings_require_pin_toggle"
                )

                SettingsFolderActionRow(
                    label = "Save to folder",
                    folderText = saveFolderDisplayName,
                    onClick = onChooseSaveFolder,
                    testTag = "settings_save_folder_pill"
                )

                SettingsToggleRow(
                    label = "Auto Finish",
                    checked = autoFinishEnabled,
                    onCheckedChange = onAutoFinishChange,
                    testTag = "settings_auto_finish_toggle"
                )

                SettingsToggleRow(
                    label = "Save to history",
                    checked = saveToHistoryEnabled,
                    onCheckedChange = onSaveToHistoryChange,
                    testTag = "settings_save_history_toggle"
                )
            }
        }

        // ============================================================
        // CARD 3: Network (matches image_a812a424.png & image_2fdad807.png)
        // ============================================================
        item {
            SettingsSectionCard(title = "Network") {
                // Server row with Restart/Refresh (↻) and Stop (■) inside the pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Server",
                            fontSize = 14.5.sp,
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (isServerRunning) "Online • $localIpAddress:8988" else "Stopped • Tap ↻ to restart",
                            fontSize = 11.5.sp,
                            color = if (isServerRunning) NeonTeal else TextSecondary
                        )
                    }

                    Row(
                        modifier = Modifier
                            .width(142.dp)
                            .height(44.dp)
                            .glassLiquidPill(
                                cornerRadius = 10.dp,
                                tintColor = NeonTeal,
                                baseColor = SettingsPillBg
                            )
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable {
                                    onRestartNetworkAndServer()
                                    Toast.makeText(
                                        context,
                                        "Network & Transfer Server Refreshed",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                .testTag("settings_restart_server_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Network & Restart Server",
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable {
                                    onStopServer()
                                    Toast.makeText(
                                        context,
                                        "Receive Server Stopped (Tap ↻ to restart)",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                .testTag("settings_stop_server_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop Server",
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Refresh Network discovery row
                SettingsButtonRow(
                    label = "Refresh Network Discovery",
                    buttonText = "Refresh",
                    onClick = {
                        onRestartNetworkAndServer()
                        Toast.makeText(context, "Scanning local Wi-Fi & refreshing server...", Toast.LENGTH_SHORT).show()
                    },
                    testTag = "settings_refresh_network_btn"
                )

                // Device Name row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Device name",
                        fontSize = 14.5.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f).padding(end = 12.dp)
                    )

                    Box(
                        modifier = Modifier
                            .width(142.dp)
                            .height(44.dp)
                            .glassLiquidPill(
                                cornerRadius = 10.dp,
                                tintColor = ElectricCyan,
                                baseColor = SettingsPillBg
                            )
                            .clickable { showRenameDialog = true }
                            .padding(horizontal = 12.dp)
                            .testTag("settings_device_name_pill"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = deviceName,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // ============================================================
        // CARD 4: Other (matches image_2fdad807.png)
        // ============================================================
        item {
            SettingsSectionCard(title = "Other") {
                SettingsButtonRow(
                    label = "About BeamDrop",
                    buttonText = "Open",
                    onClick = { showAboutDialog = true },
                    testTag = "settings_about_btn"
                )

                SettingsButtonRow(
                    label = "Support BeamDrop",
                    buttonText = "Donate",
                    onClick = { showSupportDialog = true },
                    testTag = "settings_donate_btn"
                )

                SettingsButtonRow(
                    label = "Privacy Policy",
                    buttonText = "Open",
                    onClick = { showPrivacyDialog = true },
                    testTag = "settings_privacy_btn"
                )
            }
        }

        // ============================================================
        // Advanced settings checkbox + Expandable Diagnostics (matches image_2fdad807.png)
        // ============================================================
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onAdvancedSettingsChange(!advancedSettingsEnabled) }
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                        .testTag("settings_advanced_checkbox"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (advancedSettingsEnabled) SettingsToggleActiveTrack else Color.Transparent
                            )
                            .border(
                                width = 1.6.dp,
                                color = if (advancedSettingsEnabled) SettingsToggleActiveTrack else TextPrimary,
                                shape = RoundedCornerShape(4.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (advancedSettingsEnabled) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = SettingsToggleActiveThumb,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = "Advanced settings",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                }

                AnimatedVisibility(visible = advancedSettingsEnabled) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .glassLiquidCard(
                                cornerRadius = 18.dp,
                                tintColor = NeonTeal,
                                secondaryTint = ElectricCyan,
                                baseColor = SettingsCardSurface,
                                baseAlpha = 0.56f
                            )
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Advanced Network & Engine Telemetry",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonTeal
                        )
                        Text(
                            text = "• TCP Transfer Port: 8988 (8 MB Socket Buffer / 2 MB Stream Chunk)\n" +
                                "• UDP Discovery & Swipe Sync Port: 8989\n" +
                                "• Continuity Web Bridge Port: 8990 (http://$localIpAddress:8990)\n" +
                                "• Coroutine Priority: URGENT_DISPLAY (100+ Mbps High-Throughput)\n" +
                                "• Lead Developer: Sajjad Hossain Mridul",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        // ============================================================
        // Bottom Emblem, Developer Credit & Changelog Footer (matches image_2fdad807.png)
        // ============================================================
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    BeamDropSegmentedEmblem(
                        size = 52.dp,
                        primaryColor = Color(0xFF2DD4BF),
                        secondaryColor = Color(0xFF38BDF8)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "BeamDrop",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "by Sajjad Hossain Mridul",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NeonTeal
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Version: 2.5.0 (58)",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "© 2026 Sajjad Hossain Mridul",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showChangelogDialog = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("settings_changelog_btn"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Changelog",
                        tint = NeonTeal,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Changelog",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NeonTeal
                    )
                }
            }
        }
    }

    // ============================================================
    // DIALOGS: Rename Device, About & Staff, Support/Donate, Privacy Policy, Changelog
    // ============================================================
    if (showRenameDialog) {
        var draftName by remember(deviceName) { mutableStateOf(deviceName) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = DeepNavy,
            shape = RoundedCornerShape(22.dp),
            title = {
                Text(
                    text = "Change Device Name",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter the alias shown to nearby Android and Windows devices:",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = draftName,
                        onValueChange = { draftName = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonTeal,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.28f),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateDeviceName(draftName)
                        showRenameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonTeal,
                        contentColor = CyberBlack
                    )
                ) {
                    Text("Save & Restart Server", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = DeepNavy,
            shape = RoundedCornerShape(24.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BeamDropSegmentedEmblem(size = 36.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "About BeamDrop",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Created & Developed by Sajjad Hossain Mridul",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NeonTeal
                        )
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "BeamDrop is a high-speed, zero-cloud local file sharing and continuity ecosystem for Android and Windows 10/11 featuring iOS 26 Clear Glass aesthetics, simultaneous directional swipe waves, QR & NFC pairing, and 100+ Mbps local Wi-Fi throughput.",
                        fontSize = 12.5.sp,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassLiquidCard(cornerRadius = 14.dp)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Our Staff & Core Team",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonTeal
                        )
                        Text(
                            text = "• Sajjad Hossain Mridul — Founder, Lead Architect & Principal Developer (Android & Windows)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "• BeamDrop Systems Engineering Staff — High-Speed TCP/UDP Socket & Zero-Thermal Engine",
                            fontSize = 11.5.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "• BeamDrop Liquid Glass Design Staff — iOS 26 Clear Frosted Glass & Cross-Device Continuity",
                            fontSize = 11.5.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "• BeamDrop Security & QA Staff — Local LAN Verification & Authenticode Signing",
                            fontSize = 11.5.sp,
                            color = TextSecondary
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showAboutDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonTeal,
                        contentColor = CyberBlack
                    )
                ) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showSupportDialog) {
        AlertDialog(
            onDismissRequest = { showSupportDialog = false },
            containerColor = DeepNavy,
            shape = RoundedCornerShape(24.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = NeonTeal,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Support & Donate",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "BeamDrop is built with passion by Sajjad Hossain Mridul and the BeamDrop Staff — 100% free, ad-free, and with zero cloud tracking.",
                        fontSize = 12.5.sp,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassLiquidCard(cornerRadius = 14.dp)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Ways to Support Sajjad Hossain Mridul:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonTeal
                        )
                        Text(
                            text = "• Sponsor Development: github.com/sponsors/SajjadHossainMridul\n" +
                                "• Buy a Coffee for the Staff: buymeacoffee.com/sajjadhossainmridul\n" +
                                "• Star & Share BeamDrop with friends on Android & Windows!",
                            fontSize = 12.sp,
                            color = TextPrimary,
                            lineHeight = 18.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val donateIntent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://buymeacoffee.com/sajjadhossainmridul")
                            ).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(donateIntent)
                        } catch (_: Exception) {
                            Toast.makeText(
                                context,
                                "Opening buymeacoffee.com/sajjadhossainmridul",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ActiveBeamThemeState.current.primaryAccent,
                        contentColor = CyberBlack
                    ),
                    modifier = Modifier.testTag("donate_buymeacoffee_btn")
                ) {
                    Text("Donate ☕", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showSupportDialog = false
                        Toast.makeText(
                            context,
                            "Thank you for supporting Sajjad Hossain Mridul & BeamDrop!",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ActiveBeamThemeState.current.pillBaseColor,
                        contentColor = TextPrimary
                    ),
                    modifier = Modifier.testTag("donate_thank_you_btn")
                ) {
                    Text("Thank You ♥", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            containerColor = DeepNavy,
            shape = RoundedCornerShape(24.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PrivacyTip,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Privacy Policy",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Effective Date: 2026 • Developer: Sajjad Hossain Mridul",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonTeal
                    )
                    Text(
                        text = "1. Zero Cloud Servers: All files, folders, photos, and text snippets are transferred strictly peer-to-peer over your local Wi-Fi network or Wi-Fi Direct.\n\n" +
                            "2. Zero Analytics or Telemetry: BeamDrop collects no personal data, no device identifiers, and no usage tracking.\n\n" +
                            "3. Full Local Storage Control: Received files are saved directly to your chosen device folder, and transfer history is stored strictly on-device (and can be disabled or cleared at any time in Settings).",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 17.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showPrivacyDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = CyberBlack
                    )
                ) {
                    Text("Got It", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showChangelogDialog) {
        AlertDialog(
            onDismissRequest = { showChangelogDialog = false },
            containerColor = DeepNavy,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = "BeamDrop v2.5.0 (58) Changelog",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "• Redesigned with Apple iOS 26 Clear Frosted Glass & Control Center aesthetics\n" +
                        "• Added full Settings hub (General, Receive, Network, Other) & top-bar quick access\n" +
                        "• 100+ Mbps High-Throughput TCP socket engine with 8 MB buffers & URGENT_DISPLAY priority\n" +
                        "• Simultaneous Android & Windows directional swipe Glass Locate waves\n" +
                        "• Custom Save Folder picker, QR Code pairing, and Zero-Thermal idle optimization\n" +
                        "• Developed by Sajjad Hossain Mridul",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showChangelogDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonTeal,
                        contentColor = CyberBlack
                    )
                ) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    val activeTheme = ActiveBeamThemeState.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassLiquidCard(
                cornerRadius = 22.dp,
                tintColor = activeTheme.primaryAccent,
                secondaryTint = activeTheme.secondaryAccent,
                baseColor = activeTheme.cardBaseColor,
                baseAlpha = if (activeTheme.isClearGlass) 0.44f else 0.62f
            )
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = title,
            fontSize = 16.5.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        content()
    }
}

@Composable
private fun SettingsDropdownRow(
    label: String,
    selectedValue: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    showDropdownArrow: Boolean = true,
    testTag: String
) {
    val activeTheme = ActiveBeamThemeState.current
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.5.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f).padding(end = 12.dp)
        )

        Box {
            Row(
                modifier = Modifier
                    .width(142.dp)
                    .height(44.dp)
                    .glassLiquidPill(
                        cornerRadius = 10.dp,
                        tintColor = activeTheme.primaryAccent,
                        baseColor = activeTheme.pillBaseColor
                    )
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp)
                    .testTag(testTag),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (showDropdownArrow) Arrangement.SpaceBetween else Arrangement.Center
            ) {
                Text(
                    text = selectedValue,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (showDropdownArrow) Modifier.weight(1f) else Modifier
                )
                if (showDropdownArrow) {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(activeTheme.cardBaseColor)
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option,
                                color = if (option == selectedValue) activeTheme.primaryAccent else TextPrimary,
                                fontWeight = if (option == selectedValue) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * Custom Pill Toggle Switch inside a 142x44dp rounded rectangular container,
 * matching `image_1177e953.png` and `image_a812a424.png` pixel-for-pixel.
 */
@Composable
private fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    val activeTheme = ActiveBeamThemeState.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.5.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Medium,
            lineHeight = 19.sp,
            modifier = Modifier.weight(1f).padding(end = 12.dp)
        )

        // Outer pill container (142.dp x 44.dp)
        Box(
            modifier = Modifier
                .width(142.dp)
                .height(44.dp)
                .glassLiquidPill(
                    cornerRadius = 10.dp,
                    tintColor = if (checked) activeTheme.primaryAccent else activeTheme.secondaryAccent,
                    baseColor = activeTheme.pillBaseColor
                )
                .clickable { onCheckedChange(!checked) }
                .testTag(testTag),
            contentAlignment = Alignment.Center
        ) {
            // Inner oval switch track (52.dp x 28.dp)
            Box(
                modifier = Modifier
                    .width(52.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (checked) activeTheme.primaryAccent else SettingsToggleInactiveTrack
                    )
                    .border(
                        width = if (checked) 0.dp else 1.5.dp,
                        color = if (checked) Color.Transparent else SettingsToggleInactiveBorder,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 4.dp),
                contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .size(if (checked) 20.dp else 14.dp)
                        .clip(CircleShape)
                        .background(
                            if (checked) SettingsToggleActiveThumb else SettingsToggleInactiveThumb
                        )
                )
            }
        }
    }
}

@Composable
private fun SettingsFolderActionRow(
    label: String,
    folderText: String,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.5.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f).padding(end = 12.dp)
        )

        Box(
            modifier = Modifier
                .width(142.dp)
                .glassLiquidPill(
                    cornerRadius = 10.dp,
                    tintColor = NeonTeal,
                    baseColor = SettingsPillBg
                )
                .clickable { onClick() }
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .testTag(testTag),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = folderText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun SettingsButtonRow(
    label: String,
    buttonText: String,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.5.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f).padding(end = 12.dp)
        )

        Box(
            modifier = Modifier
                .width(142.dp)
                .height(44.dp)
                .glassLiquidPill(
                    cornerRadius = 10.dp,
                    tintColor = NeonTeal,
                    baseColor = SettingsPillBg
                )
                .clickable { onClick() }
                .testTag(testTag),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = buttonText,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }
    }
}
