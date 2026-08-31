package com.example.visionbridge.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.visionbridge.MainActivity
import com.example.visionbridge.R
import com.example.visionbridge.data.RadioStation
import com.example.visionbridge.data.StoryChapter
import com.example.visionbridge.data.StoryDetail
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MediaPlaybackState {
    IDLE,
    CONNECTING,
    PLAYING,
    PAUSED,
    RECONNECTING,
    ERROR
}

enum class MediaContentMode {
    NONE,
    RADIO,
    STORY_AUDIOBOOK
}

@OptIn(UnstableApi::class)
class EntertainmentMediaService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var recoveryJob: Job? = null
    private var isForegroundActive = false

    override fun onCreate() {
        super.onCreate()
        instance = this

        createNotificationChannel()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val exo = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, false) // Managed gracefully to prevent external mic loops
            .setHandleAudioBecomingNoisy(true)
            .build()

        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                updatePlaybackState()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                updatePlaybackState()
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.w(TAG, "ExoPlayer error [code: ${error.errorCodeName}]: ${error.message}")
                handlePlaybackError()
            }
        })

        player = exo

        val sessionIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, exo)
            .setSessionActivity(pendingIntent)
            .build()

        // Observe audio session ducking
        serviceScope.launch {
            EntertainmentAudioSession.isDucked.collect { ducked ->
                player?.volume = if (ducked) 0.25f else 1.0f
            }
        }

        // Register stop callbacks in audio session singleton
        EntertainmentAudioSession.registerRadioCallbacks {
            if (_contentModeFlow.value == MediaContentMode.RADIO) {
                stopPlayback()
            }
        }
        EntertainmentAudioSession.registerStoryCallbacks {
            if (_contentModeFlow.value == MediaContentMode.STORY_AUDIOBOOK) {
                stopPlayback()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_PLAY -> resumePlayback()
            ACTION_PAUSE -> pausePlayback()
            ACTION_STOP -> stopPlayback()
            ACTION_NEXT -> handleNextAction()
            ACTION_PREV -> handlePrevAction()
        }
        return START_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        recoveryJob?.cancel()
        serviceScope.cancel()
        removeForegroundNotification()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        player = null
        instance = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VisionBridge Entertainment",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Media controls for Live Radio and Stories"
                setShowBadge(false)
                setSound(null, null)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // ── Single Authoritative State Machine ─────────────────────────────

    private fun updatePlaybackState() {
        val exo = player ?: return
        val playbackState = exo.playbackState
        val playWhenReady = exo.playWhenReady

        val newState = when (playbackState) {
            Player.STATE_IDLE -> {
                if (_playbackStateFlow.value == MediaPlaybackState.RECONNECTING ||
                    _playbackStateFlow.value == MediaPlaybackState.ERROR
                ) {
                    _playbackStateFlow.value
                } else {
                    MediaPlaybackState.IDLE
                }
            }
            Player.STATE_BUFFERING -> {
                if (_playbackStateFlow.value == MediaPlaybackState.RECONNECTING) {
                    MediaPlaybackState.RECONNECTING
                } else {
                    MediaPlaybackState.CONNECTING
                }
            }
            Player.STATE_READY -> {
                reconnectRetryCount = 0
                failoverCandidateCount = 0
                if (playWhenReady) MediaPlaybackState.PLAYING else MediaPlaybackState.PAUSED
            }
            Player.STATE_ENDED -> {
                if (_contentModeFlow.value == MediaContentMode.STORY_AUDIOBOOK) {
                    onStoryChapterEnded()
                    MediaPlaybackState.IDLE
                } else {
                    MediaPlaybackState.IDLE
                }
            }
            else -> MediaPlaybackState.IDLE
        }

        if (_playbackStateFlow.value != newState) {
            _playbackStateFlow.value = newState
            updateForegroundNotification()
        }
    }

    // ── Playback Controls ─────────────────────────────────────────────

    fun playRadioStation(station: RadioStation) {
        val exo = player ?: return
        recoveryJob?.cancel()
        EntertainmentAudioSession.requestSession(ActiveAudioFeature.RADIO)

        _contentModeFlow.value = MediaContentMode.RADIO
        _currentStationFlow.value = station
        _currentStoryFlow.value = null
        _errorMessageFlow.value = ""
        _playbackStateFlow.value = MediaPlaybackState.CONNECTING

        val metadata = MediaMetadata.Builder()
            .setTitle(station.name)
            .setArtist(if (station.city.isNotBlank()) "${station.city}, ${station.country}" else station.country)
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(station.streamUrl)
            .setMediaMetadata(metadata)
            .build()

        exo.stop()
        exo.clearMediaItems()
        exo.setMediaItem(mediaItem)
        exo.prepare()
        exo.playWhenReady = true

        startOrUpdateForegroundNotification()
    }

    fun playStoryAudiobook(story: StoryDetail, chapterIndex: Int, seekSeconds: Int = 0) {
        val exo = player ?: return
        recoveryJob?.cancel()
        EntertainmentAudioSession.requestSession(ActiveAudioFeature.STORY)

        val chapter = story.chapters.getOrNull(chapterIndex) ?: return
        if (!chapter.isAudiobook) return

        _contentModeFlow.value = MediaContentMode.STORY_AUDIOBOOK
        _currentStoryFlow.value = story
        _currentChapterIndexFlow.value = chapterIndex
        _currentStationFlow.value = null
        _errorMessageFlow.value = ""
        _playbackStateFlow.value = MediaPlaybackState.CONNECTING

        val metadata = MediaMetadata.Builder()
            .setTitle(chapter.title)
            .setArtist("${story.title} • ${story.author}")
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(chapter.audioUrl)
            .setMediaMetadata(metadata)
            .build()

        exo.stop()
        exo.clearMediaItems()
        exo.setMediaItem(mediaItem)
        exo.prepare()
        if (seekSeconds > 0) {
            exo.seekTo((seekSeconds * 1000).toLong())
        }
        exo.playWhenReady = true

        startOrUpdateForegroundNotification()
    }

    fun pausePlayback() {
        val exo = player ?: return
        recoveryJob?.cancel()
        exo.playWhenReady = false
        _playbackStateFlow.value = MediaPlaybackState.PAUSED
        updateForegroundNotification()
    }

    fun resumePlayback() {
        val exo = player ?: return
        recoveryJob?.cancel()
        val mode = _contentModeFlow.value
        if (mode == MediaContentMode.RADIO) {
            EntertainmentAudioSession.requestSession(ActiveAudioFeature.RADIO)
        } else if (mode == MediaContentMode.STORY_AUDIOBOOK) {
            EntertainmentAudioSession.requestSession(ActiveAudioFeature.STORY)
        }
        exo.playWhenReady = true
        _playbackStateFlow.value = MediaPlaybackState.PLAYING
        startOrUpdateForegroundNotification()
    }

    fun stopPlayback() {
        recoveryJob?.cancel()
        player?.stop()
        player?.clearMediaItems()
        _playbackStateFlow.value = MediaPlaybackState.IDLE
        val mode = _contentModeFlow.value
        if (mode == MediaContentMode.RADIO) {
            EntertainmentAudioSession.releaseSession(ActiveAudioFeature.RADIO)
        } else if (mode == MediaContentMode.STORY_AUDIOBOOK) {
            EntertainmentAudioSession.releaseSession(ActiveAudioFeature.STORY)
        }
        _contentModeFlow.value = MediaContentMode.NONE
        removeForegroundNotification()
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
    }

    fun getCurrentPositionMs(): Long = player?.currentPosition ?: 0L
    fun getDurationMs(): Long = player?.duration?.takeIf { it > 0 } ?: 0L

    private fun handleNextAction() {
        val mode = _contentModeFlow.value
        if (mode == MediaContentMode.RADIO) {
            val list = _stationsListFlow.value
            val current = _currentStationFlow.value
            if (list.isNotEmpty()) {
                val idx = list.indexOfFirst { it.id == current?.id }
                val nextIdx = if (idx >= 0) (idx + 1) % list.size else 0
                playRadioStation(list[nextIdx])
            }
        } else if (mode == MediaContentMode.STORY_AUDIOBOOK) {
            val story = _currentStoryFlow.value
            val idx = _currentChapterIndexFlow.value + 1
            if (story != null && idx < story.chapters.size) {
                playStoryAudiobook(story, idx, 0)
            }
        }
    }

    private fun handlePrevAction() {
        val mode = _contentModeFlow.value
        if (mode == MediaContentMode.RADIO) {
            val list = _stationsListFlow.value
            val current = _currentStationFlow.value
            if (list.isNotEmpty()) {
                val idx = list.indexOfFirst { it.id == current?.id }
                val prevIdx = if (idx > 0) idx - 1 else list.size - 1
                playRadioStation(list[prevIdx])
            }
        } else if (mode == MediaContentMode.STORY_AUDIOBOOK) {
            val story = _currentStoryFlow.value
            val idx = _currentChapterIndexFlow.value - 1
            if (story != null && idx >= 0) {
                playStoryAudiobook(story, idx, 0)
            }
        }
    }

    private fun onStoryChapterEnded() {
        val story = _currentStoryFlow.value ?: return
        val nextIdx = _currentChapterIndexFlow.value + 1
        if (nextIdx < story.chapters.size) {
            playStoryAudiobook(story, nextIdx, 0)
        } else {
            _playbackStateFlow.value = MediaPlaybackState.IDLE
            EntertainmentAudioSession.releaseSession(ActiveAudioFeature.STORY)
            removeForegroundNotification()
        }
    }

    private fun handlePlaybackError() {
        recoveryJob?.cancel()
        val mode = _contentModeFlow.value
        if (mode == MediaContentMode.RADIO) {
            val station = _currentStationFlow.value
            if (reconnectRetryCount < 1 && station != null) {
                reconnectRetryCount++
                _playbackStateFlow.value = MediaPlaybackState.RECONNECTING
                _errorMessageFlow.value = "Reconnecting to stream…"
                recoveryJob = serviceScope.launch {
                    delay(1500)
                    if (_currentStationFlow.value?.id == station.id && _contentModeFlow.value == MediaContentMode.RADIO) {
                        player?.prepare()
                        player?.playWhenReady = true
                    }
                }
                return
            }

            // Candidate failover
            val stationList = _stationsListFlow.value
            if (failoverCandidateCount < MAX_FAILOVER_CANDIDATES && stationList.size > 1) {
                failoverCandidateCount++
                reconnectRetryCount = 0
                val currentIdx = stationList.indexOfFirst { it.id == station?.id }
                val nextIdx = if (currentIdx >= 0) (currentIdx + 1) % stationList.size else 0
                val nextStation = stationList[nextIdx]
                _errorMessageFlow.value = "Station unavailable. Trying next station…"
                _playbackStateFlow.value = MediaPlaybackState.RECONNECTING
                recoveryJob = serviceScope.launch {
                    delay(1200)
                    playRadioStation(nextStation)
                }
                return
            }

            _playbackStateFlow.value = MediaPlaybackState.ERROR
            _errorMessageFlow.value = "This radio station is currently unavailable."
        } else {
            _playbackStateFlow.value = MediaPlaybackState.ERROR
            _errorMessageFlow.value = "Unable to play audio."
        }
    }

    // ── Foreground Notification Management ─────────────────────────────

    private fun buildMediaNotification(): Notification {
        val mode = _contentModeFlow.value
        val isPlaying = _playbackStateFlow.value == MediaPlaybackState.PLAYING

        val title = when (mode) {
            MediaContentMode.RADIO -> _currentStationFlow.value?.name ?: "VisionBridge Radio"
            MediaContentMode.STORY_AUDIOBOOK -> {
                val story = _currentStoryFlow.value
                val chap = story?.chapters?.getOrNull(_currentChapterIndexFlow.value)
                chap?.title ?: (story?.title ?: "VisionBridge Story")
            }
            else -> "VisionBridge Entertainment"
        }

        val subtitle = when (mode) {
            MediaContentMode.RADIO -> {
                val st = _currentStationFlow.value
                if (st != null && st.city.isNotBlank()) "${st.city}, ${st.country}" else "Live Radio"
            }
            MediaContentMode.STORY_AUDIOBOOK -> _currentStoryFlow.value?.let { "${it.title} • ${it.author}" } ?: "Audiobook"
            else -> "Playing Audio"
        }

        val sessionIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // PendingIntents for actions
        val playPauseAction = if (isPlaying) {
            val pauseIntent = Intent(this, EntertainmentMediaService::class.java).apply { action = ACTION_PAUSE }
            val pausePending = PendingIntent.getService(this, 1, pauseIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            NotificationCompat.Action(android.R.drawable.ic_media_pause, "Pause", pausePending)
        } else {
            val playIntent = Intent(this, EntertainmentMediaService::class.java).apply { action = ACTION_PLAY }
            val playPending = PendingIntent.getService(this, 2, playIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            NotificationCompat.Action(android.R.drawable.ic_media_play, "Play", playPending)
        }

        val prevIntent = Intent(this, EntertainmentMediaService::class.java).apply { action = ACTION_PREV }
        val prevPending = PendingIntent.getService(this, 3, prevIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val prevAction = NotificationCompat.Action(android.R.drawable.ic_media_previous, "Previous", prevPending)

        val nextIntent = Intent(this, EntertainmentMediaService::class.java).apply { action = ACTION_NEXT }
        val nextPending = PendingIntent.getService(this, 4, nextIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val nextAction = NotificationCompat.Action(android.R.drawable.ic_media_next, "Next", nextPending)

        val stopIntent = Intent(this, EntertainmentMediaService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(this, 5, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stopAction = NotificationCompat.Action(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPending)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setContentIntent(contentPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .addAction(stopAction)

        val session = mediaSession
        if (session != null) {
            builder.setStyle(
                androidx.media3.session.MediaStyleNotificationHelper.MediaStyle(session)
                    .setShowActionsInCompactView(0, 1, 2)
            )
        }

        return builder.build()
    }

    private fun startOrUpdateForegroundNotification() {
        val notification = buildMediaNotification()
        try {
            if (!isForegroundActive) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                isForegroundActive = true
            } else {
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update foreground notification", e)
        }
    }

    private fun updateForegroundNotification() {
        if (isForegroundActive) {
            val notification = buildMediaNotification()
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun removeForegroundNotification() {
        if (isForegroundActive) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping foreground notification", e)
            }
            isForegroundActive = false
        }
    }

    companion object {
        private const val TAG = "VB-MediaService"
        const val CHANNEL_ID = "visionbridge_media_channel"
        const val NOTIFICATION_ID = 2001
        private const val MAX_FAILOVER_CANDIDATES = 3

        const val ACTION_PLAY = "com.example.visionbridge.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.visionbridge.ACTION_PAUSE"
        const val ACTION_STOP = "com.example.visionbridge.ACTION_STOP"
        const val ACTION_NEXT = "com.example.visionbridge.ACTION_NEXT"
        const val ACTION_PREV = "com.example.visionbridge.ACTION_PREV"

        private var reconnectRetryCount = 0
        private var failoverCandidateCount = 0

        @Volatile
        var instance: EntertainmentMediaService? = null
            private set

        private val _playbackStateFlow = MutableStateFlow(MediaPlaybackState.IDLE)
        val playbackStateFlow: StateFlow<MediaPlaybackState> = _playbackStateFlow.asStateFlow()

        private val _contentModeFlow = MutableStateFlow(MediaContentMode.NONE)
        val contentModeFlow: StateFlow<MediaContentMode> = _contentModeFlow.asStateFlow()

        private val _currentStationFlow = MutableStateFlow<RadioStation?>(null)
        val currentStationFlow: StateFlow<RadioStation?> = _currentStationFlow.asStateFlow()

        private val _stationsListFlow = MutableStateFlow<List<RadioStation>>(emptyList())
        val stationsListFlow: StateFlow<List<RadioStation>> = _stationsListFlow.asStateFlow()

        private val _currentStoryFlow = MutableStateFlow<StoryDetail?>(null)
        val currentStoryFlow: StateFlow<StoryDetail?> = _currentStoryFlow.asStateFlow()

        private val _currentChapterIndexFlow = MutableStateFlow(0)
        val currentChapterIndexFlow: StateFlow<Int> = _currentChapterIndexFlow.asStateFlow()

        private val _errorMessageFlow = MutableStateFlow("")
        val errorMessageFlow: StateFlow<String> = _errorMessageFlow.asStateFlow()

        fun setStationsList(list: List<RadioStation>) {
            _stationsListFlow.value = list
        }

        fun startService(context: Context) {
            val intent = Intent(context, EntertainmentMediaService::class.java)
            context.startService(intent)
        }
    }
}
