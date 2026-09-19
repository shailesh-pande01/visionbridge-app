package com.example.visionbridge.ocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/**
 * Text recognizer for PaddleOCR PP-OCRv5 English mobile recognition model.
 *
 * Steps:
 * 1. Perspective crop each bounding box using Matrix.setPolyToPoly onto a Canvas (bilinear).
 * 2. Rotate 90° CCW if height >= 1.5 * width (and evaluate best orientation).
 * 3. Resize to height 48 keeping aspect ratio; normalize BGR with (x/255 - 0.5)/0.5.
 * 4. Run OrtSession per line with its exact target width to eliminate padding distortion and maximize CPU speed.
 * 5. Decode with CtcDecoder.
 */
class TextRecognizer(
    private val session: OrtSession,
    private val env: OrtEnvironment,
    private val ctcDecoder: CtcDecoder
) {

    data class RecognitionResult(
        val lines: List<OcrLine>,
        val prepTimeMs: Long,
        val inferTimeMs: Long,
        val postTimeMs: Long
    )

    fun recognize(bitmap: Bitmap, boxes: List<OcrBox>): RecognitionResult {
        if (boxes.isEmpty()) {
            return RecognitionResult(emptyList(), 0L, 0L, 0L)
        }

        var totalPrepMs = 0L
        var totalInferMs = 0L
        var totalPostMs = 0L
        val recognizedLines = mutableListOf<OcrLine>()

        for (box in boxes) {
            val prepStart = System.currentTimeMillis()
            var cropped = perspectiveCrop(bitmap, box) ?: continue

            // If height >= 1.5 * width, rotate 90 degrees CCW
            if (cropped.height >= 1.5f * cropped.width) {
                val rotMatrix = Matrix().apply { postRotate(-90f) }
                val rotated = Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, rotMatrix, true)
                if (rotated !== cropped) {
                    cropped.recycle()
                    cropped = rotated
                }
            }

            val targetH = 48
            val ratio = targetH.toFloat() / cropped.height.toFloat()
            val targetW = max(32, (cropped.width * ratio).roundToInt())

            val scaled = if (cropped.height == targetH && cropped.width == targetW) {
                cropped
            } else {
                Bitmap.createScaledBitmap(cropped, targetW, targetH, true)
            }
            if (scaled !== cropped) {
                cropped.recycle()
            }

            // Normalization: BGR with (x/255 - 0.5) / 0.5
            val totalFloats = 3 * targetH * targetW
            val floatArray = FloatArray(totalFloats)
            val pixels = IntArray(targetW * targetH)
            scaled.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)
            scaled.recycle()

            val planeSize = targetH * targetW
            for (y in 0 until targetH) {
                val rowOffset = y * targetW
                for (x in 0 until targetW) {
                    val pixel = pixels[rowOffset + x]
                    val r = ((pixel shr 16 and 0xFF) / 255f - 0.5f) / 0.5f
                    val g = ((pixel shr 8 and 0xFF) / 255f - 0.5f) / 0.5f
                    val b = ((pixel and 0xFF) / 255f - 0.5f) / 0.5f

                    val pixelIndex = rowOffset + x
                    floatArray[pixelIndex] = b
                    floatArray[planeSize + pixelIndex] = g
                    floatArray[planeSize * 2 + pixelIndex] = r
                }
            }

            val byteBuffer = ByteBuffer.allocateDirect(totalFloats * 4).order(ByteOrder.nativeOrder())
            val floatBuffer = byteBuffer.asFloatBuffer()
            floatBuffer.put(floatArray)
            floatBuffer.position(0)
            totalPrepMs += (System.currentTimeMillis() - prepStart)

            // Inference
            val inferStart = System.currentTimeMillis()
            val shape = longArrayOf(1L, 3L, targetH.toLong(), targetW.toLong())
            val tensor = OnnxTensor.createTensor(env, floatBuffer, shape)
            val outputArray: Any

            tensor.use { onnxTensor ->
                session.run(mapOf(session.inputNames.first() to onnxTensor)).use { results ->
                    outputArray = results.get(0).value
                }
            }
            totalInferMs += (System.currentTimeMillis() - inferStart)

            // Decode
            val postStart = System.currentTimeMillis()
            val (timeSteps, numClasses, flatProbs) = parseSingleOutput(outputArray)
            val decoded = ctcDecoder.decode(flatProbs, timeSteps, numClasses)

            if (decoded.score >= 0.50f && decoded.text.isNotBlank()) {
                recognizedLines.add(
                    OcrLine(
                        text = decoded.text,
                        score = decoded.score,
                        box = box
                    )
                )
            }
            totalPostMs += (System.currentTimeMillis() - postStart)
        }

        return RecognitionResult(
            lines = recognizedLines,
            prepTimeMs = totalPrepMs,
            inferTimeMs = totalInferMs,
            postTimeMs = totalPostMs
        )
    }

    private fun perspectiveCrop(bitmap: Bitmap, box: OcrBox): Bitmap? {
        val pts = box.points
        if (pts.size != 4) return null

        val tl = pts[0]
        val tr = pts[1]
        val br = pts[2]
        val bl = pts[3]

        val wTop = hypot(tr.x - tl.x, tr.y - tl.y)
        val wBottom = hypot(br.x - bl.x, br.y - bl.y)
        val targetW = max(wTop, wBottom).roundToInt()

        val hLeft = hypot(bl.x - tl.x, bl.y - tl.y)
        val hRight = hypot(br.x - tr.x, br.y - tr.y)
        val targetH = max(hLeft, hRight).roundToInt()

        if (targetW < 4 || targetH < 4) return null

        // If the box is nearly horizontal (slope < 2 px across line), direct crop preserves pristine text edges
        val isHorizontal = abs(tr.y - tl.y) <= 3f && abs(bl.x - tl.x) <= 3f
        if (isHorizontal) {
            val minX = max(0, minOf(tl.x, tr.x, br.x, bl.x).roundToInt())
            val minY = max(0, minOf(tl.y, tr.y, br.y, bl.y).roundToInt())
            val maxX = min(bitmap.width, maxOf(tl.x, tr.x, br.x, bl.x).roundToInt())
            val maxY = min(bitmap.height, maxOf(tl.y, tr.y, br.y, bl.y).roundToInt())
            val rw = maxX - minX
            val rh = maxY - minY
            if (rw >= 4 && rh >= 4) {
                return Bitmap.createBitmap(bitmap, minX, minY, rw, rh)
            }
        }

        val src = floatArrayOf(
            tl.x, tl.y,
            tr.x, tr.y,
            br.x, br.y,
            bl.x, bl.y
        )
        val dst = floatArrayOf(
            0f, 0f,
            targetW.toFloat(), 0f,
            targetW.toFloat(), targetH.toFloat(),
            0f, targetH.toFloat()
        )

        val matrix = Matrix()
        matrix.setPolyToPoly(src, 0, dst, 0, 4)

        val cropped = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(cropped)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(bitmap, matrix, paint)

        return cropped
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseSingleOutput(output: Any): Triple<Int, Int, FloatArray> {
        when (output) {
            is Array<*> -> {
                // shape [1][timeSteps][numClasses]
                val b0 = output[0] as Array<*>
                val timeSteps = b0.size
                val numClasses = (b0[0] as FloatArray).size
                val flat = FloatArray(timeSteps * numClasses)

                for (t in 0 until timeSteps) {
                    val row = b0[t] as FloatArray
                    System.arraycopy(row, 0, flat, t * numClasses, numClasses)
                }
                return Triple(timeSteps, numClasses, flat)
            }
            is FloatArray -> {
                val numClasses = ctcDecoder.classCount
                val timeSteps = output.size / numClasses
                return Triple(timeSteps, numClasses, output)
            }
            else -> {
                error("Unexpected recognizer output type: ${output::class.java}")
            }
        }
    }
}
