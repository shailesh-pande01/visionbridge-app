package com.example.visionbridge.ui.screens.entertainment

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.visionbridge.data.StoryDetail
import com.example.visionbridge.data.StorySummary
import com.example.visionbridge.ui.theme.*
import com.example.visionbridge.voice.ScreenActionRegistry
import com.example.visionbridge.voice.VoiceManager

private val STORY_LANGUAGES = listOf(
    Pair("all", "🌐 All"),
    Pair("en", "🇬🇧 English"),
    Pair("hi", "🇮🇳 हिंदी"),
    Pair("mr", "🇮🇳 मराठी")
)

private val STORY_GENRES = listOf(
    Pair("all", "📚 All Stories"),
    Pair("popular", "⭐ Popular"),
    Pair("mystery", "🕵️ Mystery"),
    Pair("adventure", "🗺️ Adventure"),
    Pair("fantasy", "✨ Fantasy"),
    Pair("horror", "👻 Horror"),
    Pair("classics", "📖 Classics"),
    Pair("comedy", "😂 Folklore")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryScreen(
    onBack: () -> Unit,
    viewModel: StoryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val voiceManager = remember { VoiceManager.getInstance(context) }

    // Register screen actions for voice assistant
    DisposableEffect(Unit) {
        ScreenActionRegistry.registerScreen(
            screenId = "stories",
            onCancel = { viewModel.stopStory() }
        )
        onDispose {
            ScreenActionRegistry.unregisterScreen("stories")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.activeMode == "player") "📖 Now Playing" else "📖 Stories & Audiobooks",
                        fontWeight = FontWeight.Bold,
                        color = Accent,
                        fontSize = 22.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (uiState.activeMode == "player") {
                                viewModel.switchToLibrary()
                            } else {
                                onBack()
                            }
                        },
                        modifier = Modifier.semantics {
                            contentDescription = if (uiState.activeMode == "player") "Back to story library" else "Go back to Entertainment Hub"
                        }
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(BgPrimary)
        ) {
            if (uiState.activeMode == "player" && uiState.selectedStory != null) {
                StoryPlayerContent(
                    uiState = uiState,
                    story = uiState.selectedStory!!,
                    onTogglePlayPause = { viewModel.togglePlayPause() },
                    onNextChapter = { viewModel.nextChapter() },
                    onPrevChapter = { viewModel.prevChapter() },
                    onRestartStory = { viewModel.restartStory() },
                    onSelectChapter = { chapIdx -> viewModel.playStory(uiState.selectedStory!!.id, chapIdx, 0) },
                    onToggleTranscript = { viewModel.toggleTranscript() },
                    onToggleChapterDrawer = { viewModel.toggleChapterDrawer() },
                    onHoldToSpeak = { voiceManager.activateVoice() }
                )
            } else {
                StoryLibraryContent(
                    uiState = uiState,
                    onSelectLanguage = { viewModel.setLanguageFilter(it) },
                    onSelectGenre = { viewModel.setGenreFilter(it) },
                    onSearch = { viewModel.searchStories(it) },
                    onSelectStory = { story -> viewModel.playStory(story.id, 0, 0) },
                    onContinueStory = { viewModel.continueStory() }
                )
            }
        }
    }
}

