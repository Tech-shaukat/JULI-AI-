package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CosmicMagenta
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DeepSpace
import com.example.ui.theme.DeepViolet
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.StateError
import com.example.ui.theme.StateListening
import com.example.ui.theme.StateSpeaking
import com.example.ui.theme.StateThinking
import kotlin.math.cos
import kotlin.math.sin

enum class OrbState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    ERROR
}

@Composable
fun JuliOrb(
    state: OrbState,
    rmsLevel: Float = 0f,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    onClick: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_infinite")

    // Continuous rotation for gyroscope rings
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (state == OrbState.THINKING) 3000 else 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Reverse rotation for counter ring
    val counterRotationAngle by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (state == OrbState.THINKING) 4000 else 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "counter_rotation"
    )

    // Idle breathing pulse
    val breathingPulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )

    // Speaking wave expansion
    val speakingWave by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "speaking_wave"
    )

    // Dynamic reactive scale based on state and microphone RMS volume
    val reactiveScale = remember { Animatable(1f) }
    LaunchedEffect(state, rmsLevel) {
        val target = when (state) {
            OrbState.LISTENING -> 1.0f + (rmsLevel * 0.28f)
            OrbState.SPEAKING -> 1.05f + (speakingWave * 0.08f)
            OrbState.THINKING -> 1.02f
            OrbState.IDLE -> breathingPulse
            OrbState.ERROR -> 0.95f
        }
        reactiveScale.animateTo(target, tween(120))
    }

    // Determine color scheme based on current assistant state
    val primaryColor = when (state) {
        OrbState.LISTENING -> StateListening
        OrbState.THINKING -> StateThinking
        OrbState.SPEAKING -> StateSpeaking
        OrbState.IDLE -> NeonPurple
        OrbState.ERROR -> StateError
    }

    val secondaryColor = when (state) {
        OrbState.LISTENING -> CyberCyan
        OrbState.THINKING -> CosmicMagenta
        OrbState.SPEAKING -> NeonPurple
        OrbState.IDLE -> CyberCyan
        OrbState.ERROR -> DeepViolet
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag("assistant_orb")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseRadius = this.size.minDimension / 2f
            val orbRadius = baseRadius * 0.52f * reactiveScale.value

            // 1. Ambient Background Glow Aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = if (state == OrbState.LISTENING || state == OrbState.SPEAKING) 0.35f else 0.22f),
                        secondaryColor.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 0.98f
                ),
                radius = baseRadius * 0.98f,
                center = center
            )

            // 2. Ripple waves for LISTENING / SPEAKING state
            if (state == OrbState.LISTENING) {
                val rippleRadius = orbRadius + (rmsLevel * baseRadius * 0.40f)
                drawCircle(
                    color = primaryColor.copy(alpha = (1f - rmsLevel * 0.5f).coerceIn(0.2f, 0.8f)),
                    radius = rippleRadius,
                    center = center,
                    style = Stroke(width = 2.5.dp.toPx())
                )
                drawCircle(
                    color = secondaryColor.copy(alpha = 0.3f),
                    radius = orbRadius + (rmsLevel * baseRadius * 0.22f),
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            } else if (state == OrbState.SPEAKING) {
                val waveR1 = orbRadius + (speakingWave * baseRadius * 0.38f)
                val waveAlpha1 = (1f - speakingWave).coerceIn(0f, 0.7f)
                drawCircle(
                    color = StateSpeaking.copy(alpha = waveAlpha1),
                    radius = waveR1,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
                val waveR2 = orbRadius + (((speakingWave + 0.5f) % 1f) * baseRadius * 0.38f)
                val waveAlpha2 = (1f - ((speakingWave + 0.5f) % 1f)).coerceIn(0f, 0.7f)
                drawCircle(
                    color = NeonPurple.copy(alpha = waveAlpha2),
                    radius = waveR2,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            // 3. Orbital Dashed Rings (Clockwise)
            val ringRadius = baseRadius * 0.78f
            drawOrbitalRing(
                center = center,
                radius = ringRadius,
                rotationDegrees = rotationAngle,
                color = secondaryColor.copy(alpha = 0.65f),
                strokeWidth = 1.8.dp.toPx(),
                dashOn = 20f,
                dashOff = 16f
            )

            // 4. Counter-rotating Gyro Ring
            drawOrbitalRing(
                center = center,
                radius = ringRadius * 0.86f,
                rotationDegrees = counterRotationAngle,
                color = primaryColor.copy(alpha = 0.55f),
                strokeWidth = 1.4.dp.toPx(),
                dashOn = 35f,
                dashOff = 25f
            )

            // 5. Energy nodes / Satellite dots along the rings
            val nodeAngleRad = Math.toRadians(rotationAngle.toDouble())
            val nodeX = center.x + (ringRadius * cos(nodeAngleRad)).toFloat()
            val nodeY = center.y + (ringRadius * sin(nodeAngleRad)).toFloat()
            drawCircle(
                color = primaryColor,
                radius = 3.5.dp.toPx(),
                center = Offset(nodeX, nodeY)
            )

            val node2AngleRad = Math.toRadians((counterRotationAngle + 180).toDouble())
            val node2X = center.x + (ringRadius * 0.86f * cos(node2AngleRad)).toFloat()
            val node2Y = center.y + (ringRadius * 0.86f * sin(node2AngleRad)).toFloat()
            drawCircle(
                color = secondaryColor,
                radius = 2.8.dp.toPx(),
                center = Offset(node2X, node2Y)
            )

            // 6. Glowing Core Orb
            val coreGradient = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.95f),
                    secondaryColor.copy(alpha = 0.90f),
                    primaryColor.copy(alpha = 0.85f),
                    DeepViolet.copy(alpha = 0.95f),
                    DeepSpace
                ),
                center = center,
                radius = orbRadius
            )
            drawCircle(
                brush = coreGradient,
                radius = orbRadius,
                center = center
            )

            // 7. Core Perimeter Highlight Ring
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        CyberCyan,
                        NeonPurple,
                        CosmicMagenta,
                        ElectricBlue,
                        CyberCyan
                    ),
                    center = center
                ),
                radius = orbRadius,
                center = center,
                style = Stroke(width = 2.5.dp.toPx())
            )

            // 8. Inner Specular Highlight / Lens reflection
            val highlightCenter = Offset(center.x - orbRadius * 0.28f, center.y - orbRadius * 0.28f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.65f),
                        Color.White.copy(alpha = 0.0f)
                    ),
                    center = highlightCenter,
                    radius = orbRadius * 0.45f
                ),
                radius = orbRadius * 0.45f,
                center = highlightCenter
            )
        }
    }
}

private fun DrawScope.drawOrbitalRing(
    center: Offset,
    radius: Float,
    rotationDegrees: Float,
    color: Color,
    strokeWidth: Float,
    dashOn: Float,
    dashOff: Float
) {
    drawCircle(
        color = color,
        radius = radius,
        center = center,
        style = Stroke(
            width = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(
                intervals = floatArrayOf(dashOn, dashOff),
                phase = rotationDegrees
            )
        )
    )
}
