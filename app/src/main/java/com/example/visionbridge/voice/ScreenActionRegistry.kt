package com.example.visionbridge.voice

import android.util.Log

/**
 * Registry where active Compose screens register their capabilities.
 * Allows global voice commands (e.g. "Vision, capture", "Vision, cancel request")
 * to invoke the active screen's handlers without coupling the voice system to screen instances.
 */
object ScreenActionRegistry {

    private const val TAG = "VB-ScreenAction"

    private var activeScreen: String = "home"
    private var captureHandler: (() -> Unit)? = null
    private var cancelHandler: (() -> Unit)? = null
    private var findObjectHandler: ((String) -> Unit)? = null
    private var submitHandler: (() -> Unit)? = null
    private var replayHandler: (() -> Unit)? = null

    fun registerScreen(
        screenId: String,
        onCapture: (() -> Unit)? = null,
        onCancel: (() -> Unit)? = null,
        onFindObject: ((String) -> Unit)? = null,
        onSubmit: (() -> Unit)? = null,
        onReplay: (() -> Unit)? = null
    ) {
        activeScreen = screenId
        captureHandler = onCapture
        cancelHandler = onCancel
        findObjectHandler = onFindObject
        submitHandler = onSubmit
        replayHandler = onReplay
        Log.d(TAG, "Screen registered: $screenId (canCapture=${onCapture != null}, canReplay=${onReplay != null})")
    }

    fun unregisterScreen(screenId: String) {
        if (activeScreen == screenId) {
            captureHandler = null
            cancelHandler = null
            findObjectHandler = null
            submitHandler = null
            replayHandler = null
            activeScreen = "home"
            Log.d(TAG, "Screen unregistered: $screenId, activeScreen reset to home")
        }
    }

    fun getActiveScreen(): String = activeScreen

    fun canCapture(): Boolean = captureHandler != null

    fun executeCapture(): Boolean {
        val handler = captureHandler
        return if (handler != null) {
            handler.invoke()
            true
        } else {
            false
        }
    }

    fun executeCancel(): Boolean {
        val handler = cancelHandler
        return if (handler != null) {
            handler.invoke()
            true
        } else {
            false
        }
    }

    fun executeFindObject(target: String): Boolean {
        val handler = findObjectHandler
        return if (handler != null) {
            handler.invoke(target)
            true
        } else {
            false
        }
    }

    fun executeSubmit(): Boolean {
        val handler = submitHandler
        return if (handler != null) {
            handler.invoke()
            true
        } else {
            false
        }
    }

    fun canReplay(): Boolean = replayHandler != null

    fun executeReplay(): Boolean {
        val handler = replayHandler
        return if (handler != null) {
            handler.invoke()
            true
        } else {
            false
        }
    }
}
