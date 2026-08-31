package com.example.visionbridge.api

import android.content.Context
import com.example.visionbridge.data.CurrencyAnalysis
import com.example.visionbridge.data.CurrencyItem
import com.example.visionbridge.data.HazardAnalysis
import com.example.visionbridge.data.SceneAnalysis
import org.json.JSONObject

class VisionApi(private val context: Context) {

    private val apiClient = ApiClient.getInstance(context)

    fun analyzeSurroundings(
        imageBase64: String,
        mimeType: String = "image/jpeg",
        language: String = "en"
    ): ApiResult<SceneAnalysis> {
        val body = JSONObject()
            .put("imageBase64", imageBase64)
            .put("mimeType", mimeType)
            .put("language", language)

        return when (val res = apiClient.post("/api/vision/analyze", body)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                val data = res.value.optJSONObject("data")
                if (data != null) {
                    val scene = data.optString("scene", "Scene detected")
                    val confidence = data.optDouble("confidence", 0.85)
                    val description = data.optString("description", "")
                    val lighting = data.optString("lighting", "")
                    val timeOfDay = data.optString("timeOfDay", "")

                    val objects = mutableListOf<String>()
                    val objectsArr = data.optJSONArray("objects")
                    if (objectsArr != null) {
                        for (i in 0 until objectsArr.length()) {
                            objects.add(objectsArr.getString(i))
                        }
                    }

                    val obstacles = mutableListOf<String>()
                    val obstaclesArr = data.optJSONArray("obstacles")
                    if (obstaclesArr != null) {
                        for (i in 0 until obstaclesArr.length()) {
                            obstacles.add(obstaclesArr.getString(i))
                        }
                    }

                    ApiResult.Success(
                        SceneAnalysis(
                            scene = scene,
                            confidence = confidence,
                            description = description,
                            objects = objects,
                            obstacles = obstacles,
                            lighting = lighting,
                            timeOfDay = timeOfDay
                        )
                    )
                } else {
                    ApiResult.Failure(
                        ApiError(
                            kind = ApiError.Kind.MALFORMED_RESPONSE,
                            userMessage = "Could not parse surroundings analysis.",
                            technicalDetail = "Missing data object in vision analyze response"
                        )
                    )
                }
            }
        }
    }

    fun analyzeHazard(
        imageBase64: String,
        mimeType: String = "image/jpeg",
        sceneMemory: String = "",
        language: String = "en"
    ): ApiResult<HazardAnalysis> {
        val body = JSONObject()
            .put("imageBase64", imageBase64)
            .put("mimeType", mimeType)
            .put("sceneMemory", sceneMemory)
            .put("language", language)

        return when (val res = apiClient.post("/api/vision/hazard", body)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                val data = res.value.optJSONObject("data")
                if (data != null) {
                    val speech = data.optString("speech", "No significant change.")
                    val summary = data.optString("sceneSummary", sceneMemory)
                    val timestamp = data.optLong("timestamp", System.currentTimeMillis())
                    ApiResult.Success(HazardAnalysis(speech, summary, timestamp))
                } else {
                    ApiResult.Failure(
                        ApiError(
                            kind = ApiError.Kind.MALFORMED_RESPONSE,
                            userMessage = "Could not parse hazard analysis.",
                            technicalDetail = "Missing data object in vision hazard response"
                        )
                    )
                }
            }
        }
    }

    fun analyzeCurrency(
        imageBase64: String,
        mimeType: String = "image/jpeg",
        language: String = "en"
    ): ApiResult<CurrencyAnalysis> {
        val body = JSONObject()
            .put("imageBase64", imageBase64)
            .put("mimeType", mimeType)
            .put("language", language)

        return when (val res = apiClient.post("/api/vision/currency", body)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                val data = res.value.optJSONObject("data")
                if (data != null) {
                    val currency = data.optString("currency", "INR")
                    val symbol = data.optString("symbol", "₹")
                    val confidence = data.optDouble("confidence", 0.85)
                    val speech = data.optString("speech", "")

                    val total = if (data.isNull("total")) null else data.optDouble("total")

                    val items = mutableListOf<CurrencyItem>()
                    val itemsArr = data.optJSONArray("items")
                    if (itemsArr != null) {
                        for (i in 0 until itemsArr.length()) {
                            val itemObj = itemsArr.optJSONObject(i)
                            if (itemObj != null) {
                                items.add(
                                    CurrencyItem(
                                        denomination = itemObj.optDouble("denomination", 0.0),
                                        quantity = itemObj.optInt("quantity", 1),
                                        confidence = itemObj.optDouble("confidence", 0.85)
                                    )
                                )
                            }
                        }
                    }

                    ApiResult.Success(
                        CurrencyAnalysis(
                            currency = currency,
                            symbol = symbol,
                            items = items,
                            total = total,
                            confidence = confidence,
                            speech = speech
                        )
                    )
                } else {
                    ApiResult.Failure(
                        ApiError(
                            kind = ApiError.Kind.MALFORMED_RESPONSE,
                            userMessage = "Could not parse currency analysis.",
                            technicalDetail = "Missing data object in currency response"
                        )
                    )
                }
            }
        }
    }
}
