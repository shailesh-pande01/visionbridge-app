package com.example.visionbridge.ui.screens.surroundings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import com.example.visionbridge.ai.ConfidenceLevel
import com.example.visionbridge.ai.FallbackContext
import com.example.visionbridge.data.ContextMemoryManager
import com.example.visionbridge.data.SessionManager
import com.example.visionbridge.ui.theme.*
import com.example.visionbridge.utils.TextToSpeechManager
import com.example.visionbridge.voice.ScreenActionRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurroundingsScreen(
    onBack: () -> Unit,
    onVolunteerHelp: (FallbackContext?) -> Unit,
    viewModel: SurroundingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val sessionManager = remember { SessionManager.getInstance(context) }
    val currentLanguage by sessionManager.language.collectAsStateWithLifecycle()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val tts = remember { TextToSpeechManager(context) }
    val isSpeaking by tts.isSpeaking.collectAsStateWithLifecycle()

    LaunchedEffect(currentLanguage) {
        tts.setLanguage(currentLanguage)
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionPermanentlyDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) {
            val activity = context as? Activity
            permissionPermanentlyDenied = activity?.shouldShowRequestPermissionRationale(
                Manifest.permission.CAMERA
            ) == false
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    val previewShouldBeLive = uiState is SurroundingsUiState.Camera || uiState is SurroundingsUiState.Processing

    LaunchedEffect(hasPermission, previewShouldBeLive) {
        if (!hasPermission) return@LaunchedEffect

        try {
            val provider = cameraProvider ?: withContext(Dispatchers.IO) {
                ProcessCameraProvider.getInstance(context).get()
            }.also { cameraProvider = it }

            if (!previewShouldBeLive) {
                provider.unbindAll()
                imageCapture = null
                return@LaunchedEffect
            }

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                capture
            )
            imageCapture = capture
        } catch (e: Exception) {
            Log.e("SurroundingsScreen", "Could not start camera", e)
        }
    }

    fun capture() {
        val capture = imageCapture ?: return
        viewModel.onCaptureStarted()

        capture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    var rotation = 0
                    val bytes = try {
                        rotation = image.imageInfo.rotationDegrees
                        val buffer = image.planes[0].buffer
                        buffer.rewind()
                        ByteArray(buffer.remaining()).also { buffer.get(it) }
                    } catch (e: Exception) {
                        null
                    } finally {
                        image.close()
                    }

                    scope.launch {
                        if (bytes != null && bytes.isNotEmpty()) {
                            viewModel.onImageCaptured(bytes, rotation, currentLanguage)
                        } else {
                            viewModel.reset()
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    scope.launch {
                        viewModel.reset()
                    }
                }
            }
        )
    }

    // Dynamic voice action routing based on current UI state
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is SurroundingsUiState.LowConfidence -> {
                ScreenActionRegistry.registerScreen(
                    screenId = "surroundings",
                    onCapture = {
                        tts.stop()
                        viewModel.retryCapture()
                        capture()
                    },
                    onSubmit = {
                        tts.stop()
                        onVolunteerHelp(state.fallbackContext)
                    },
                    onCancel = {
                        tts.stop()
                        viewModel.onFallbackDeclined()
                    }
                )
            }
            is SurroundingsUiState.Result -> {
                ScreenActionRegistry.registerScreen(
                    screenId = "surroundings",
                    onCapture = {
                        tts.stop()
                        viewModel.reset()
                    },
                    onReplay = {
                        tts.speak(state.speech)
                    },
                    onCancel = {
                        tts.stop()
                        onBack()
                    }
                )
            }
            is SurroundingsUiState.Failed -> {
                ScreenActionRegistry.registerScreen(
                    screenId = "surroundings",
                    onCapture = {
                        tts.stop()
                        viewModel.retryCapture()
                    },
                    onSubmit = {
                        tts.stop()
                        onVolunteerHelp(null)
                    },
                    onCancel = {
                        tts.stop()
                        onBack()
                    }
                )
            }
            else -> {
                ScreenActionRegistry.registerScreen(
                    screenId = "surroundings",
                    onCapture = { capture() },
                    onCancel = { onBack() }
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            ScreenActionRegistry.unregisterScreen("surroundings")
            cameraProvider?.unbindAll()
            cameraExecutor.shutdown()
            tts.shutdown()
        }
    }

    // Spoken TTS feedback corresponding to UI transitions
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is SurroundingsUiState.Processing -> {
                tts.speak(context.getString(R.string.surroundings_processing_speech))
            }
            is SurroundingsUiState.LowConfidence -> {
                // Low confidence: explain uncertainty and proactively offer volunteer help
                tts.speak(state.spokenPrompt)
            }
            is SurroundingsUiState.Result -> {
                ContextMemoryManager.setContext("surroundings", "scene description", state.analysis.description)
                val fullSpeech = buildString {
                    if (state.isQualified && state.confidenceLevel == ConfidenceLevel.MEDIUM) {
                        append(context.getString(R.string.confidence_medium_qualifier))
                        append(". ")
                    }
                    append(state.analysis.scene)
                    append(". ")
                    append(state.analysis.description)
                    if (state.analysis.obstacles.isNotEmpty()) {
                        append(". ")
                        append(state.analysis.obstacles.joinToString(", "))
                    }
                }
                tts.speak(fullSpeech)
            }
            is SurroundingsUiState.Failed -> {
                val errorSpeech = if (state.canOfferVolunteer) {
                    context.getString(R.string.ai_technical_failure_prompt)
                } else {
                    state.message
                }
                tts.speak(errorSpeech)
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            com.example.visionbridge.ui.components.AppTopBar(
                title = stringResource(R.string.feature_surroundings),
                subtitle = stringResource(R.string.surroundings_subtitle),
                onBack = {
                    tts.stop()
                    onBack()
                },
                backContentDescription = stringResource(R.string.common_back)
            )
        },
        containerColor = BgPrimary
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (!hasPermission) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.camera_permission_required),
                            color = TextPrimary,
                            fontSize = 20.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = {
                                if (permissionPermanentlyDenied) {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.fromParts("package", context.packageName, null)
                                        }
                                    )
                                } else {
                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent)
                        ) {
                            Text(stringResource(R.string.camera_permission_grant), color = BgPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier.fillMaxSize()
                    )

                    when (val state = uiState) {
                        is SurroundingsUiState.Processing -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(BgPrimary.copy(alpha = 0.85f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Accent)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = stringResource(R.string.surroundings_processing_speech),
                                        color = TextPrimary,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        // STEP: LOW CONFIDENCE HANDOFF (AI Uncertainty detected)
                        is SurroundingsUiState.LowConfidence -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(BgCard)
                                    .padding(24.dp)
                                    .verticalScroll(rememberScrollState())
                                    .semantics { liveRegion = LiveRegionMode.Polite },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(text = "🤔", fontSize = 56.sp)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.confidence_low_prompt),
                                    color = Accent,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.semantics { heading() }
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.confidence_ask_volunteer),
                                    color = TextPrimary,
                                    fontSize = 19.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 26.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.ai_fallback_hint_voice),
                                    color = TextMuted,
                                    fontSize = 15.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(32.dp))
                                Button(
                                    onClick = {
                                        tts.stop()
                                        onVolunteerHelp(state.fallbackContext)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 60.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.confidence_connect_volunteer),
                                        color = BgPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 19.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        tts.stop()
                                        viewModel.onFallbackDeclined()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BgSecondary, contentColor = TextPrimary),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 60.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.confidence_try_again),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 19.sp
                                    )
                                }
                            }
                        }

                        is SurroundingsUiState.Result -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(BgCard)
                                    .padding(20.dp)
                                    .verticalScroll(rememberScrollState())
                                    .semantics { liveRegion = LiveRegionMode.Polite }
                            ) {
                                // Scene Badge
                                Surface(
                                    color = BgSecondary,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = "📍", fontSize = 28.sp, modifier = Modifier.padding(end = 12.dp))
                                        Column {
                                            Text(
                                                text = state.analysis.scene,
                                                color = Accent,
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (state.analysis.lighting.isNotBlank()) {
                                                Text(
                                                    text = state.analysis.lighting,
                                                    color = TextMuted,
                                                    fontSize = 14.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Medium Confidence Qualification Banner
                                if (state.confidenceLevel == ConfidenceLevel.MEDIUM) {
                                    Surface(
                                        color = AccentDim,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = "ℹ️", fontSize = 20.sp, modifier = Modifier.padding(end = 10.dp))
                                            Text(
                                                text = stringResource(R.string.confidence_medium_qualifier),
                                                color = TextPrimary,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                // Obstacles Warning (if any)
                                if (state.analysis.obstacles.isNotEmpty()) {
                                    Surface(
                                        color = Emergency,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 16.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                text = "⚠️ OBSTACLES DETECTED",
                                                color = Color.White,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            state.analysis.obstacles.forEach { obstacle ->
                                                Text(
                                                    text = "• $obstacle",
                                                    color = Color.White,
                                                    fontSize = 17.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }

                                // Main Description
                                Text(
                                    text = state.analysis.description,
                                    color = TextPrimary,
                                    fontSize = 22.sp,
                                    lineHeight = 32.sp,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Objects list
                                if (state.analysis.objects.isNotEmpty()) {
                                    Text(
                                        text = stringResource(R.string.surroundings_objects),
                                        color = Accent,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.semantics { heading() }
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    state.analysis.objects.forEach { obj ->
                                        Text(
                                            text = "• $obj",
                                            color = TextPrimary,
                                            fontSize = 18.sp,
                                            lineHeight = 24.sp
                                        )
                                    }
                                }

                                // Optional Volunteer Assistance Card (available for medium confidence / qualified results)
                                if (state.fallbackContext != null) {
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Surface(
                                        color = BgSecondary,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                text = stringResource(R.string.confidence_ask_volunteer),
                                                color = TextPrimary,
                                                fontSize = 17.sp
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Button(
                                                onClick = {
                                                    tts.stop()
                                                    onVolunteerHelp(state.fallbackContext)
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(min = 54.dp)
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.confidence_connect_volunteer),
                                                    color = BgPrimary,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 18.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // STEP: TECHNICAL FAILURE
                        is SurroundingsUiState.Failed -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(BgCard)
                                    .padding(24.dp)
                                    .verticalScroll(rememberScrollState())
                                    .semantics { liveRegion = LiveRegionMode.Polite },
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(text = "⚠️", fontSize = 54.sp)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.ai_technical_failure_prompt),
                                    color = Emergency,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                if (!state.technicalDetail.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = state.technicalDetail,
                                        color = TextMuted,
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                Spacer(modifier = Modifier.height(28.dp))
                                Button(
                                    onClick = {
                                        tts.stop()
                                        viewModel.retryCapture()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 60.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                                ) {
                                    Text(stringResource(R.string.common_retry), fontSize = 19.sp, color = BgPrimary, fontWeight = FontWeight.Bold)
                                }
                                if (state.canOfferVolunteer) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Button(
                                        onClick = {
                                            tts.stop()
                                            onVolunteerHelp(null)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 60.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = BgSecondary, contentColor = TextPrimary)
                                    ) {
                                        Text(stringResource(R.string.confidence_connect_volunteer), fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        else -> {}
                    }
                }
            }

            // Bottom Controls (Visible during Camera, Processing, and Result states)
            if (uiState is SurroundingsUiState.Camera || uiState is SurroundingsUiState.Result) {
                Surface(
                    color = BgCard,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when (val state = uiState) {
                            is SurroundingsUiState.Camera -> {
                                Box(
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(CircleShape)
                                        .background(Accent)
                                        .clickable { capture() }
                                        .semantics { contentDescription = "Capture image" },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(76.dp)
                                            .clip(CircleShape)
                                            .background(BgPrimary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(68.dp)
                                                .clip(CircleShape)
                                                .background(Accent)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.camera_tap_to_capture),
                                    color = TextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            is SurroundingsUiState.Result -> {
                                Button(
                                    onClick = {
                                        tts.stop()
                                        viewModel.reset()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 64.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                                ) {
                                    Text(stringResource(R.string.camera_scan_again), fontSize = 20.sp, color = BgPrimary, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Button(
                                        onClick = { tts.speak(state.speech) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 54.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = BgSecondary, contentColor = TextPrimary)
                                    ) {
                                        Text("🔊 " + stringResource(R.string.common_replay), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = { tts.stop() },
                                        enabled = isSpeaking,
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 54.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = BgSecondary, contentColor = TextPrimary)
                                    ) {
                                        Text("⏹ " + stringResource(R.string.common_stop), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            else -> {}
                        }
                    }
                }
            }
        }
    }
}
