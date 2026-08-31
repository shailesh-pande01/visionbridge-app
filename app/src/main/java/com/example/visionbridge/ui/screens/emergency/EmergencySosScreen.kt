package com.example.visionbridge.ui.screens.emergency

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.visionbridge.R
import com.example.visionbridge.data.SessionManager
import com.example.visionbridge.ui.theme.*
import com.example.visionbridge.utils.TextToSpeechManager
import com.example.visionbridge.voice.ScreenActionRegistry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencySosScreen(
    onBack: () -> Unit,
    onManageContacts: () -> Unit,
    viewModel: EmergencySosViewModel = viewModel()
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager.getInstance(context) }
    val currentLanguage by sessionManager.language.collectAsStateWithLifecycle()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val tts = remember { TextToSpeechManager(context) }

    LaunchedEffect(currentLanguage) {
        tts.setLanguage(currentLanguage)
    }

    LaunchedEffect(Unit) {
        viewModel.startCountdown()
    }

    DisposableEffect(Unit) {
        ScreenActionRegistry.registerScreen(
            screenId = "emergency",
            onCancel = {
                tts.stop()
                viewModel.cancelSos()
                onBack()
            },
            onSubmit = {
                viewModel.sendEmergencyAlert()
            }
        )
        onDispose {
            ScreenActionRegistry.unregisterScreen("emergency")
            tts.shutdown()
        }
    }

    // TTS alerts
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is SosUiState.Countdown -> {
                if (state.secondsLeft == 5) {
                    tts.speak("Emergency SOS triggered. Sending alert in 5 seconds. Tap cancel to stop.")
                }
            }
            is SosUiState.Sending -> {
                tts.speak("Sending emergency distress alerts with your live location.")
            }
            is SosUiState.Active -> {
                tts.speak("Emergency SOS is active. Your contacts have been notified on WhatsApp.")
            }
            is SosUiState.Cancelled -> {
                tts.speak("Emergency SOS cancelled.")
            }
            is SosUiState.Error -> {
                tts.speak(state.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_sos), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(
                        onClick = {
                            tts.stop()
                            viewModel.cancelSos()
                            onBack()
                        }
                    ) {
                        Text(stringResource(R.string.common_back), color = TextPrimary, fontSize = 18.sp)
                    }
                },
                actions = {
                    TextButton(onClick = onManageContacts) {
                        Text("Contacts", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (val state = uiState) {
                is SosUiState.Countdown -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .clip(CircleShape)
                                .background(Emergency),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${state.secondsLeft}",
                                color = Color.White,
                                fontSize = 64.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        Text(
                            text = stringResource(R.string.sos_countdown_title),
                            color = Emergency,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.semantics { heading() }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = stringResource(R.string.sos_countdown_sub, state.secondsLeft),
                            color = TextPrimary,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(40.dp))

                        Button(
                            onClick = {
                                viewModel.cancelSos()
                                onBack()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BgSecondary)
                        ) {
                            Text(
                                text = stringResource(R.string.sos_cancel_alert),
                                color = TextPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = { viewModel.sendEmergencyAlert() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Emergency)
                        ) {
                            Text(
                                text = stringResource(R.string.sos_send_now),
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                is SosUiState.Sending -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = Emergency,
                            strokeWidth = 6.dp,
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Dispatching Emergency WhatsApp Alert…",
                            color = TextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                is SosUiState.Active -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
                    ) {
                        Surface(
                            color = Emergency,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(24.dp)) {
                                Text(
                                    text = stringResource(R.string.sos_active_title),
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.semantics { heading() }
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = stringResource(R.string.sos_active_desc),
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    lineHeight = 26.sp
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Surface(
                                    color = Color.Black.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = if (state.event.whatsappSent) stringResource(R.string.sos_whatsapp_sent) else stringResource(R.string.sos_whatsapp_failed),
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        Button(
                            onClick = onManageContacts,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BgSecondary)
                        ) {
                            Text("👥 " + stringResource(R.string.sos_contacts_title), color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                viewModel.endEmergency()
                                onBack()
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Emergency)
                        ) {
                            Text(
                                text = stringResource(R.string.sos_end_emergency),
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                is SosUiState.Cancelled -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "✅", fontSize = 60.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Emergency mode inactive.",
                            color = Accent,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(28.dp))
                        Button(
                            onClick = onBack,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Accent)
                        ) {
                            Text(stringResource(R.string.common_home), color = BgPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                is SosUiState.Error -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "⚠️", fontSize = 60.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = state.message,
                            color = Emergency,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(28.dp))
                        Button(
                            onClick = { viewModel.sendEmergencyAlert() },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Emergency)
                        ) {
                            Text(stringResource(R.string.common_retry), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
