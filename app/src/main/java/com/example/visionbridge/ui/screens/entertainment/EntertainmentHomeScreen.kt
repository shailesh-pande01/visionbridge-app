package com.example.visionbridge.ui.screens.entertainment

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.visionbridge.ui.components.AppTopBar
import com.example.visionbridge.ui.components.SectionHeader
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
            AppTopBar(
                title = androidx.compose.ui.res.stringResource(com.example.visionbridge.R.string.entertainment_title),
                subtitle = androidx.compose.ui.res.stringResource(com.example.visionbridge.R.string.entertainment_subtitle),
                onBack = onBack,
                backContentDescription = androidx.compose.ui.res.stringResource(com.example.visionbridge.R.string.common_back)
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
            // Voice hint banner
            val voiceBannerTalkback = androidx.compose.ui.res.stringResource(com.example.visionbridge.R.string.entertainment_voice_banner_talkback)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = BgCard,
                border = BorderStroke(1.dp, Border),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
                    .semantics {
                        contentDescription = voiceBannerTalkback
                    }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🎙️", fontSize = 28.sp, modifier = Modifier.padding(end = 12.dp))
                    Column {
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.visionbridge.R.string.entertainment_voice_banner_title),
                            color = Accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.visionbridge.R.string.entertainment_voice_banner_desc),
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Continue listening banner if progress is present
            if (savedStoryProgress != null && savedStoryProgress?.storyId?.isNotBlank() == true) {
                val resumeTalkback = androidx.compose.ui.res.stringResource(
                    com.example.visionbridge.R.string.entertainment_resume_talkback,
                    savedStoryProgress?.storyTitle ?: ""
                )
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = BgSecondary,
                    border = BorderStroke(2.dp, Accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                        .clickable { onNavigate("stories") }
                        .semantics {
                            contentDescription = resumeTalkback
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🎧", fontSize = 32.sp, modifier = Modifier.padding(end = 14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                androidx.compose.ui.res.stringResource(com.example.visionbridge.R.string.entertainment_continue_listening),
                                color = Accent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Text(
                                text = savedStoryProgress?.storyTitle ?: "",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Text(
                            androidx.compose.ui.res.stringResource(com.example.visionbridge.R.string.entertainment_btn_resume),
                            color = Accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            SectionHeader(
                title = androidx.compose.ui.res.stringResource(com.example.visionbridge.R.string.entertainment_channels_header),
                icon = "📻"
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 1. Radio
                ActionCard(
                    icon = "📻",
                    title = androidx.compose.ui.res.stringResource(com.example.visionbridge.R.string.entertainment_card_radio_title),
                    subtitle = androidx.compose.ui.res.stringResource(com.example.visionbridge.R.string.entertainment_card_radio_sub),
                    badgeText = androidx.compose.ui.res.stringResource(com.example.visionbridge.R.string.action_badge_live_stream),
                    onClick = { onNavigate("radio") }
                )

                // 2. Stories
                ActionCard(
                    icon = "📖",
                    title = androidx.compose.ui.res.stringResource(com.example.visionbridge.R.string.entertainment_card_stories_title),
                    subtitle = androidx.compose.ui.res.stringResource(com.example.visionbridge.R.string.entertainment_card_stories_sub),
                    badgeText = androidx.compose.ui.res.stringResource(com.example.visionbridge.R.string.action_badge_audiobooks),
                    onClick = { onNavigate("stories") }
                )

                // 3. Audio Games & Daily Challenge
                ActionCard(
                    icon = "🎮",
                    title = androidx.compose.ui.res.stringResource(com.example.visionbridge.R.string.entertainment_card_games_title),
                    subtitle = androidx.compose.ui.res.stringResource(com.example.visionbridge.R.string.entertainment_card_games_sub),
                    badgeText = androidx.compose.ui.res.stringResource(com.example.visionbridge.R.string.action_badge_xp_25),
                    onClick = { onNavigate("games") }
                )

                // 4. My Progress
                ActionCard(
                    icon = "⭐",
                    title = androidx.compose.ui.res.stringResource(com.example.visionbridge.R.string.entertainment_card_progress_title),
                    subtitle = androidx.compose.ui.res.stringResource(com.example.visionbridge.R.string.entertainment_card_progress_sub),
                    onClick = { onNavigate("progress") }
                )
            }
        }
    }
}
