package com.example.visionbridge.live

import android.content.Context
import android.util.Log
import com.example.visionbridge.api.ApiError
import com.example.visionbridge.api.ApiResult
import com.example.visionbridge.supabase.SupabaseClient
import com.example.visionbridge.supabase.SupabaseConfig
import org.json.JSONObject

class LiveApi(private val context: Context) {

    private val supabaseClient = SupabaseClient.getInstance(context)

    fun requestLiveSession(
        mode: LiveMode = LiveMode.VISION,
        language: String = "en"
    ): ApiResult<LiveSessionData> {
        val payload = JSONObject().apply {
            put("language", language)
            put("mode", if (mode == LiveMode.VOICE) "voice" else "vision")
        }

        Log.d(TAG, "Requesting Live session from Supabase (mode=${mode.name}, lang=$language)...")

        return when (val result = supabaseClient.callFunction(SupabaseConfig.FUNCTION_LIVE_SESSION, payload, authRequired = true)) {
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

    fun executeLiveTool(toolName: String, args: JSONObject = JSONObject()): ApiResult<JSONObject> {
        val payload = JSONObject().apply {
            put("toolName", toolName)
            put("args", args)
        }

        return supabaseClient.callFunction(SupabaseConfig.FUNCTION_LIVE_TOOL, payload, authRequired = false)
    }

    companion object {
        private const val TAG = "VB-LiveApi"
    }
}
