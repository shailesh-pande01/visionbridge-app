package com.example.visionbridge.ui.screens.medication

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.visionbridge.R
import com.example.visionbridge.data.SessionManager
import com.example.visionbridge.ui.theme.*
import com.example.visionbridge.utils.TextToSpeechManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationSafetyScreen(
    onBack: () -> Unit,
    onVolunteerHelp: (requestType: String, description: String) -> Unit,
    viewModel: MedicationSafetyViewModel = viewModel()
) {
    val context = LocalContext.current
    val currentSubScreen by viewModel.currentSubScreen.collectAsStateWithLifecycle()
    val sessionManager = remember { SessionManager.getInstance(context) }
    val currentLanguage by sessionManager.language.collectAsStateWithLifecycle()

    val tts = remember { TextToSpeechManager(context) }
    val isSpeaking by tts.isSpeaking.collectAsStateWithLifecycle()

    LaunchedEffect(currentLanguage) {
        tts.setLanguage(currentLanguage)
    }

    BackHandler {
        tts.stop()
        if (currentSubScreen != MedSafeSubScreen.HUB) {
            viewModel.navigateTo(MedSafeSubScreen.HUB)
        } else {
            onBack()
        }
    }

    when (currentSubScreen) {
        MedSafeSubScreen.HUB -> {
            MedicationHubView(
                onBack = {
                    tts.stop()
                    onBack()
                },
                onNavigate = { subScreen ->
                    tts.stop()
                    viewModel.navigateTo(subScreen)
                },
                onVolunteerHelp = {
                    tts.stop()
                    onVolunteerHelp("MEDICATION", "Medication verification assistance requested from Medication Safety Hub")
                },
                tts = tts,
                isSpeaking = isSpeaking
            )
        }
        MedSafeSubScreen.SCAN -> {
            MedicationScanScreen(
                onBack = { viewModel.navigateTo(MedSafeSubScreen.HUB) },
                onVolunteerHelp = onVolunteerHelp,
                viewModel = viewModel
            )
        }
        MedSafeSubScreen.MY_MEDICINES -> {
            MyMedicinesScreen(
                onBack = { viewModel.navigateTo(MedSafeSubScreen.HUB) },
                onNavigateToScan = { viewModel.navigateTo(MedSafeSubScreen.SCAN) },
                viewModel = viewModel
            )
        }
        MedSafeSubScreen.TODAYS_REMINDERS -> {
            TodayRemindersScreen(
                onBack = { viewModel.navigateTo(MedSafeSubScreen.HUB) },
                viewModel = viewModel
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicationHubView(
    onBack: () -> Unit,
    onNavigate: (MedSafeSubScreen) -> Unit,
    onVolunteerHelp: () -> Unit,
    tts: TextToSpeechManager,
    isSpeaking: Boolean
) {
    val hubOverview = "Medication Safety Hub. Choose an option: Scan Medicine, My Medicines, Today's Reminders, or Connect to Human Helper."

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.medsafe_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (isSpeaking) tts.stop() else tts.speak(hubOverview)
                        }
                    ) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = if (isSpeaking) "Stop speech" else "Hear overview",
                            tint = if (isSpeaking) Emergency else Accent
                        )
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Accessible Medical Safety Disclaimer Banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = BgCard,
                border = androidx.compose.foundation.BorderStroke(1.dp, Border)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(text = "🛡️", fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "ASSISTIVE PACKAGING READER",
                            color = Accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.medsafe_safety_disclaimer),
                            color = TextMuted,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Text(
                text = "Choose a Feature",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .semantics { heading() }
            )

            // 1. Scan Medicine Packaging Card
            MedSafeHubCard(
                icon = "💊",
                title = stringResource(R.string.medsafe_scan_button),
                subtitle = "Point camera at a medicine box, strip, or bottle to identify name, strength, expiry & printed directions.",
                badge = "CAMERA OCR",
                badgeColor = AccentDim,
                badgeTextColor = Accent,
                onClick = { onNavigate(MedSafeSubScreen.SCAN) }
            )

            // 2. My Medicines Card
            MedSafeHubCard(
                icon = "📚",
                title = stringResource(R.string.medsafe_cabinet_title),
                subtitle = "View and search your saved medications, active ingredients, and verified packaging information.",
                badge = "OFFLINE CABINET",
                badgeColor = BgSecondary,
                badgeTextColor = TextMuted,
                onClick = { onNavigate(MedSafeSubScreen.MY_MEDICINES) }
            )

            // 3. Today's Reminders Card
            MedSafeHubCard(
                icon = "⏰",
                title = stringResource(R.string.medsafe_reminders_title),
                subtitle = "Review scheduled daily medication alarms, test notification sounds, and manage reminders.",
                badge = "ALARM ALERTS",
                badgeColor = SuccessDim,
                badgeTextColor = Success,
                onClick = { onNavigate(MedSafeSubScreen.TODAYS_REMINDERS) }
            )

            // 4. Human Volunteer Verification Card
            MedSafeHubCard(
                icon = "🤝",
                title = stringResource(R.string.medsafe_volunteer_verify),
                subtitle = "Connect via live video with a sighted volunteer to verify unclear, curved, or damaged packaging.",
                badge = "HUMAN HELPER",
                badgeColor = WarningDim,
                badgeTextColor = Warning,
                onClick = onVolunteerHelp
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MedSafeHubCard(
    icon: String,
    title: String,
    subtitle: String,
    badge: String,
    badgeColor: androidx.compose.ui.graphics.Color,
    badgeTextColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "$title. $subtitle. Badge: $badge."
            },
        shape = RoundedCornerShape(16.dp),
        color = BgCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Large Touch Target Icon
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgSecondary),
                contentAlignment = Alignment.Center
            ) {
                Text(text = icon, fontSize = 26.sp)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeColor
                    ) {
                        Text(
                            text = badge,
                            color = badgeTextColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
