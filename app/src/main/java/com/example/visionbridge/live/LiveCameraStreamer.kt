package com.example.visionbridge.live

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Base64
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Adaptive Camera Capture & Frame Streaming for VisionBridge Live.
 *
 * Visual Modes:
 *   - Fast Live Vision: Max dimension 800px, 72% JPEG quality (~1200ms background interval ~0.8 FPS).
 *   - High-Detail Read Mode: On-demand 1280px crisp frame, 85% JPEG quality for exact OCR / text.
 *   - Fresh Frame Capture: Captures immediate frame on user conversational turn.
 */
class LiveCameraStreamer(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val onFrame: (base64Jpeg: String) -> Unit
) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var preview: Preview? = null
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    private var backgroundScheduler: ScheduledExecutorService? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val isStreaming = AtomicBoolean(false)
    private val isPreviewing = AtomicBoolean(false)

    // Cached latest frame for instant non-blocking delivery
    @Volatile
    private var latestCapturedFrameBase64: String? = null

    /**
     * Starts the CameraX preview and sets up the ImageCapture pipeline.
     */
    fun startPreview(onReady: (() -> Unit)? = null) {
        if (isPreviewing.get()) return

        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                cameraProvider = future.get()
                bindCamera()
                isPreviewing.set(true)
                onReady?.invoke()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize CameraX provider", e)
            }
        }, cameraExecutor)
    }

    private fun bindCamera() {
        val provider = cameraProvider ?: return
        try {
            provider.unbindAll()

            preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )

            Log.d(TAG, "Camera bound successfully. Facing: ${if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) "BACK" else "FRONT"}")
        } catch (e: Exception) {
            Log.e(TAG, "Error binding camera lifecycle", e)
        }
    }

    /**
     * Toggles between front and rear cameras.
     */
    fun switchFacingMode() {
        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        if (isPreviewing.get()) {
            bindCamera()
        }
    }

    /**
     * Starts continuous background frame sampling (~1200ms interval ~0.8 FPS).
     * Must be called only after setupComplete handshake is received from Gemini.
     */
    @Synchronized
    fun startFrameStreaming() {
        if (isStreaming.get()) return

        isStreaming.set(true)
        backgroundScheduler = Executors.newSingleThreadScheduledExecutor()
        backgroundScheduler?.scheduleWithFixedDelay(
            {
                if (isStreaming.get()) {
                    captureFrameInternal(highDetail = false) { base64 ->
                        if (base64 != null && isStreaming.get()) {
                            latestCapturedFrameBase64 = base64
                            onFrame(base64)
                        }
                    }
                }
            },
            500,
            DEFAULT_FRAME_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        )

        Log.d(TAG, "Background frame streaming started (~${DEFAULT_FRAME_INTERVAL_MS}ms interval)")
    }

    @Synchronized
    fun stopFrameStreaming() {
        isStreaming.set(false)
        backgroundScheduler?.shutdownNow()
        backgroundScheduler = null
        Log.d(TAG, "Background frame streaming stopped.")
    }

    /**
     * Asynchronously captures the freshest immediate frame (e.g. on user speech start/end).
     */
    fun captureCurrentFrame(highDetail: Boolean = false, callback: (String?) -> Unit) {
        captureFrameInternal(highDetail = highDetail, callback = callback)
    }

    /**
     * Internal frame capture from CameraX ImageCapture with adaptive downsampling.
     */
    private fun captureFrameInternal(highDetail: Boolean, callback: (String?) -> Unit) {
        val capture = imageCapture
        if (capture == null || !isPreviewing.get()) {
            callback(null)
            return
        }

        capture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    var rotation = 0
                    val bytes = try {
                        rotation = image.imageInfo.rotationDegrees
                        val buffer = image.planes[0].buffer
                        buffer.rewind()
                        ByteArray(buffer.remaining()).also { buffer.get(it) }
                    } catch (e: Exception) {
                        null
                    } finally {
                        image.close()
                    }

                    if (bytes != null && bytes.isNotEmpty()) {
                        val base64 = processAndCompress(bytes, rotation, highDetail)
                        callback(base64)
                    } else {
                        callback(null)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.w(TAG, "Image capture error: ${exception.message}")
                    callback(null)
                }
            }
        )
    }

    /**
     * Resizes and compresses image to match web performance:
     *   - Fast mode: 800px max dimension, 72% JPEG quality.
     *   - High-detail mode: 1280px max dimension, 85% JPEG quality.
     */
    private fun processAndCompress(imageBytes: ByteArray, rotationDegrees: Int, highDetail: Boolean): String? {
        return try {
            val maxDim = if (highDetail) HIGH_DETAIL_MAX_DIMENSION else DEFAULT_MAX_DIMENSION
            val quality = if (highDetail) HIGH_DETAIL_JPEG_QUALITY else DEFAULT_JPEG_QUALITY

            // 1. Decode bounds
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bounds)

            val longestSide = max(bounds.outWidth, bounds.outHeight)
            if (longestSide <= 0) return null

            var sampleSize = 1
            while (longestSide / (sampleSize * 2) >= maxDim) {
                sampleSize *= 2
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            var bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options) ?: return null

            // 2. Rotate if needed
            if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated !== bitmap) bitmap.recycle()
                bitmap = rotated
            }

            // 3. Scale down towards maxDim
            val w = bitmap.width
            val h = bitmap.height
            if (w > maxDim || h > maxDim) {
                val ratio = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
                val scaled = Bitmap.createScaledBitmap(
                    bitmap,
                    (w * ratio).roundToInt().coerceAtLeast(1),
                    (h * ratio).roundToInt().coerceAtLeast(1),
                    true
                )
                if (scaled !== bitmap) bitmap.recycle()
                bitmap = scaled
            }

            // 4. Compress to JPEG
            val outStream = ByteArrayOutputStream()
            val ok = bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outStream)
            bitmap.recycle()

            if (!ok) return null
            Base64.encodeToString(outStream.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Frame compression error", e)
            null
        }
    }

    @Synchronized
    fun stop() {
        Log.d(TAG, "Stopping LiveCameraStreamer...")
        stopFrameStreaming()
        isPreviewing.set(false)

        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.w(TAG, "Error unbinding CameraX", e)
        }
        cameraProvider = null
        imageCapture = null
        preview = null
        latestCapturedFrameBase64 = null
    }

    fun release() {
        stop()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "VB-LiveCamera"

        const val DEFAULT_FRAME_INTERVAL_MS = 1200L // ~0.8 FPS
        const val DEFAULT_MAX_DIMENSION = 800
        const val DEFAULT_JPEG_QUALITY = 72

        const val HIGH_DETAIL_MAX_DIMENSION = 1280
        const val HIGH_DETAIL_JPEG_QUALITY = 85
    }
}
