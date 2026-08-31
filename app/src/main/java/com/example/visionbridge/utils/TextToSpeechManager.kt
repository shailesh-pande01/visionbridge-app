package com.example.visionbridge.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Speaks extracted text and voice assistant responses aloud.
 *
 * Provides utterance tracking, callback on speech completion, and echo detection
 * so the microphone recognizer does not re-capture the device's own speech.
 */
class TextToSpeechManager(
    context: Context,
    private val onError: (String) -> Unit = {}
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)

    @Volatile
    private var isInitialized = false

    @Volatile
    private var pendingText: String? = null
    private var pendingOnDone: (() -> Unit)? = null

    @Volatile
    private var currentText: String = ""

    private var onDoneCallback: (() -> Unit)? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.e(TAG, "Text-to-speech engine failed to initialize (status $status)")
            onError("Text-to-speech is unavailable on this device, so the text is shown but not read aloud.")
            return
        }

        val engine = tts
        if (engine == null) {
            Log.w(TAG, "Engine initialized after shutdown — ignoring")
            return
        }

        updateLanguage(Locale.getDefault())

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId == null || utteranceId.endsWith(LAST_CHUNK_SUFFIX)) {
                    _isSpeaking.value = false
                    val cb = onDoneCallback
                    onDoneCallback = null
                    currentText = ""
                    cb?.invoke()
                }
            }

            @Deprecated("Superseded by onError(String, Int)", ReplaceWith(""))
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                Log.e(TAG, "Speech failed for utterance $utteranceId")
                this@TextToSpeechManager.onError("Could not read the text aloud.")
                val cb = onDoneCallback
                onDoneCallback = null
                currentText = ""
                cb?.invoke()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _isSpeaking.value = false
                Log.e(TAG, "Speech failed for utterance $utteranceId (code $errorCode)")
                this@TextToSpeechManager.onError("Could not read the text aloud.")
                val cb = onDoneCallback
                onDoneCallback = null
                currentText = ""
                cb?.invoke()
            }
        })

        isInitialized = true

        pendingText?.let { queued ->
            val queuedCb = pendingOnDone
            pendingText = null
            pendingOnDone = null
            Log.d(TAG, "Speaking text that was queued before initialization finished")
            speak(queued, queuedCb)
        }
    }

    /** Interrupts anything currently being spoken and reads [text] from the start. */
    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (text.isBlank()) {
            onDone?.invoke()
            return
        }

        currentText = text

        if (!isInitialized) {
            pendingText = text
            pendingOnDone = onDone
            return
        }

        val engine = tts ?: run {
            onDone?.invoke()
            return
        }

        onDoneCallback = onDone
        val chunks = chunk(text)

        chunks.forEachIndexed { index, part ->
            val isLast = index == chunks.lastIndex
            val utteranceId = "vb-read-$index" + if (isLast) LAST_CHUNK_SUFFIX else ""
            val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD

            val result = engine.speak(part, queueMode, null, utteranceId)
            if (result == TextToSpeech.ERROR) {
                Log.e(TAG, "speak() rejected chunk $index")
                onError("Could not read the text aloud.")
                if (isLast) {
                    _isSpeaking.value = false
                    onDoneCallback = null
                    currentText = ""
                    onDone?.invoke()
                }
            }
        }
    }

    fun stop() {
        pendingText = null
        pendingOnDone = null
        val cb = onDoneCallback
        onDoneCallback = null
        currentText = ""
        tts?.stop()
        _isSpeaking.value = false
        cb?.invoke()
    }

    fun shutdown() {
        pendingText = null
        pendingOnDone = null
        onDoneCallback = null
        currentText = ""
        isInitialized = false
        tts?.stop()
        tts?.shutdown()
        tts = null
        _isSpeaking.value = false
    }

    fun getCurrentSpeechText(): String = currentText

    /**
     * Rejects transcript if it is an echo of the assistant's own TTS output.
     */
    fun isEchoOfCurrentSpeech(transcript: String?): Boolean {
        if (!_isSpeaking.value || currentText.isBlank() || transcript.isNullOrBlank()) return false

        val normalize = { s: String ->
            s.lowercase()
                .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        val spoken = normalize(currentText)
        val heard = normalize(transcript)

        if (heard.isBlank()) return false
        if (spoken.contains(heard)) return true

        val heardWords = heard.split(" ").filter { it.length > 2 }
        if (heardWords.isEmpty()) return false

        val overlap = heardWords.count { spoken.contains(it) }
        return (overlap.toDouble() / heardWords.size) > 0.7
    }

    /**
     * A long menu or document can exceed the engine's per-utterance limit, which makes
     * it refuse the whole string. Split on sentence-ish boundaries instead.
     */
    private fun chunk(text: String): List<String> {
        val limit = maxInputLength()
        if (text.length <= limit) return listOf(text)

        val chunks = mutableListOf<String>()
        var remaining = text

        while (remaining.length > limit) {
            val window = remaining.substring(0, limit)
            val breakAt = window.lastIndexOfAny(charArrayOf('.', '!', '?', '\n'))
                .takeIf { it > limit / 2 }
                ?: window.lastIndexOf(' ').takeIf { it > limit / 2 }
                ?: (limit - 1)

            chunks += remaining.substring(0, breakAt + 1).trim()
            remaining = remaining.substring(breakAt + 1).trim()
        }

        if (remaining.isNotEmpty()) chunks += remaining
        return chunks
    }

    fun setLanguage(langCode: String) {
        val locale = LocaleHelper.getLocale(langCode)
        updateLanguage(locale)
    }

    private fun updateLanguage(locale: Locale) {
        val engine = tts ?: return
        when (engine.setLanguage(locale)) {
            TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED -> {
                Log.w(TAG, "Language $locale unsupported, falling back to English")
                engine.setLanguage(Locale.ENGLISH)
            }
        }
    }

    private fun maxInputLength(): Int = try {
        (TextToSpeech.getMaxSpeechInputLength() - 64).coerceAtLeast(256)
    } catch (e: Exception) {
        Log.w(TAG, "Could not read the engine's max input length", e)
        3_000
    }

    private companion object {
        const val TAG = "VB-TTS"
        const val LAST_CHUNK_SUFFIX = "-last"
    }
}
