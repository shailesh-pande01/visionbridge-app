package com.example.visionbridge.ui.screens.volunteer

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.visionbridge.R
import com.example.visionbridge.data.HelpRequest
import com.example.visionbridge.data.SessionManager
import com.example.visionbridge.ui.theme.*
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolunteerDashboardScreen(
    onLogout: () -> Unit,
    viewModel: VolunteerDashboardViewModel = viewModel()
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager.getInstance(context) }
    val currentUser by sessionManager.currentUser.collectAsStateWithLifecycle()
    val dashboardState by viewModel.dashboardState.collectAsStateWithLifecycle()
    val remoteStream by viewModel.remoteMediaStream.collectAsStateWithLifecycle()

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasAudioPermission = it }

    LaunchedEffect(Unit) {
        if (!hasAudioPermission) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        viewModel.loadRequests()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.volunteer_dashboard_title), fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(
                        onClick = {
                            sessionManager.clearUser()
                            onLogout()
                        }
                    ) {
                        Text(stringResource(R.string.auth_logout), color = Emergency, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
            when (val state = dashboardState) {
                is VolunteerDashboardState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Accent)
                    }
                }

                is VolunteerDashboardState.RequestList -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Greeting Card
                        Surface(
                            color = BgSecondary,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Accent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "🤝", fontSize = 24.sp)
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = "Welcome, ${currentUser?.name ?: "Volunteer"}!",
                                        color = TextPrimary,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Ready to assist low-vision users.",
                                        color = TextMuted,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.volunteer_pending_requests),
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.semantics { heading() }
                            )
                            IconButton(onClick = { viewModel.loadRequests() }) {
                                Text(text = "🔄", fontSize = 18.sp)
                            }
                        }

                        if (state.requests.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(top = 60.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = "✨", fontSize = 48.sp)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = stringResource(R.string.volunteer_no_pending),
                                        color = TextMuted,
                                        fontSize = 18.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(state.requests) { req ->
                                    RequestItemCard(
                                        request = req,
                                        onAccept = { viewModel.acceptRequest(req) }
                                    )
                                }
                            }
                        }
                    }
                }

                is VolunteerDashboardState.InCall -> {
                    // Active Video Call UI
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    ) {
                        // Video Feed from Low-Vision User
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            val callManager = viewModel.callManager
                            if (callManager != null) {
                                AndroidView(
                                    factory = { ctx ->
                                        SurfaceViewRenderer(ctx).apply {
                                            init(callManager.eglBase.eglBaseContext, null)
                                            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                                            setEnableHardwareScaler(true)
                                            val videoTrack = remoteStream?.videoTracks?.firstOrNull()
                                            videoTrack?.addSink(this)
                                        }
                                    },
                                    update = { renderer ->
                                        val videoTrack = remoteStream?.videoTracks?.firstOrNull()
                                        videoTrack?.addSink(renderer)
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // Requester Info Overlay
                            Surface(
                                color = BgPrimary.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "Assisting: ${state.request.requesterName ?: "User"}",
                                    color = Accent,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }

                        // Call Controls
                        Surface(
                            color = BgCard,
                            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.toggleMute() },
                                    modifier = Modifier.weight(1f).heightIn(min = 58.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (state.isMuted) Emergency else BgSecondary,
                                        contentColor = if (state.isMuted) Color.White else TextPrimary
                                    )
                                ) {
                                    Text(
                                        text = if (state.isMuted) "🔇 Unmute" else "🎙️ Mute",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Button(
                                    onClick = { viewModel.endCall(isLocal = true) },
                                    modifier = Modifier.weight(1f).heightIn(min = 58.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                                ) {
                                    Text(
                                        text = stringResource(R.string.volunteer_complete_btn),
                                        color = BgPrimary,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                is VolunteerDashboardState.Error -> {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "⚠️", fontSize = 48.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = state.message,
                                color = Emergency,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = { viewModel.loadRequests() },
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
}

@Composable
private fun RequestItemCard(
    request: HelpRequest,
    onAccept: () -> Unit
) {
    Surface(
        color = BgCard,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = request.requesterName ?: "Low-Vision User",
                    color = TextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = AccentDim,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = request.requestType.uppercase(),
                        color = Accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = request.helpDescription,
                color = TextMuted,
                fontSize = 16.sp,
                lineHeight = 22.sp
            )

            if (!request.address.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "📍 ${request.address}",
                    color = TextMuted,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Text(
                    text = stringResource(R.string.volunteer_accept_btn),
                    color = BgPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
