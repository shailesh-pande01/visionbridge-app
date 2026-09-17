package com.example.visionbridge.ui.screens.medication

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import com.example.visionbridge.R
import com.example.visionbridge.data.MedicationExtraction
import com.example.visionbridge.data.SessionManager
import com.example.visionbridge.ui.theme.*
import com.example.visionbridge.utils.TextToSpeechManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "VB-MedScan"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationScanScreen(
    onBack: () -> Unit,
    onVolunteerHelp: (requestType: String, description: String) -> Unit,
    viewModel: MedicationSafetyViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val scanUiState by viewModel.scanUiState.collectAsStateWithLifecycle()
    val sessionManager = remember { SessionManager.getInstance(context) }
    val currentLanguage by sessionManager.language.collectAsStateWithLifecycle()

    val tts = remember { TextToSpeechManager(context) }
    val isSpeaking by tts.isSpeaking.collectAsStateWithLifecycle()

    var showReminderDialogForExtraction by remember { mutableStateOf<MedicationExtraction?>(null) }
    var saveSuccessMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentLanguage) {
        tts.setLanguage(currentLanguage)
    }

    BackHandler {
        tts.stop()
        if (scanUiState !is MedicationScanUiState.Ready) {
            viewModel.resetScan()
        } else {
            onBack()
        }
    }

    // Camera permission
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionRequested by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        permissionRequested = true
    }

    LaunchedEffect(Unit) {
        if (!hasPermission && !permissionRequested) {
            permissionRequested = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // CameraX setup
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    val previewShouldBeLive = scanUiState is MedicationScanUiState.Ready ||
            scanUiState is MedicationScanUiState.Processing

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
            Log.d(TAG, "Medication camera bound successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Could not start camera for medication scan", e)
            imageCapture = null
            viewModel.onCaptureFailed(
                userMessage = "Could not initialize the camera. Please check camera permissions and retry.",
                technicalDetail = "${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraProvider?.unbindAll()
                cameraExecutor.shutdown()
                tts.stop()
            } catch (e: Exception) {
                Log.e(TAG, "Error disposing camera resources", e)
            }
        }
    }

    fun capturePhoto() {
        val capture = imageCapture
        if (capture == null) {
            viewModel.onCaptureFailed(
                userMessage = "Camera is initializing. Please wait a second and press capture again.",
                technicalDetail = "imageCapture was null"
            )
            return
        }

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
                        Log.e(TAG, "Error reading captured frame bytes", e)
                        null
                    } finally {
                        image.close()
                    }

                    scope.launch {
                        if (bytes == null || bytes.isEmpty()) {
                            Log.e(TAG, "MEDSAFE: image proxy gave null or empty bytes")
                            viewModel.onCaptureFailed(
                                userMessage = "Could not read photo from camera. Please capture again.",
                                technicalDetail = "Empty byte array from ImageProxy"
                            )
                        } else {
                            Log.d(TAG, "MEDSAFE: capture successful, bytes=${bytes.size}, rotation=$rotation")
                            viewModel.onImageCaptured(bytes, rotation, currentLanguage)
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Medication capture error", exception)
                    scope.launch {
                        viewModel.onCaptureFailed(
                            userMessage = "Camera failed to capture photo. Please try again.",
                            technicalDetail = "ImageCaptureException: ${exception.message}"
                        )
                    }
                }
            }
        )
    }

    // Auto-announce scan results for accessibility
    LaunchedEffect(scanUiState) {
        when (val state = scanUiState) {
            is MedicationScanUiState.ConfidentResult -> {
                tts.speak(state.speechText)
            }
            is MedicationScanUiState.LowConfidenceResult -> {
                tts.speak(state.speechText)
            }
            is MedicationScanUiState.NoMedicationText -> {
                tts.speak(state.message)
            }
            is MedicationScanUiState.Error -> {
                tts.speak(state.userMessage)
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.medsafe_scan_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            tts.stop()
                            if (scanUiState !is MedicationScanUiState.Ready) {
                                viewModel.resetScan()
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    if (isSpeaking) {
                        IconButton(onClick = { tts.stop() }) {
                            Icon(
                                imageVector = Icons.Default.VolumeOff,
                                contentDescription = "Stop speech",
                                tint = Emergency
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BgPrimary,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary
                )
            )
        },
        containerColor = BgPrimary
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = scanUiState) {
                is MedicationScanUiState.Ready,
                is MedicationScanUiState.Processing -> {
                    // Camera Viewfinder & Capture Controls
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (hasPermission) {
                            AndroidView(
                                factory = { previewView },
                                modifier = Modifier.fillMaxSize()
                            )

                            // High-contrast Aiming Frame overlay for low vision users
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 32.dp, vertical = 80.dp)
                                    .border(
                                        width = 3.dp,
                                        color = Accent,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .semantics {
                                        contentDescription = "Camera viewfinder. Center medicine box or strip inside frame."
                                    }
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(BgPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Text(
                                        text = "Camera permission is required to scan medicine packaging.",
                                        color = TextPrimary,
                                        textAlign = TextAlign.Center,
                                        fontSize = 16.sp
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Accent)
                                    ) {
                                        Text("Grant Camera Access", color = BgPrimary, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Top Instruction Pill
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = BgPrimary.copy(alpha = 0.85f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                        ) {
                            Text(
                                text = stringResource(R.string.medsafe_aim_hint),
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        // Processing Overlay
                        if (state is MedicationScanUiState.Processing) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(BgPrimary.copy(alpha = 0.85f))
                                    .semantics {
                                        liveRegion = LiveRegionMode.Polite
                                        contentDescription = "Analyzing medicine label with safety verification. Please wait."
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        color = Accent,
                                        strokeWidth = 4.dp,
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Text(
                                        text = stringResource(R.string.medsafe_analyzing),
                                        color = TextPrimary,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Verifying safety & packaging details...",
                                        color = TextMuted,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }

                        // Bottom Shutter Bar (When ready)
                        if (state is MedicationScanUiState.Ready && hasPermission) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth(),
                                color = BgPrimary.copy(alpha = 0.9f),
                                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // Big Accessible Shutter Button
                                    Box(
                                        modifier = Modifier
                                            .size(88.dp)
                                            .clip(CircleShape)
                                            .background(Accent)
                                            .clickable(onClick = { capturePhoto() })
                                            .semantics {
                                                contentDescription = "Capture medicine photo"
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(72.dp)
                                                .clip(CircleShape)
                                                .background(BgPrimary)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(60.dp)
                                                    .clip(CircleShape)
                                                    .background(Accent)
                                                    .align(Alignment.Center)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(R.string.medsafe_capture_button),
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                is MedicationScanUiState.ConfidentResult -> {
                    ConfidentResultView(
                        result = state,
                        isSpeaking = isSpeaking,
                        onReadAloud = {
                            if (isSpeaking) tts.stop() else tts.speak(state.speechText)
                        },
                        onSave = {
                            val ext = state.extraction
                            viewModel.saveMedication(
                                name = ext.medicineName ?: "Unknown Medicine",
                                strength = ext.strength ?: "",
                                form = ext.form ?: "",
                                activeIngredients = ext.activeIngredients,
                                printedDirections = ext.printedDirections ?: "",
                                expiryDate = ext.expiryDate ?: "",
                                storageInformation = ext.storageInformation ?: "",
                                warnings = ext.warningsVisibleOnPackage,
                                manufacturer = ext.manufacturer ?: "",
                                batchNumber = ext.batchNumber ?: "",
                                confidence = ext.confidence
                            ) {
                                saveSuccessMessage = "Saved to My Medicines"
                                tts.speak("Medication saved to My Medicines.")
                            }
                        },
                        onSetReminder = {
                            showReminderDialogForExtraction = state.extraction
                        },
                        onScanAgain = {
                            tts.stop()
                            viewModel.resetScan()
                        },
                        saveSuccessMessage = saveSuccessMessage
                    )
                }

                is MedicationScanUiState.LowConfidenceResult -> {
                    LowConfidenceRefusalView(
                        state = state,
                        onVolunteerHelp = {
                            tts.stop()
                            onVolunteerHelp("MEDICATION", "Medication verification: low confidence extraction (${(state.extraction.confidence * 100).toInt()}%)")
                        },
                        onTryAgain = {
                            tts.stop()
                            viewModel.resetScan()
                        }
                    )
                }

                is MedicationScanUiState.NoMedicationText -> {
                    NoMedicationFoundView(
                        message = state.message,
                        onTryAgain = {
                            tts.stop()
                            viewModel.resetScan()
                        },
                        onVolunteerHelp = {
                            tts.stop()
                            onVolunteerHelp("MEDICATION", "Packaging could not be identified automatically")
                        }
                    )
                }

                is MedicationScanUiState.Error -> {
                    ScanErrorView(
                        message = state.userMessage,
                        technicalDetail = state.technicalDetail,
                        onTryAgain = {
                            tts.stop()
                            viewModel.resetScan()
                        }
                    )
                }
            }

            // Set Reminder Quick Dialog from Scan Result
            showReminderDialogForExtraction?.let { extraction ->
                AddReminderQuickDialog(
                    medicineName = extraction.medicineName ?: "Medicine",
                    defaultDosage = extraction.strength ?: "",
                    onDismiss = { showReminderDialogForExtraction = null },
                    onConfirm = { time, dosage ->
                        viewModel.createReminder(
                            medicationName = extraction.medicineName ?: "Medicine",
                            reminderTime = time,
                            dosageLabel = dosage
                        ) {
                            tts.speak("Reminder created for ${extraction.medicineName} at $time.")
                        }
                        showReminderDialogForExtraction = null
                    }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Result Sub-Views
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ConfidentResultView(
    result: MedicationScanUiState.ConfidentResult,
    isSpeaking: Boolean,
    onReadAloud: () -> Unit,
    onSave: () -> Unit,
    onSetReminder: () -> Unit,
    onScanAgain: () -> Unit,
    saveSuccessMessage: String?
) {
    val ext = result.extraction

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // High Contrast Header Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = BgCard,
            border = androidx.compose.foundation.BorderStroke(2.dp, Border)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Confidence / Verification Badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SuccessDim
                    ) {
                        Text(
                            text = "VERIFIED • ${(ext.confidence * 100).toInt()}% CONFIDENCE",
                            color = Success,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    if (ext.isExpired) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = EmergencyDim
                        ) {
                            Text(
                                text = "EXPIRED",
                                color = Emergency,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Medicine Name
                Text(
                    text = ext.medicineName ?: "Unknown Medicine",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )

                // Strength & Form
                val strengthForm = listOfNotNull(ext.strength, ext.form)
                    .filter { it.isNotBlank() }
                    .joinToString(" • ")
                if (strengthForm.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = strengthForm,
                        color = Accent,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Expired Warning Banner (Safety Principle: High Visibility)
        if (ext.isExpired) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = EmergencyDim,
                border = androidx.compose.foundation.BorderStroke(2.dp, Emergency)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "⚠️", fontSize = 28.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "PAST EXPIRY DATE",
                            color = Emergency,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Printed expiry: ${ext.expiryDate}. Do not consume expired medicine without consulting a healthcare professional.",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        // Active Ingredients
        if (ext.activeIngredients.isNotEmpty()) {
            DetailSectionCard(
                title = stringResource(R.string.medsafe_ingredients),
                icon = "🧪"
            ) {
                Text(
                    text = ext.activeIngredients.joinToString(", "),
                    color = TextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            }
        }

        // Printed Directions (Verbatim packaging text)
        if (!ext.printedDirections.isNullOrBlank()) {
            DetailSectionCard(
                title = stringResource(R.string.medsafe_directions),
                icon = "📋"
            ) {
                Text(
                    text = "\"${ext.printedDirections}\"",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "As printed on packaging. Follow your physician's prescribed dosage.",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
        }

        // Expiry & Storage Row
        if (!ext.expiryDate.isNullOrBlank() || !ext.storageInformation.isNullOrBlank()) {
            DetailSectionCard(
                title = "Expiry & Storage",
                icon = "📅"
            ) {
                if (!ext.expiryDate.isNullOrBlank()) {
                    Text(
                        text = "Expiry: ${ext.expiryDate}",
                        color = if (ext.isExpired) Emergency else TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = if (ext.isExpired) FontWeight.Bold else FontWeight.Normal
                    )
                }
                if (!ext.storageInformation.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Storage: ${ext.storageInformation}",
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Printed Warnings
        if (ext.warningsVisibleOnPackage.isNotEmpty()) {
            DetailSectionCard(
                title = stringResource(R.string.medsafe_warnings),
                icon = "⚠️"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ext.warningsVisibleOnPackage.forEach { warning ->
                        Text(
                            text = "• $warning",
                            color = Warning,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }

        // Manufacturer & Batch (if available)
        val mfgBatch = listOfNotNull(
            ext.manufacturer?.takeIf { it.isNotBlank() }?.let { "Mfg: $it" },
            ext.batchNumber?.takeIf { it.isNotBlank() }?.let { "Batch: $it" }
        ).joinToString(" | ")
        if (mfgBatch.isNotBlank()) {
            Text(
                text = mfgBatch,
                color = TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        // Save confirmation toast/banner
        if (saveSuccessMessage != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = SuccessDim,
                border = androidx.compose.foundation.BorderStroke(1.dp, Success)
            ) {
                Text(
                    text = "✓ $saveSuccessMessage",
                    color = Success,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(12.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action Buttons Row 1: Read Aloud & Save
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onReadAloud,
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSpeaking) Emergency else Accent
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = if (isSpeaking) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = null,
                    tint = BgPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isSpeaking) "Stop" else stringResource(R.string.medsafe_read_aloud),
                    color = BgPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Button(
                onClick = onSave,
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BgCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, Border),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Bookmark, contentDescription = null, tint = Accent)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.medsafe_save_button),
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }

        // Action Buttons Row 2: Set Reminder & Scan Again
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onSetReminder,
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BgCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, Border),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Alarm, contentDescription = null, tint = Accent)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.medsafe_set_reminder),
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Button(
                onClick = onScanAgain,
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BgCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, Border),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, tint = TextMuted)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.medsafe_scan_again),
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun LowConfidenceRefusalView(
    state: MedicationScanUiState.LowConfidenceResult,
    onVolunteerHelp: () -> Unit,
    onTryAgain: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Warning Emblem
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(WarningDim),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "🛡️", fontSize = 36.sp)
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = stringResource(R.string.medsafe_low_confidence_title),
            color = Warning,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Safety Explanation
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = BgCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, Warning)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.medsafe_low_confidence_msg),
                    color = TextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                val confPercent = (state.extraction.confidence * 100).toInt()
                Text(
                    text = "Confidence: $confPercent% (Safe threshold is 70%)",
                    color = TextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                state.extraction.uncertaintyReason?.let { reason ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Reason: $reason",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Connect to Human Helper (Section 14 & 16 Fallback)
        Button(
            onClick = onVolunteerHelp,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = BgPrimary)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.medsafe_human_fallback_btn),
                color = BgPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Try Again
        OutlinedButton(
            onClick = onTryAgain,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Border),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = TextPrimary)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.medsafe_try_another_photo),
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun NoMedicationFoundView(
    message: String,
    onTryAgain: () -> Unit,
    onVolunteerHelp: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "🔍", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Medicine Packaging Detected",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = message,
            color = TextMuted,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = onTryAgain,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(text = "Try Again", color = BgPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onVolunteerHelp,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Border),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(text = "Ask Human Helper", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun ScanErrorView(
    message: String,
    technicalDetail: String?,
    onTryAgain: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "⚠️", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Scanning Error",
            color = Emergency,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = message,
            color = TextPrimary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
        if (!technicalDetail.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = technicalDetail,
                color = TextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onTryAgain,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(text = "Retry Scan", color = BgPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun DetailSectionCard(
    title: String,
    icon: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = BgCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = icon, fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    color = Accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Quick Add Reminder Dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AddReminderQuickDialog(
    medicineName: String,
    defaultDosage: String = "",
    onDismiss: () -> Unit,
    onConfirm: (time: String, dosage: String) -> Unit
) {
    var selectedHour by remember { mutableIntStateOf(8) }
    var selectedMinute by remember { mutableIntStateOf(0) }
    var isPm by remember { mutableStateOf(false) }
    var dosageText by remember { mutableStateOf(defaultDosage) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        title = {
            Text(
                text = "Set Reminder for $medicineName",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Select daily alarm time:",
                    color = TextMuted,
                    fontSize = 14.sp
                )

                // Time picker row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Hour selector
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Hour", color = TextMuted, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                selectedHour = if (selectedHour <= 1) 12 else selectedHour - 1
                            }) {
                                Text("-", color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = String.format("%02d", selectedHour),
                                color = TextPrimary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = {
                                selectedHour = if (selectedHour >= 12) 1 else selectedHour + 1
                            }) {
                                Text("+", color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Text(":", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)

                    // Minute selector
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Minute", color = TextMuted, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                selectedMinute = if (selectedMinute <= 0) 55 else (selectedMinute - 5)
                            }) {
                                Text("-", color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = String.format("%02d", selectedMinute),
                                color = TextPrimary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = {
                                selectedMinute = if (selectedMinute >= 55) 0 else (selectedMinute + 5)
                            }) {
                                Text("+", color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // AM/PM Toggle
                    Button(
                        onClick = { isPm = !isPm },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentDim),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Accent)
                    ) {
                        Text(
                            text = if (isPm) "PM" else "AM",
                            color = Accent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Dosage notes
                OutlinedTextField(
                    value = dosageText,
                    onValueChange = { dosageText = it },
                    label = { Text("Dosage note (e.g., 1 tablet after food)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Border
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val militaryHour = when {
                        isPm && selectedHour < 12 -> selectedHour + 12
                        !isPm && selectedHour == 12 -> 0
                        else -> selectedHour
                    }
                    val timeString = String.format("%02d:%02d", militaryHour, selectedMinute)
                    onConfirm(timeString, dosageText)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Text("Save Reminder", color = BgPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}
