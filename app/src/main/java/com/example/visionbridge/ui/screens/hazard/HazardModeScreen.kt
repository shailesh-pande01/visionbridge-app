package com.example.visionbridge.ui.screens.hazard

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
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
import com.example.visionbridge.data.ContextMemoryManager
import com.example.visionbridge.data.SessionManager
import com.example.visionbridge.ui.theme.*
import com.example.visionbridge.utils.TextToSpeechManager
import com.example.visionbridge.voice.ScreenActionRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HazardModeScreen(
    onBack: () -> Unit,
    viewModel: HazardModeViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val sessionManager = remember { SessionManager.getInstance(context) }
    val currentLanguage by sessionManager.language.collectAsStateWithLifecycle()

    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val lastAlert by viewModel.lastAlert.collectAsStateWithLifecycle()

    val tts = remember { TextToSpeechManager(context) }

    LaunchedEffect(currentLanguage) {
        tts.setLanguage(currentLanguage)
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
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

    LaunchedEffect(hasPermission) {
        if (!hasPermission) return@LaunchedEffect
        try {
            val provider = withContext(kotlinx.coroutines.Dispatchers.IO) {
                ProcessCameraProvider.getInstance(context).get()
            }
            cameraProvider = provider

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
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
            // Camera error
        }
    }

    DisposableEffect(Unit) {
        ScreenActionRegistry.registerScreen(
            screenId = "hazard",
            onCancel = {
                tts.stop()
                viewModel.stop()
                onBack()
            }
        )
        onDispose {
            ScreenActionRegistry.unregisterScreen("hazard")
            viewModel.stop()
            cameraProvider?.unbindAll()
            cameraExecutor.shutdown()
            tts.shutdown()
        }
    }

    // Periodic scanning loop (every 5 seconds)
    LaunchedEffect(isScanning, imageCapture) {
        if (isScanning && imageCapture != null) {
            tts.speak(context.getString(R.string.hazard_active))
            while (isActive && isScanning) {
                delay(5000)
                if (!isScanning) break

                val capture = imageCapture ?: continue
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

                            if (bytes != null && bytes.isNotEmpty()) {
                                viewModel.processFrame(bytes, rotation, currentLanguage) { speechText ->
                                    tts.speak(speechText)
                                }
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {}
                    }
                )
            }
        }
    }

    // Pulse animation for radar scanning
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_hazard), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(
                        onClick = {
                            tts.stop()
                            onBack()
                        }
                    ) {
                        Text(stringResource(R.string.common_back), color = TextPrimary, fontSize = 18.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BgPrimary,
                    titleContentColor = TextPrimary
                )
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
                if (hasPermission) {
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Overlay Status Bar
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Top live banner
                        Surface(
                            color = if (isScanning) Emergency else BgSecondary,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().alpha(if (isScanning && isAnalyzing) alpha else 1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(if (isScanning) Color.White else TextMuted)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (isScanning) {
                                        if (isAnalyzing) "Scanning for obstacles…" else "Hazard Mode: Active (5s scan)"
                                    } else "Hazard Mode: Paused",
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Bottom Alert Card (if hazard detected)
                        val alert = lastAlert
                        if (alert != null) {
                            Surface(
                                color = Emergency,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive }
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Text(
                                        text = "🚨 HAZARD WARNING",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.semantics { heading() }
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = alert.speech,
                                        color = Color.White,
                                        fontSize = 20.sp,
                                        lineHeight = 28.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        } else {
                            Surface(
                                color = BgPrimary.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(R.string.hazard_no_change),
                                    color = TextPrimary,
                                    fontSize = 17.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Bottom control
            Surface(
                color = BgCard,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = {
                            viewModel.toggleScanning()
                            if (!isScanning) {
                                tts.speak("Hazard mode resumed.")
                            } else {
                                tts.speak("Hazard mode paused.")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 68.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isScanning) Emergency else Accent
                        )
                    ) {
                        Text(
                            text = if (isScanning) stringResource(R.string.hazard_stop) else stringResource(R.string.hazard_start),
                            fontSize = 22.sp,
                            color = if (isScanning) Color.White else BgPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
