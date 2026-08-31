package com.example.visionbridge.data

import android.util.Log
import com.example.visionbridge.api.ApiError
import com.example.visionbridge.api.ApiResult
import com.example.visionbridge.api.ReadingExtraction
import com.example.visionbridge.api.SmartReadingApi
import com.example.visionbridge.utils.ImageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns raw camera bytes into extracted text: optimize locally, then ask the existing
 * VisionBridge backend, which owns the Gemini call.
 */
class SmartReadingRepository(
    private val api: SmartReadingApi = SmartReadingApi()
) {

    suspend fun extractText(
        imageBytes: ByteArray,
        rotationDegrees: Int
    ): ApiResult<ReadingExtraction> {

        val optimized = try {
            withContext(Dispatchers.Default) {
                ImageHelper.optimizeAndEncode(imageBytes, rotationDegrees)
            }
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Ran out of memory optimizing the capture", e)
            return ApiResult.Failure(
                ApiError(
                    kind = ApiError.Kind.IMAGE_PROCESSING,
                    userMessage = "The photo was too large for this device to process. Please capture again.",
                    technicalDetail = "OutOfMemoryError while decoding ${imageBytes.size} bytes"
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Could not optimize the capture", e)
            return ApiResult.Failure(
                ApiError(
                    kind = ApiError.Kind.IMAGE_PROCESSING,
                    userMessage = "The photo could not be prepared for reading. Please capture again.",
                    technicalDetail = "${e.javaClass.simpleName}: ${e.message}"
                )
            )
        }

        Log.i(
            TAG,
            "Sending ${optimized.width}x${optimized.height} ${optimized.mimeType} (~${optimized.sizeKb} KB)"
        )

        return withContext(Dispatchers.IO) {
            api.extractText(optimized.base64, optimized.mimeType)
        }
    }

    private companion object {
        const val TAG = "VB-ReadingRepo"
    }
}
