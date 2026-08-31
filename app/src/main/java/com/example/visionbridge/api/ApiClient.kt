package com.example.visionbridge.api

import android.content.Context
import android.util.Log
import com.example.visionbridge.data.SessionManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class ApiClient private constructor(private val context: Context) {

    private val sessionManager = SessionManager.getInstance(context)

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun getBaseUrl(): ApiResult<String> {
        return when (val resolution = BackendLocator.resolve()) {
            is BackendLocator.Resolution.Found -> ApiResult.Success(resolution.baseUrl)
            is BackendLocator.Resolution.NotFound -> {
                val detail = resolution.attempts.joinToString(" | ")
                Log.e(TAG, "No VisionBridge backend answered: $detail")
                ApiResult.Failure(
                    ApiError(
                        kind = ApiError.Kind.BACKEND_UNREACHABLE,
                        userMessage = "Cannot reach the VisionBridge server. Please ensure backend is running.",
                        technicalDetail = "Tried: ${Config.describeCandidates}. Details: $detail"
                    )
                )
            }
        }
    }

    fun post(
        path: String,
        bodyJson: JSONObject,
        authRequired: Boolean = false
    ): ApiResult<JSONObject> {
        val baseUrl = when (val res = getBaseUrl()) {
            is ApiResult.Success -> res.value
            is ApiResult.Failure -> return res
        }

        val url = baseUrl + path
        val requestBody = bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE)

        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBody)

        if (authRequired) {
            val token = sessionManager.token
            if (!token.isNullOrBlank()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }
        }

        val request = requestBuilder.build()
        val startedAt = System.currentTimeMillis()

        return try {
            client.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                val elapsedMs = System.currentTimeMillis() - startedAt
                Log.d(TAG, "POST $path -> HTTP ${response.code} (${elapsedMs}ms)")
                Log.d(TAG, "Final Request URL: $url | HTTP Method: POST | Response Status: ${response.code} | Response Body: $rawBody")
                parseJsonResponse(response.code, response.isSuccessful, rawBody)
            }
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "POST $path timed out. Final Request URL: $url | Exception Type: ${e.javaClass.simpleName} | Exception Message: ${e.message}", e)
            ApiResult.Failure(
                ApiError(
                    kind = ApiError.Kind.TIMEOUT,
                    userMessage = "The server took too long to answer. Please try again.",
                    technicalDetail = "SocketTimeoutException at $url: ${e.message}"
                )
            )
        } catch (e: IOException) {
            BackendLocator.invalidate()
            Log.e(TAG, "POST $path network error. Final Request URL: $url | Exception Type: ${e.javaClass.simpleName} | Exception Message: ${e.message}", e)
            ApiResult.Failure(
                ApiError(
                    kind = ApiError.Kind.NETWORK_IO,
                    userMessage = "Network connection lost. Please try again.",
                    technicalDetail = "${e.javaClass.simpleName} at $url: ${e.message}"
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "POST $path unexpected error. Final Request URL: $url | Exception Type: ${e.javaClass.simpleName} | Exception Message: ${e.message}", e)
            ApiResult.Failure(
                ApiError(
                    kind = ApiError.Kind.UNKNOWN,
                    userMessage = "An unexpected error occurred. Please try again.",
                    technicalDetail = "${e.javaClass.simpleName} at $url: ${e.message}"
                )
            )
        }
    }

    fun get(
        path: String,
        authRequired: Boolean = false
    ): ApiResult<JSONObject> {
        val baseUrl = when (val res = getBaseUrl()) {
            is ApiResult.Success -> res.value
            is ApiResult.Failure -> return res
        }

        val url = baseUrl + path
        val requestBuilder = Request.Builder().url(url).get()

        if (authRequired) {
            val token = sessionManager.token
            if (!token.isNullOrBlank()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }
        }

        val request = requestBuilder.build()
        val startedAt = System.currentTimeMillis()

        return try {
            client.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                val elapsedMs = System.currentTimeMillis() - startedAt
                Log.d(TAG, "GET $path -> HTTP ${response.code} (${elapsedMs}ms)")
                parseJsonResponse(response.code, response.isSuccessful, rawBody)
            }
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "GET $path timed out", e)
            ApiResult.Failure(
                ApiError(
                    kind = ApiError.Kind.TIMEOUT,
                    userMessage = "The server took too long to answer. Please try again.",
                    technicalDetail = "SocketTimeoutException at $url: ${e.message}"
                )
            )
        } catch (e: IOException) {
            BackendLocator.invalidate()
            Log.e(TAG, "GET $path network error", e)
            ApiResult.Failure(
                ApiError(
                    kind = ApiError.Kind.NETWORK_IO,
                    userMessage = "Network connection lost. Please try again.",
                    technicalDetail = "${e.javaClass.simpleName} at $url: ${e.message}"
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "GET $path unexpected error", e)
            ApiResult.Failure(
                ApiError(
                    kind = ApiError.Kind.UNKNOWN,
                    userMessage = "An unexpected error occurred. Please try again.",
                    technicalDetail = "${e.javaClass.simpleName} at $url: ${e.message}"
                )
            )
        }
    }

    fun put(
        path: String,
        bodyJson: JSONObject,
        authRequired: Boolean = false
    ): ApiResult<JSONObject> {
        val baseUrl = when (val res = getBaseUrl()) {
            is ApiResult.Success -> res.value
            is ApiResult.Failure -> return res
        }

        val url = baseUrl + path
        val requestBody = bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE)

        val requestBuilder = Request.Builder()
            .url(url)
            .put(requestBody)

        if (authRequired) {
            val token = sessionManager.token
            if (!token.isNullOrBlank()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }
        }

        val request = requestBuilder.build()
        val startedAt = System.currentTimeMillis()

        return try {
            client.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                val elapsedMs = System.currentTimeMillis() - startedAt
                Log.d(TAG, "PUT $path -> HTTP ${response.code} (${elapsedMs}ms)")
                parseJsonResponse(response.code, response.isSuccessful, rawBody)
            }
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "PUT $path timed out", e)
            ApiResult.Failure(
                ApiError(
                    kind = ApiError.Kind.TIMEOUT,
                    userMessage = "The server took too long to answer. Please try again.",
                    technicalDetail = "SocketTimeoutException at $url: ${e.message}"
                )
            )
        } catch (e: IOException) {
            BackendLocator.invalidate()
            Log.e(TAG, "PUT $path network error", e)
            ApiResult.Failure(
                ApiError(
                    kind = ApiError.Kind.NETWORK_IO,
                    userMessage = "Network connection lost. Please try again.",
                    technicalDetail = "${e.javaClass.simpleName} at $url: ${e.message}"
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "PUT $path unexpected error", e)
            ApiResult.Failure(
                ApiError(
                    kind = ApiError.Kind.UNKNOWN,
                    userMessage = "An unexpected error occurred. Please try again.",
                    technicalDetail = "${e.javaClass.simpleName} at $url: ${e.message}"
                )
            )
        }
    }

    fun delete(
        path: String,
        authRequired: Boolean = false
    ): ApiResult<JSONObject> {
        val baseUrl = when (val res = getBaseUrl()) {
            is ApiResult.Success -> res.value
            is ApiResult.Failure -> return res
        }

        val url = baseUrl + path
        val requestBuilder = Request.Builder().url(url).delete()

        if (authRequired) {
            val token = sessionManager.token
            if (!token.isNullOrBlank()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }
        }

        val request = requestBuilder.build()
        return try {
            client.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                parseJsonResponse(response.code, response.isSuccessful, rawBody)
            }
        } catch (e: Exception) {
            Log.e(TAG, "DELETE $path error", e)
            ApiResult.Failure(
                ApiError(
                    kind = ApiError.Kind.NETWORK_IO,
                    userMessage = "Request failed. Please try again.",
                    technicalDetail = "${e.javaClass.simpleName}: ${e.message}"
                )
            )
        }
    }

    private fun parseJsonResponse(
        status: Int,
        isSuccessful: Boolean,
        rawBody: String
    ): ApiResult<JSONObject> {
        val json = try {
            if (rawBody.isBlank()) null else JSONObject(rawBody)
        } catch (e: Exception) {
            null
        }

        if (!isSuccessful) {
            val errorMsg = json?.optString("error") ?: "Server returned error (HTTP $status)"
            val code = json?.optString("code") ?: "HTTP_$status"
            return ApiResult.Failure(
                ApiError(
                    kind = ApiError.Kind.HTTP_ERROR,
                    userMessage = errorMsg,
                    technicalDetail = "HTTP $status [$code]: $errorMsg"
                )
            )
        }

        if (json == null) {
            return ApiResult.Failure(
                ApiError(
                    kind = ApiError.Kind.MALFORMED_RESPONSE,
                    userMessage = "Unreadable response from server.",
                    technicalDetail = "HTTP $status non-JSON body"
                )
            )
        }

        return ApiResult.Success(json)
    }

    companion object {
        private const val TAG = "VB-ApiClient"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        @Volatile
        private var instance: ApiClient? = null

        fun getInstance(context: Context): ApiClient {
            return instance ?: synchronized(this) {
                instance ?: ApiClient(context.applicationContext).also { instance = it }
            }
        }
    }
}
