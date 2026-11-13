package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Hearing
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
fun ActionsScreen(
    isWakeWordActive: Boolean,
    onToggleWakeWord: () -> Unit,
    onRequestCall: () -> Unit,
    onRequestSms: () -> Unit,
    onRequestWhatsApp: () -> Unit,
    onOpenYouTube: () -> Unit,
    onOpenChrome: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenSettings: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DeepSpace)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section: "Hey JULI" Foreground Wake Word Control
        item {
            Text(
                text = "Wake-Word Detection (\"Hey JULI\")",
                color = CyberCyan,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = androidx.compose.foundation.BorderStroke(
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

        // Section: Communication Shortcuts (Call, SMS, WhatsApp with Confirmation)
        item {
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
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
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
                        text = "बोलकर या टैप करके कॉल, SMS और WhatsApp संदेश तैयार करें — हर एक्शन से पहले स्क्रीन पर Confirmation मिलेगा।",
                        color = TextDim,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }

        // Section: System App Shortcuts
        item {
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
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
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

        // Section: Android 15 & Battery Optimization Notice
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
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
                        text = "On Android 15 (Realme 11 Pro), the system may restrict background microphone access if aggressive battery saving is active. Keep JULI excluded from aggressive battery optimization in system settings for stable background detection.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
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
                .size(44.dp)
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
