package com.example.visionbridge.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import kotlin.math.max

/**
 * Decodes camera JPEG bytes for offline OCR processing.
 *
 * Ensures the bitmap is ARGB_8888, subsampled to a maximum dimension of 2048 px
 * to prevent OutOfMemoryError, with EXIF rotation applied, falling back to CameraX rotationDegrees.
 */
object BitmapDecoder {

    private const val TAG = "VB-OfflineOcr"
    private const val MAX_OCR_SIDE = 2048

    fun decodeForOcr(imageBytes: ByteArray, rotationDegrees: Int = 0): Bitmap {
        require(imageBytes.isNotEmpty()) { "Captured image bytes were empty." }

        // 1. Measure dimensions without allocating full pixel memory
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, boundsOptions)

        val longestSide = max(boundsOptions.outWidth, boundsOptions.outHeight)
        check(longestSide > 0) { "Captured bytes could not be decoded as an image." }

        // 2. Subsample so longest side <= 2048
        var sampleSize = 1
        while (longestSide / (sampleSize * 2) >= MAX_OCR_SIDE) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        var bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, decodeOptions)
            ?: error("BitmapFactory returned null decoding image bytes.")

        // 3. Determine rotation: prefer EXIF, fallback to CameraX rotationDegrees
        val exifDegrees = readExifRotation(imageBytes)
        val degreesToRotate = if (exifDegrees != 0) exifDegrees else rotationDegrees

        if (degreesToRotate != 0) {
            val matrix = Matrix().apply { postRotate(degreesToRotate.toFloat()) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) {
                bitmap.recycle()
                bitmap = rotated
            }
        }

        Log.d(
            TAG,
            "Decoded OCR bitmap: ${bitmap.width}x${bitmap.height} (subsample=$sampleSize, rotation=${degreesToRotate}°)"
        )
        return bitmap
    }

    private fun readExifRotation(imageBytes: ByteArray): Int = try {
        ByteArrayInputStream(imageBytes).use { stream ->
            val exifInterface = ExifInterface(stream)
            when (exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Could not read EXIF orientation", e)
        0
    }
}
