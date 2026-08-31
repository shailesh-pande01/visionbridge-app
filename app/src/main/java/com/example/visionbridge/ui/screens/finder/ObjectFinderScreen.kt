package com.example.visionbridge.ui.screens.finder

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
import com.example.visionbridge.utils.SpeechRecognizerManager
import com.example.visionbridge.utils.TextToSpeechManager
import com.example.visionbridge.voice.ScreenActionRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObjectFinderScreen(
    initialTarget: String? = null,
    onBack: () -> Unit,
    onVolunteerHelp: () -> Unit,
    viewModel: ObjectFinderViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val sessionManager = remember { SessionManager.getInstance(context) }
    val currentLanguage by sessionManager.language.collectAsStateWithLifecycle()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val targetObject by viewModel.targetObject.collectAsStateWithLifecycle()

    val tts = remember { TextToSpeechManager(context) }
    val isSpeaking by tts.isSpeaking.collectAsStateWithLifecycle()
    val speechRecognizer = remember { SpeechRecognizerManager(context) }
    var isListeningForTarget by remember { mutableStateOf(false) }

    LaunchedEffect(currentLanguage) {
        tts.setLanguage(currentLanguage)
    }

    LaunchedEffect(initialTarget) {
        if (!initialTarget.isNullOrBlank()) {
            viewModel.setTarget(initialTarget)
        }
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasCameraPermission = it }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasAudioPermission = it }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        if (!hasAudioPermission) audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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

    val previewShouldBeLive = uiState is FinderUiState.Camera || uiState is FinderUiState.Processing

    LaunchedEffect(hasCameraPermission, previewShouldBeLive) {
        if (!hasCameraPermission) return@LaunchedEffect

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
                            viewModel.resetToCamera()
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    scope.launch { viewModel.resetToCamera() }
                }
            }
        )
    }

    DisposableEffect(Unit) {
        ScreenActionRegistry.registerScreen(
            screenId = "objectFinder",
            onCapture = { capture() },
            onCancel = { onBack() },
            onFindObject = { target -> viewModel.setTarget(target) }
        )
        onDispose {
            ScreenActionRegistry.unregisterScreen("objectFinder")
            cameraProvider?.unbindAll()
            cameraExecutor.shutdown()
            tts.shutdown()
            speechRecognizer.destroy()
        }
    }

    // TTS feedback on result
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is FinderUiState.PromptTarget -> {
                tts.speak("What object are you looking for? Tap the microphone or type below.")
            }
            is FinderUiState.Processing -> {
                tts.speak("Looking for $targetObject…")
            }
            is FinderUiState.Result -> {
                ContextMemoryManager.setContext("objectFinder", "object search result", state.analysis.speech)
                tts.speak(state.analysis.speech)
            }
            is FinderUiState.Failed -> {
                tts.speak(state.message)
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_finder), fontWeight = FontWeight.Bold) },
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
                actions = {
                    if (targetObject.isNotBlank()) {
                        TextButton(onClick = { viewModel.changeTarget() }) {
                            Text("Change Object", color = Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
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
            when (val state = uiState) {
                is FinderUiState.PromptTarget -> {
                    var textInput by remember { mutableStateOf("") }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.finder_target_prompt),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.finder_target_hint),
                            fontSize = 16.sp,
                            color = TextMuted,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        // Big Mic Button
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .clip(CircleShape)
                                .background(if (isListeningForTarget) Emergency else Accent)
                                .clickable {
                                    if (isListeningForTarget) {
                                        speechRecognizer.stopListening()
                                        isListeningForTarget = false
                                    } else {
                                        if (!hasAudioPermission) {
                                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            return@clickable
                                        }
                                        speechRecognizer.startListening(
                                            languageCode = currentLanguage,
                                            onReady = { isListeningForTarget = true },
                                            onResult = { spoken ->
                                                isListeningForTarget = false
                                                viewModel.setTarget(spoken)
                                            },
                                            onError = { isListeningForTarget = false }
                                        )
                                    }
                                }
                                .semantics { contentDescription = "Tap to speak object name" },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isListeningForTarget) "🎙️" else "🎤",
                                fontSize = 48.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (isListeningForTarget) "Listening…" else "Tap to Speak Object",
                            color = if (isListeningForTarget) Emergency else TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(32.dp))
                        Text(text = "— OR TYPE —", color = TextMuted, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            label = { Text("Object Name", color = TextMuted) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = Accent,
                                unfocusedBorderColor = BorderSubtle,
                                focusedContainerColor = BgCard,
                                unfocusedContainerColor = BgCard
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (textInput.isNotBlank()) {
                                    viewModel.setTarget(textInput)
                                }
                            },
                            enabled = textInput.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Accent)
                        ) {
                            Text("Start Searching", color = BgPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                else -> {
                    // Camera / Result / Failed
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        if (hasCameraPermission) {
                            AndroidView(
                                factory = { previewView },
                                modifier = Modifier.fillMaxSize()
                            )

                            // Top Target Pill
                            Surface(
                                color = BgPrimary.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "🎯 Target: $targetObject",
                                    color = Accent,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                                )
                            }

                            when (val st = uiState) {
                                is FinderUiState.Processing -> {
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
                                                text = "Searching for $targetObject…",
                                                color = TextPrimary,
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                is FinderUiState.Result -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(BgCard)
                                            .padding(20.dp)
                                            .verticalScroll(rememberScrollState())
                                            .semantics { liveRegion = LiveRegionMode.Polite }
                                    ) {
                                        // Found status banner
                                        Surface(
                                            color = if (st.analysis.found) Accent else Emergency,
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(20.dp)) {
                                                Text(
                                                    text = if (st.analysis.found) "✅ FOUND: ${st.analysis.objectName.uppercase()}" else "❌ NOT FOUND: ${st.analysis.objectName.uppercase()}",
                                                    color = if (st.analysis.found) BgPrimary else Color.White,
                                                    fontSize = 22.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.semantics { heading() }
                                                )
                                                if (st.analysis.direction.isNotBlank()) {
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text(
                                                        text = "Direction: ${st.analysis.direction}",
                                                        color = if (st.analysis.found) BgPrimary else Color.White,
                                                        fontSize = 18.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                                if (st.analysis.distance.isNotBlank()) {
                                                    Text(
                                                        text = "Distance: ${st.analysis.distance}",
                                                        color = if (st.analysis.found) BgPrimary else Color.White,
                                                        fontSize = 18.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

                                        Text(
                                            text = st.analysis.speech,
                                            color = TextPrimary,
                                            fontSize = 20.sp,
                                            lineHeight = 30.sp
                                        )

                                        if (!st.analysis.found) {
                                            Spacer(modifier = Modifier.height(20.dp))
                                            Surface(
                                                color = BgSecondary,
                                                shape = RoundedCornerShape(14.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(16.dp)) {
                                                    Text(
                                                        text = "Can't locate your $targetObject? A volunteer can help you search in real time.",
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
                                                        Text("🤝 Connect with Volunteer", color = BgPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                is FinderUiState.Failed -> {
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
                                            text = st.message,
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
                            when (val st = uiState) {
                                is FinderUiState.Camera -> {
                                    Box(
                                        modifier = Modifier
                                            .size(96.dp)
                                            .clip(CircleShape)
                                            .background(Accent)
                                            .clickable { capture() }
                                            .semantics { contentDescription = "Scan to find $targetObject" },
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
                                        text = "Scan for $targetObject",
                                        color = TextPrimary,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                is FinderUiState.Result -> {
                                    Button(
                                        onClick = {
                                            tts.stop()
                                            viewModel.resetToCamera()
                                        },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Accent)
                                    ) {
                                        Text("Scan Another Angle", fontSize = 20.sp, color = BgPrimary, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Button(
                                            onClick = { tts.speak(st.analysis.speech) },
                                            modifier = Modifier.weight(1f).heightIn(min = 54.dp),
                                            shape = RoundedCornerShape(14.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = BgSecondary, contentColor = TextPrimary)
                                        ) {
                                            Text("🔊 Replay", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Button(
                                            onClick = { tts.stop() },
                                            enabled = isSpeaking,
                                            modifier = Modifier.weight(1f).heightIn(min = 54.dp),
                                            shape = RoundedCornerShape(14.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = BgSecondary, contentColor = TextPrimary)
                                        ) {
                                            Text("⏹ Stop", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                is FinderUiState.Failed -> {
                                    Button(
                                        onClick = { viewModel.resetToCamera() },
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
    }
}
