package com.example.visionbridge.data

import android.content.Context
import android.util.Log
import com.example.visionbridge.api.ApiError
import com.example.visionbridge.api.ApiResult
import com.example.visionbridge.api.ReadingExtraction
import com.example.visionbridge.api.SmartReadingApi
import com.example.visionbridge.ocr.BitmapDecoder
import com.example.visionbridge.ocr.OfflineOcrEngine
import com.example.visionbridge.utils.ImageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Configuration switch controlling whether Smart Reading uses on-device offline OCR
 * or the legacy cloud Gemini endpoint.
 */
object SmartReadingOcrConfig {
    const val USE_OFFLINE_OCR = true
}

/**
 * Turns raw camera bytes into extracted text.
 * When [SmartReadingOcrConfig.USE_OFFLINE_OCR] is true, executes on-device via [OfflineOcrEngine]
 * without network calls. When false, routes to [SmartReadingApi] (cloud Gemini).
 */
class SmartReadingRepository(
    private val context: Context? = null,
    private val api: SmartReadingApi = SmartReadingApi(context)
) {

    suspend fun extractText(
        imageBytes: ByteArray,
        rotationDegrees: Int,
        language: String = "en"
    ): ApiResult<ReadingExtraction> {

        if (SmartReadingOcrConfig.USE_OFFLINE_OCR) {
            val app = context?.applicationContext ?: appContext

            if (app == null) {
                Log.e(TAG, "Application context is null — cannot initialize OfflineOcrEngine")
                return ApiResult.Failure(
                    ApiError(
                        kind = ApiError.Kind.UNKNOWN,
                        userMessage = "Offline reading engine is unavailable. Please restart the app.",
                        technicalDetail = "SmartReadingRepository received null context"
                    )
                )
            }

            return withContext(Dispatchers.Default) {
                try {
                    val bitmap = BitmapDecoder.decodeForOcr(imageBytes, rotationDegrees)
                    val engine = OfflineOcrEngine.getInstance(app)
                    val ocrOutput = engine.read(bitmap)
                    bitmap.recycle()

                    if (ocrOutput.fullText.isBlank() || ocrOutput.lines.isEmpty()) {
                        val noTextMessage = try {
                            app.getString(com.example.visionbridge.R.string.reading_no_text_actionable)
                        } catch (_: Throwable) {
                            "I couldn't find readable text. Hold the phone 20 to 30 cm from the text, add more light, and try again."
                        }
                        ApiResult.Success(
                            ReadingExtraction(
                                extractedText = null,
                                confidence = ocrOutput.confidence,
                                message = noTextMessage
                            )
                        )
                    } else {
                        ApiResult.Success(
                            ReadingExtraction(
                                extractedText = ocrOutput.fullText,
                                confidence = ocrOutput.confidence,
                                message = null
                            )
                        )
                    }
                } catch (e: OutOfMemoryError) {
                    Log.e(TAG, "Ran out of memory during offline OCR", e)
                    ApiResult.Failure(
                        ApiError(
                            kind = ApiError.Kind.IMAGE_PROCESSING,
                            userMessage = "The photo was too large for this device to process. Please capture again.",
                            technicalDetail = "OutOfMemoryError during offline OCR processing (${imageBytes.size} bytes)"
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Offline OCR inference failed", e)
                    ApiResult.Failure(
                        ApiError(
                            kind = ApiError.Kind.UNKNOWN,
                            userMessage = "Something went wrong while reading the text. Please try again.",
                            technicalDetail = "${e.javaClass.simpleName}: ${e.message}"
                        )
                    )
                }
            }
        }

        // ── Legacy cloud path (untouched, active when USE_OFFLINE_OCR = false) ──
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
            "Sending ${optimized.width}x${optimized.height} ${optimized.mimeType} (~${optimized.sizeKb} KB) in lang=$language"
        )

        return withContext(Dispatchers.IO) {
            api.extractText(optimized.base64, optimized.mimeType, language)
        }
    }

    companion object {
        private const val TAG = "VB-ReadingRepo"

        @Volatile
        var appContext: Context? = null
    }
}
