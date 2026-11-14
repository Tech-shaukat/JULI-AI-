package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Search
import com.example.intents.InstalledAppInfo
import com.example.service.CapturedNotification
import com.example.ui.components.CreativeStudioPanel
import com.example.ui.components.MapsPanel
import com.example.ui.components.NotificationsPanel
import com.example.ui.theme.CosmicMagenta
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DeepSpace
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.StateError
import com.example.ui.theme.StateSuccess
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardElevated
import com.example.ui.theme.TextDim
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.ActionsSubTab
import com.example.viewmodel.ImageStudioUiState
import com.example.viewmodel.VideoStudioUiState
import java.io.File

@Composable
fun ActionsScreen(
    selectedSubTab: ActionsSubTab,
    onSelectSubTab: (ActionsSubTab) -> Unit,
    isWakeWordActive: Boolean,
    onToggleWakeWord: () -> Unit,
    hasContactsPermission: Boolean,
    hasCallPhonePermission: Boolean,
    directCallEnabled: Boolean,
    onRequestCallPermissions: () -> Unit,
    onToggleDirectCall: (Boolean) -> Unit,
    onCallByNameOrNumber: (String) -> Unit,
    onRequestCall: () -> Unit,
    onRequestSms: () -> Unit,
    onRequestWhatsApp: () -> Unit,
    onOpenYouTube: () -> Unit,
    onOpenChrome: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenSettings: (String) -> Unit,
    installedApps: List<InstalledAppInfo>,
    onOpenAppByName: (String) -> Unit,
    onLaunchInstalledApp: (InstalledAppInfo) -> Unit,
    mapDestination: String,
    mapTravelMode: String,
    onMapDestinationChange: (String) -> Unit,
    onMapTravelModeChange: (String) -> Unit,
    onStartMapNavigation: (String, String) -> Unit,
    onSearchMapPlace: (String) -> Unit,
    isNotificationAccessEnabled: Boolean,
    autoAnnounceNotifs: Boolean,
    readNotifContent: Boolean,
    capturedNotifications: List<CapturedNotification>,
    onOpenNotificationSettings: () -> Unit,
    onToggleAutoAnnounceNotifs: (Boolean) -> Unit,
    onToggleReadNotifContent: (Boolean) -> Unit,
    onReadAllNotificationsAloud: () -> Unit,
    onReadSingleNotificationAloud: (CapturedNotification) -> Unit,
    onClearNotifications: () -> Unit,
    imageStudioState: ImageStudioUiState,
    videoStudioState: VideoStudioUiState,
    hasVideoApiKey: Boolean,
    onGenerateImage: (String, String) -> Unit,
    onEditImage: (Bitmap, String) -> Unit,
    onGenerateVideo: (String, String, Boolean) -> Unit,
    onShareGeneratedMedia: (File, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DeepSpace)
    ) {
        // Sub-tab switcher: Calls & Apps | Maps | Alerts | Studio
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val subTabs = listOf(
                Triple(ActionsSubTab.CALLS, "Apps", Icons.Default.Apps),
                Triple(ActionsSubTab.MAPS, "Maps", Icons.Default.Map),
                Triple(ActionsSubTab.ALERTS, "Alerts", Icons.Default.NotificationsActive),
                Triple(ActionsSubTab.STUDIO, "Studio", Icons.Default.AutoAwesome)
            )
            subTabs.forEach { (tab, label, icon) ->
                val isSelected = selectedSubTab == tab
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectSubTab(tab) },
                    label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) },
                    leadingIcon = {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyberCyan.copy(alpha = 0.22f),
                        selectedLabelColor = CyberCyan,
                        selectedLeadingIconColor = CyberCyan,
                        containerColor = SurfaceCard,
                        labelColor = TextSecondary,
                        iconColor = TextDim
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("actions_subtab_${tab.name.lowercase()}")
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (selectedSubTab) {
                ActionsSubTab.CALLS -> {
                    item {
                        DirectCallingCard(
                            hasContactsPermission = hasContactsPermission,
                            hasCallPhonePermission = hasCallPhonePermission,
                            directCallEnabled = directCallEnabled,
                            onRequestPermissions = onRequestCallPermissions,
                            onToggleDirectCall = onToggleDirectCall,
                            onCallByNameOrNumber = onCallByNameOrNumber
                        )
                    }

                    item {
                        WakeWordSectionCard(
                            isWakeWordActive = isWakeWordActive,
                            onToggleWakeWord = onToggleWakeWord
                        )
                    }

                    item {
                        CommunicationShortcutsCard(
                            onRequestCall = onRequestCall,
                            onRequestSms = onRequestSms,
                            onRequestWhatsApp = onRequestWhatsApp
                        )
                    }

                    item {
                        InstalledAppLauncherCard(
                            installedApps = installedApps,
                            onOpenAppByName = onOpenAppByName,
                            onLaunchInstalledApp = onLaunchInstalledApp
                        )
                    }

                    item {
                        MobileAppsGridCard(
                            onOpenYouTube = onOpenYouTube,
                            onOpenChrome = onOpenChrome,
                            onOpenCamera = onOpenCamera,
                            onOpenSettings = onOpenSettings
                        )
                    }

                    item {
                        BatteryReliabilityCard()
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                ActionsSubTab.MAPS -> {
                    item {
                        MapsPanel(
                            currentDestination = mapDestination,
                            currentTravelMode = mapTravelMode,
                            onDestinationChange = onMapDestinationChange,
                            onTravelModeChange = onMapTravelModeChange,
                            onStartNavigation = onStartMapNavigation,
                            onSearchPlace = onSearchMapPlace
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                ActionsSubTab.ALERTS -> {
                    item {
                        NotificationsPanel(
                            isNotificationAccessEnabled = isNotificationAccessEnabled,
                            autoAnnounceEnabled = autoAnnounceNotifs,
                            readContentEnabled = readNotifContent,
                            notifications = capturedNotifications,
                            onOpenNotificationSettings = onOpenNotificationSettings,
                            onToggleAutoAnnounce = onToggleAutoAnnounceNotifs,
                            onToggleReadContent = onToggleReadNotifContent,
                            onReadAllAloud = onReadAllNotificationsAloud,
                            onReadSingleAloud = onReadSingleNotificationAloud,
                            onClearAll = onClearNotifications
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                ActionsSubTab.STUDIO -> {
                    item {
                        CreativeStudioPanel(
                            imageState = imageStudioState,
                            videoState = videoStudioState,
                            hasVideoApiKey = hasVideoApiKey,
                            onGenerateImage = onGenerateImage,
                            onEditImage = onEditImage,
                            onGenerateVideo = onGenerateVideo,
                            onShareMedia = onShareGeneratedMedia
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectCallingCard(
    hasContactsPermission: Boolean,
    hasCallPhonePermission: Boolean,
    directCallEnabled: Boolean,
    onRequestPermissions: () -> Unit,
    onToggleDirectCall: (Boolean) -> Unit,
    onCallByNameOrNumber: (String) -> Unit
) {
    var quickCallQuery by remember { mutableStateOf("") }
    val allGranted = hasContactsPermission && hasCallPhonePermission

    Text(
        text = "Direct Voice Calling (नाम बोलकर कॉल करें)",
        color = CyberCyan,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(8.dp))

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, if (allGranted) StateSuccess.copy(alpha = 0.5f) else SurfaceBorder),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.Contacts,
                        contentDescription = "Contacts & Direct Call",
                        tint = if (allGranted) StateSuccess else CyberCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (allGranted) "Direct Contact Calling Ready" else "Contacts & Phone Permission Needed",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "बोलें: \"Rahul ko call karo\" या \"Papa ko phone lagao\"",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
                Switch(
                    checked = directCallEnabled,
                    onCheckedChange = onToggleDirectCall,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = DeepSpace,
                        checkedTrackColor = StateSuccess,
                        uncheckedThumbColor = TextDim,
                        uncheckedTrackColor = SurfaceBorder
                    ),
                    modifier = Modifier.testTag("direct_call_switch")
                )
            }

            if (!allGranted) {
                OutlinedButton(
                    onClick = onRequestPermissions,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("grant_contacts_call_permission_button")
                ) {
                    Text("Allow Contacts & Direct Phone Call", color = CyberCyan, fontWeight = FontWeight.SemiBold)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = quickCallQuery,
                    onValueChange = { quickCallQuery = it },
                    placeholder = { Text("नाम या नंबर लिखें...", color = TextDim, fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = StateSuccess,
                        unfocusedBorderColor = SurfaceBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = SurfaceCardElevated,
                        unfocusedContainerColor = SurfaceCardElevated
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_call_name_input")
                )

                Button(
                    onClick = { onCallByNameOrNumber(quickCallQuery) },
                    enabled = quickCallQuery.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StateSuccess,
                        disabledContainerColor = SurfaceBorder
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("quick_call_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Call Contact",
                        tint = DeepSpace,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Call", color = DeepSpace, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun WakeWordSectionCard(
    isWakeWordActive: Boolean,
    onToggleWakeWord: () -> Unit
) {
    Text(
        text = "Wake-Word Detection (\"Hey JULI\")",
        color = CyberCyan,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(10.dp))

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(
            1.dp,
            if (isWakeWordActive) CyberCyan else SurfaceBorder
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isWakeWordActive) CyberCyan.copy(alpha = 0.2f) else SurfaceCardElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hearing,
                            contentDescription = null,
                            tint = if (isWakeWordActive) CyberCyan else TextDim,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isWakeWordActive) "Listening for \"Hey JULI\"" else "Background Listening Disabled",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isWakeWordActive) "Foreground microphone service active" else "Tap below to enable hands-free listening",
                            color = if (isWakeWordActive) StateSuccess else TextDim,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "• 100% On-Device: Audio is never streamed to Gemini or any cloud server while waiting for the wake word.\n• Persistent Notification: Required by Android to keep the microphone active safely.\n• Privacy First: You can stop background listening anytime from the notification or right here.",
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onToggleWakeWord,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isWakeWordActive) StateError else CyberCyan
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("wake_word_control_button")
            ) {
                Icon(
                    imageVector = if (isWakeWordActive) Icons.Default.PowerSettingsNew else Icons.Default.Hearing,
                    contentDescription = null,
                    tint = if (isWakeWordActive) Color.White else DeepSpace
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isWakeWordActive) "Stop Background Listening" else "Start Background Listening (\"Hey JULI\")",
                    color = if (isWakeWordActive) Color.White else DeepSpace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CommunicationShortcutsCard(
    onRequestCall: () -> Unit,
    onRequestSms: () -> Unit,
    onRequestWhatsApp: () -> Unit
) {
    Text(
        text = "Call, SMS & WhatsApp (पुष्टि सहित)",
        color = CyberCyan,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(10.dp))

    val commActions = listOf(
        AppShortcutItem("Phone Call", Icons.Default.Call, StateSuccess, onRequestCall),
        AppShortcutItem("Send SMS", Icons.Default.Sms, CyberCyan, onRequestSms),
        AppShortcutItem("WhatsApp", Icons.AutoMirrored.Filled.Chat, Color(0xFF25D366), onRequestWhatsApp)
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, SurfaceBorder),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                commActions.forEach { action ->
                    ShortcutTile(item = action)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "बोलकर या टैप करके कॉल, SMS और WhatsApp संदेश तैयार करें।",
                color = TextDim,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun InstalledAppLauncherCard(
    installedApps: List<InstalledAppInfo>,
    onOpenAppByName: (String) -> Unit,
    onLaunchInstalledApp: (InstalledAppInfo) -> Unit
) {
    var appQuery by remember { mutableStateOf("") }
    val filteredApps = remember(appQuery, installedApps) {
        val clean = appQuery.trim().lowercase()
        if (clean.isEmpty()) {
            installedApps.take(12)
        } else {
            installedApps.filter {
                it.appName.lowercase().contains(clean) ||
                    it.packageName.lowercase().contains(clean)
            }.take(16)
        }
    }

    Text(
        text = "Open Any Installed App (${installedApps.size})",
        color = CyberCyan,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(8.dp))

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, SurfaceBorder),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "किसी भी ऐप का नाम बोलें (जैसे \"Instagram kholo\", \"Paytm चालू करो\") या नीचे खोजकर खोलें:",
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = appQuery,
                    onValueChange = { appQuery = it },
                    placeholder = { Text("ऐप का नाम लिखें...", color = TextDim, fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Installed App",
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = SurfaceBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = SurfaceCardElevated,
                        unfocusedContainerColor = SurfaceCardElevated
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("installed_app_search_input")
                )

                Button(
                    onClick = { onOpenAppByName(appQuery) },
                    enabled = appQuery.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberCyan,
                        disabledContainerColor = SurfaceBorder
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("open_searched_app_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open App",
                        tint = DeepSpace,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Open", color = DeepSpace, fontWeight = FontWeight.Bold)
                }
            }

            if (filteredApps.isEmpty()) {
                Text(
                    text = "इस नाम का कोई इंस्टॉल ऐप नहीं मिला।",
                    color = TextDim,
                    fontSize = 11.sp
                )
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        FilterChip(
                            selected = false,
                            onClick = { onLaunchInstalledApp(app) },
                            label = {
                                Text(
                                    text = app.appName,
                                    fontSize = 12.sp,
                                    color = TextPrimary,
                                    maxLines = 1
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Apps,
                                    contentDescription = "Launch ${app.appName}",
                                    tint = CyberCyan,
                                    modifier = Modifier.size(15.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = SurfaceCardElevated,
                                labelColor = TextPrimary
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("installed_app_chip_${app.packageName}")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileAppsGridCard(
    onOpenYouTube: () -> Unit,
    onOpenChrome: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenSettings: (String) -> Unit
) {
    Text(
        text = "Mobile App Shortcuts",
        color = CyberCyan,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(10.dp))

    val apps = listOf(
        AppShortcutItem("YouTube", Icons.Default.SmartDisplay, Color(0xFFFF0033), onOpenYouTube),
        AppShortcutItem("Chrome", Icons.Default.Public, CyberCyan, onOpenChrome),
        AppShortcutItem("Camera", Icons.Default.CameraAlt, CosmicMagenta, onOpenCamera),
        AppShortcutItem("WiFi Settings", Icons.Default.Wifi, ElectricBlue) { onOpenSettings("wifi") },
        AppShortcutItem("Bluetooth", Icons.Default.Bluetooth, ElectricBlue) { onOpenSettings("bluetooth") },
        AppShortcutItem("Sound Settings", Icons.Default.VolumeUp, NeonPurple) { onOpenSettings("sound") },
        AppShortcutItem("Display", Icons.Default.BrightnessMedium, CyberCyan) { onOpenSettings("display") },
        AppShortcutItem("All Settings", Icons.Default.Settings, TextSecondary) { onOpenSettings("general") }
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, SurfaceBorder),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            val rows = apps.chunked(4)
            rows.forEach { rowItems ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    rowItems.forEach { app ->
                        ShortcutTile(item = app)
                    }
                }
            }
        }
    }
}

@Composable
private fun BatteryReliabilityCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, SurfaceBorder),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.BatteryChargingFull,
                    contentDescription = null,
                    tint = CyberCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Battery Optimization & Reliability",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "On Android 15, the system may restrict background microphone or notification listener access if aggressive battery saving is active. Keep JULI excluded from aggressive battery optimization in system settings for stable background detection.",
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun ShortcutTile(item: AppShortcutItem) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = item.onClick)
            .padding(6.dp)
            .testTag("shortcut_${item.name}")
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(SurfaceCardElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.name,
                tint = item.tint,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.name,
            fontSize = 10.sp,
            color = TextSecondary,
            maxLines = 1
        )
    }
}

private data class AppShortcutItem(
    val name: String,
    val icon: ImageVector,
    val tint: Color,
    val onClick: () -> Unit
)
