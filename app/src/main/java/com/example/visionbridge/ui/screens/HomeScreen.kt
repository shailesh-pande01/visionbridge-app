package com.example.visionbridge.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.visionbridge.R
import com.example.visionbridge.data.SessionManager
import com.example.visionbridge.ui.components.ActionCard
import com.example.visionbridge.ui.components.ConfirmLogoutDialog
import com.example.visionbridge.ui.components.LanguageSelector
import com.example.visionbridge.ui.components.SectionHeader
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

    var showLogoutDialog by remember { mutableStateOf(false) }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            voiceManager.startAmbientListening()
        }
    }

    // Ensure ambient wake-word listening is active if mic permission is granted
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        // App Logo Icon
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AccentDim),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.app_logo),
                                contentDescription = null,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 20.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val fallbackHubName = stringResource(R.string.common_accessibility_hub)
                            val userDisplayName = currentUser?.name?.takeIf { it.isNotBlank() } ?: fallbackHubName
                            Text(
                                text = userDisplayName,
                                color = Accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        // Compact Accessible Language Selector
                        LanguageSelector(
                            currentLanguageCode = currentLanguage,
                            onLanguageSelected = { langCode ->
                                sessionManager.setLanguage(langCode)
                            }
                        )

                        // Logout Button (opens confirmation dialog)
                        val logoutTalkback = stringResource(R.string.dialog_sign_out_talkback)
                        IconButton(
                            onClick = { showLogoutDialog = true },
                            modifier = Modifier
                                .size(44.dp)
                                .semantics { contentDescription = logoutTalkback }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                tint = Emergency.copy(alpha = 0.9f),
                                modifier = Modifier.size(22.dp)
                            )
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
                .background(BgPrimary)
                .verticalScroll(scrollState)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Primary Hero: Voice Assistant Interactive Area
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

            Spacer(modifier = Modifier.height(12.dp))

            // Gesture Discovery Hint Card
            val gestureHintTalkback = stringResource(R.string.gesture_home_hint_talkback)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                shape = RoundedCornerShape(14.dp),
                color = BgCard,
                border = androidx.compose.foundation.BorderStroke(1.dp, Border)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .semantics(mergeDescendants = true) {
                            contentDescription = gestureHintTalkback
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "👆✌️",
                        fontSize = 18.sp,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                    Text(
                        text = stringResource(R.string.gesture_home_hint),
                        color = TextMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 18.sp
                    )
                }
            }

            // Administrator Console Entry Card (Only for verified admin users)
            if (currentUser?.role == "admin") {
                Spacer(modifier = Modifier.height(14.dp))
                ActionCard(
                    icon = "🛡️",
                    title = stringResource(R.string.admin_home_banner_title),
                    subtitle = stringResource(R.string.admin_home_banner_sub),
                    badgeText = stringResource(R.string.admin_dashboard_badge),
                    onClick = { onNavigate("admin_dashboard") }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Section: AI Assistance
            SectionHeader(
                title = stringResource(R.string.home_section_ai_assistance),
                icon = "✨",
                accentBadgeColor = AccentDim
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // VisionBridge Live (Real-time camera + voice WebSocket)
                ActionCard(
                    icon = "⚡",
                    title = stringResource(R.string.feature_live_vision),
                    subtitle = stringResource(R.string.feature_live_vision_sub),
                    badgeText = stringResource(R.string.action_badge_realtime),
                    onClick = { onNavigate("live_vision") }
                )

                // AI Voice Call (Conversational voice assistant)
                ActionCard(
                    icon = "🎙️",
                    title = stringResource(R.string.feature_voice_call),
                    subtitle = stringResource(R.string.feature_voice_call_sub),
                    badgeText = stringResource(R.string.action_badge_audio_call),
                    onClick = { onNavigate("voice_call") }
                )

                // Smart Reading (OCR + TTS)
                ActionCard(
                    icon = "📖",
                    title = stringResource(R.string.feature_reading),
                    subtitle = stringResource(R.string.feature_reading_sub),
                    onClick = { onNavigate("reading") }
                )

                // Medication Safety & Reminder Companion (AAVISHKAR 2026-2027)
                ActionCard(
                    icon = "💊",
                    title = stringResource(R.string.feature_medsafe),
                    subtitle = stringResource(R.string.feature_medsafe_sub),
                    badgeText = "AAVISHKAR",
                    onClick = { onNavigate("medication") }
                )

                // Surroundings (Scene description)
                ActionCard(
                    icon = "📷",
                    title = stringResource(R.string.feature_surroundings),
                    subtitle = stringResource(R.string.feature_surroundings_sub),
                    onClick = { onNavigate("surroundings") }
                )

                // Smart Object Finder
                ActionCard(
                    icon = "🔍",
                    title = stringResource(R.string.feature_finder),
                    subtitle = stringResource(R.string.feature_finder_sub),
                    onClick = { onNavigate("finder") }
                )

                // Currency Reader
                ActionCard(
                    icon = "💵",
                    title = stringResource(R.string.feature_currency),
                    subtitle = stringResource(R.string.feature_currency_sub),
                    onClick = { onNavigate("currency") }
                )

                // Public Transport Assistant
                ActionCard(
                    icon = "🚍",
                    title = stringResource(R.string.feature_transport),
                    subtitle = stringResource(R.string.feature_transport_sub),
                    onClick = { onNavigate("transport") }
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 3. Section: Human Assistance & Safety
            SectionHeader(
                title = stringResource(R.string.home_section_human_assistance),
                icon = "🛡️",
                accentBadgeColor = EmergencyDim
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Volunteer Help (Live Sighted Volunteer)
                ActionCard(
                    icon = "🤝",
                    title = stringResource(R.string.feature_volunteer),
                    subtitle = stringResource(R.string.feature_volunteer_sub),
                    badgeText = stringResource(R.string.action_badge_live_volunteer),
                    onClick = { onNavigate("volunteer") }
                )

                // Emergency SOS (Instant alert + WhatsApp GPS dispatch)
                ActionCard(
                    icon = "🚨",
                    title = stringResource(R.string.feature_sos),
                    subtitle = stringResource(R.string.feature_sos_sub),
                    isEmergency = true,
                    onClick = { onNavigate("sos") }
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 4. Section: Everyday Companion
            SectionHeader(
                title = stringResource(R.string.home_section_everyday),
                icon = "🌟",
                accentBadgeColor = AccentDim
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Phone Assistant (Calling assistant & contacts)
                ActionCard(
                    icon = "📞",
                    title = stringResource(R.string.feature_calling_title),
                    subtitle = stringResource(R.string.feature_calling_sub),
                    onClick = { onNavigate("calling") }
                )

                // Daily News
                ActionCard(
                    icon = "📰",
                    title = stringResource(R.string.feature_news_title),
                    subtitle = stringResource(R.string.feature_news_sub),
                    onClick = { onNavigate("news") }
                )

                // Entertainment Hub (Live Radio, Stories, Audio Games & Daily Streak)
                ActionCard(
                    icon = "🎧",
                    title = stringResource(R.string.feature_entertainment),
                    subtitle = stringResource(R.string.feature_entertainment_sub),
                    badgeText = stringResource(R.string.action_badge_radio_games),
                    onClick = { onNavigate("entertainment") }
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 5. Section: Location & Utilities
            SectionHeader(
                title = stringResource(R.string.home_section_utilities),
                icon = "📍",
                accentBadgeColor = AccentDim
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Where Am I?
                ActionCard(
                    icon = "📍",
                    title = stringResource(R.string.feature_location),
                    subtitle = stringResource(R.string.feature_location_sub),
                    onClick = { onNavigate("location") }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Confirmation Logout Dialog
    if (showLogoutDialog) {
        ConfirmLogoutDialog(
            onConfirm = {
                showLogoutDialog = false
                voiceManager.stopVoice()
                sessionManager.clearUser()
                onLogout()
            },
            onDismiss = { showLogoutDialog = false }
        )
    }
}
