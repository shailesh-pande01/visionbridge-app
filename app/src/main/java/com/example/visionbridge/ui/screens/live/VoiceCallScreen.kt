package com.example.visionbridge.ui.screens.live

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.visionbridge.R
import com.example.visionbridge.data.SessionManager
import com.example.visionbridge.live.LiveMode
import com.example.visionbridge.live.LiveState
import com.example.visionbridge.live.LiveViewModel
import com.example.visionbridge.ui.theme.*
import com.example.visionbridge.voice.ScreenActionRegistry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceCallScreen(
    onBack: () -> Unit,
    onSwitchToVisionLive: () -> Unit,
    viewModel: LiveViewModel = viewModel()
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager.getInstance(context) }
    val currentLanguage by sessionManager.language.collectAsStateWithLifecycle()

    val liveState by viewModel.liveState.collectAsStateWithLifecycle()
    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
    val isAiSpeaking by viewModel.isAiSpeaking.collectAsStateWithLifecycle()
    val currentUserQuery by viewModel.currentUserQuery.collectAsStateWithLifecycle()
    val currentResponse by viewModel.currentResponse.collectAsStateWithLifecycle()
    val conversationTurns by viewModel.conversationTurns.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    var micGranted by remember { mutableStateOf(hasMic) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micGranted = granted
    }

    val isActive = liveState == LiveState.LISTENING ||
            liveState == LiveState.SPEAKING ||
            liveState == LiveState.CONNECTING ||
            liveState == LiveState.STARTING ||
            liveState == LiveState.THINKING

    // Auto-start voice call when opened
    LaunchedEffect(Unit) {
        if (!micGranted) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(micGranted) {
        if (micGranted && liveState == LiveState.IDLE) {
            viewModel.startSession(mode = LiveMode.VOICE)
        }
    }

    DisposableEffect(Unit) {
        ScreenActionRegistry.registerScreen(
            screenId = "voice_call",
            onCapture = {},
            onCancel = {
                viewModel.stopSession()
                onBack()
            }
        )

        onDispose {
            ScreenActionRegistry.unregisterScreen("voice_call")
            viewModel.stopSession()
        }
    }

    // Status text
    val statusText = when (liveState) {
        LiveState.STARTING -> stringResource(R.string.voice_call_status_starting)
        LiveState.CONNECTING -> stringResource(R.string.voice_call_status_connecting)
        LiveState.LISTENING -> if (isMuted) stringResource(R.string.voice_call_status_muted) else stringResource(R.string.voice_call_status_listening)
        LiveState.THINKING -> stringResource(R.string.voice_call_status_thinking)
        LiveState.SPEAKING -> stringResource(R.string.voice_call_status_speaking)
        LiveState.RECONNECTING -> stringResource(R.string.voice_call_status_reconnecting)
        LiveState.ERROR -> stringResource(R.string.voice_call_status_error)
        LiveState.STOPPED -> stringResource(R.string.voice_call_status_stopped)
        else -> stringResource(R.string.voice_call_status_listening)
    }

    // Quick conversational prompt suggestions
    val quickPrompts = when (currentLanguage) {
        "hi" -> listOf(
            "आज की ताज़ा ख़बरें क्या हैं?",
            "क्वांटम कंप्यूटिंग सरल शब्दों में समझाओ।",
            "ऑस्ट्रेलिया की राजधानी क्या है?",
            "मेरे दिन की योजना बनाने में मदद करो।"
        )
        "mr" -> listOf(
            "आजच्या ताज्या बातम्या काय आहेत?",
            "क्वांटम कॉम्प्युटिंग सोप्या भाषेत समजावून सांगा.",
            "ऑस्ट्रेलियाची राजधानी कोणती आहे?",
            "माझ्या दिवसाचे नियोजन करण्यास मदत करा."
        )
        else -> listOf(
            stringResource(R.string.voice_call_prompt_news),
            stringResource(R.string.voice_call_prompt_tech),
            stringResource(R.string.voice_call_prompt_capital),
            stringResource(R.string.voice_call_prompt_day)
        )
    }

    // Animated pulsing wave for speaking / listening states
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (liveState == LiveState.SPEAKING) 1.22f else 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (liveState == LiveState.SPEAKING) 600 else 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "avatarScale"
    )

    Scaffold(
        topBar = {
            com.example.visionbridge.ui.components.AppTopBar(
                title = stringResource(R.string.feature_voice_call),
                subtitle = statusText,
                onBack = {
                    viewModel.stopSession()
                    onBack()
                },
                backContentDescription = stringResource(R.string.common_back),
                actions = {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = BgSecondary,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = "🎙️ " + stringResource(R.string.voice_call_voice_only_badge),
                            color = Accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            )
        },
        containerColor = BgPrimary
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(BgPrimary)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section: Animated Avatar & State Headline
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                // Animated Glowing Voice Avatar
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(160.dp)
                        .clickable(enabled = isAiSpeaking) { viewModel.interruptAi() }
                ) {
                    // Outer glow wave
                    if (isActive) {
                        Box(
                            modifier = Modifier
                                .size(150.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            if (isMuted) Emergency.copy(alpha = 0.25f)
                                            else if (liveState == LiveState.SPEAKING) Accent.copy(alpha = 0.4f)
                                            else Success.copy(alpha = 0.25f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                    }

                    // Main Avatar Circle
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .background(
                                if (isMuted) Emergency
                                else if (liveState == LiveState.SPEAKING) Accent
                                else BgCard
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isMuted) "🔇" else if (liveState == LiveState.SPEAKING) "🔊" else "🎙️",
                            fontSize = 44.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Headline
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Hint
                Text(
                    text = if (liveState == LiveState.SPEAKING) stringResource(R.string.voice_call_hint_interrupt)
                    else if (isMuted) stringResource(R.string.voice_call_hint_muted)
                    else stringResource(R.string.voice_call_hint_speak),
                    color = TextMuted,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )

                // Error Message Card
                if (liveState == LiveState.ERROR) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        color = Emergency.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = errorMessage.ifBlank { "Could not connect to AI Voice Call." },
                                color = Emergency,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.startSession(mode = LiveMode.VOICE) },
                                colors = ButtonDefaults.buttonColors(containerColor = Accent)
                            ) {
                                Text("Retry Connection", color = BgPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Live Subtitles & Conversational Transcript Box
                if (currentUserQuery.isNotBlank() || currentResponse.isNotBlank() || conversationTurns.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Surface(
                        color = BgCard,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { liveRegion = LiveRegionMode.Polite }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            if (currentUserQuery.isNotBlank()) {
                                Text(
                                    text = "You: $currentUserQuery",
                                    color = Accent,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (currentResponse.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "AI: $currentResponse",
                                    color = TextPrimary,
                                    fontSize = 18.sp,
                                    lineHeight = 26.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            conversationTurns.takeLast(2).forEach { turn ->
                                if (currentResponse.isBlank() || turn.text != currentResponse) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = if (turn.role == "user") "You: ${turn.text}" else "AI: ${turn.text}",
                                        color = if (turn.role == "user") Accent.copy(alpha = 0.8f) else TextMuted,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Bottom Section: Quick Prompts & Action Buttons
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Quick prompt pills
                if (isActive) {
                    Text(
                        text = stringResource(R.string.voice_call_quick_topics),
                        color = TextMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickPrompts.take(2).forEach { prompt ->
                            Button(
                                onClick = { viewModel.sendUserQuery(prompt) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BgCard),
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = prompt,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }

                // Primary Hero Call Button (START CALL / END CALL)
                val heroContentDesc = if (isActive) stringResource(R.string.voice_call_end) else stringResource(R.string.voice_call_start)
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .clip(CircleShape)
                        .background(if (isActive) Emergency else Accent)
                        .clickable {
                            if (isActive) {
                                viewModel.stopSession()
                            } else {
                                viewModel.startSession(mode = LiveMode.VOICE)
                            }
                        }
                        .semantics {
                            contentDescription = heroContentDesc
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(BgPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isActive) "📞" else "🎙️",
                            fontSize = 32.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isActive) stringResource(R.string.voice_call_tap_to_end) else stringResource(R.string.voice_call_tap_to_start),
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Secondary Controls: Mute, Interrupt, Switch to Vision Live
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Mute / Unmute
                    Button(
                        onClick = { viewModel.toggleMute() },
                        enabled = isActive,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isMuted) Emergency.copy(alpha = 0.2f) else BgCard,
                            contentColor = if (isMuted) Emergency else TextPrimary
                        ),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text(if (isMuted) stringResource(R.string.voice_call_unmute) else stringResource(R.string.voice_call_mute), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    // Interrupt
                    if (isAiSpeaking) {
                        Button(
                            onClick = { viewModel.interruptAi() },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = BgPrimary),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) {
                            Text("✋ " + stringResource(R.string.common_stop), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    // Switch to Vision Live
                    Button(
                        onClick = {
                            viewModel.stopSession()
                            onSwitchToVisionLive()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BgCard, contentColor = TextPrimary),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text("👁️ " + stringResource(R.string.feature_live_vision), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Need Visual Assistance Banner Card
                Surface(
                    color = BgSecondary,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.stopSession()
                            onSwitchToVisionLive()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("👁️", fontSize = 26.sp, modifier = Modifier.padding(end = 12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.voice_call_need_vision_title),
                                color = Accent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = stringResource(R.string.voice_call_need_vision_desc),
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
