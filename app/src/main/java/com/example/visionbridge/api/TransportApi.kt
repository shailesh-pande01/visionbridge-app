package com.example.visionbridge.api

import android.content.Context
import com.example.visionbridge.data.TransportAnalysis
import org.json.JSONObject

class TransportApi(private val context: Context) {

    private val apiClient = ApiClient.getInstance(context)

    fun analyzeTransport(
        imageBase64: String,
        mimeType: String = "image/jpeg",
        language: String = "en"
    ): ApiResult<TransportAnalysis> {
        val body = JSONObject()
            .put("imageBase64", imageBase64)
            .put("mimeType", mimeType)
            .put("language", language)

        return when (val res = apiClient.post("/api/transport/analyze", body)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                val data = res.value.optJSONObject("data")
                if (data != null) {
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
                } else {
                    ApiResult.Failure(
                        ApiError(
                            kind = ApiError.Kind.MALFORMED_RESPONSE,
                            userMessage = "Could not parse transport information.",
                            technicalDetail = "Missing data object in transport response"
                        )
                    )
                }
            }
        }
    }
}
