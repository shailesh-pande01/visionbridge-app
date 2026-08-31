package com.example.visionbridge.audio

import android.content.Context
import android.util.Log
import com.example.visionbridge.data.EntertainmentRepository
import com.example.visionbridge.data.StoryChapter
import com.example.visionbridge.data.StoryDetail
import com.example.visionbridge.data.StoryProgressData
import com.example.visionbridge.utils.TextToSpeechManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.regex.Pattern

class StoryChunkedTtsPlayer private constructor(private val context: Context) {

    private val tts = TextToSpeechManager(context)
    private val repo = EntertainmentRepository.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _currentStory = MutableStateFlow<StoryDetail?>(null)
    val currentStory: StateFlow<StoryDetail?> = _currentStory.asStateFlow()

    private val _currentChapterIndex = MutableStateFlow(0)
    val currentChapterIndex: StateFlow<Int> = _currentChapterIndex.asStateFlow()

    private val _currentChunkIndex = MutableStateFlow(0)
    val currentChunkIndex: StateFlow<Int> = _currentChunkIndex.asStateFlow()

    private val _totalChunks = MutableStateFlow(0)
    val totalChunks: StateFlow<Int> = _totalChunks.asStateFlow()

    private var chunks: List<String> = emptyList()
    private var activeJob: Job? = null
    private var autoSaveJob: Job? = null

    init {
        EntertainmentAudioSession.registerStoryCallbacks {
            stop()
        }
    }

    fun playStory(story: StoryDetail, chapterIndex: Int = 0, resumeChunkIndex: Int = 0) {
        val chapter = story.chapters.getOrNull(chapterIndex) ?: return
        EntertainmentAudioSession.requestSession(ActiveAudioFeature.STORY)

        _currentStory.value = story
        _currentChapterIndex.value = chapterIndex

        // Set TTS language matching the story language
        tts.setLanguage(story.language)

        chunks = splitTextIntoSpeechChunks(chapter.text.ifBlank { chapter.summary })
        _totalChunks.value = chunks.size
        _currentChunkIndex.value = resumeChunkIndex.coerceIn(0, (chunks.size - 1).coerceAtLeast(0))

        _isPlaying.value = true
        _isPaused.value = false

        startChunkSequence(_currentChunkIndex.value)
        startAutosave()
    }

    private fun startChunkSequence(fromIndex: Int) {
        activeJob?.cancel()
        activeJob = scope.launch {
            for (idx in fromIndex until chunks.size) {
                if (!_isPlaying.value || _isPaused.value) break
                _currentChunkIndex.value = idx
                val text = chunks[idx]

                val chunkCompleted = CompletableDeferred<Boolean>()
                tts.speak(text) {
                    chunkCompleted.complete(true)
                }

                chunkCompleted.await()
                delay(350) // Small natural sentence pause
            }

            if (_isPlaying.value && !_isPaused.value && _currentChunkIndex.value >= chunks.size - 1) {
                // Chapter finished, advance to next chapter if available
                onChapterCompleted()
            }
        }
    }

    private fun onChapterCompleted() {
        val story = _currentStory.value ?: return
        val nextIdx = _currentChapterIndex.value + 1
        if (nextIdx < story.chapters.size) {
            playStory(story, nextIdx, 0)
        } else {
            _isPlaying.value = false
            _isPaused.value = false
            EntertainmentAudioSession.releaseSession(ActiveAudioFeature.STORY)
            saveCurrentProgress()
        }
    }

    fun pause() {
        if (!_isPlaying.value) return
        _isPaused.value = true
        _isPlaying.value = false
        activeJob?.cancel()
        tts.stop()
        saveCurrentProgress()
    }

    fun resume() {
        if (!_isPaused.value && _currentStory.value == null) return
        val story = _currentStory.value ?: return
        EntertainmentAudioSession.requestSession(ActiveAudioFeature.STORY)

        _isPlaying.value = true
        _isPaused.value = false
        tts.setLanguage(story.language)
        startChunkSequence(_currentChunkIndex.value)
        startAutosave()
    }

