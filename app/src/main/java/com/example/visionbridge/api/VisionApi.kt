package com.example.visionbridge.api

import android.content.Context
import com.example.visionbridge.data.CurrencyAnalysis
import com.example.visionbridge.data.CurrencyItem
import com.example.visionbridge.data.SceneAnalysis
import com.example.visionbridge.supabase.SupabaseClient
import com.example.visionbridge.supabase.SupabaseConfig
import org.json.JSONObject

class VisionApi(private val context: Context) {

    private val supabaseClient = SupabaseClient.getInstance(context)

    fun analyzeSurroundings(
        imageBase64: String,
        mimeType: String = "image/jpeg",
        language: String = "en"
    ): ApiResult<SceneAnalysis> {
        val body = JSONObject()
            .put("imageBase64", imageBase64)
            .put("mimeType", mimeType)
            .put("language", language)

        return when (val res = supabaseClient.callFunction(SupabaseConfig.FUNCTION_VISION_ANALYZE, body)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                val data = res.value.optJSONObject("data") ?: res.value
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

        return when (val res = supabaseClient.callFunction(SupabaseConfig.FUNCTION_VISION_CURRENCY, body)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                val data = res.value.optJSONObject("data") ?: res.value
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
            }
        }
    }
}
