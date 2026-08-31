package com.example.visionbridge.api

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * `POST /api/reading/extract` on the existing VisionBridge MERN backend.
 *
 * The contract is taken verbatim from the working web client
 * (`client/src/services/readingService.js` → `server/routes/readingRoutes.js`):
 *
 *   POST {base}/api/reading/extract
 *   Content-Type: application/json
 *   { "imageBase64": "<raw base64, no data: prefix>", "mimeType": "image/jpeg" }
 *
 *   200 → { "success": true,
 *           "data": { "extractedText": string|null, "confidence": number, "message"?: string } }
 *   4xx/5xx → { "success": false, "error": string, "code": string }
 *
 * The route carries no auth middleware, so no token or key is sent — and the Gemini
 * key never leaves the server.
 */
class SmartReadingApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        // Measured round trips against the real backend run to about a minute, and the
        // server's model-fallback loop can add more when Gemini returns 503 for the
        // first model it tries. The web client sets no timeout at all; this one is
        // generous enough not to abandon a request the backend is still working on.
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun extractText(
        imageBase64: String,
        mimeType: String = "image/jpeg"
    ): ApiResult<ReadingExtraction> {

        val baseUrl = when (val resolution = BackendLocator.resolve()) {
            is BackendLocator.Resolution.Found -> resolution.baseUrl
            is BackendLocator.Resolution.NotFound -> {
                val detail = resolution.attempts.joinToString(" | ")
                Log.e(TAG, "No VisionBridge backend answered. Attempts: $detail")
                return ApiResult.Failure(
                    ApiError(
                        kind = ApiError.Kind.BACKEND_UNREACHABLE,
                        userMessage = "Cannot reach the VisionBridge server. " +
                                "Start the backend with \"node server.js\" in the server folder, " +
                                "then make sure this device can reach it.",
                        technicalDetail = "Tried ${Config.describeCandidates}. $detail"
                    )
                )
            }
        }

        val url = baseUrl + Config.READING_EXTRACT_PATH
        val body = JSONObject()
            .put("imageBase64", imageBase64)
            .put("mimeType", mimeType)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder().url(url).post(body).build()

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
            Log.e(TAG, "Timed out after ${elapsedMs}ms talking to $url", e)
            ApiResult.Failure(
                ApiError(
                    kind = ApiError.Kind.TIMEOUT,
                    userMessage = "The server took too long to answer. Please try again.",
                    technicalDetail = "SocketTimeoutException after ${elapsedMs}ms at $url: ${e.message}"
                )
            )
        } catch (e: IOException) {
            // The address worked at probe time but the socket failed — re-probe next call.
            BackendLocator.invalidate()
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
            return ApiResult.Failure(httpError(status, json, rawBody))
        }

        if (json == null) {
            return ApiResult.Failure(
                ApiError(
                    kind = ApiError.Kind.MALFORMED_RESPONSE,
                    userMessage = "The server sent a response the app could not read. Please try again.",
                    technicalDetail = "HTTP $status with a non-JSON body (${rawBody.length} chars)"
                )
            )
        }

        // 200 with success:false — the backend reports a handled failure.
        if (!json.optBoolean("success", false)) {
            val serverError = json.optString("error").ifBlank { "Text extraction failed." }
            return ApiResult.Failure(
                ApiError(
                    kind = ApiError.Kind.HTTP_ERROR,
                    userMessage = serverError,
                    technicalDetail = "HTTP $status success=false code=${json.optString("code")} error=$serverError"
                )
            )
        }

        val data = json.optJSONObject("data")
            ?: return ApiResult.Failure(
                ApiError(
                    kind = ApiError.Kind.MALFORMED_RESPONSE,
                    userMessage = "The server sent an unexpected response. Please try again.",
                    technicalDetail = "HTTP $status success=true but no \"data\" object"
                )
            )

        // The backend sends JSON null for extractedText when nothing was readable;
        // optString would turn that into the literal string "null".
        val extractedText = if (data.isNull("extractedText")) {
            null
        } else {
            data.optString("extractedText").trim().ifBlank { null }
        }

        val confidence = if (data.has("confidence") && !data.isNull("confidence")) {
            data.optDouble("confidence").takeUnless { it.isNaN() }
        } else {
            null
        }

        val message = if (data.isNull("message")) null else data.optString("message").ifBlank { null }

        return ApiResult.Success(ReadingExtraction(extractedText, confidence, message))
    }

    /** Mirrors the code/status mapping the web client uses in readingService.js. */
    private fun httpError(status: Int, json: JSONObject?, rawBody: String): ApiError {
        val code = json?.optString("code").orEmpty()
        val serverMessage = json?.optString("error").orEmpty()
        val detail = "HTTP $status code=${code.ifBlank { "-" }} error=${
            serverMessage.ifBlank { rawBody.take(200).ifBlank { "<empty body>" } }
        }"

        Log.e(TAG, "Backend rejected the request — $detail")

        val (kind, message) = when (code) {
            "MISSING_API_KEY" ->
                ApiError.Kind.BACKEND_AUTH to
                        "The server is missing its Gemini API key. Add GEMINI_API_KEY to server/.env."

            "INVALID_API_KEY" ->
                ApiError.Kind.BACKEND_AUTH to
                        "The server's Gemini API key was rejected. Check GEMINI_API_KEY in server/.env."

            "PERMISSION_DENIED" ->
                ApiError.Kind.BACKEND_AUTH to
                        "The server's Gemini key does not have permission for this request."

            "RATE_LIMIT" ->
                ApiError.Kind.RATE_LIMITED to
                        "The reading service is busy right now. Please wait a moment and try again."

            "MODEL_NOT_FOUND" ->
                ApiError.Kind.HTTP_ERROR to
                        "No Gemini model is available on the server. Set GEMINI_MODEL in server/.env."

            "INVALID_REQUEST" ->
                ApiError.Kind.HTTP_ERROR to
                        "The server could not process this image. Please capture again."

            else -> when (status) {
                400 -> ApiError.Kind.HTTP_ERROR to
                        (serverMessage.ifBlank { "The server rejected the image. Please capture again." })

                401, 403 -> ApiError.Kind.BACKEND_AUTH to
                        "The server refused the request. Check the backend's API key configuration."

                404 -> ApiError.Kind.HTTP_ERROR to
                        "The reading endpoint was not found on the server. Check that the backend is up to date."

                413 -> ApiError.Kind.PAYLOAD_TOO_LARGE to
                        "That photo was too large to send. Please capture again."

                429 -> ApiError.Kind.RATE_LIMITED to
                        "Too many requests. Please wait a moment and try again."

                in 500..599 -> ApiError.Kind.HTTP_ERROR to
                        (serverMessage.ifBlank { "The server had a problem reading this image. Please try again." })

                else -> ApiError.Kind.HTTP_ERROR to
                        (serverMessage.ifBlank { "The request failed (HTTP $status)." })
            }
        }

        return ApiError(kind = kind, userMessage = message, technicalDetail = detail)
    }

    private companion object {
        const val TAG = "VB-ReadingApi"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
