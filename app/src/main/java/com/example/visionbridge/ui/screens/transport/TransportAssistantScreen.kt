package com.example.visionbridge.ui.screens.transport

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
fun TransportAssistantScreen(
    onBack: () -> Unit,
    onVolunteerHelp: () -> Unit,
    viewModel: TransportViewModel = viewModel()
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

    val previewShouldBeLive = uiState is TransportUiState.Camera || uiState is TransportUiState.Processing

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
            // Camera error
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

    DisposableEffect(Unit) {
        ScreenActionRegistry.registerScreen(
            screenId = "transport",
            onCapture = { capture() },
            onCancel = { onBack() }
        )
        onDispose {
            ScreenActionRegistry.unregisterScreen("transport")
            cameraProvider?.unbindAll()
            cameraExecutor.shutdown()
            tts.shutdown()
        }
    }

    // TTS feedback
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is TransportUiState.Processing -> {
                tts.speak("Scanning for transport information. Please wait.")
            }
            is TransportUiState.Result -> {
                ContextMemoryManager.setContext("transport", "transport information", state.analysis.speech)
                tts.speak(state.analysis.speech)
            }
            is TransportUiState.Failed -> {
                tts.speak(state.message)
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_transport), fontWeight = FontWeight.Bold) },
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

                    when (val state = uiState) {
                        is TransportUiState.Processing -> {
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
                                        text = "Scanning transport & signs…",
                                        color = TextPrimary,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        is TransportUiState.Result -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(BgCard)
                                    .padding(20.dp)
                                    .verticalScroll(rememberScrollState())
                                    .semantics { liveRegion = LiveRegionMode.Polite }
                            ) {
                                Surface(
                                    color = BgSecondary,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(20.dp)) {
                                        Text(
                                            text = state.analysis.type.uppercase(),
                                            color = Accent,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.semantics { heading() }
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        if (state.analysis.title.isNotBlank()) {
                                            Text(
                                                text = state.analysis.title,
                                                color = TextPrimary,
                                                fontSize = 28.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        if (state.analysis.destination.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "To: ${state.analysis.destination}",
                                                color = Accent,
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = state.analysis.speech,
                                    color = TextPrimary,
                                    fontSize = 20.sp,
                                    lineHeight = 30.sp
                                )

                                // Low confidence volunteer prompt
                                if (state.analysis.confidence < 0.65 || state.analysis.type.equals("None", ignoreCase = true)) {
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Surface(
                                        color = BgSecondary,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                text = stringResource(R.string.confidence_low_prompt),
                                                color = TextPrimary,
                                                fontSize = 17.sp
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Button(
                                                onClick = onVolunteerHelp,
                                                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
                                            ) {
                                                Text(stringResource(R.string.confidence_connect_volunteer), color = BgPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        is TransportUiState.Failed -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(BgCard)
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(text = "⚠️", fontSize = 54.sp)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = state.message,
                                    color = Emergency,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        else -> {}
                    }
                }
            }

            // Bottom Controls
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
                        is TransportUiState.Camera -> {
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(CircleShape)
                                    .background(Accent)
                                    .clickable { capture() }
                                    .semantics { contentDescription = "Scan transport sign" },
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

                        is TransportUiState.Result -> {
                            Button(
                                onClick = {
                                    tts.stop()
                                    viewModel.reset()
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
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
                                    onClick = { tts.speak(state.analysis.speech) },
                                    modifier = Modifier.weight(1f).heightIn(min = 54.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = BgSecondary, contentColor = TextPrimary)
                                ) {
                                    Text("🔊 " + stringResource(R.string.common_replay), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { tts.stop() },
                                    enabled = isSpeaking,
                                    modifier = Modifier.weight(1f).heightIn(min = 54.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = BgSecondary, contentColor = TextPrimary)
                                ) {
                                    Text("⏹ " + stringResource(R.string.common_stop), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        is TransportUiState.Failed -> {
                            Button(
                                onClick = { viewModel.reset() },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Accent)
                            ) {
                                Text(stringResource(R.string.common_retry), fontSize = 20.sp, color = BgPrimary, fontWeight = FontWeight.Bold)
                            }
                        }

                        else -> {}
                    }
                }
            }
        }
    }
}
