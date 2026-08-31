package com.example.visionbridge.api

/**
 * What went wrong, in enough detail to actually diagnose it.
 *
 * [userMessage] is what a person sees and hears — short and actionable.
 * [technicalDetail] is the real exception / HTTP status / backend error code. It is
 * logged and shown in a small diagnostic line so a failure is never flattened into
 * a meaningless "Network error". It never carries credentials — the reading endpoint
 * is unauthenticated and the Gemini key stays on the server.
 */
data class ApiError(
    val kind: Kind,
    val userMessage: String,
    val technicalDetail: String
) {
    enum class Kind {
        /** No candidate backend URL answered the health probe. */
        BACKEND_UNREACHABLE,

        /** The host answered before but the socket failed this time. */
        NETWORK_IO,

        /** Connect/read timed out — server reachable but slow, or Gemini is taking too long. */
        TIMEOUT,

        /** Backend replied with a non-2xx status. */
        HTTP_ERROR,

        /** Backend replied 2xx but the body was not the expected JSON shape. */
        MALFORMED_RESPONSE,

        /** Gemini key rejected or missing on the server. */
        BACKEND_AUTH,

        /** Gemini quota / rate limit. */
        RATE_LIMITED,

        /** Image rejected as too large (HTTP 413). */
        PAYLOAD_TOO_LARGE,

        /** Local failure before the request went out (decode/encode). */
        IMAGE_PROCESSING,

        UNKNOWN
    }
}

/** A call either produced a value or a described failure. */
sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

/**
 * The `data` object returned by `POST /api/reading/extract`.
 *
 * The backend answers HTTP 200 both when it read text and when it found none —
 * "no text" is a successful call with a null [extractedText] and an explanatory
 * [message], not an error.
 */
data class ReadingExtraction(
    val extractedText: String?,
    val confidence: Double?,
    val message: String?
)