    fun stop() {
        activeJob?.cancel()
        autoSaveJob?.cancel()
        tts.stop()
        if (_isPlaying.value || _isPaused.value) {
            saveCurrentProgress()
        }
        _isPlaying.value = false
        _isPaused.value = false
        _currentStory.value = null
        EntertainmentAudioSession.releaseSession(ActiveAudioFeature.STORY)
    }

    fun nextChapter(): Boolean {
        val story = _currentStory.value ?: return false
        val nextIdx = _currentChapterIndex.value + 1
        return if (nextIdx < story.chapters.size) {
            playStory(story, nextIdx, 0)
            true
        } else {
            false
        }
    }

    fun prevChapter(): Boolean {
        val story = _currentStory.value ?: return false
        val prevIdx = _currentChapterIndex.value - 1
        return if (prevIdx >= 0) {
            playStory(story, prevIdx, 0)
            true
        } else {
            false
        }
    }

    fun restartStory() {
        val story = _currentStory.value ?: return
        playStory(story, 0, 0)
    }

    private fun startAutosave() {
        autoSaveJob?.cancel()
        autoSaveJob = scope.launch {
            while (_isPlaying.value) {
                delay(15000)
                saveCurrentProgress()
            }
        }
    }

    private fun saveCurrentProgress() {
        val story = _currentStory.value ?: return
        val chapIdx = _currentChapterIndex.value
        val chapter = story.chapters.getOrNull(chapIdx)
        val progress = StoryProgressData(
            storyId = story.id,
            chapterIndex = chapIdx,
            positionSeconds = _currentChunkIndex.value * 8, // approximate seconds
            storyTitle = story.title,
            chapterTitle = chapter?.title ?: "Chapter ${chapIdx + 1}",
            author = story.author,
            genre = story.genre
        )
        scope.launch {
            repo.saveStoryProgress(progress)
        }
    }

    companion object {
        private const val TAG = "VB-StoryTtsPlayer"

        @Volatile
        private var instance: StoryChunkedTtsPlayer? = null

        fun getInstance(context: Context): StoryChunkedTtsPlayer {
            return instance ?: synchronized(this) {
                instance ?: StoryChunkedTtsPlayer(context.applicationContext).also { instance = it }
            }
        }

        /**
         * Splits text on English and Devanagari punctuation (।, ॥, ., !, ?, \n)
         * to create natural 120-180 character chunks for smooth Android TTS flow.
         */
        fun splitTextIntoSpeechChunks(text: String, maxChunkLen: Int = 180): List<String> {
            if (text.isBlank()) return emptyList()
            val clean = text.replace("\r\n", "\n").replace(Regex("\\s+"), " ").trim()

            val sentencePattern = Pattern.compile("[^.!?।॥\\n]+[.!?।॥\\n]*")
            val matcher = sentencePattern.matcher(clean)
            val sentences = mutableListOf<String>()

            while (matcher.find()) {
                val s = matcher.group().trim()
                if (s.isNotBlank()) sentences.add(s)
            }

            if (sentences.isEmpty()) sentences.add(clean)

            val chunks = mutableListOf<String>()
            var currentChunk = ""

            for (sentence in sentences) {
                if (sentence.length > maxChunkLen) {
                    if (currentChunk.isNotBlank()) {
                        chunks.add(currentChunk.trim())
                        currentChunk = ""
                    }
                    val clauses = sentence.split(Regex("[,;—–:]+"))
                    for (c in clauses) {
                        val clause = c.trim()
                        if (clause.isBlank()) continue
                        if ((currentChunk + " " + clause).length > maxChunkLen) {
                            if (currentChunk.isNotBlank()) chunks.add(currentChunk.trim())
                            currentChunk = clause
                        } else {
                            currentChunk = if (currentChunk.isEmpty()) clause else "$currentChunk $clause"
                        }
                    }
                } else {
                    if ((currentChunk + " " + sentence).length > maxChunkLen) {
                        if (currentChunk.isNotBlank()) chunks.add(currentChunk.trim())
                        currentChunk = sentence
                    } else {
                        currentChunk = if (currentChunk.isEmpty()) sentence else "$currentChunk $sentence"
                    }
                }
            }

            if (currentChunk.isNotBlank()) {
                chunks.add(currentChunk.trim())
            }

            return chunks
        }
    }
}
