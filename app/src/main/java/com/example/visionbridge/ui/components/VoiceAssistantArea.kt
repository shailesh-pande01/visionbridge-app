package com.example.visionbridge.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.visionbridge.ui.theme.Accent
import com.example.visionbridge.ui.theme.BgPrimary
import com.example.visionbridge.ui.theme.BgSecondary
import com.example.visionbridge.voice.VoiceState
import com.example.visionbridge.voice.VoiceStatus

@Composable
fun VoiceAssistantArea(
    voiceState: VoiceState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (stateText, iconText) = when (voiceState.status) {
        VoiceStatus.IDLE -> Pair("Tap or say \"Vision\"", "🎙️")
        VoiceStatus.LISTENING -> Pair("Listening for \"Vision\"…", "🎙️")
        VoiceStatus.AWAITING_COMMAND -> Pair("Listening for command…", "✨")
        VoiceStatus.PROCESSING -> Pair("Understanding command…", "⏳")
        VoiceStatus.NAVIGATING -> Pair("Opening feature…", "➡️")
        VoiceStatus.SPEAKING -> Pair(voiceState.message.ifBlank { "Speaking…" }, "🔊")
        VoiceStatus.PAUSED -> Pair("Voice assistant paused", "⏸️")
        VoiceStatus.BLOCKED -> Pair(voiceState.error.ifBlank { "Microphone permission required" }, "🚫")
        VoiceStatus.UNSUPPORTED -> Pair(voiceState.error.ifBlank { "Voice not supported" }, "⚠️")
        VoiceStatus.ERROR -> Pair(voiceState.error.ifBlank { "Couldn't understand" }, "⚠️")
    }

    val isListening = voiceState.status == VoiceStatus.LISTENING ||
            voiceState.status == VoiceStatus.AWAITING_COMMAND

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        shape = RoundedCornerShape(24.dp),
        color = BgSecondary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (voiceState.transcript.isNotBlank()) "“${voiceState.transcript}”" else stateText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val scale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = if (isListening) 1.4f else 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse_scale"
            )

            val alpha by infiniteTransition.animateFloat(
                initialValue = if (isListening) 0.5f else 0f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "pulse_alpha"
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(160.dp)
            ) {
                if (isListening || voiceState.status == VoiceStatus.PROCESSING) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .scale(if (isListening) scale else 1.1f)
                            .background(Accent.copy(alpha = if (isListening) alpha else 0.2f), shape = CircleShape)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(if (voiceState.status == VoiceStatus.IDLE) Accent.copy(alpha = 0.8f) else Accent)
                        .clickable { onClick() }
                        .semantics {
                            contentDescription = "Voice Assistant Area. Current state: $stateText. Tap to interact."
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = iconText, fontSize = 48.sp)
                }
            }
        }
    }
}
