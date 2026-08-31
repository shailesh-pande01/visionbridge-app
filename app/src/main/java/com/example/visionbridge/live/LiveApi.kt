package com.example.visionbridge.live

import android.content.Context
import android.util.Log
import com.example.visionbridge.api.ApiClient
import com.example.visionbridge.api.ApiError
import com.example.visionbridge.api.ApiResult
import org.json.JSONObject

/**
 * Client API for requesting ephemeral Gemini Live credentials and executing tools.
 * Connects to the existing Render backend endpoints:
 *   - POST /api/live/session
 *   - POST /api/live/tool
 */
class LiveApi(private val context: Context) {

    private val apiClient = ApiClient.getInstance(context)

    /**
     * Requests a short-lived ephemeral token and Live setup payload from Render backend.
     * The permanent GEMINI_API_KEY is never transmitted to or stored on Android.
     */
    fun requestLiveSession(
        mode: LiveMode = LiveMode.VISION,
        language: String = "en"
    ): ApiResult<LiveSessionData> {
        val payload = JSONObject().apply {
            put("language", language)
            put("mode", if (mode == LiveMode.VOICE) "voice" else "vision")
        }

        Log.d(TAG, "Requesting Live session from backend (mode=${mode.name}, lang=$language)...")

        return when (val result = apiClient.post(LIVE_SESSION_PATH, payload, authRequired = true)) {
            is ApiResult.Success -> {
                try {
                    val root = result.value
                    val data = root.optJSONObject("data") ?: root
                    val ephemeralToken = data.optString("ephemeralToken").takeIf { it.isNotBlank() }
                    val endpoint = data.optString(
                        "endpoint",
                        "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContentConstrained"
                    )
                    val returnedMode = data.optString("mode", if (mode == LiveMode.VOICE) "voice" else "vision")
                    val model = data.optString("model", "models/gemini-2.5-flash-native-audio-latest")
                    val voice = data.optString("voice", "Aoede")
                    val lang = data.optString("language", language)
                    val expireTime = data.optString("expireTime").takeIf { it.isNotBlank() }
                    val setupPayload = data.optJSONObject("setupPayload") ?: JSONObject()

                    val sessionData = LiveSessionData(
                        ephemeralToken = ephemeralToken,
                        endpoint = endpoint,
                        mode = returnedMode,
                        model = model,
                        voice = voice,
                        language = lang,
                        expireTime = expireTime,
                        setupPayload = setupPayload
                    )

                    Log.d(TAG, "Live session created successfully. Model: $model, Voice: $voice")
                    ApiResult.Success(sessionData)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse Live session payload", e)
                    ApiResult.Failure(
                        ApiError(
                            kind = ApiError.Kind.MALFORMED_RESPONSE,
                            userMessage = "Could not parse live session response from server.",
                            technicalDetail = "${e.javaClass.simpleName}: ${e.message}"
                        )
                    )
                }
            }

            is ApiResult.Failure -> {
                Log.e(TAG, "Failed to create Live session: ${result.error.technicalDetail}")
                result
            }
        }
    }

    /**
     * Executes validated safe application tools triggered by AI function calls.
     */
    fun executeLiveTool(toolName: String, args: JSONObject = JSONObject()): ApiResult<JSONObject> {
        val payload = JSONObject().apply {
            put("toolName", toolName)
            put("args", args)
        }

        return apiClient.post(LIVE_TOOL_PATH, payload, authRequired = true)
    }

    companion object {
        private const val TAG = "VB-LiveApi"
        const val LIVE_SESSION_PATH = "/api/live/session"
        const val LIVE_TOOL_PATH = "/api/live/tool"
    }
}
