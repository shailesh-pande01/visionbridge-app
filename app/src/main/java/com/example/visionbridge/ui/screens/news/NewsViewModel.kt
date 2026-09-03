package com.example.visionbridge.ui.screens.news

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.visionbridge.api.ApiResult
import com.example.visionbridge.api.NewsApi
import com.example.visionbridge.data.NewsArticle
import com.example.visionbridge.data.NewsBriefing
import com.example.visionbridge.data.NewsPlaybackState
import com.example.visionbridge.data.SessionManager
import com.example.visionbridge.utils.LocationHelper
import com.example.visionbridge.utils.TextToSpeechManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

class NewsViewModel(application: Application) : AndroidViewModel(application) {

    private val newsApi = NewsApi(application)
    private val sessionManager = SessionManager.getInstance(application)

    private val _playbackState = MutableStateFlow(NewsPlaybackState.LOADING)
    val playbackState: StateFlow<NewsPlaybackState> = _playbackState.asStateFlow()

    private val _briefing = MutableStateFlow<NewsBriefing?>(null)
    val briefing: StateFlow<NewsBriefing?> = _briefing.asStateFlow()

    private val _activeStoryIndex = MutableStateFlow(0)
    val activeStoryIndex: StateFlow<Int> = _activeStoryIndex.asStateFlow()

    private val _selectedCategory = MutableStateFlow("general")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _speechRate = MutableStateFlow(1.0f)
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

    private val playbackSeq = AtomicLong(0)
    private var ttsManager: TextToSpeechManager? = null

    init {
        fetchNewsBriefing()
    }

    fun setTts(tts: TextToSpeechManager) {
        this.ttsManager = tts
    }

    fun fetchNewsBriefing(category: String = _selectedCategory.value, forceRefresh: Boolean = false) {
        _selectedCategory.value = category
        _playbackState.value = NewsPlaybackState.LOADING
        _errorMessage.value = ""

        viewModelScope.launch {
            val language = sessionManager.getLanguage()
            val coords = try {
                LocationHelper.getCurrentLocation(getApplication())
            } catch (e: Exception) {
                Pair(LocationHelper.DEFAULT_LATITUDE, LocationHelper.DEFAULT_LONGITUDE)
            }

            val result = withContext(Dispatchers.IO) {
                newsApi.getNewsBriefing(
                    language = language,
                    city = "Pune",
                    state = "Maharashtra",
                    country = "India",
                    category = category
                )
            }

            when (result) {
                is ApiResult.Success -> {
                    val data = result.value
                    if (data.articles.isNotEmpty()) {
                        _briefing.value = data
                        _activeStoryIndex.value = 0
                        _playbackState.value = NewsPlaybackState.PREPARING
                        val newsSummary = data.articles.take(5).joinToString(". ") { "${it.title}: ${it.description}" }
                        com.example.visionbridge.data.ContextMemoryManager.rememberContext("news", "daily news briefing", newsSummary)
                        playActiveStory()
                    } else {
                        _playbackState.value = NewsPlaybackState.ERROR
                        _errorMessage.value = "No news articles found for this category."
                    }
                }
                is ApiResult.Failure -> {
                    _playbackState.value = NewsPlaybackState.ERROR
                    _errorMessage.value = result.error.userMessage
                }
            }
        }
    }

    fun playActiveStory() {
        val articles = _briefing.value?.articles ?: return
        val idx = _activeStoryIndex.value
        if (idx !in articles.indices) {
            _playbackState.value = NewsPlaybackState.COMPLETED
            return
        }

        val article = articles[idx]
        val currentPlayId = playbackSeq.incrementAndGet()
        _playbackState.value = NewsPlaybackState.SPEAKING

        val textToSpeak = buildString {
            append("Story ${idx + 1} of ${articles.size}. ")
            append(article.title)
            append(". ")
            if (article.description.isNotBlank()) {
                append(article.description)
            }
        }

        ttsManager?.setSpeechRate(_speechRate.value)
        ttsManager?.speak(textToSpeak)
    }

    fun nextStory() {
        val count = _briefing.value?.articles?.size ?: 0
        if (_activeStoryIndex.value < count - 1) {
            _activeStoryIndex.value += 1
            playActiveStory()
        } else {
            _playbackState.value = NewsPlaybackState.COMPLETED
            ttsManager?.speak("You have reached the end of the news briefing.")
        }
    }

    fun prevStory() {
        if (_activeStoryIndex.value > 0) {
            _activeStoryIndex.value -= 1
            playActiveStory()
        } else {
            playActiveStory()
        }
    }

    fun repeatStory() {
        playActiveStory()
    }

    fun pauseNews() {
        ttsManager?.stop()
        _playbackState.value = NewsPlaybackState.PAUSED
    }

    fun resumeNews() {
        playActiveStory()
    }

    fun stopNews() {
        ttsManager?.stop()
        _playbackState.value = NewsPlaybackState.STOPPED
    }

    fun setSpeed(rate: Float) {
        _speechRate.value = rate
        ttsManager?.setSpeechRate(rate)
    }

    fun selectStory(index: Int) {
        val count = _briefing.value?.articles?.size ?: 0
        if (index in 0 until count) {
            _activeStoryIndex.value = index
            playActiveStory()
        }
    }
}
