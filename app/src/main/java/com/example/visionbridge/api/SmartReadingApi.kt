package com.example.visionbridge.api

import android.content.Context
import android.util.Log
import com.example.visionbridge.supabase.SupabaseClient
import com.example.visionbridge.supabase.SupabaseConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class SmartReadingApi(private val context: Context? = null) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun extractText(
        imageBase64: String,
        mimeType: String = "image/jpeg",
        language: String = "en"
    ): ApiResult<ReadingExtraction> {
        val body = JSONObject()
            .put("imageBase64", imageBase64)
            .put("mimeType", mimeType)
            .put("language", language)

        if (context != null) {
            val supabaseClient = SupabaseClient.getInstance(context)
            return when (val res = supabaseClient.callFunction(SupabaseConfig.FUNCTION_READING_EXTRACT, body, authRequired = false)) {
                is ApiResult.Failure -> res
                is ApiResult.Success -> parseReadingResponse(res.value)
            }
        }

        val url = "${SupabaseConfig.FUNCTIONS_URL}/${SupabaseConfig.FUNCTION_READING_EXTRACT}"
        val requestBody = body.toString().toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .addHeader("Content-Type", "application/json")
            .build()

        Log.d(TAG, "POST $url — mimeType=$mimeType, base64 chars=${imageBase64.length}")
        val startedAt = System.currentTimeMillis()

        return try {
            client.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                val elapsedMs = System.currentTimeMillis() - startedAt
                Log.d(TAG, "HTTP ${response.code} in ${elapsedMs}ms (${rawBody.length} chars)")
                parseResponse(response.code, response.isSuccessful, rawBody)
            }
        } catch (e: SocketTimeoutException) {
            val elapsedMs = System.currentTimeMillis() - startedAt
            Log.e(TAG, "Timed out after ${elapsedMs}ms at $url", e)
            ApiResult.Failure(
                ApiError(
                    kind = ApiError.Kind.TIMEOUT,
                    userMessage = "The reading server took too long to answer. Please try again.",
                    technicalDetail = "SocketTimeoutException after ${elapsedMs}ms at $url: ${e.message}"
                )
            )
        } catch (e: IOException) {
            Log.e(TAG, "Network failure talking to $url", e)
            ApiResult.Failure(
                ApiError(
                    kind = ApiError.Kind.NETWORK_IO,
                    userMessage = "Lost connection to the VisionBridge server. Please try again.",
                    technicalDetail = "${e.javaClass.simpleName} at $url: ${e.message}"
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected failure talking to $url", e)
            ApiResult.Failure(
                ApiError(
                    kind = ApiError.Kind.UNKNOWN,
                    userMessage = "Something went wrong while reading the text. Please try again.",
                    technicalDetail = "${e.javaClass.simpleName} at $url: ${e.message}"
                )
            )
        }
    }

    private fun parseReadingResponse(json: JSONObject): ApiResult<ReadingExtraction> {
        val data = json.optJSONObject("data") ?: json
        val extractedText = if (data.isNull("extractedText")) null else data.optString("extractedText", "").takeIf { it.isNotBlank() }
        val confidence = if (data.has("confidence") && !data.isNull("confidence")) data.optDouble("confidence") else if (extractedText != null) 0.9 else 0.0
        val message = if (data.has("message") && !data.isNull("message")) data.optString("message") else null

        return ApiResult.Success(ReadingExtraction(extractedText, confidence, message))
    }

    private fun parseResponse(
        status: Int,
        isSuccessful: Boolean,
        rawBody: String
    ): ApiResult<ReadingExtraction> {
        val json = try {
            if (rawBody.isBlank()) null else JSONObject(rawBody)
        } catch (e: Exception) {
            Log.e(TAG, "Response body was not JSON", e)
            null
        }

        if (!isSuccessful) {
            val errMessage = json?.optString("error") ?: "Text extraction failed."
            return ApiResult.Failure(
                ApiError(
                    kind = ApiError.Kind.HTTP_ERROR,
                    userMessage = errMessage,
                    technicalDetail = "HTTP $status error: $errMessage (Raw: $rawBody)"
                )
            )
        }

        if (json == null) {
            return ApiResult.Failure(
                ApiError(
                    kind = ApiError.Kind.MALFORMED_RESPONSE,
                    userMessage = "The server sent an unreadable response. Please try again.",
                    technicalDetail = "HTTP $status with a non-JSON body (${rawBody.length} chars)"
                )
            )
        }

        return parseReadingResponse(json)
    }

    companion object {
        private const val TAG = "VB-SmartReadingApi"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
