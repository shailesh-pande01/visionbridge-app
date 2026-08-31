package com.example.visionbridge.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ActiveAudioFeature {
    NONE,
    RADIO,
    STORY,
    GAME,
    ASSISTANT
}

object EntertainmentAudioSession {

    private val _activeFeature = MutableStateFlow(ActiveAudioFeature.NONE)
    val activeFeature: StateFlow<ActiveAudioFeature> = _activeFeature.asStateFlow()

    private val _isDucked = MutableStateFlow(false)
    val isDucked: StateFlow<Boolean> = _isDucked.asStateFlow()

    private var onRadioStopCallback: (() -> Unit)? = null
    private var onStoryStopCallback: (() -> Unit)? = null
    private var onGameStopCallback: (() -> Unit)? = null

    fun registerRadioCallbacks(onStop: () -> Unit) {
        this.onRadioStopCallback = onStop
    }

    fun registerStoryCallbacks(onStop: () -> Unit) {
        this.onStoryStopCallback = onStop
    }

    fun registerGameCallbacks(onStop: () -> Unit) {
        this.onGameStopCallback = onStop
    }

    /**
     * Request exclusive entertainment audio session for a feature.
     * Automatically stops/pauses competing entertainment audio.
     */
    fun requestSession(feature: ActiveAudioFeature) {
        val current = _activeFeature.value
        if (current == feature) return

        when (current) {
            ActiveAudioFeature.RADIO -> {
                if (feature != ActiveAudioFeature.ASSISTANT) {
                    onRadioStopCallback?.invoke()
                }
            }
            ActiveAudioFeature.STORY -> {
                if (feature != ActiveAudioFeature.ASSISTANT) {
                    onStoryStopCallback?.invoke()
                }
            }
            ActiveAudioFeature.GAME -> {
                if (feature != ActiveAudioFeature.ASSISTANT) {
                    onGameStopCallback?.invoke()
                }
            }
            else -> {}
        }

        if (feature != ActiveAudioFeature.ASSISTANT) {
            _activeFeature.value = feature
        }
    }

    fun releaseSession(feature: ActiveAudioFeature) {
        if (_activeFeature.value == feature) {
            _activeFeature.value = ActiveAudioFeature.NONE
        }
    }

    fun setDucked(ducked: Boolean) {
        _isDucked.value = ducked
    }
}
