package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserMemory
import com.example.ui.theme.CosmicMagenta
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkCosmos
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
fun SettingsScreen(
    currentLanguage: String,
    selectedLiveVoice: String,
    speechRate: Float,
    pitch: Float,
    isWakeWordActive: Boolean,
    memories: List<UserMemory>,
    onLanguageChange: (String) -> Unit,
    onSelectLiveVoice: (String) -> Unit,
    onSpeechParamsChange: (Float, Float) -> Unit,
    onToggleWakeWord: () -> Unit,
    onDeleteMemory: (String) -> Unit,
    onRequestClearAllData: () -> Unit,
    modifier: Modifier = Modifier
) {
    var rateValue by remember(speechRate) { mutableFloatStateOf(speechRate) }
    var pitchValue by remember(pitch) { mutableFloatStateOf(pitch) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DeepSpace)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section: "Hey JULI" Wake Word Toggle
        item {
            Text(
                text = "Wake-Word Service (\"Hey JULI\")",
                color = CyberCyan,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Background Listening (\"Hey JULI\")",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isWakeWordActive) "Active • Persistent notification running" else "Disabled • Tap to enable background detection",
                            color = if (isWakeWordActive) StateSuccess else TextDim,
                            fontSize = 11.sp
                        )
                    }

                    Switch(
                        checked = isWakeWordActive,
                        onCheckedChange = { onToggleWakeWord() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DeepSpace,
                            checkedTrackColor = CyberCyan,
                            uncheckedThumbColor = TextDim,
                            uncheckedTrackColor = SurfaceBorder
                        ),
                        modifier = Modifier.testTag("wake_word_switch")
                    )
                }
            }
        }

        // Section: Language Preference (Hindi, English, Bhojpuri, Auto)
        item {
            Text(
                text = "Language & Voice (भाषा एवं आवाज़)",
                color = CyberCyan,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Choose your preferred assistant language:",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LanguageSelectButton(
                            label = "हिंदी (Hindi)",
                            isSelected = currentLanguage == "hi",
                            onClick = { onLanguageChange("hi") },
                            modifier = Modifier.weight(1f)
                        )
                        LanguageSelectButton(
                            label = "English",
                            isSelected = currentLanguage == "en",
                            onClick = { onLanguageChange("en") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LanguageSelectButton(
                            label = "भोजपुरी (Bhojpuri)",
                            isSelected = currentLanguage == "bho",
                            onClick = { onLanguageChange("bho") },
                            modifier = Modifier.weight(1f)
                        )
                        LanguageSelectButton(
                            label = "Auto Detect",
                            isSelected = currentLanguage == "auto",
                            onClick = { onLanguageChange("auto") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Section: Gemini Live Female Voice Selection
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Gemini Live Female Voice",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Applies on next Gemini Live session start:",
                        color = TextDim,
                        fontSize = 11.sp
                    )

                    com.example.ai.JuliLiveManager.femaleVoices.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            pair.forEach { voice ->
                                LanguageSelectButton(
                                    label = "${voice.label} (${voice.style.substringBefore(" ")})",
                                    isSelected = selectedLiveVoice == voice.id,
                                    onClick = { onSelectLiveVoice(voice.id) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section: Sweet Female Voice Speed and Pitch controls
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Speech Speed (${String.format(Locale.US, "%.2f", rateValue)}x)",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(text = "Default: 0.95x", color = TextDim, fontSize = 11.sp)
                    }

                    Slider(
                        value = rateValue,
                        onValueChange = {
                            rateValue = it
                            onSpeechParamsChange(rateValue, pitchValue)
                        },
                        valueRange = 0.7f..1.3f,
                        colors = SliderDefaults.colors(
                            thumbColor = CyberCyan,
                            activeTrackColor = CyberCyan,
                            inactiveTrackColor = SurfaceBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Sweet Female Pitch (${String.format(Locale.US, "%.2f", pitchValue)}x)",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(text = "Natural: 1.08x", color = CosmicMagenta, fontSize = 11.sp)
                    }

                    Slider(
                        value = pitchValue,
                        onValueChange = {
                            pitchValue = it
                            onSpeechParamsChange(rateValue, pitchValue)
                        },
                        valueRange = 0.8f..1.4f,
                        colors = SliderDefaults.colors(
                            thumbColor = CosmicMagenta,
                            activeTrackColor = CosmicMagenta,
                            inactiveTrackColor = SurfaceBorder
                        )
                    )
                }
            }
        }

        // Section: User Memory & Stored Preferences
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Stored Memory (${memories.size})",
                    color = CyberCyan,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                if (memories.isNotEmpty()) {
                    Text(
                        text = "User-controlled",
                        color = TextDim,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (memories.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = TextDim,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "No saved memory yet",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Say: \"Mera naam Rahul hai, yaad rakho\"",
                            color = TextDim,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        items(memories, key = { it.key }) { memory ->
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = memory.value,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Key: ${memory.key} • Category: ${memory.category}",
                            color = TextDim,
                            fontSize = 10.sp
                        )
                    }

                    IconButton(
                        onClick = { onDeleteMemory(memory.key) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Memory",
                            tint = StateError.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Section: Privacy & Danger Zone
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Privacy & Data Management",
                color = StateError,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, StateError.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "All your notes, tasks, and memory are stored 100% locally on your device. You can erase all data at any time.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onRequestClearAllData,
                        colors = ButtonDefaults.buttonColors(containerColor = StateError),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear All Stored Data", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Section: Device & Android 15 Notice
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCardElevated),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "About JULI AI", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "App: JULI AI Personal Voice Assistant", color = TextSecondary, fontSize = 11.sp)
                    Text(text = "Wake-Word: 'Hey JULI' (Lightweight On-Device Engine)", color = TextSecondary, fontSize = 11.sp)
                    Text(text = "Device Profile: Realme 11 Pro (Android 15)", color = TextSecondary, fontSize = 11.sp)
                    Text(text = "Security & Privacy: Zero cloud streaming during idle wake-word wait.", color = TextDim, fontSize = 10.sp)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun LanguageSelectButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) CyberCyan.copy(alpha = 0.2f) else SurfaceCardElevated,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) CyberCyan else SurfaceBorder
        ),
        modifier = modifier.height(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) CyberCyan else TextSecondary
            )
        }
    }
}
