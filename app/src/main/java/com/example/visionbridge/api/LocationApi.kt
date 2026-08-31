package com.example.visionbridge.api

import android.content.Context
import com.example.visionbridge.data.LocationAnalysis
import org.json.JSONObject

class LocationApi(private val context: Context) {

    private val apiClient = ApiClient.getInstance(context)

    fun getCurrentLocation(
        latitude: Double,
        longitude: Double,
        language: String = "en"
    ): ApiResult<LocationAnalysis> {
        val body = JSONObject()
            .put("latitude", latitude)
            .put("longitude", longitude)
            .put("language", language)

        return when (val res = apiClient.post("/api/location/current", body)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                val data = res.value.optJSONObject("data")
                if (data != null) {
                    val summary = data.optString("summary", "You are at your current location.")
                    val address = if (data.isNull("address")) null else data.optString("address")

                    val landmarks = mutableListOf<String>()
                    val landmarksArr = data.optJSONArray("landmarks")
                    if (landmarksArr != null) {
                        for (i in 0 until landmarksArr.length()) {
                            landmarks.add(landmarksArr.getString(i))
                        }
                    }

                    ApiResult.Success(
                        LocationAnalysis(
                            summary = summary,
                            address = address,
                            landmarks = landmarks,
                            latitude = latitude,
                            longitude = longitude
                        )
                    )
                } else {
                    ApiResult.Failure(
                        ApiError(
                            kind = ApiError.Kind.MALFORMED_RESPONSE,
                            userMessage = "Could not parse location summary.",
                            technicalDetail = "Missing data object in location response"
                        )
                    )
                }
            }
        }
    }
}
