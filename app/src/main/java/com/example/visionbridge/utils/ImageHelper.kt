package com.example.visionbridge.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Base64
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Shrinks a captured photo before it is sent to the backend, so Gemini sees fewer
 * image tokens.
 *
 * These numbers are the ones the web app already uses — `compressImage()` in
 * `client/src/modules/Reading/index.jsx` resizes to at most 1024 px on the longest
 * side and re-encodes as JPEG at quality 0.75, and the live camera path exports with
 * the same 0.75 quality. Keeping them identical means Android and the web client send
 * the backend equivalent images.
 */
object ImageHelper {

    private const val TAG = "VB-Image"

    /** Longest side after resizing — matches MAX_SIDE in the web client's compressImage(). */
    private const val MAX_SIDE = 1024

    /** JPEG quality — matches the web client's canvas.toDataURL('image/jpeg', 0.75). */
    private const val QUALITY = 75

    /** The optimized image plus the numbers worth logging. */
    data class OptimizedImage(
        val base64: String,
        val mimeType: String,
        val width: Int,
        val height: Int,
        val sizeKb: Int
    )

    /**
     * Decodes the captured JPEG, applies its orientation, resizes it and returns raw
     * base64 with no `data:` prefix — exactly what `POST /api/reading/extract` expects.
     *
     * @param rotationDegrees `ImageProxy.imageInfo.rotationDegrees` from CameraX, used
     *        when the JPEG carries no EXIF orientation of its own. Text that reaches
     *        Gemini sideways extracts badly, so this is not cosmetic.
     * @throws IllegalStateException if the bytes cannot be decoded as an image.
     */
    fun optimizeAndEncode(imageBytes: ByteArray, rotationDegrees: Int = 0): OptimizedImage {
        require(imageBytes.isNotEmpty()) { "Captured image was empty." }

        var bitmap = decodeSampled(imageBytes)

        val exifDegrees = readExifRotation(imageBytes)
        val degrees = if (exifDegrees != 0) exifDegrees else rotationDegrees
        if (degrees != 0) {
            bitmap = rotate(bitmap, degrees.toFloat())
        }

        bitmap = scaleToMaxSide(bitmap)

        val output = ByteArrayOutputStream()
        val compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, output)
        val width = bitmap.width
        val height = bitmap.height
        bitmap.recycle()

        check(compressed) { "Could not compress the captured image." }

        val bytes = output.toByteArray()
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

        Log.d(
            TAG,
            "Optimized capture: ${width}x$height, ${bytes.size / 1024} KB JPEG " +
                    "(rotation applied ${degrees}°, exif=$exifDegrees, cameraX=$rotationDegrees)"
        )

        return OptimizedImage(
            base64 = base64,
            mimeType = "image/jpeg",
            width = width,
            height = height,
            sizeKb = bytes.size / 1024
        )
    }

    /**
     * Full-resolution phone captures are large enough to OOM on decode, so measure
     * first and let BitmapFactory subsample down towards the target on the way in.
     */
    private fun decodeSampled(imageBytes: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bounds)

        val longestSide = max(bounds.outWidth, bounds.outHeight)
        check(longestSide > 0) { "Captured bytes could not be decoded as an image." }

        var sampleSize = 1
        while (longestSide / (sampleSize * 2) >= MAX_SIDE) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)
            ?: error("Captured bytes could not be decoded as an image.")
    }

    private fun scaleToMaxSide(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= MAX_SIDE && height <= MAX_SIDE) return bitmap

        val ratio = minOf(MAX_SIDE.toFloat() / width, MAX_SIDE.toFloat() / height)
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (width * ratio).roundToInt().coerceAtLeast(1),
            (height * ratio).roundToInt().coerceAtLeast(1),
            true
        )
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    private fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    /** 0 when the JPEG has no usable EXIF orientation, so the caller falls back to CameraX. */
    private fun readExifRotation(imageBytes: ByteArray): Int = try {
        val orientation = ByteArrayInputStream(imageBytes).use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (e: Exception) {
        Log.w(TAG, "Could not read EXIF orientation, falling back to CameraX rotation", e)
        0
    }
}
