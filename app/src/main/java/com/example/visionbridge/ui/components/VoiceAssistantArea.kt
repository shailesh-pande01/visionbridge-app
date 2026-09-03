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

import androidx.compose.ui.res.stringResource
import com.example.visionbridge.R

@Composable
fun VoiceAssistantArea(
    voiceState: VoiceState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stateText = when (voiceState.status) {
        VoiceStatus.IDLE -> stringResource(R.string.voice_status_tap_or_say_vision)
        VoiceStatus.LISTENING -> stringResource(R.string.voice_status_listening_for_vision)
        VoiceStatus.AWAITING_COMMAND -> stringResource(R.string.voice_status_listening_for_command)
        VoiceStatus.PROCESSING -> stringResource(R.string.voice_status_processing_command)
        VoiceStatus.NAVIGATING -> stringResource(R.string.voice_status_opening_feature)
        VoiceStatus.SPEAKING -> voiceState.message.ifBlank { stringResource(R.string.voice_status_speaking) }
        VoiceStatus.PAUSED -> stringResource(R.string.voice_status_paused)
        VoiceStatus.BLOCKED -> voiceState.error.ifBlank { stringResource(R.string.voice_status_blocked) }
        VoiceStatus.UNSUPPORTED -> voiceState.error.ifBlank { stringResource(R.string.voice_status_unsupported) }
        VoiceStatus.ERROR -> voiceState.error.ifBlank { stringResource(R.string.voice_status_error) }
    }

    val iconText = when (voiceState.status) {
        VoiceStatus.IDLE -> "🎙️"
        VoiceStatus.LISTENING -> "🎙️"
        VoiceStatus.AWAITING_COMMAND -> "✨"
        VoiceStatus.PROCESSING -> "⏳"
        VoiceStatus.NAVIGATING -> "➡️"
        VoiceStatus.SPEAKING -> "🔊"
        VoiceStatus.PAUSED -> "⏸️"
        VoiceStatus.BLOCKED -> "🚫"
        VoiceStatus.UNSUPPORTED -> "⚠️"
        VoiceStatus.ERROR -> "⚠️"
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

                val areaDescription = stringResource(R.string.voice_status_description, stateText)
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(if (voiceState.status == VoiceStatus.IDLE) Accent.copy(alpha = 0.8f) else Accent)
                        .clickable { onClick() }
                        .semantics {
                            contentDescription = areaDescription
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = iconText, fontSize = 48.sp)
                }
            }
        }
    }
}
