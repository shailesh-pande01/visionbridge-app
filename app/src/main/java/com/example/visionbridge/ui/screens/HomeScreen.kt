package com.example.visionbridge.ui.screens

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.visionbridge.R
import com.example.visionbridge.data.SessionManager
import com.example.visionbridge.ui.components.ActionCard
import com.example.visionbridge.ui.components.VoiceAssistantArea
import com.example.visionbridge.ui.theme.*
import com.example.visionbridge.voice.VoiceManager
import com.example.visionbridge.voice.VoiceStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager.getInstance(context) }
    val currentLanguage by sessionManager.language.collectAsStateWithLifecycle()
    val currentUser by sessionManager.currentUser.collectAsStateWithLifecycle()

    val voiceManager = remember { VoiceManager.getInstance(context) }
    val voiceState by voiceManager.voiceState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            voiceManager.startAmbientListening()
        }
    }

    // Ensure ambient wake-word listening is active if permission is granted
    LaunchedEffect(Unit) {
        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            voiceManager.startAmbientListening()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            fontWeight = FontWeight.Bold,
                            color = Accent,
                            fontSize = 24.sp
                        )
                        if (currentUser != null) {
                            Text(
                                text = currentUser?.name ?: "",
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                        }
                    }
                },
                actions = {
                    // Language Switcher (EN / HI / MR)
                    Row(
                        modifier = Modifier.padding(end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (currentLanguage == "en") Accent else BgCard,
                            modifier = Modifier.padding(2.dp)
                        ) {
                            TextButton(
                                onClick = { sessionManager.setLanguage("en") },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text(
                                    "EN",
                                    color = if (currentLanguage == "en") BgPrimary else TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (currentLanguage == "hi") Accent else BgCard,
                            modifier = Modifier.padding(2.dp)
                        ) {
                            TextButton(
                                onClick = { sessionManager.setLanguage("hi") },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text(
                                    "हि",
                                    color = if (currentLanguage == "hi") BgPrimary else TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (currentLanguage == "mr") Accent else BgCard,
                            modifier = Modifier.padding(2.dp)
                        ) {
                            TextButton(
                                onClick = { sessionManager.setLanguage("mr") },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text(
                                    "म",
                                    color = if (currentLanguage == "mr") BgPrimary else TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    // Logout Button
                    TextButton(
                        onClick = {
                            voiceManager.stopVoice()
                            sessionManager.clearUser()
                            onLogout()
                        }
                    ) {
                        Text(stringResource(R.string.auth_logout), color = Emergency, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
                .background(BgPrimary)
                .verticalScroll(scrollState)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Primary: Voice Assistant Area
            VoiceAssistantArea(
                voiceState = voiceState,
                onClick = {
                    val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                    if (!hasMic) {
                        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        if (voiceState.status == VoiceStatus.AWAITING_COMMAND || voiceState.status == VoiceStatus.SPEAKING) {
                            voiceManager.stopVoice()
                            voiceManager.startAmbientListening()
                        } else {
                            voiceManager.activateVoice()
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Section: Quick Actions
            Text(
                text = stringResource(R.string.home_quick_actions),
                style = MaterialTheme.typography.titleMedium,
                color = TextMuted,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
                    .semantics { heading() }
            )

            // 0a. VisionBridge Live (Real-time camera + voice)
            ActionCard(
                icon = "⚡",
                title = stringResource(R.string.feature_live_vision),
                subtitle = stringResource(R.string.feature_live_vision_sub),
                onClick = { onNavigate("live_vision") }
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 0b. AI Voice Call (Real-time conversational voice assistant)
            ActionCard(
                icon = "🎙️",
                title = stringResource(R.string.feature_voice_call),
                subtitle = stringResource(R.string.feature_voice_call_sub),
                onClick = { onNavigate("voice_call") }
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 1. Smart Reading
            ActionCard(
                icon = "📖",
                title = stringResource(R.string.feature_reading),
                subtitle = stringResource(R.string.feature_reading_sub),
                onClick = { onNavigate("reading") }
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 2. Surroundings
            ActionCard(
                icon = "🎙️",
                title = stringResource(R.string.feature_surroundings),
                subtitle = stringResource(R.string.feature_surroundings_sub),
                onClick = { onNavigate("surroundings") }
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 3. Hazard Mode
            ActionCard(
                icon = "🚨",
                title = stringResource(R.string.feature_hazard),
                subtitle = stringResource(R.string.feature_hazard_sub),
                onClick = { onNavigate("hazard") }
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 4. Currency Reader
            ActionCard(
                icon = "💵",
                title = stringResource(R.string.feature_currency),
                subtitle = stringResource(R.string.feature_currency_sub),
                onClick = { onNavigate("currency") }
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 5. Public Transport
            ActionCard(
                icon = "🚍",
                title = stringResource(R.string.feature_transport),
                subtitle = stringResource(R.string.feature_transport_sub),
                onClick = { onNavigate("transport") }
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 6. Smart Object Finder
            ActionCard(
                icon = "🔍",
                title = stringResource(R.string.feature_finder),
                subtitle = stringResource(R.string.feature_finder_sub),
                onClick = { onNavigate("finder") }
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 7. Entertainment Hub
            ActionCard(
                icon = "🎧",
                title = stringResource(R.string.feature_entertainment),
                subtitle = stringResource(R.string.feature_entertainment_sub),
                onClick = { onNavigate("entertainment") }
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Section: Support & Emergency
            Text(
                text = stringResource(R.string.home_support_emergency),
                style = MaterialTheme.typography.titleMedium,
                color = TextMuted,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
                    .semantics { heading() }
            )

            // 7. Where Am I?
            ActionCard(
                icon = "📍",
                title = stringResource(R.string.feature_location),
                subtitle = stringResource(R.string.feature_location_sub),
                onClick = { onNavigate("location") }
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 8. Volunteer Help
            ActionCard(
                icon = "🤝",
                title = stringResource(R.string.feature_volunteer),
                subtitle = stringResource(R.string.feature_volunteer_sub),
                onClick = { onNavigate("volunteer") }
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 9. Emergency SOS
            ActionCard(
                icon = "🚨",
                title = stringResource(R.string.feature_sos),
                subtitle = stringResource(R.string.feature_sos_sub),
                onClick = { onNavigate("sos") },
                containerColor = EmergencyDim,
                contentColor = Emergency,
                borderColor = Emergency.copy(alpha = 0.3f),
                isDestructive = true
            )

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}
