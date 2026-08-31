package com.example.visionbridge.ui.screens.entertainment

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.visionbridge.ui.theme.*
import com.example.visionbridge.voice.ScreenActionRegistry
import com.example.visionbridge.voice.VoiceManager

private val GAME_MODES = listOf(
    Pair("daily", "🏆 Daily Challenge"),
    Pair("trivia", "❓ Trivia"),
    Pair("riddles", "🧩 Riddles"),
    Pair("twenty_questions", "💭 20 Questions"),
    Pair("memory", "🧠 Memory Game")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesScreen(
    onBack: () -> Unit,
    viewModel: GamesViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val voiceManager = remember { VoiceManager.getInstance(context) }
    val scrollState = rememberScrollState()

    // Register screen actions for voice assistant
    DisposableEffect(Unit) {
        ScreenActionRegistry.registerScreen(
            screenId = "games",
            onSubmit = { viewModel.submitAnswer() }
        )
        onDispose {
            ScreenActionRegistry.unregisterScreen("games")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "🎮 Audio Games & Daily",
                        fontWeight = FontWeight.Bold,
                        color = Accent,
                        fontSize = 20.sp
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
            // Mode Selector Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(GAME_MODES) { (modeId, label) ->
                    FilterChip(
                        selected = uiState.selectedMode == modeId,
                        onClick = { viewModel.setMode(modeId) },
                        label = { Text(label, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Accent,
                            selectedLabelColor = BgPrimary,
                            containerColor = BgCard,
                            labelColor = TextPrimary
                        ),
                        modifier = Modifier.height(44.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Game Hero Container Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = BgCard,
                border = BorderStroke(2.dp, Border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    when (uiState.selectedMode) {
                        "daily" -> {
                            val daily = uiState.dailyChallenge
                            Text(
                                text = "🏆 Today's Daily Challenge (+25 XP)",
                                style = MaterialTheme.typography.titleMedium,
                                color = Accent,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            if (daily != null) {
                                Text(
                                    text = daily.challenge.question,
                                    color = TextPrimary,
                                    fontSize = 18.sp,
                                    lineHeight = 26.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        "trivia" -> {
                            val trivia = uiState.triviaQuestion
                            Text(
                                text = "❓ Trivia Question: ${trivia?.category ?: "General"}",
                                style = MaterialTheme.typography.titleMedium,
                                color = Accent,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            if (trivia != null) {
                                Text(
                                    text = trivia.question,
                                    color = TextPrimary,
                                    fontSize = 18.sp,
                                    lineHeight = 26.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (trivia.options.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        trivia.options.forEach { opt ->
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = BgSecondary,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { viewModel.submitAnswer(opt) }
                                                    .semantics { contentDescription = "Option: $opt. Tap to select." }
                                            ) {
                                                Text(
                                                    text = "• $opt",
                                                    color = TextPrimary,
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = 15.sp,
                                                    modifier = Modifier.padding(10.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        "riddles" -> {
                            val riddle = uiState.riddle
                            Text(
                                text = "🧩 Riddle Challenge",
                                style = MaterialTheme.typography.titleMedium,
                                color = Accent,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            if (riddle != null) {
                                Text(
                                    text = riddle.riddle,
                                    color = TextPrimary,
                                    fontSize = 18.sp,
                                    lineHeight = 26.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        "twenty_questions" -> {
                            Text(
                                text = "💭 20 Questions (${uiState.twentyQCount}/20 Asked)",
                                style = MaterialTheme.typography.titleMedium,
                                color = Accent,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "I'm thinking of a secret everyday object. Ask questions or guess the object directly!",
                                color = TextMuted,
                                fontSize = 14.sp
                            )
                            if (uiState.twentyQHistory.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    uiState.twentyQHistory.takeLast(4).forEach { (q, a) ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = BgSecondary,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text("Q: $q", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                                Text("A: $a", color = Accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        "memory" -> {
                            val memory = uiState.memoryChallenge
                            Text(
                                text = "🧠 Memory Challenge (Level ${memory?.level ?: 1})",
                                style = MaterialTheme.typography.titleMedium,
                                color = Accent,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Listen to the word sequence and repeat them back in order:",
                                color = TextMuted,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = memory?.prompt ?: "",
                                color = Accent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action buttons: Read aloud & Clue
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.repeatPrompt() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent),
                            border = BorderStroke(1.dp, Accent),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Hear Again", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        if (uiState.selectedMode == "riddles") {
                            OutlinedButton(
                                onClick = { viewModel.giveClue() },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF59E0B)),
                                border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Text("💡 Need Clue", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Feedback Banner if answered
            if (uiState.feedback.isNotBlank()) {
                val isWin = uiState.isCorrect == true
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isWin) SuccessDim else EmergencyDim,
                    border = BorderStroke(1.dp, if (isWin) Success else Emergency),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isWin) "🎉" else "❌",
                            fontSize = 28.sp,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Text(
                            text = uiState.feedback,
                            color = if (isWin) Success else Emergency,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Answer Input Box
            Text(
                text = "Your Answer",
                style = MaterialTheme.typography.titleMedium,
                color = TextMuted,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp).semantics { heading() }
            )

            OutlinedTextField(
                value = uiState.userAnswerText,
                onValueChange = { viewModel.setUserAnswerText(it) },
                placeholder = { Text("Type your answer here…", color = TextMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Border,
                    focusedContainerColor = BgCard,
                    unfocusedContainerColor = BgCard
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Submit Button
            Button(
                onClick = { viewModel.submitAnswer() },
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = BgPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .semantics { contentDescription = "Submit answer" }
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(color = BgPrimary, modifier = Modifier.size(24.dp))
                } else {
                    Text("Submit Answer", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Voice Answer Button & Next Round Button
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { voiceManager.activateVoice() },
                    colors = ButtonDefaults.buttonColors(containerColor = BgSecondary, contentColor = TextPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(imageVector = Icons.Default.Mic, contentDescription = null, tint = Accent)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Speak Answer", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { viewModel.loadGame() },
                    colors = ButtonDefaults.buttonColors(containerColor = BgSecondary, contentColor = TextPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("New Round", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
