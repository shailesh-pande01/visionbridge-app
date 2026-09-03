package com.example.visionbridge.ui.screens.volunteer

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.visionbridge.R
import com.example.visionbridge.data.SessionManager
import com.example.visionbridge.ui.theme.*
import com.example.visionbridge.utils.TextToSpeechManager
import com.example.visionbridge.voice.ScreenActionRegistry
import com.example.visionbridge.voice.VoiceManager

import androidx.activity.compose.BackHandler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolunteerHelpScreen(
    onBack: () -> Unit,
    viewModel: VolunteerViewModel = viewModel()
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager.getInstance(context) }
    val currentLanguage by sessionManager.language.collectAsStateWithLifecycle()

    val callState by viewModel.callState.collectAsStateWithLifecycle()

    val tts = remember { TextToSpeechManager(context) }

    BackHandler {
        tts.stop()
        viewModel.cancelRequest()
        onBack()
    }

    LaunchedEffect(currentLanguage) {
        tts.setLanguage(currentLanguage)
    }

    var hasPermissions by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermissions = results.values.all { it }
        if (hasPermissions) {
            viewModel.startHelpRequest(context)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            permissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            viewModel.startHelpRequest(context)
        }
    }

    DisposableEffect(Unit) {
        VoiceManager.getInstance(context).pauseForCall()
        ScreenActionRegistry.registerScreen(
            screenId = "volunteer",
            onCancel = {
                tts.stop()
                viewModel.cancelRequest()
                onBack()
            }
        )
        onDispose {
            ScreenActionRegistry.unregisterScreen("volunteer")
            VoiceManager.getInstance(context).resumeAfterCall()
            tts.shutdown()
        }
    }

    // TTS speech on status transitions and WebRTC mic prioritization
    LaunchedEffect(callState) {
        when (val state = callState) {
            is VolunteerCallState.LocatingAndBroadcasting -> {
                tts.speak(context.getString(R.string.volunteer_locating))
            }
            is VolunteerCallState.SearchingVolunteer -> {
                tts.speak(context.getString(R.string.volunteer_help_searching_speech))
            }
            is VolunteerCallState.Accepted -> {
                VoiceManager.getInstance(context).pauseForCall()
                val vol = if (!state.volunteerName.isNullOrBlank()) state.volunteerName else "A volunteer"
                tts.speak(context.getString(R.string.volunteer_help_accepted_speech, vol))
            }
            is VolunteerCallState.Connecting -> {
                VoiceManager.getInstance(context).pauseForCall()
                tts.speak(context.getString(R.string.volunteer_help_connecting_speech))
            }
            is VolunteerCallState.Connected -> {
                VoiceManager.getInstance(context).pauseForCall()
                tts.speak(context.getString(R.string.volunteer_call_connected))
            }
            is VolunteerCallState.Completed -> {
                VoiceManager.getInstance(context).resumeAfterCall()
                tts.speak(state.message)
            }
            is VolunteerCallState.Failed -> {
                VoiceManager.getInstance(context).resumeAfterCall()
                tts.speak(state.message)
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            com.example.visionbridge.ui.components.AppTopBar(
                title = stringResource(R.string.feature_volunteer),
                subtitle = stringResource(R.string.volunteer_help_subtitle),
                onBack = {
                    tts.stop()
                    viewModel.cancelRequest()
                    onBack()
                },
                backContentDescription = stringResource(R.string.common_cancel)
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
            when (val state = callState) {
                is VolunteerCallState.LocatingAndBroadcasting,
                is VolunteerCallState.SearchingVolunteer -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .background(BgSecondary),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Accent,
                                strokeWidth = 5.dp,
                                modifier = Modifier.size(90.dp)
                            )
                            Text(text = "🤝", fontSize = 42.sp)
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        Text(
                            text = stringResource(R.string.volunteer_searching),
                            color = TextPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.semantics { heading() }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.volunteer_searching_desc),
                            color = TextMuted,
                            fontSize = 17.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 24.sp
                        )

                        Spacer(modifier = Modifier.height(48.dp))

                        Button(
                            onClick = {
                                viewModel.cancelRequest()
                                onBack()
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Emergency)
                        ) {
                            Text(
                                text = stringResource(R.string.volunteer_cancel_request),
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                is VolunteerCallState.Accepted,
                is VolunteerCallState.Connecting -> {
                    val volName = when (state) {
                        is VolunteerCallState.Accepted -> state.volunteerName
                        is VolunteerCallState.Connecting -> state.volunteerName
                        else -> null
                    } ?: "Volunteer"

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .background(BgSecondary),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Accent,
                                strokeWidth = 6.dp,
                                modifier = Modifier.size(90.dp)
                            )
                            Text(text = "👤", fontSize = 42.sp)
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        Text(
                            text = stringResource(R.string.volunteer_accepted_title, volName),
                            color = Accent,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.semantics { heading() }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.volunteer_connecting_desc),
                            color = TextPrimary,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 24.sp
                        )

                        Spacer(modifier = Modifier.height(48.dp))

                        Button(
                            onClick = {
                                viewModel.cancelRequest()
                                onBack()
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Emergency)
                        ) {
                            Text(
                                text = stringResource(R.string.volunteer_cancel_request),
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                is VolunteerCallState.Connected -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
                    ) {
                        Surface(
                            color = Accent,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "🟢", fontSize = 24.sp, modifier = Modifier.padding(end = 12.dp))
                                Column {
                                    Text(
                                        text = stringResource(R.string.volunteer_call_badge),
                                        color = BgPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = stringResource(R.string.volunteer_connected_title),
                                        color = BgPrimary,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = stringResource(R.string.volunteer_connected_instruction),
                            color = TextPrimary,
                            fontSize = 18.sp,
                            lineHeight = 26.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(40.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Button(
                                onClick = { viewModel.toggleMute() },
                                modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (state.isMuted) Emergency else BgSecondary,
                                    contentColor = if (state.isMuted) Color.White else TextPrimary
                                )
                            ) {
                                Text(
                                    text = if (state.isMuted) stringResource(R.string.volunteer_btn_unmute) else stringResource(R.string.volunteer_btn_mute),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = {
                                    viewModel.endCall(isLocal = true)
                                    onBack()
                                },
                                modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Emergency)
                            ) {
                                Text(
                                    text = stringResource(R.string.volunteer_end_call),
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Local Camera Preview (Debug):", color = TextPrimary)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black)
                        ) {
                            androidx.compose.ui.viewinterop.AndroidView(
                                factory = { ctx ->
                                    org.webrtc.SurfaceViewRenderer(ctx).apply {
                                        viewModel.callManager?.eglBase?.eglBaseContext?.let { eglContext ->
                                            init(eglContext, null)
                                            setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                                            setEnableHardwareScaler(true)
                                            setMirror(false) // Assuming back camera
                                            viewModel.callManager?.attachLocalPreview(this)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                                update = { renderer ->
                                    // Make sure it remains attached if re-rendered
                                    viewModel.callManager?.attachLocalPreview(renderer)
                                },
                                onRelease = { renderer ->
                                    viewModel.callManager?.detachLocalPreview(renderer)
                                    renderer.release()
                                }
                            )
                        }
                    }
                }

                is VolunteerCallState.Completed -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    ) {
                        Text(text = "✅", fontSize = 60.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = state.message,
                            color = Accent,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(32.dp))
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

                is VolunteerCallState.Failed -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
                    ) {
                        Text(text = "⚠️", fontSize = 60.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = state.message,
                            color = Emergency,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(
                            onClick = { viewModel.startHelpRequest(context) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Accent)
                        ) {
                            Text(stringResource(R.string.common_retry), color = BgPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                else -> {}
            }
        }
    }
}
