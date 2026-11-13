package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.CapturedNotification
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DeepSpace
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.StateError
import com.example.ui.theme.StateSuccess
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardElevated
import com.example.ui.theme.TextDim
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotificationsPanel(
    isNotificationAccessEnabled: Boolean,
    autoAnnounceEnabled: Boolean,
    readContentEnabled: Boolean,
    notifications: List<CapturedNotification>,
    onOpenNotificationSettings: () -> Unit,
    onToggleAutoAnnounce: (Boolean) -> Unit,
    onToggleReadContent: (Boolean) -> Unit,
    onReadAllAloud: () -> Unit,
    onReadSingleAloud: (CapturedNotification) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Messages & Notification Reader (बोलकर सूचना)",
            color = CyberCyan,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(
                1.dp,
                if (isNotificationAccessEnabled) StateSuccess.copy(alpha = 0.6f) else StateError.copy(alpha = 0.6f)
            ),
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
                            imageVector = if (isNotificationAccessEnabled) {
                                Icons.Default.NotificationsActive
                            } else {
                                Icons.Default.NotificationsOff
                            },
                            contentDescription = "Notification Reader Status",
                            tint = if (isNotificationAccessEnabled) StateSuccess else StateError,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (isNotificationAccessEnabled) {
                                    "Notification Reader Active"
                                } else {
                                    "Notification Access Needed"
                                },
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isNotificationAccessEnabled) {
                                    "WhatsApp, SMS, Gmail और अन्य नोटिफिकेशन पढ़े जा रहे हैं"
                                } else {
                                    "आने वाले मैसेज व नोटिफिकेशन बोलकर बताने के लिए अनुमति चालू करें"
                                },
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    if (isNotificationAccessEnabled) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Enabled",
                            tint = StateSuccess,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (!isNotificationAccessEnabled) {
                    Button(
                        onClick = onOpenNotificationSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("enable_notification_reader_button")
                    ) {
                        Text(
                            text = "Enable Notification Access in Settings",
                            color = DeepSpace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = onOpenNotificationSettings,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("manage_notification_reader_button")
                    ) {
                        Text("Manage Notification Access", color = CyberCyan, fontSize = 12.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Announce New Notifications",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "नया नोटिफिकेशन आते ही बताएं कि किसका है",
                            color = TextDim,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = autoAnnounceEnabled,
                        onCheckedChange = onToggleAutoAnnounce,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DeepSpace,
                            checkedTrackColor = CyberCyan,
                            uncheckedThumbColor = TextDim,
                            uncheckedTrackColor = SurfaceBorder
                        ),
                        modifier = Modifier.testTag("auto_announce_notifications_switch")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Read Message Text Automatically",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "नाम के साथ मैसेज की पूरी लाइन भी बोलकर सुनाएं",
                            color = TextDim,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = readContentEnabled,
                        onCheckedChange = onToggleReadContent,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DeepSpace,
                            checkedTrackColor = NeonPurple,
                            uncheckedThumbColor = TextDim,
                            uncheckedTrackColor = SurfaceBorder
                        ),
                        modifier = Modifier.testTag("read_notification_content_switch")
                    )
                }
            }
        }

        // Actions row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onReadAllAloud,
                colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("read_all_notifications_button")
            ) {
                Icon(
                    imageVector = Icons.Default.RecordVoiceOver,
                    contentDescription = "Read Recent Messages",
                    tint = TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Read Aloud (${notifications.size})", color = TextPrimary, fontWeight = FontWeight.Bold)
            }

            if (notifications.isNotEmpty()) {
                OutlinedButton(
                    onClick = onClearAll,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("clear_notifications_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ClearAll,
                        contentDescription = "Clear Notifications",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (notifications.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = BorderStroke(1.dp, SurfaceBorder),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(18.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "अभी कोई कैप्चर किया गया नोटिफिकेशन नहीं है",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "WhatsApp या SMS आने पर यहाँ दिखेंगे और आप बोलकर भी सुन सकते हैं: \"किसका नोटिफिकेशन आया है?\"",
                        color = TextDim,
                        fontSize = 11.sp
                    )
                }
            }
        } else {
            notifications.forEach { item ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    border = BorderStroke(1.dp, SurfaceBorder),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("notification_card_${item.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    color = CyberCyan.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = item.appName,
                                        color = CyberCyan,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = timeFormatter.format(Date(item.timestamp)),
                                    color = TextDim,
                                    fontSize = 10.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.sender,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (item.message.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.message,
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(
                            onClick = { onReadSingleAloud(item) },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("read_single_notification_${item.id}")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Read notification aloud",
                                tint = CyberCyan
                            )
                        }
                    }
                }
            }
        }
    }
}
