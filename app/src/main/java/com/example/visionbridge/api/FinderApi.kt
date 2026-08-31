package com.example.visionbridge.api

import android.content.Context
import com.example.visionbridge.data.FinderAnalysis
import org.json.JSONObject

class FinderApi(private val context: Context) {

    private val apiClient = ApiClient.getInstance(context)

    fun searchObject(
        imageBase64: String,
        objectName: String,
        mimeType: String = "image/jpeg",
        language: String = "en"
    ): ApiResult<FinderAnalysis> {
        val body = JSONObject()
            .put("imageBase64", imageBase64)
            .put("objectName", objectName.trim())
            .put("mimeType", mimeType)
            .put("language", language)

        return when (val res = apiClient.post("/api/object-finder/search", body)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                val data = res.value.optJSONObject("data")
                if (data != null) {
                    val found = data.optBoolean("found", false)
                    val obj = data.optString("object", objectName)
                    val direction = data.optString("direction", "")
                    val distance = data.optString("distance", "")
                    val reference = data.optString("reference", "")
                    val speech = data.optString("speech", "")
                    val confidence = data.optDouble("confidence", if (found) 0.9 else 0.5)

                    ApiResult.Success(
                        FinderAnalysis(
                            found = found,
                            objectName = obj,
                            direction = direction,
                            distance = distance,
                            reference = reference,
                            speech = speech,
                            confidence = confidence
                        )
                    )
                } else {
                    ApiResult.Failure(
                        ApiError(
                            kind = ApiError.Kind.MALFORMED_RESPONSE,
                            userMessage = "Could not parse object finder response.",
                            technicalDetail = "Missing data object in finder response"
                        )
                    )
                }
            }
        }
    }
}
