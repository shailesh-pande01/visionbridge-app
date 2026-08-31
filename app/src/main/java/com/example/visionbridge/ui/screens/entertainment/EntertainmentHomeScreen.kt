package com.example.visionbridge.ui.screens.entertainment

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.visionbridge.data.EntertainmentRepository
import com.example.visionbridge.data.StoryProgressData
import com.example.visionbridge.ui.components.ActionCard
import com.example.visionbridge.ui.theme.*
import com.example.visionbridge.voice.ScreenActionRegistry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntertainmentHomeScreen(
    onNavigate: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { EntertainmentRepository.getInstance(context) }
    val scrollState = rememberScrollState()

    var savedStoryProgress by remember { mutableStateOf<StoryProgressData?>(null) }

    LaunchedEffect(Unit) {
        savedStoryProgress = repo.getStoryProgress()
    }

    DisposableEffect(Unit) {
        ScreenActionRegistry.registerScreen(screenId = "entertainment")
        onDispose {
            ScreenActionRegistry.unregisterScreen("entertainment")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "🎧 Entertainment Hub",
                        fontWeight = FontWeight.Bold,
                        color = Accent,
                        fontSize = 22.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics { contentDescription = "Go back to main home" }
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
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Voice hint
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = BgCard,
                border = BorderStroke(1.dp, Border),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
                    .semantics {
                        contentDescription = "You can say: Vision, play local radio. Vision, open stories. Vision, start today's challenge."
                    }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🎙️", fontSize = 28.sp, modifier = Modifier.padding(end = 12.dp))
                    Column {
                        Text(
                            text = "Voice-First Entertainment",
                            color = Accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Say “Vision, play local radio” or “Vision, start daily challenge”.",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Continue listening banner if present
            if (savedStoryProgress != null && savedStoryProgress?.storyId?.isNotBlank() == true) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = BgSecondary,
                    border = BorderStroke(2.dp, Accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                        .clickable { onNavigate("stories") }
                        .semantics {
                            contentDescription = "Resume listening to ${savedStoryProgress?.storyTitle}. Tap to open."
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🎧", fontSize = 32.sp, modifier = Modifier.padding(end = 14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("CONTINUE LISTENING", color = Accent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text(
                                text = savedStoryProgress?.storyTitle ?: "",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Text("▶ Resume", color = Accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }

            Text(
                text = "Entertainment Channels",
                style = MaterialTheme.typography.titleMedium,
                color = TextMuted,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
                    .semantics { heading() }
            )

            // 1. Radio
            ActionCard(
                icon = "📻",
                title = "Live Radio",
                subtitle = "Tune in to live local & regional radio stations",
                onClick = { onNavigate("radio") }
            )
            Spacer(modifier = Modifier.height(14.dp))

            // 2. Stories
            ActionCard(
                icon = "📖",
                title = "Stories & Audiobooks",
                subtitle = "Public-domain classics in English, Hindi & Marathi",
                onClick = { onNavigate("stories") }
            )
            Spacer(modifier = Modifier.height(14.dp))

            // 3. Audio Games & Daily Challenge
            ActionCard(
                icon = "🎮",
                title = "Games & Daily Challenge",
                subtitle = "Trivia, Riddles, 20 Questions & Daily Streak",
                onClick = { onNavigate("games") }
            )
            Spacer(modifier = Modifier.height(14.dp))

            // 4. My Progress
            ActionCard(
                icon = "⭐",
                title = "My Progress & Streak",
                subtitle = "Track your XP, daily streak & unlocked achievements",
                onClick = { onNavigate("progress") }
            )
        }
    }
}
