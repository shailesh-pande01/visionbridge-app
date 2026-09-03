package com.example.visionbridge.ui.screens.location

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.visionbridge.R
import com.example.visionbridge.data.ContextMemoryManager
import com.example.visionbridge.data.SessionManager
import com.example.visionbridge.ui.theme.*
import com.example.visionbridge.utils.TextToSpeechManager
import com.example.visionbridge.voice.ScreenActionRegistry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationAssistantScreen(
    onBack: () -> Unit,
    viewModel: LocationViewModel = viewModel()
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager.getInstance(context) }
    val currentLanguage by sessionManager.language.collectAsStateWithLifecycle()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val tts = remember { TextToSpeechManager(context) }
    val isSpeaking by tts.isSpeaking.collectAsStateWithLifecycle()

    LaunchedEffect(currentLanguage) {
        tts.setLanguage(currentLanguage)
    }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (hasLocationPermission) {
            viewModel.fetchCurrentLocation(currentLanguage)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            viewModel.fetchCurrentLocation(currentLanguage)
        }
    }

    DisposableEffect(Unit) {
        ScreenActionRegistry.registerScreen(
            screenId = "location",
            onCancel = {
                tts.stop()
                onBack()
            }
        )
        onDispose {
            ScreenActionRegistry.unregisterScreen("location")
            tts.shutdown()
        }
    }

    // TTS speech on result
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is LocationUiState.Loading -> {
                tts.speak(context.getString(R.string.location_finding_speech))
            }
            is LocationUiState.Success -> {
                ContextMemoryManager.setContext("location", "location information", state.analysis.summary)
                val fullSpeech = buildString {
                    append(state.analysis.summary)
                    if (state.analysis.landmarks.isNotEmpty()) {
                        append(". ")
                        append(state.analysis.landmarks.take(3).joinToString(", "))
                    }
                }
                tts.speak(fullSpeech)
            }
            is LocationUiState.Error -> {
                tts.speak(state.message)
            }
        }
    }

    Scaffold(
        topBar = {
            com.example.visionbridge.ui.components.AppTopBar(
                title = stringResource(R.string.feature_location),
                subtitle = stringResource(R.string.location_subtitle),
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
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val state = uiState) {
                is LocationUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(top = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Accent, modifier = Modifier.size(54.dp))
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = stringResource(R.string.location_finding),
                                color = TextPrimary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                is LocationUiState.Success -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { liveRegion = LiveRegionMode.Polite }
                    ) {
                        // Summary card
                        Surface(
                            color = BgSecondary,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(text = "📍", fontSize = 32.sp, modifier = Modifier.padding(end = 14.dp))
                                Column {
                                    Text(
                                        text = stringResource(R.string.location_summary),
                                        color = Accent,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.semantics { heading() }
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = state.analysis.summary,
                                        color = TextPrimary,
                                        fontSize = 22.sp,
                                        lineHeight = 32.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        if (!state.analysis.address.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Surface(
                                color = BgCard,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Text(
                                        text = stringResource(R.string.location_address, state.analysis.address),
                                        color = TextPrimary,
                                        fontSize = 18.sp,
                                        lineHeight = 24.sp
                                    )
                                }
                            }
                        }

                        if (state.analysis.landmarks.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(18.dp))
                            Text(
                                text = stringResource(R.string.location_nearby_places),
                                color = Accent,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.semantics { heading() }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            state.analysis.landmarks.forEach { landmark ->
                                Surface(
                                    color = BgCard,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = "🏛️", fontSize = 20.sp, modifier = Modifier.padding(end = 12.dp))
                                        Text(
                                            text = landmark,
                                            color = TextPrimary,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        // Controls
                        Button(
                            onClick = { viewModel.fetchCurrentLocation(currentLanguage) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Accent)
                        ) {
                            Text("🔄 " + stringResource(R.string.common_refresh), fontSize = 20.sp, color = BgPrimary, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    val fullSpeech = "${state.analysis.summary}. ${state.analysis.landmarks.take(3).joinToString(", ")}"
                                    tts.speak(fullSpeech)
                                },
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
                }

                is LocationUiState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
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
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { viewModel.fetchCurrentLocation(currentLanguage) },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent)
                        ) {
                            Text(stringResource(R.string.common_retry), color = BgPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
