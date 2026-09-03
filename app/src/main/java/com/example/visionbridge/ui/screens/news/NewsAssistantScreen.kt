package com.example.visionbridge.ui.screens.news

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.visionbridge.R
import com.example.visionbridge.data.NewsArticle
import com.example.visionbridge.data.NewsPlaybackState
import com.example.visionbridge.data.SessionManager
import com.example.visionbridge.ui.components.AppTopBar
import com.example.visionbridge.ui.theme.*
import com.example.visionbridge.utils.TextToSpeechManager
import com.example.visionbridge.voice.ScreenActionRegistry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsAssistantScreen(
    onBack: () -> Unit,
    viewModel: NewsViewModel = viewModel()
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager.getInstance(context) }
    val currentLanguage by sessionManager.language.collectAsStateWithLifecycle()

    val briefing by viewModel.briefing.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val activeStoryIndex by viewModel.activeStoryIndex.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val speechRate by viewModel.speechRate.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    val tts = remember { TextToSpeechManager(context) }

    LaunchedEffect(currentLanguage) {
        tts.setLanguage(currentLanguage)
        viewModel.setTts(tts)
    }

    DisposableEffect(Unit) {
        viewModel.setTts(tts)
        ScreenActionRegistry.registerScreen(
            screenId = "news",
            onCancel = {
                viewModel.stopNews()
                onBack()
            }
        )
        onDispose {
            ScreenActionRegistry.unregisterScreen("news")
            viewModel.stopNews()
            tts.shutdown()
        }
    }

    val categories = listOf(
        "general" to stringResource(R.string.news_cat_general),
        "tech" to stringResource(R.string.news_cat_tech),
        "business" to stringResource(R.string.news_cat_business),
        "science" to stringResource(R.string.news_cat_science),
        "sports" to stringResource(R.string.news_cat_sports),
        "entertainment" to stringResource(R.string.news_cat_entertainment)
    )

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.feature_news_title),
                subtitle = stringResource(R.string.news_subtitle),
                onBack = {
                    viewModel.stopNews()
                    onBack()
                },
                backContentDescription = stringResource(R.string.common_back),
                actions = {
                    TextButton(
                        onClick = { viewModel.fetchNewsBriefing(selectedCategory, forceRefresh = true) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.news_btn_refresh), color = Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
        },
        containerColor = BgPrimary
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Category Selector Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { (catKey, catLabel) ->
                    val isSelected = selectedCategory == catKey
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.fetchNewsBriefing(catKey) },
                        label = {
                            Text(
                                text = catLabel,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
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

            // Playback Control Panel
            Surface(
                color = BgCard,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val articles = briefing?.articles ?: emptyList()
                    val currentArticle = if (activeStoryIndex in articles.indices) articles[activeStoryIndex] else null

                    Text(
                        text = if (articles.isNotEmpty()) stringResource(R.string.news_story_counter, activeStoryIndex + 1, articles.size) else stringResource(R.string.news_briefing_header),
                        color = Accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() }
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = currentArticle?.title ?: stringResource(R.string.news_loading_stories),
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous
                        IconButton(
                            onClick = { viewModel.prevStory() },
                            enabled = activeStoryIndex > 0,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Text("⏮️", fontSize = 26.sp)
                        }

                        // Play / Pause
                        Button(
                            onClick = {
                                if (playbackState == NewsPlaybackState.SPEAKING) {
                                    viewModel.pauseNews()
                                } else {
                                    viewModel.resumeNews()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(
                                text = if (playbackState == NewsPlaybackState.SPEAKING) stringResource(R.string.news_btn_pause) else stringResource(R.string.news_btn_play),
                                color = BgPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }

                        // Next
                        IconButton(
                            onClick = { viewModel.nextStory() },
                            enabled = activeStoryIndex < (articles.size - 1),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Text("⏭️", fontSize = 26.sp)
                        }

                        // Repeat
                        IconButton(
                            onClick = { viewModel.repeatStory() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Text("🔁", fontSize = 24.sp)
                        }

                        // Speed Toggle
                        TextButton(
                            onClick = {
                                val nextSpeed = when (speechRate) {
                                    1.0f -> 1.2f
                                    1.2f -> 0.8f
                                    else -> 1.0f
                                }
                                viewModel.setSpeed(nextSpeed)
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("${speechRate}x", color = Accent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }

            // State feedback
            when (playbackState) {
                NewsPlaybackState.LOADING -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Accent)
                    }
                }
                NewsPlaybackState.ERROR -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (errorMessage.isNotBlank()) errorMessage else stringResource(R.string.news_err_could_not_load),
                            color = Emergency,
                            fontSize = 18.sp
                        )
                    }
                }
                else -> {
                    // Articles List
                    val articles = briefing?.articles ?: emptyList()
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        itemsIndexed(articles, key = { _, item -> item.id }) { index, article ->
                            val isActive = index == activeStoryIndex
                            Surface(
                                color = if (isActive) BgSecondary else BgCard,
                                shape = RoundedCornerShape(12.dp),
                                border = if (isActive) androidx.compose.foundation.BorderStroke(2.dp, Accent) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.selectStory(index) }
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${index + 1}. ${article.category.uppercase()}",
                                            color = Accent,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = article.source,
                                            color = TextMuted,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = article.title,
                                        color = TextPrimary,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (article.description.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = article.description,
                                            color = TextMuted,
                                            fontSize = 14.sp,
                                            maxLines = 3
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
}
