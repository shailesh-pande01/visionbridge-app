package com.example.visionbridge.ui.screens.entertainment

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.example.visionbridge.R
import com.example.visionbridge.audio.MediaPlaybackState
import com.example.visionbridge.data.RadioStation
import com.example.visionbridge.ui.theme.*
import com.example.visionbridge.voice.ScreenActionRegistry
import com.example.visionbridge.voice.VoiceManager

private val LANGUAGE_FILTERS = listOf(
    Pair("local", "📍 Local / Nearby"),
    Pair("marathi", "🚩 Marathi"),
    Pair("hindi", "📻 Hindi"),
    Pair("english", "🌐 English")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioScreen(
    onBack: () -> Unit,
    viewModel: RadioViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val voiceManager = remember { VoiceManager.getInstance(context) }

    // Register screen actions for voice assistant
    DisposableEffect(Unit) {
        ScreenActionRegistry.registerScreen(
            screenId = "radio",
            onCancel = { viewModel.stopRadio() }
        )
        onDispose {
            ScreenActionRegistry.unregisterScreen("radio")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "📻 Live Radio",
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(BgPrimary)
        ) {
            LiveRadioContent(
                uiState = uiState,
                onTogglePlayPause = { viewModel.togglePlayPause() },
                onNextStation = { viewModel.nextStation() },
                onPrevStation = { viewModel.prevStation() },
                onStopRadio = { viewModel.stopRadio() },
                onSelectFilter = { viewModel.loadLocalStations(it) },
                onSelectStation = { viewModel.playStation(it) },
                onSearch = { viewModel.searchStations(it) },
                onToggleDrawer = { viewModel.toggleDrawer() },
                onHoldToSpeak = { voiceManager.activateVoice() }
            )
        }
    }
}

@Composable
private fun LiveRadioContent(
    uiState: RadioUiState,
    onTogglePlayPause: () -> Unit,
    onNextStation: () -> Unit,
    onPrevStation: () -> Unit,
    onStopRadio: () -> Unit,
    onSelectFilter: (String) -> Unit,
    onSelectStation: (RadioStation) -> Unit,
    onSearch: (String) -> Unit,
    onToggleDrawer: () -> Unit,
    onHoldToSpeak: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Scoped Gesture Zone & Hero Now Playing Card
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
                                onNextStation()
                            } else if (totalDrag < -80f) {
                                onPrevStation()
                            }
                            totalDrag = 0f
                        }
                    )
                }
                .semantics {
                    val statusText = when (uiState.playbackState) {
                        MediaPlaybackState.PLAYING -> "Live and playing"
                        MediaPlaybackState.CONNECTING -> "Connecting to stream"
                        MediaPlaybackState.RECONNECTING -> "Reconnecting to stream"
                        MediaPlaybackState.PAUSED -> "Paused"
                        MediaPlaybackState.ERROR -> "Error"
                        else -> "Stopped"
                    }
                    val stName = uiState.currentStation?.name ?: "No station selected"
                    contentDescription = "$stName. Status: $statusText. Double tap to play or pause. Swipe right for next station. Hold for voice."
                }
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Live Status Badge
                val (badgeBg, badgeFg, badgeText) = when (uiState.playbackState) {
                    MediaPlaybackState.PLAYING -> Triple(SuccessDim, Success, "● LIVE")
                    MediaPlaybackState.CONNECTING -> Triple(AccentDim, Accent, "CONNECTING…")
                    MediaPlaybackState.RECONNECTING -> Triple(Color(0x22F59E0B), Color(0xFFF59E0B), "RECONNECTING…")
                    MediaPlaybackState.PAUSED -> Triple(BgSecondary, TextMuted, "PAUSED")
                    MediaPlaybackState.ERROR -> Triple(EmergencyDim, Emergency, "STREAM ERROR")
                    else -> Triple(BgSecondary, TextMuted, "IDLE")
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = badgeBg,
                    border = BorderStroke(1.dp, badgeFg.copy(alpha = 0.4f)),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = badgeFg,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                Text(
                    text = "NOW PLAYING",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Station Title
                Text(
                    text = uiState.currentStation?.name ?: "VisionBridge Live Radio",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Station Location / Meta
                val locText = uiState.currentStation?.let {
                    val parts = listOf(it.city, it.state, it.country).filter { p -> p.isNotBlank() }
                    if (parts.isNotEmpty()) parts.joinToString(", ") else it.country
                } ?: "Location-based discovery"

                Text(
                    text = locText,
                    color = Accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )

                if (uiState.errorMessage.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = uiState.errorMessage,
                        color = Emergency,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Main Hero Playback Controls (Min 56dp touch targets)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous Station Button
                    FilledIconButton(
                        onClick = onPrevStation,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = BgSecondary,
                            contentColor = TextPrimary
                        ),
                        modifier = Modifier
                            .size(56.dp)
                            .semantics { contentDescription = "Previous radio station" }
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
                                contentDescription = if (uiState.isPlaying) "Pause radio" else "Play radio"
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (uiState.isLoading || uiState.isReconnecting) {
                                CircularProgressIndicator(
                                    color = BgPrimary,
                                    modifier = Modifier.size(36.dp),
                                    strokeWidth = 3.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = BgPrimary,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                    }

                    // Next Station Button
                    FilledIconButton(
                        onClick = onNextStation,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = BgSecondary,
                            contentColor = TextPrimary
                        ),
                        modifier = Modifier
                            .size(56.dp)
                            .semantics { contentDescription = "Next radio station" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Stop Radio Button
                OutlinedButton(
                    onClick = onStopRadio,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Emergency
                    ),
                    border = BorderStroke(1.dp, Emergency.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(48.dp)
                        .semantics { contentDescription = "Stop radio playback" }
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Stop Radio", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Station Filters Header
        Text(
            text = "Station Filters",
            style = MaterialTheme.typography.titleMedium,
            color = TextMuted,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .semantics { heading() }
        )

        // Filter Chips Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(LANGUAGE_FILTERS) { (filterId, label) ->
                FilterChip(
                    selected = uiState.selectedFilter == filterId,
                    onClick = { onSelectFilter(filterId) },
                    label = { Text(label, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Accent,
                        selectedLabelColor = BgPrimary,
                        containerColor = BgCard,
                        labelColor = TextPrimary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = Border,
                        selectedBorderColor = Accent,
                        enabled = true,
                        selected = uiState.selectedFilter == filterId
                    ),
                    modifier = Modifier.height(44.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Station Drawer Toggle
        Button(
            onClick = onToggleDrawer,
            colors = ButtonDefaults.buttonColors(
                containerColor = BgSecondary,
                contentColor = TextPrimary
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .semantics {
                    contentDescription = if (uiState.drawerOpen) "Close station list" else "Open station list, ${uiState.stations.size} stations available"
                }
        ) {
            Icon(
                imageVector = if (uiState.drawerOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Browse Stations (${uiState.stations.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        // Stations List (Expandable)
        AnimatedVisibility(visible = uiState.drawerOpen) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Accent)
                    }
                } else if (uiState.stations.isEmpty()) {
                    Text(
                        text = "No stations found for this filter.",
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                } else {
                    uiState.stations.forEachIndexed { index, station ->
                        val isSelected = uiState.currentStation?.id == station.id
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) AccentDim else BgCard,
                            border = BorderStroke(1.dp, if (isSelected) Accent else Border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onSelectStation(station) }
                                .semantics {
                                    contentDescription = "${station.name}, ${station.city}, ${station.language}. Tap to play."
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isSelected && uiState.isPlaying) "▶" else "${index + 1}",
                                    color = if (isSelected) Accent else TextMuted,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    modifier = Modifier.width(36.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = station.name,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val sub = listOf(station.city, station.language.uppercase()).filter { it.isNotBlank() }.joinToString(" • ")
                                    if (sub.isNotBlank()) {
                                        Text(
                                            text = sub,
                                            color = TextMuted,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Text("PLAYING", color = Accent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
