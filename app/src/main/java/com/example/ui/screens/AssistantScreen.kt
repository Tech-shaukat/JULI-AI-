package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.LivePhase
import com.example.ai.LiveUiState
import com.example.ui.components.AssistantControlBar
import com.example.ui.components.AudioVisualizer
import com.example.ui.components.JuliOrb
import com.example.ui.components.OrbState
import com.example.ui.theme.CosmicMagenta
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkCosmos
import com.example.ui.theme.DeepSpace
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.StateError
import com.example.ui.theme.StateListening
import com.example.ui.theme.StateSpeaking
import com.example.ui.theme.StateSuccess
import com.example.ui.theme.StateThinking
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextDim
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun AssistantScreen(
    assistantState: OrbState,
    liveUiState: LiveUiState,
    rmsLevel: Float,
    statusText: String,
    liveTranscription: String,
    lastActionBadge: String?,
    language: String,
    isWakeWordActive: Boolean,
    onToggleLive: () -> Unit,
    onQuickVoiceClick: () -> Unit,
    onStopSpeaking: () -> Unit,
    onLanguageChange: (String) -> Unit,
    onToggleWakeWord: () -> Unit,
    onQuickAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(DarkCosmos, DeepSpace, Color(0xFF070B14))
                )
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Upper section: State Pill, Wake Word Toggle & Live Captions
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 14.dp, start = 16.dp, end = 16.dp)
        ) {
            StateBadge(state = assistantState, statusText = statusText)

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WakeWordTogglePill(
                    isActive = isWakeWordActive,
                    onToggle = onToggleWakeWord
                )

                if (lastActionBadge != null) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = NeonPurple.copy(alpha = 0.18f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonPurple.copy(alpha = 0.6f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = lastActionBadge,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Live User/Model Transcription Card
            val userText = liveUiState.userCaption.ifBlank { liveTranscription }
            val modelText = liveUiState.modelCaption
            AnimatedVisibility(
                visible = userText.isNotBlank() || (liveUiState.isConnected && modelText.isNotBlank()),
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceCard,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (liveUiState.phase == LivePhase.SPEAKING) NeonPurple.copy(alpha = 0.6f)
                        else CyberCyan.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth(0.94f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        if (userText.isNotBlank()) {
                            Text(
                                text = "आप: \"$userText\"",
                                color = CyberCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (liveUiState.isConnected && modelText.isNotBlank()) {
                            if (userText.isNotBlank()) Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "JULI: $modelText",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Center section: The Animated Glowing Orb (tapping toggles Gemini Live!)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 6.dp)
        ) {
            JuliOrb(
                state = assistantState,
                rmsLevel = rmsLevel,
                size = 210.dp,
                onClick = onToggleLive
            )

            Spacer(modifier = Modifier.height(10.dp))

            AudioVisualizer(
                state = assistantState,
                rmsLevel = rmsLevel
            )
        }

        // Bottom section: Quick Action Suggestions & Live/Mic Controls
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Text(
                text = "Voice Shortcuts • बोलकर काम करवाएँ",
                color = TextDim,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            QuickShortcutsRow(onSelect = onQuickAction)

            Spacer(modifier = Modifier.height(10.dp))

            AssistantControlBar(
                state = assistantState,
                livePhase = liveUiState.phase,
                language = language,
                onToggleLive = onToggleLive,
                onQuickVoiceClick = onQuickVoiceClick,
                onStopSpeaking = onStopSpeaking,
                onLanguageChange = onLanguageChange
            )
        }
    }
}

@Composable
private fun WakeWordTogglePill(
    isActive: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(20.dp),
        color = if (isActive) CyberCyan.copy(alpha = 0.16f) else SurfaceCard,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) CyberCyan else SurfaceBorder
        ),
        modifier = Modifier.testTag("wake_word_toggle_pill")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isActive) StateSuccess else TextDim)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isActive) "Hey JULI: ON" else "Hey JULI: OFF",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isActive) CyberCyan else TextSecondary
            )
        }
    }
}

@Composable
private fun StateBadge(state: OrbState, statusText: String) {
    val dotColor = when (state) {
        OrbState.LISTENING -> StateListening
        OrbState.THINKING -> StateThinking
        OrbState.SPEAKING -> StateSpeaking
        OrbState.IDLE -> CyberCyan
        OrbState.ERROR -> StateError
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = SurfaceCard.copy(alpha = 0.9f),
        border = androidx.compose.foundation.BorderStroke(1.dp, dotColor.copy(alpha = 0.5f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = statusText,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuickShortcutsRow(onSelect: (String) -> Unit) {
    val items = listOf(
        ShortcutItem("YouTube पर गाना चलाओ", Icons.Default.SmartDisplay, Color(0xFFFF0033)),
        ShortcutItem("WhatsApp मैसेज भेजो: नमस्ते!", Icons.AutoMirrored.Filled.Chat, Color(0xFF25D366)),
        ShortcutItem("कॉल लगाओ", Icons.Default.Call, StateSuccess),
        ShortcutItem("मेरा नया नोट लिखो", Icons.Default.EditNote, CyberCyan),
        ShortcutItem("एक ज़रूरी टास्क जोड़ो", Icons.Default.CheckCircle, NeonPurple),
        ShortcutItem("कैमरा खोलो", Icons.Default.CameraAlt, CosmicMagenta),
        ShortcutItem("मेरे नोट्स और टास्क बताओ", Icons.Default.Language, CyberCyan)
    )

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        items(items) { item ->
            Surface(
                onClick = { onSelect(item.label) },
                shape = RoundedCornerShape(16.dp),
                color = SurfaceCard,
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = item.tint,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = item.label,
                        fontSize = 12.sp,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

private data class ShortcutItem(
    val label: String,
    val icon: ImageVector,
    val tint: Color
)
