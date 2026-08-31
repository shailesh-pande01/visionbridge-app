package com.example.visionbridge.ui.screens.entertainment

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.graphics.Color
import com.example.visionbridge.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    onBack: () -> Unit,
    viewModel: ProgressViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "⭐ My Progress",
                        fontWeight = FontWeight.Bold,
                        color = Accent,
                        fontSize = 22.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics { contentDescription = "Go back to Entertainment Hub" }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = TextPrimary
                        )
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
                .padding(16.dp)
        ) {
            // Stats Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // XP Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = BgCard,
                    border = BorderStroke(1.dp, Border),
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = "${uiState.progress.xp} Total XP" }
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "⚡", fontSize = 28.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${uiState.progress.xp}",
                            color = Accent,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(text = "Total XP", color = TextMuted, fontSize = 12.sp)
                    }
                }

                // Streak Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = BgCard,
                    border = BorderStroke(1.dp, Border),
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = "${uiState.progress.streak} days streak" }
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "🔥", fontSize = 28.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${uiState.progress.streak}",
                            color = Color(0xFFF59E0B),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(text = "Day Streak", color = TextMuted, fontSize = 12.sp)
                    }
                }

                // Correct Answers Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = BgCard,
                    border = BorderStroke(1.dp, Border),
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = "${uiState.progress.correctAnswers} correct answers" }
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "🎯", fontSize = 28.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${uiState.progress.correctAnswers}",
                            color = Success,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(text = "Correct", color = TextMuted, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Hear Stats Aloud Button
            Button(
                onClick = { viewModel.readStatsAloud() },
                colors = ButtonDefaults.buttonColors(containerColor = BgSecondary, contentColor = TextPrimary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .semantics { contentDescription = "Hear my progress read aloud" }
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = Accent)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Hear My Progress", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Achievements Section
            Text(
                text = "🏆 Achievements",
                style = MaterialTheme.typography.titleMedium,
                color = TextMuted,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp).semantics { heading() }
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                uiState.achievements.forEach { ach ->
                    val unlocked = ach.isUnlocked
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (unlocked) BgCard else BgPrimary,
                        border = BorderStroke(1.dp, if (unlocked) Accent else Border.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                val status = if (unlocked) "Unlocked" else "Locked"
                                contentDescription = "${ach.title}: ${ach.desc}. Status: $status"
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (unlocked) ach.icon else "🔒",
                                fontSize = 32.sp,
                                modifier = Modifier.padding(end = 16.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ach.title,
                                    color = if (unlocked) TextPrimary else TextMuted,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = ach.desc,
                                    color = TextMuted,
                                    fontSize = 13.sp
                                )
                            }
                            if (unlocked) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = SuccessDim
                                ) {
                                    Text(
                                        text = "✓ UNLOCKED",
                                        color = Success,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
