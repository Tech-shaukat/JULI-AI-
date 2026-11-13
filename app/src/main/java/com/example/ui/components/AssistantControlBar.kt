package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.LivePhase
import com.example.ui.theme.CosmicMagenta
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DeepSpace
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.StateError
import com.example.ui.theme.StateListening
import com.example.ui.theme.StateSpeaking
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun AssistantControlBar(
    state: OrbState,
    livePhase: LivePhase,
    language: String,
    onToggleLive: () -> Unit,
    onQuickVoiceClick: () -> Unit,
    onStopSpeaking: () -> Unit,
    onLanguageChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isLiveConnected = livePhase == LivePhase.LISTENING ||
        livePhase == LivePhase.THINKING ||
        livePhase == LivePhase.SPEAKING
    val isConnecting = livePhase == LivePhase.CONNECTING

    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_aura"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        // Stop Speaking Button (Shown when TTS speaking outside Live mode)
        AnimatedVisibility(
            visible = state == OrbState.SPEAKING && !isLiveConnected,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            ElevatedButton(
                onClick = onStopSpeaking,
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = StateSpeaking.copy(alpha = 0.9f),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .padding(bottom = 10.dp)
                    .testTag("stop_speaking_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop Speaking",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (language == "hi") "आवाज़ बंद करें (Stop)" else "Stop Speaking",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        // Language Selector Pills
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 10.dp)
        ) {
            LanguageChip(
                label = "हिंदी",
                isSelected = language == "hi",
                onClick = { onLanguageChange("hi") }
            )
            LanguageChip(
                label = "English",
                isSelected = language == "en",
                onClick = { onLanguageChange("en") }
            )
            LanguageChip(
                label = "भोजपुरी",
                isSelected = language == "bho",
                onClick = { onLanguageChange("bho") }
            )
            LanguageChip(
                label = "Auto",
                isSelected = language == "auto",
                onClick = { onLanguageChange("auto") }
            )
        }

        // Action Row: Quick Voice Cmd + Central Gemini Live Button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Secondary Quick Voice Command button (SpeechRecognizer -> Gemini 3.8 Flash)
            Surface(
                onClick = onQuickVoiceClick,
                shape = RoundedCornerShape(24.dp),
                color = if (state == OrbState.LISTENING && !isLiveConnected) StateListening.copy(alpha = 0.22f) else SurfaceCard,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (state == OrbState.LISTENING && !isLiveConnected) StateListening else SurfaceBorder
                ),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("microphone_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Quick Voice Command",
                        tint = if (state == OrbState.LISTENING && !isLiveConnected) StateListening else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Quick Cmd",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
            }

            // Primary Central Gemini Live Voice Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(86.dp)
            ) {
                if (isLiveConnected) {
                    Box(
                        modifier = Modifier
                            .size(82.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(StateListening.copy(alpha = 0.22f))
                    )
                }

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                if (isLiveConnected) {
                                    listOf(StateError, CosmicMagenta)
                                } else {
                                    listOf(CyberCyan, ElectricBlue, NeonPurple)
                                }
                            )
                        )
                        .padding(3.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .clip(CircleShape)
                            .background(if (isLiveConnected) StateError else SurfaceCardElevated)
                            .clickable(onClick = onToggleLive)
                            .testTag("gemini_live_button")
                    ) {
                        when {
                            isConnecting -> CircularProgressIndicator(
                                color = CyberCyan,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(28.dp)
                            )
                            isLiveConnected -> Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "End Gemini Live Session",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                            else -> Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Start Gemini Live Voice Conversation",
                                tint = CyberCyan,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = when {
                isConnecting -> "Connecting Gemini Live..."
                isLiveConnected -> "Gemini Live Active • Tap Red to End"
                else -> "Tap Wave for Gemini Live Talk"
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (isLiveConnected) CyberCyan else TextSecondary
        )
    }
}

@Composable
private fun LanguageChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) CyberCyan.copy(alpha = 0.18f) else SurfaceCard,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isSelected) CyberCyan else SurfaceBorder
        ),
        modifier = Modifier.height(32.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) CyberCyan else TextSecondary
            )
        }
    }
}
