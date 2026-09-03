package com.example.visionbridge.ui.screens.live

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.visionbridge.R
import com.example.visionbridge.data.SessionManager
import com.example.visionbridge.live.LiveMode
import com.example.visionbridge.live.LiveState
import com.example.visionbridge.live.LiveViewModel
import com.example.visionbridge.live.VisualQualityMode
import com.example.visionbridge.ui.theme.*
import com.example.visionbridge.voice.ScreenActionRegistry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisionLiveScreen(
    onBack: () -> Unit,
    onNavigateFallback: (String) -> Unit,
    viewModel: LiveViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val sessionManager = remember { SessionManager.getInstance(context) }
    val currentLanguage by sessionManager.language.collectAsStateWithLifecycle()

    val liveState by viewModel.liveState.collectAsStateWithLifecycle()
    val visualMode by viewModel.visualQualityMode.collectAsStateWithLifecycle()
    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
    val isAiSpeaking by viewModel.isAiSpeaking.collectAsStateWithLifecycle()
    val currentUserQuery by viewModel.currentUserQuery.collectAsStateWithLifecycle()
    val currentResponse by viewModel.currentResponse.collectAsStateWithLifecycle()
    val conversationTurns by viewModel.conversationTurns.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()

    val hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    var permissionsGranted by remember { mutableStateOf(hasCamera && hasMic) }
    var permanentlyDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { map ->
        val cameraGranted = map[Manifest.permission.CAMERA] == true
        val micGranted = map[Manifest.permission.RECORD_AUDIO] == true
        permissionsGranted = cameraGranted && micGranted

        if (!permissionsGranted) {
            val activity = context as? Activity
            val cameraRation = activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) ?: true
            val micRation = activity?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) ?: true
            permanentlyDenied = !cameraRation || !micRation
        }
    }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    val isActive = liveState == LiveState.LISTENING ||
            liveState == LiveState.SPEAKING ||
            liveState == LiveState.CONNECTING ||
            liveState == LiveState.STARTING ||
            liveState == LiveState.THINKING

    // Auto-request permissions and start session on screen open
    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    LaunchedEffect(permissionsGranted) {
        if (permissionsGranted && liveState == LiveState.IDLE) {
            viewModel.startSession(
                mode = LiveMode.VISION,
                lifecycleOwner = lifecycleOwner,
                previewView = previewView
            )
        }
    }

    DisposableEffect(Unit) {
        ScreenActionRegistry.registerScreen(
            screenId = "live_vision",
            onCapture = {
                if (isActive) {
                    viewModel.sendUserQuery("What is in front of me?")
                }
            },
            onCancel = {
                viewModel.stopSession()
                onBack()
            }
        )

        onDispose {
            ScreenActionRegistry.unregisterScreen("live_vision")
            viewModel.stopSession()
        }
    }

    // Status display text
    val statusText = when (liveState) {
        LiveState.STARTING -> stringResource(R.string.live_status_starting)
        LiveState.CONNECTING -> stringResource(R.string.live_status_connecting)
        LiveState.LISTENING -> if (isMuted) stringResource(R.string.live_status_muted) else stringResource(R.string.live_status_listening)
        LiveState.THINKING -> if (visualMode == VisualQualityMode.HIGH_DETAIL) stringResource(R.string.live_status_reading) else stringResource(R.string.live_status_thinking)
        LiveState.SPEAKING -> stringResource(R.string.live_status_speaking)
        LiveState.RECONNECTING -> stringResource(R.string.live_status_reconnecting)
        LiveState.ERROR -> stringResource(R.string.live_status_error)
        LiveState.STOPPED -> stringResource(R.string.live_status_stopped)
        else -> stringResource(R.string.live_status_listening)
    }

    // Quick prompts
    val quickPrompts = when (currentLanguage) {
        "hi" -> listOf(
            "सामने क्या है?",
            "दिखाई दे रहा टेक्स्ट पढ़ें।",
            "क्या कोई खतरा है?",
            "कीमत या नंबर क्या है?"
        )
        "mr" -> listOf(
            "समोर काय आहे?",
            "दिसणारा सर्व मजकूर वाचा.",
            "काही धोका आहे का?",
            "किंमत किंवा नंबर काय आहे?"
        )
        else -> listOf(
            stringResource(R.string.live_prompt_front),
            stringResource(R.string.live_prompt_read),
            stringResource(R.string.live_prompt_hazard),
            stringResource(R.string.live_prompt_price)
        )
    }

    Scaffold(
        topBar = {
            com.example.visionbridge.ui.components.AppTopBar(
                title = stringResource(R.string.feature_live_vision),
                subtitle = statusText,
                onBack = {
                    viewModel.stopSession()
                    onBack()
                },
                backContentDescription = stringResource(R.string.common_back),
                actions = {
                    // Visual Mode Indicator
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (visualMode == VisualQualityMode.HIGH_DETAIL) Warning.copy(alpha = 0.2f) else Accent.copy(alpha = 0.15f),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = if (visualMode == VisualQualityMode.HIGH_DETAIL) stringResource(R.string.live_mode_high_detail) else stringResource(R.string.live_mode_fast),
                            color = if (visualMode == VisualQualityMode.HIGH_DETAIL) Warning else Accent,
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
        ) {
            // Main Camera Preview View
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black)
                    .clickable(enabled = isAiSpeaking) { viewModel.interruptAi() }
            ) {
                if (!permissionsGranted) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Camera and microphone permissions are required for VisionBridge Live.",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = {
                                if (permanentlyDenied) {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.fromParts("package", context.packageName, null)
                                        }
                                    )
                                } else {
                                    permissionLauncher.launch(
                                        arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent)
                        ) {
                            Text("Grant Permissions", color = BgPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Speaking Overlay
                    if (liveState == LiveState.SPEAKING) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Accent.copy(alpha = 0.2f))
                                .padding(12.dp)
                                .align(Alignment.TopCenter),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🔊 " + stringResource(R.string.live_tap_to_interrupt),
                                color = Accent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Listening Overlay
                    if (liveState == LiveState.LISTENING) {
                        Box(
                            modifier = Modifier
                                .padding(16.dp)
                                .align(Alignment.TopStart)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = BgPrimary.copy(alpha = 0.85f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(if (isMuted) Emergency else Success)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isMuted) "Muted" else "Listening",
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Error Card
                    if (liveState == LiveState.ERROR) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(BgPrimary.copy(alpha = 0.9f))
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("⚠️", fontSize = 48.sp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = errorMessage.ifBlank { "Could not connect to VisionBridge Live." },
                                    color = Emergency,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Button(
                                    onClick = {
                                        viewModel.startSession(
                                            mode = LiveMode.VISION,
                                            lifecycleOwner = lifecycleOwner,
                                            previewView = previewView
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
                                ) {
                                    Text("Retry Connection", color = BgPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                }
                            }
                        }
                    }

                    // Live Subtitles Box
                    if (currentUserQuery.isNotBlank() || currentResponse.isNotBlank() || conversationTurns.isNotEmpty()) {
                        Surface(
                            color = BgPrimary.copy(alpha = 0.88f),
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
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
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Vision: $currentResponse",
                                        color = TextPrimary,
                                        fontSize = 18.sp,
                                        lineHeight = 26.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                conversationTurns.takeLast(2).forEach { turn ->
                                    if (currentResponse.isBlank() || turn.text != currentResponse) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (turn.role == "user") "You: ${turn.text}" else "Vision: ${turn.text}",
                                            color = if (turn.role == "user") Accent.copy(alpha = 0.8f) else TextMuted,
                                            fontSize = 15.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Controls Area
            Surface(
                color = BgCard,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Quick Prompts
                    if (isActive) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            quickPrompts.take(2).forEach { prompt ->
                                Button(
                                    onClick = { viewModel.sendUserQuery(prompt) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = BgSecondary),
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
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Primary Hero Button (START LIVE / STOP LIVE)
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .clip(CircleShape)
                            .background(if (isActive) Emergency else Accent)
                            .clickable {
                                if (isActive) {
                                    viewModel.stopSession()
                                } else {
                                    viewModel.startSession(
                                        mode = LiveMode.VISION,
                                        lifecycleOwner = lifecycleOwner,
                                        previewView = previewView
                                    )
                                }
                            }
                            .semantics {
                                contentDescription = if (isActive) "Stop Live Vision" else "Start Live Vision"
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
                                text = if (isActive) "⏹" else "⚡",
                                fontSize = 32.sp,
                                color = if (isActive) Emergency else Accent
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isActive) stringResource(R.string.live_tap_to_stop) else stringResource(R.string.live_tap_to_start),
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Secondary Controls: Mute, Flip Camera, Interrupt
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
                                containerColor = if (isMuted) Emergency.copy(alpha = 0.2f) else BgSecondary,
                                contentColor = if (isMuted) Emergency else TextPrimary
                            ),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) {
                            Text(if (isMuted) stringResource(R.string.live_unmute) else stringResource(R.string.live_mute), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        // Flip Camera
                        Button(
                            onClick = { viewModel.switchCamera() },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BgSecondary, contentColor = TextPrimary),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) {
                            Text("🔄 " + stringResource(R.string.camera_flip), fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                    }
                }
            }
        }
    }
}