@Composable
private fun StoryLibraryContent(
    uiState: StoryUiState,
    onSelectLanguage: (String) -> Unit,
    onSelectGenre: (String) -> Unit,
    onSearch: (String) -> Unit,
    onSelectStory: (StorySummary) -> Unit,
    onContinueStory: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // "Continue Listening" Hero Card if progress exists
        if (uiState.savedProgress != null && uiState.savedProgress.storyId.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = BgCard,
                border = BorderStroke(2.dp, Accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onContinueStory)
                    .semantics {
                        contentDescription = "Continue listening to ${uiState.savedProgress.storyTitle}, Chapter ${uiState.savedProgress.chapterIndex + 1}. Tap to resume."
                    }
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🎧",
                        fontSize = 36.sp,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CONTINUE LISTENING",
                            color = Accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = uiState.savedProgress.storyTitle,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = uiState.savedProgress.chapterTitle.ifBlank { "Chapter ${uiState.savedProgress.chapterIndex + 1}" },
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                    Button(
                        onClick = onContinueStory,
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = BgPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(42.dp)
                    ) {
                        Text("Resume", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // Language Filters
        Text(
            text = "Language",
            style = MaterialTheme.typography.titleMedium,
            color = TextMuted,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp).semantics { heading() }
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(STORY_LANGUAGES) { (langId, label) ->
                FilterChip(
                    selected = uiState.selectedLanguage == langId,
                    onClick = { onSelectLanguage(langId) },
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

        // Genre Filters
        Text(
            text = "Genre",
            style = MaterialTheme.typography.titleMedium,
            color = TextMuted,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp).semantics { heading() }
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(STORY_GENRES) { (genreId, label) ->
                FilterChip(
                    selected = uiState.selectedGenre == genreId,
                    onClick = { onSelectGenre(genreId) },
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

        Spacer(modifier = Modifier.height(20.dp))

        // Stories Catalog List
        Text(
            text = "Stories Library (${uiState.stories.size})",
            style = MaterialTheme.typography.titleMedium,
            color = TextMuted,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp).semantics { heading() }
        )

        if (uiState.isLoadingCatalog) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Accent)
            }
        } else if (uiState.stories.isEmpty()) {
            Text(
                text = "No stories found matching your filter.",
                color = TextMuted,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                uiState.stories.forEach { story ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = BgCard,
                        border = BorderStroke(1.dp, Border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectStory(story) }
                            .semantics {
                                val audioType = if (story.audioAvailable) "Audiobook recording available" else "Narrated via speech"
                                contentDescription = "${story.title} by ${story.author}. ${story.chapterCount} chapters. $audioType. Tap to listen."
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = story.coverTheme.icon,
                                fontSize = 36.sp,
                                modifier = Modifier.padding(end = 16.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = story.title,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "By ${story.author}",
                                    color = TextMuted,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${story.chapterCount} Ch.",
                                        color = Accent,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text("•", color = TextMuted, fontSize = 12.sp)
                                    Text(
                                        text = if (story.audioAvailable) "🎧 Audiobook" else "🗣️ Speech Audio",
                                        color = if (story.audioAvailable) Success else TextMuted,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
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

@Composable
private fun StoryPlayerContent(
    uiState: StoryUiState,
    story: StoryDetail,
    onTogglePlayPause: () -> Unit,
    onNextChapter: () -> Unit,
    onPrevChapter: () -> Unit,
    onRestartStory: () -> Unit,
    onSelectChapter: (Int) -> Unit,
    onToggleTranscript: () -> Unit,
    onToggleChapterDrawer: () -> Unit,
    onHoldToSpeak: () -> Unit
) {
    val scrollState = rememberScrollState()
    val currentChapter = story.chapters.getOrNull(uiState.currentChapterIndex)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hero Player Card with Scoped Gestures
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = BgCard,
            border = BorderStroke(2.dp, if (uiState.isPlaying) Accent else Border),
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { onTogglePlayPause() },
                        onLongPress = { onHoldToSpeak() }
                    )
                }
                .pointerInput(Unit) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                        onDragEnd = {
                            if (totalDrag > 80f) {
                                onNextChapter()
                            } else if (totalDrag < -80f) {
                                onPrevChapter()
                            }
                            totalDrag = 0f
                        }
                    )
                }
                .semantics {
                    val statusText = if (uiState.isPlaying) "Playing" else if (uiState.isPaused) "Paused" else "Stopped"
                    val chapTitle = currentChapter?.title ?: "Chapter ${uiState.currentChapterIndex + 1}"
                    contentDescription = "${story.title}, $chapTitle. $statusText. Double tap to play or pause. Swipe right for next chapter. Hold for voice."
                }
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Audio type badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (uiState.isAudiobook) SuccessDim else AccentDim,
                    border = BorderStroke(1.dp, if (uiState.isAudiobook) Success.copy(alpha = 0.4f) else Accent.copy(alpha = 0.4f)),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Text(
                        text = if (uiState.isAudiobook) "🎧 LIBRIVOX AUDIOBOOK" else "🗣️ TEXT-TO-SPEECH NARRATION",
                        color = if (uiState.isAudiobook) Success else Accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Text(
                    text = story.coverTheme.icon,
                    fontSize = 48.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = story.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "By ${story.author}",
                    color = TextMuted,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Chapter index / title
                Text(
                    text = currentChapter?.title ?: "Chapter ${uiState.currentChapterIndex + 1} of ${story.chapters.size}",
                    color = Accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Hero Playback Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous Chapter Button
                    FilledIconButton(
                        onClick = onPrevChapter,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = BgSecondary,
                            contentColor = TextPrimary
                        ),
                        modifier = Modifier
                            .size(56.dp)
                            .semantics { contentDescription = "Previous chapter" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Main Central Play/Pause Button (72dp)
                    Surface(
                        shape = CircleShape,
                        color = Accent,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onTogglePlayPause)
                            .semantics {
                                contentDescription = if (uiState.isPlaying) "Pause story" else "Play story"
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = BgPrimary,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    // Next Chapter Button
                    FilledIconButton(
                        onClick = onNextChapter,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = BgSecondary,
                            contentColor = TextPrimary
                        ),
                        modifier = Modifier
                            .size(56.dp)
                            .semantics { contentDescription = "Next chapter" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Chapter Drawer & Transcript Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onToggleChapterDrawer,
                colors = ButtonDefaults.buttonColors(containerColor = BgSecondary, contentColor = TextPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .semantics { contentDescription = "Browse chapters, ${story.chapters.size} available" }
            ) {
                Text("Chapters (${story.chapters.size})", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onToggleTranscript,
                colors = ButtonDefaults.buttonColors(containerColor = BgSecondary, contentColor = TextPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .semantics { contentDescription = if (uiState.transcriptOpen) "Hide chapter text" else "Read chapter text" }
            ) {
                Text(if (uiState.transcriptOpen) "Hide Text" else "Read Text", fontWeight = FontWeight.Bold)
            }
        }

        // Chapters List (Expandable)
        AnimatedVisibility(visible = uiState.chapterDrawerOpen) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                story.chapters.forEachIndexed { idx, ch ->
                    val isCurrent = idx == uiState.currentChapterIndex
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isCurrent) AccentDim else BgCard,
                        border = BorderStroke(1.dp, if (isCurrent) Accent else Border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectChapter(idx) }
                            .semantics { contentDescription = "${ch.title}. Tap to play." }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isCurrent && uiState.isPlaying) "▶" else "${idx + 1}",
                                color = if (isCurrent) Accent else TextMuted,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(32.dp)
                            )
                            Text(
                                text = ch.title,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            if (ch.duration.isNotBlank()) {
                                Text(text = ch.duration, color = TextMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Transcript Text (Expandable)
        AnimatedVisibility(visible = uiState.transcriptOpen) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = BgCard,
                border = BorderStroke(1.dp, Border),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = currentChapter?.title ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        color = Accent,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = currentChapter?.text?.ifBlank { currentChapter.summary } ?: "",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        lineHeight = 26.sp
                    )
                }
            }
        }
    }
}
