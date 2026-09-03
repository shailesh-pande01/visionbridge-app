package com.example.visionbridge.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.visionbridge.ui.theme.*
import com.example.visionbridge.voice.VoiceState
import com.example.visionbridge.voice.VoiceStatus

import androidx.compose.ui.res.stringResource
import com.example.visionbridge.R

/**
 * Global accessible status bar displaying the state of the voice engine.
 * Visible across low-vision user screens to provide high-contrast visual and TalkBack feedback.
 */
@Composable
fun VoiceStatusBar(
    voiceState: VoiceState,
    onActivate: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusTitle = when (voiceState.status) {
        VoiceStatus.IDLE -> stringResource(R.string.voice_status_ready)
        VoiceStatus.LISTENING -> stringResource(R.string.voice_status_listening_for_vision)
        VoiceStatus.AWAITING_COMMAND -> stringResource(R.string.voice_status_listening_for_command)
        VoiceStatus.PROCESSING -> stringResource(R.string.voice_status_processing_command)
        VoiceStatus.NAVIGATING -> stringResource(R.string.voice_status_opening_feature)
        VoiceStatus.SPEAKING -> stringResource(R.string.voice_status_speaking)
        VoiceStatus.PAUSED -> stringResource(R.string.voice_status_paused)
        VoiceStatus.BLOCKED -> stringResource(R.string.voice_status_blocked)
        VoiceStatus.UNSUPPORTED -> stringResource(R.string.voice_status_unsupported)
        VoiceStatus.ERROR -> stringResource(R.string.voice_status_error)
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

    val isPulse = voiceState.status == VoiceStatus.LISTENING ||
            voiceState.status == VoiceStatus.AWAITING_COMMAND

    val isListening = voiceState.status == VoiceStatus.LISTENING ||
            voiceState.status == VoiceStatus.AWAITING_COMMAND

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_trans")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isPulse) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = BgSecondary,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = "$statusTitle. ${voiceState.transcript.ifBlank { voiceState.message }}"
                }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(if (isListening) Accent.copy(alpha = 0.25f) else BgCard)
                            .clickable { onActivate() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = iconText, fontSize = 22.sp)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = statusTitle,
                            color = if (isListening) Accent else TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )

                        if (voiceState.status == VoiceStatus.LISTENING) {
                            Text(
                                text = stringResource(R.string.voice_status_say_vision_to_speak),
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                if (voiceState.status == VoiceStatus.LISTENING ||
                    voiceState.status == VoiceStatus.AWAITING_COMMAND ||
                    voiceState.status == VoiceStatus.SPEAKING
                ) {
                    TextButton(
                        onClick = onStop,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.common_stop),
                            color = TextMuted,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            AnimatedVisibility(visible = voiceState.transcript.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = BgCard,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "“${voiceState.transcript}”",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            AnimatedVisibility(visible = voiceState.error.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = voiceState.error,
                    color = Emergency,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
