package com.example.visionbridge.ui.screens.entertainment

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.visionbridge.api.ApiResult
import com.example.visionbridge.audio.*
import com.example.visionbridge.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class StoryUiState(
    val activeMode: String = "library", // "library" | "player"
    val stories: List<StorySummary> = emptyList(),
    val selectedLanguage: String = "all", // "all" | "en" | "hi" | "mr"
    val selectedGenre: String = "all",
    val searchQuery: String = "",
    val isLoadingCatalog: Boolean = true,
    val savedProgress: StoryProgressData? = null,
    val selectedStory: StoryDetail? = null,
    val currentChapterIndex: Int = 0,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val isAudiobook: Boolean = false,
    val currentChunkIndex: Int = 0,
    val totalChunks: Int = 0,
    val currentPositionSeconds: Int = 0,
    val totalDurationSeconds: Int = 0,
    val transcriptOpen: Boolean = false,
    val chapterDrawerOpen: Boolean = false,
    val errorMessage: String = ""
)

class StoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = EntertainmentRepository.getInstance(application)
    private val chunkedTtsPlayer = StoryChunkedTtsPlayer.getInstance(application)

    private val _uiState = MutableStateFlow(StoryUiState())
    val uiState: StateFlow<StoryUiState> = _uiState.asStateFlow()

    init {
        // Observe ExoPlayer for audiobooks
        viewModelScope.launch {
            EntertainmentMediaService.playbackStateFlow.collect { pState ->
                val mode = EntertainmentMediaService.contentModeFlow.value
                if (mode == MediaContentMode.STORY_AUDIOBOOK) {
                    _uiState.update {
                        it.copy(
                            isPlaying = pState == MediaPlaybackState.PLAYING,
                            isPaused = pState == MediaPlaybackState.PAUSED
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            EntertainmentMediaService.currentStoryFlow.collect { story ->
                if (story != null && EntertainmentMediaService.contentModeFlow.value == MediaContentMode.STORY_AUDIOBOOK) {
                    _uiState.update { it.copy(selectedStory = story) }
                }
            }
        }

        viewModelScope.launch {
            EntertainmentMediaService.currentChapterIndexFlow.collect { chapIdx ->
                if (EntertainmentMediaService.contentModeFlow.value == MediaContentMode.STORY_AUDIOBOOK) {
                    _uiState.update { it.copy(currentChapterIndex = chapIdx) }
                }
            }
        }

        // Observe Chunked TTS for text chapters
        viewModelScope.launch {
            chunkedTtsPlayer.isPlaying.collect { isPlaying ->
                if (!_uiState.value.isAudiobook) {
                    _uiState.update { it.copy(isPlaying = isPlaying) }
                }
            }
        }

        viewModelScope.launch {
            chunkedTtsPlayer.isPaused.collect { isPaused ->
                if (!_uiState.value.isAudiobook) {
                    _uiState.update { it.copy(isPaused = isPaused) }
                }
            }
        }

        viewModelScope.launch {
            chunkedTtsPlayer.currentChapterIndex.collect { chapIdx ->
                if (!_uiState.value.isAudiobook) {
                    _uiState.update { it.copy(currentChapterIndex = chapIdx) }
                }
            }
        }

        viewModelScope.launch {
            chunkedTtsPlayer.currentChunkIndex.collect { chunkIdx ->
                if (!_uiState.value.isAudiobook) {
                    _uiState.update { it.copy(currentChunkIndex = chunkIdx) }
                }
            }
        }

        viewModelScope.launch {
            chunkedTtsPlayer.totalChunks.collect { total ->
                if (!_uiState.value.isAudiobook) {
                    _uiState.update { it.copy(totalChunks = total) }
                }
            }
        }

        loadCatalog()
        loadSavedProgress()
    }

    fun setLanguageFilter(lang: String) {
        _uiState.update { it.copy(selectedLanguage = lang) }
        loadCatalog(language = lang)
    }

    fun setGenreFilter(genre: String) {
        _uiState.update { it.copy(selectedGenre = genre) }
        loadCatalog(genre = genre)
    }

    fun searchStories(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        loadCatalog(search = query)
    }

    fun loadCatalog(
        genre: String? = null,
        search: String? = null,
        language: String? = null
    ) {
        val g = genre ?: _uiState.value.selectedGenre
        val s = search ?: _uiState.value.searchQuery
        val l = language ?: _uiState.value.selectedLanguage

        _uiState.update { it.copy(isLoadingCatalog = true, errorMessage = "") }

        viewModelScope.launch {
            val result = repo.getStoriesCatalog(genre = g, search = s, language = l)
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            stories = result.value,
                            isLoadingCatalog = false
                        )
                    }
                }
                is ApiResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isLoadingCatalog = false,
                            errorMessage = result.error.userMessage
                        )
                    }
                }
            }
        }
    }

    fun loadSavedProgress() {
        viewModelScope.launch {
            val progress = repo.getStoryProgress()
            _uiState.update { it.copy(savedProgress = progress) }
        }
    }

    fun playStory(storySummaryOrId: String, chapterIdx: Int = 0, seekSec: Int = 0) {
        viewModelScope.launch {
            val result = repo.getStoryDetail(storySummaryOrId)
            when (result) {
                is ApiResult.Success -> {
                    val detail = result.value
                    val chapter = detail.chapters.getOrNull(chapterIdx)
                    val isAudio = chapter?.isAudiobook == true

                    _uiState.update {
                        it.copy(
                            selectedStory = detail,
                            currentChapterIndex = chapterIdx,
                            isAudiobook = isAudio,
                            activeMode = "player",
                            chapterDrawerOpen = false,
                            errorMessage = ""
                        )
                    }

                    if (isAudio) {
                        chunkedTtsPlayer.stop()
                        EntertainmentMediaService.instance?.playStoryAudiobook(detail, chapterIdx, seekSec)
                    } else {
                        EntertainmentMediaService.instance?.stopPlayback()
                        chunkedTtsPlayer.playStory(detail, chapterIdx, resumeChunkIndex = 0)
                    }
                }
                is ApiResult.Failure -> {
                    _uiState.update { it.copy(errorMessage = result.error.userMessage) }
                }
            }
        }
    }

    fun continueStory() {
        val progress = _uiState.value.savedProgress
        if (progress != null && progress.storyId.isNotBlank()) {
            playStory(progress.storyId, progress.chapterIndex, progress.positionSeconds)
        } else {
            setGenreFilter("popular")
            _uiState.update { it.copy(activeMode = "library") }
        }
    }

    fun togglePlayPause() {
        if (_uiState.value.isAudiobook) {
            val service = EntertainmentMediaService.instance ?: return
            if (_uiState.value.isPlaying) {
                service.pausePlayback()
            } else {
                service.resumePlayback()
            }
        } else {
            if (_uiState.value.isPlaying) {
                chunkedTtsPlayer.pause()
            } else {
                chunkedTtsPlayer.resume()
            }
        }
    }

    fun nextChapter() {
        val story = _uiState.value.selectedStory ?: return
        val nextIdx = _uiState.value.currentChapterIndex + 1
        if (nextIdx < story.chapters.size) {
            playStory(story.id, nextIdx, 0)
        }
    }

    fun prevChapter() {
        val story = _uiState.value.selectedStory ?: return
        val prevIdx = _uiState.value.currentChapterIndex - 1
        if (prevIdx >= 0) {
            playStory(story.id, prevIdx, 0)
        }
    }

    fun restartStory() {
        val story = _uiState.value.selectedStory ?: return
        playStory(story.id, 0, 0)
    }

    fun stopStory() {
        if (_uiState.value.isAudiobook) {
            EntertainmentMediaService.instance?.stopPlayback()
        } else {
            chunkedTtsPlayer.stop()
        }
        _uiState.update { it.copy(isPlaying = false, isPaused = false) }
    }

    fun switchToLibrary() {
        _uiState.update { it.copy(activeMode = "library") }
        loadSavedProgress()
    }

    fun switchToPlayer() {
        if (_uiState.value.selectedStory != null) {
            _uiState.update { it.copy(activeMode = "player") }
        }
    }

    fun toggleTranscript() {
        _uiState.update { it.copy(transcriptOpen = !it.transcriptOpen) }
    }

    fun toggleChapterDrawer() {
        _uiState.update { it.copy(chapterDrawerOpen = !it.chapterDrawerOpen) }
    }

    fun getStoryVoiceInfo(): String {
        val story = _uiState.value.selectedStory
        return if (story != null) {
            val chap = story.chapters.getOrNull(_uiState.value.currentChapterIndex)
            val chapTitle = chap?.title ?: "Chapter ${_uiState.value.currentChapterIndex + 1}"
            "Now playing ${story.title}, $chapTitle."
        } else {
            "No story is currently playing."
        }
    }
}
