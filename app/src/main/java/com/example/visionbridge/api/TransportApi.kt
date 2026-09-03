package com.example.visionbridge.api

import android.content.Context
import com.example.visionbridge.data.TransportAnalysis
import com.example.visionbridge.supabase.SupabaseClient
import com.example.visionbridge.supabase.SupabaseConfig
import org.json.JSONObject

class TransportApi(private val context: Context) {

    private val supabaseClient = SupabaseClient.getInstance(context)

    fun analyzeTransport(
        imageBase64: String,
        mimeType: String = "image/jpeg",
        language: String = "en"
    ): ApiResult<TransportAnalysis> {
        val body = JSONObject()
            .put("imageBase64", imageBase64)
            .put("mimeType", mimeType)
            .put("language", language)

        return when (val res = supabaseClient.callFunction(SupabaseConfig.FUNCTION_TRANSPORT_ANALYZE, body)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                val data = res.value.optJSONObject("data") ?: res.value
                val type = data.optString("type", "None")
                val title = data.optString("title", "")
                val destination = data.optString("destination", "")
                val speech = data.optString("speech", "")
                val confidence = data.optDouble("confidence", 0.85)

                ApiResult.Success(
                    TransportAnalysis(
                        type = type,
                        title = title,
                        destination = destination,
                        speech = speech,
                        confidence = confidence
                    )
                )
            }
        }
    }
}
