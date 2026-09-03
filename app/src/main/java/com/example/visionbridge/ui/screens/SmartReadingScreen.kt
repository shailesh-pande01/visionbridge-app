package com.example.visionbridge.ui.screens

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.example.visionbridge.ui.screens.reading.ReadingUiState
import com.example.visionbridge.ui.screens.reading.SmartReadingViewModel
import com.example.visionbridge.ui.screens.reading.SpokenState
import com.example.visionbridge.ui.theme.Accent
import com.example.visionbridge.ui.theme.BgCard
import com.example.visionbridge.ui.theme.BgPrimary
import com.example.visionbridge.ui.theme.BgSecondary
import com.example.visionbridge.ui.theme.Emergency
import com.example.visionbridge.ui.theme.TextMuted
import com.example.visionbridge.ui.theme.TextPrimary
import com.example.visionbridge.data.ContextMemoryManager
import com.example.visionbridge.utils.TextToSpeechManager
import com.example.visionbridge.voice.ScreenActionRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "VB-Reading"

/**
 * Smart Reading: the camera opens on entry, one large button captures, the photo goes
 * to the existing VisionBridge backend for Gemini text extraction, and the result is
 * shown large and read aloud.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartReadingScreen(
    title: String = "Read Text",
    onBack: () -> Unit,
    viewModel: SmartReadingViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sessionManager = remember { com.example.visionbridge.data.SessionManager.getInstance(context) }
    val currentLanguage by sessionManager.language.collectAsStateWithLifecycle()

    // ── Text-to-speech ────────────────────────────────────────────────
    var ttsWarning by remember { mutableStateOf<String?>(null) }
    val tts = remember { TextToSpeechManager(context) { message -> ttsWarning = message } }
    val isSpeaking by tts.isSpeaking.collectAsStateWithLifecycle()

    LaunchedEffect(currentLanguage) {
        tts.setLanguage(currentLanguage)
    }

    // ── Camera permission ─────────────────────────────────────────────
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionRequested by remember { mutableStateOf(false) }
    var permissionPermanentlyDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        permissionRequested = true
        if (!granted) {
            // No rationale after a denial means "don't ask again" — send them to Settings.
            val activity = context as? Activity
            permissionPermanentlyDenied = activity?.shouldShowRequestPermissionRationale(
                Manifest.permission.CAMERA
            ) == false
        }
    }

    // The camera opens by itself — the user should not have to hunt for a start button.
    LaunchedEffect(Unit) {
        if (!hasPermission && !permissionRequested) {
            permissionRequested = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // ── CameraX ───────────────────────────────────────────────────────
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    // Keep the preview live only while it is useful; unbind once a result is on screen.
    val previewShouldBeLive =
        uiState is ReadingUiState.Camera || uiState is ReadingUiState.Processing

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
            Log.d(TAG, "Camera bound and ready")
        } catch (e: Exception) {
            Log.e(TAG, "Could not start the camera", e)
            imageCapture = null
            viewModel.onCaptureFailed(
                userMessage = "The camera could not be started. Close other apps using the camera and try again.",
                technicalDetail = "${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }

    fun capture() {
        val capture = imageCapture
        if (capture == null) {
            viewModel.onCaptureFailed(
                userMessage = "The camera is not ready yet. Please wait a moment and try again.",
                technicalDetail = "takePicture() requested while ImageCapture was unbound"
            )
            return
        }

        viewModel.onCaptureStarted()

        capture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    // Everything must be read off the proxy before it is closed.
                    var rotation = 0
                    val bytes = try {
                        rotation = image.imageInfo.rotationDegrees
                        val buffer = image.planes[0].buffer
                        buffer.rewind()
                        ByteArray(buffer.remaining()).also { buffer.get(it) }
                    } catch (e: Exception) {
                        Log.e(TAG, "Could not read the captured frame", e)
                        null
                    } finally {
                        image.close()
                    }

                    scope.launch {
                        if (bytes == null || bytes.isEmpty()) {
                            viewModel.onCaptureFailed(
                                userMessage = "The photo could not be read from the camera. Please capture again.",
                                technicalDetail = "Empty JPEG buffer from ImageProxy"
                            )
                        } else {
                            viewModel.onImageCaptured(bytes, rotation, currentLanguage)
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "takePicture failed", exception)
                    scope.launch {
                        viewModel.onCaptureFailed(
                            userMessage = "The camera could not take the photo. Please try again.",
                            technicalDetail = "ImageCaptureException code=${exception.imageCaptureError}: ${exception.message}"
                        )
                    }
                }
            }
        )
    }

    DisposableEffect(Unit) {
        ScreenActionRegistry.registerScreen(
            screenId = "reading",
            onCapture = { capture() },
            onCancel = { onBack() }
        )
        onDispose {
            ScreenActionRegistry.unregisterScreen("reading")
            cameraProvider?.unbindAll()
            cameraExecutor.shutdown()
            tts.shutdown()
        }
    }

    // ── Speak each outcome exactly once ───────────────────────────────
    val processingSpeech = androidx.compose.ui.res.stringResource(com.example.visionbridge.R.string.reading_processing_speech)
    LaunchedEffect(uiState) {
        if (uiState is ReadingUiState.Processing) {
            tts.speak(processingSpeech)
        } else if (uiState is ReadingUiState.Result) {
            val res = uiState as ReadingUiState.Result
            ContextMemoryManager.setContext("reading", "text you captured", res.text)
        }
    }
    val spoken = uiState as? SpokenState
    LaunchedEffect(spoken?.id) {
        spoken?.let { tts.speak(it.speech) }
    }

    Scaffold(
        topBar = {
            com.example.visionbridge.ui.components.AppTopBar(
                title = androidx.compose.ui.res.stringResource(com.example.visionbridge.R.string.feature_reading),
                subtitle = androidx.compose.ui.res.stringResource(com.example.visionbridge.R.string.reading_subtitle),
                onBack = {
                    tts.stop()
                    onBack()
                },
                backContentDescription = androidx.compose.ui.res.stringResource(com.example.visionbridge.R.string.common_home)
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
                when {
                    !hasPermission -> CameraPermissionPanel(
                        permanentlyDenied = permissionPermanentlyDenied,
                        onGrant = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        onOpenSettings = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)
                                )
                            )
                        }
                    )

                    else -> {
                        AndroidView(
                            factory = { previewView },
                            modifier = Modifier
                                .fillMaxSize()
                                .semantics {
                                    contentDescription =
                                        "Live camera view. Point the camera at the text you want read."
                                }
                        )

                        when (val state = uiState) {
                            is ReadingUiState.Camera -> AimingHint()
                            is ReadingUiState.Processing -> ProcessingPanel()
                            is ReadingUiState.Result -> ResultPanel(state, isSpeaking)
                            is ReadingUiState.NoText -> MessagePanel(
                                icon = "🔍",
                                heading = "No text found",
                                message = state.message,
                                accentColor = Accent
                            )

                            is ReadingUiState.Failed -> MessagePanel(
                                icon = "⚠️",
                                heading = "Could not read the text",
                                message = state.message,
                                accentColor = Emergency,
                                technicalDetail = state.technicalDetail
                            )
                        }
                    }
                }
            }

            Surface(
                color = BgCard,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ttsWarning?.let { warning ->
                        Text(
                            text = warning,
                            color = TextMuted,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        )
                    }

                    when (uiState) {
                        is ReadingUiState.Camera -> CaptureControls(onCapture = ::capture)

                        is ReadingUiState.Processing -> {
                            CircularProgressIndicator(color = Accent)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Reading text…",
                                color = TextPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        is ReadingUiState.Result -> ResultControls(
                            isSpeaking = isSpeaking,
                            onReplay = { (uiState as? ReadingUiState.Result)?.let { tts.speak(it.text) } },
                            onStop = { tts.stop() },
                            onCaptureAgain = {
                                tts.stop()
                                viewModel.reset()
                            }
                        )

                        is ReadingUiState.NoText,
                        is ReadingUiState.Failed -> PrimaryActionButton(
                            label = "Capture Again",
                            description = "Capture again. Reopens the camera.",
                            onClick = {
                                tts.stop()
                                viewModel.reset()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AimingHint() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            color = BgPrimary.copy(alpha = 0.75f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = androidx.compose.ui.res.stringResource(com.example.visionbridge.R.string.reading_hint),
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
            )
        }
    }
}

@Composable
private fun ProcessingPanel() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary.copy(alpha = 0.92f))
            .padding(32.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = Accent)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = androidx.compose.ui.res.stringResource(com.example.visionbridge.R.string.reading_processing_speech),
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = androidx.compose.ui.res.stringResource(com.example.visionbridge.R.string.reading_hint),
            color = TextMuted,
            fontSize = 17.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ResultPanel(state: ReadingUiState.Result, isSpeaking: Boolean) {
    val wordCount = remember(state.text) {
        state.text.split(Regex("\\s+")).count { it.isNotBlank() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgCard)
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = androidx.compose.ui.res.stringResource(com.example.visionbridge.R.string.reading_extracted_text),
                color = Accent,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = "$wordCount words",
                color = TextMuted,
                fontSize = 15.sp
            )
        }

        if (isSpeaking) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "🔊 ${androidx.compose.ui.res.stringResource(com.example.visionbridge.R.string.voice_status_speaking)}",
                color = Accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }

        if (state.isLowConfidence) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = BgSecondary,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "This text was hard to read, so some words may be wrong. " +
                            "Capture again from closer if it does not sound right.",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = state.text,
            color = TextPrimary,
            fontSize = 26.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun MessagePanel(
    icon: String,
    heading: String,
    message: String,
    accentColor: Color,
    technicalDetail: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgCard)
            .padding(28.dp)
            .verticalScroll(rememberScrollState())
            .semantics { liveRegion = LiveRegionMode.Assertive },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = icon, fontSize = 56.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = heading,
            color = accentColor,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            color = TextPrimary,
            fontSize = 21.sp,
            lineHeight = 30.sp,
            textAlign = TextAlign.Center
        )

        technicalDetail?.let { detail ->
            Spacer(modifier = Modifier.height(24.dp))
            Surface(
                color = BgSecondary,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Technical detail",
                        color = TextMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = detail,
                        color = TextMuted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraPermissionPanel(
    permanentlyDenied: Boolean,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgCard)
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "📷", fontSize = 56.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = androidx.compose.ui.res.stringResource(com.example.visionbridge.R.string.camera_permission_required),
            color = Accent,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (permanentlyDenied) {
                "Camera permission is turned off. Open Settings and allow the camera so " +
                        "VisionBridge can read text for you."
            } else {
                "VisionBridge needs the camera to photograph the text you want read aloud."
            },
            color = TextPrimary,
            fontSize = 20.sp,
            lineHeight = 28.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = if (permanentlyDenied) onOpenSettings else onGrant,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent)
        ) {
            Text(
                text = if (permanentlyDenied) "Open Settings" else androidx.compose.ui.res.stringResource(com.example.visionbridge.R.string.camera_permission_grant),
                fontSize = 20.sp,
                color = BgPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CaptureControls(onCapture: () -> Unit) {
    val tapToCaptureText = androidx.compose.ui.res.stringResource(com.example.visionbridge.R.string.camera_tap_to_capture)
    Box(
        modifier = Modifier
            .size(112.dp)
            .clip(CircleShape)
            .background(Accent)
            .clickable(onClick = onCapture)
            .semantics {
                contentDescription = tapToCaptureText
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(CircleShape)
                .background(BgPrimary),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Accent)
            )
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = tapToCaptureText,
        color = TextPrimary,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun ResultControls(
    isSpeaking: Boolean,
    onReplay: () -> Unit,
    onStop: () -> Unit,
    onCaptureAgain: () -> Unit
) {
    PrimaryActionButton(
        label = androidx.compose.ui.res.stringResource(com.example.visionbridge.R.string.reading_btn_capture_again),
        description = androidx.compose.ui.res.stringResource(com.example.visionbridge.R.string.reading_talkback_capture_again),
        onClick = onCaptureAgain
    )
    Spacer(modifier = Modifier.height(14.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Button(
            onClick = onReplay,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 64.dp)
                .semantics { contentDescription = "Read again" },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BgPrimary,
                contentColor = TextPrimary
            )
        ) {
            Text(
                androidx.compose.ui.res.stringResource(com.example.visionbridge.R.string.reading_btn_read_again),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Button(
            onClick = onStop,
            enabled = isSpeaking,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 64.dp)
                .semantics { contentDescription = "Stop reading" },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BgSecondary,
                contentColor = TextPrimary,
                disabledContainerColor = BgSecondary.copy(alpha = 0.4f),
                disabledContentColor = TextMuted
            )
        ) {
            Text(
                androidx.compose.ui.res.stringResource(com.example.visionbridge.R.string.reading_btn_stop),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PrimaryActionButton(
    label: String,
    description: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .semantics { contentDescription = description },
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Accent)
    ) {
        Text(
            text = label,
            fontSize = 21.sp,
            color = BgPrimary,
            fontWeight = FontWeight.Bold
        )
    }
}
