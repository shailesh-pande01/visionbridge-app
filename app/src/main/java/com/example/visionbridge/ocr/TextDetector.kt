package com.example.visionbridge.ocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.*

/**
 * Text detector for PaddleOCR PP-OCRv5 mobile detector model.
 *
 * Preprocessing:
 * - Scaled so max(width, height) <= 960, both sides rounded to multiples of 32 (min 32).
 * - BGR channel order with mean [0.485, 0.456, 0.406] and std [0.229, 0.224, 0.225].
 * - NCHW float direct buffer.
 *
 * Postprocessing:
 * - Threshold 0.3 on probability map.
 * - 8-connected components.
 * - Minimum-area bounding box via convex hull and rotating calipers.
 * - Box score >= 0.6.
 * - Polygon unclip expansion (ratio 2.0).
 * - Reading order sorting.
 */
class TextDetector(
    private val session: OrtSession,
    private val env: OrtEnvironment
) {

    data class DetectionResult(
        val boxes: List<OcrBox>,
        val prepTimeMs: Long,
        val inferTimeMs: Long,
        val postTimeMs: Long
    )

    fun detect(bitmap: Bitmap): DetectionResult {
        val origW = bitmap.width
        val origH = bitmap.height

        val prepStart = System.currentTimeMillis()
        val (targetW, targetH) = calculateTargetDims(origW, origH)

        val scaledBitmap = if (origW == targetW && origH == targetH) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        }

        val floatBuffer = bitmapToFloatBuffer(scaledBitmap, targetW, targetH)
        if (scaledBitmap !== bitmap) {
            scaledBitmap.recycle()
        }
        val prepTimeMs = System.currentTimeMillis() - prepStart

        // Model Inference
        val inferStart = System.currentTimeMillis()
        val shape = longArrayOf(1, 3, targetH.toLong(), targetW.toLong())
        val tensor = OnnxTensor.createTensor(env, floatBuffer, shape)
        val probMap: FloatArray

        tensor.use { onnxTensor ->
            session.run(mapOf(session.inputNames.first() to onnxTensor)).use { results ->
                val rawOutput = results.get(0).value
                probMap = extractFlatFloatArray(rawOutput, targetH, targetW)
            }
        }
        val inferTimeMs = System.currentTimeMillis() - inferStart

        // Post-processing
        val postStart = System.currentTimeMillis()
        val boxes = postProcess(probMap, targetW, targetH, origW, origH)
        val postTimeMs = System.currentTimeMillis() - postStart

        return DetectionResult(
            boxes = boxes,
            prepTimeMs = prepTimeMs,
            inferTimeMs = inferTimeMs,
            postTimeMs = postTimeMs
        )
    }

    private fun calculateTargetDims(origW: Int, origH: Int): Pair<Int, Int> {
        val maxSide = max(origW, origH)
        var w = origW.toFloat()
        var h = origH.toFloat()

        if (maxSide > 960) {
            val ratio = 960f / maxSide
            w *= ratio
            h *= ratio
        }

        var targetW = (w / 32f).roundToInt() * 32
        var targetH = (h / 32f).roundToInt() * 32
        targetW = max(32, targetW)
        targetH = max(32, targetH)

        return Pair(targetW, targetH)
    }

    private fun bitmapToFloatBuffer(bitmap: Bitmap, w: Int, h: Int): FloatBuffer {
        val totalFloats = 3 * h * w
        val byteBuffer = ByteBuffer.allocateDirect(totalFloats * 4).order(ByteOrder.nativeOrder())
        val floatBuffer = byteBuffer.asFloatBuffer()

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val planeSize = w * h
        val meanB = 0.485f
        val meanG = 0.456f
        val meanR = 0.406f
        val stdB = 0.229f
        val stdG = 0.224f
        val stdR = 0.225f

        for (i in 0 until planeSize) {
            val pixel = pixels[i]
            val r = (pixel shr 16 and 0xFF) / 255.0f
            val g = (pixel shr 8 and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f

            val normB = (b - meanB) / stdB
            val normG = (g - meanG) / stdG
            val normR = (r - meanR) / stdR

            floatBuffer.put(i, normB)
            floatBuffer.put(planeSize + i, normG)
            floatBuffer.put(planeSize * 2 + i, normR)
        }
        floatBuffer.position(0)
        return floatBuffer
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractFlatFloatArray(output: Any, h: Int, w: Int): FloatArray {
        val flat = FloatArray(h * w)
        when (output) {
            is Array<*> -> {
                val b0 = output[0] as Array<*>
                val c0 = b0[0] as Array<*>
                for (y in 0 until h) {
                    val row = c0[y] as FloatArray
                    System.arraycopy(row, 0, flat, y * w, w)
                }
            }
            is FloatArray -> {
                System.arraycopy(output, 0, flat, 0, minOf(flat.size, output.size))
            }
            else -> {
                error("Unexpected model output format: ${output::class.java}")
            }
        }
        return flat
    }

    private fun postProcess(
        probMap: FloatArray,
        targetW: Int,
        targetH: Int,
        origW: Int,
        origH: Int
    ): List<OcrBox> {
        val threshold = 0.3f
        val binMap = BooleanArray(targetW * targetH) { i -> probMap[i] > threshold }

        // Find 8-connected components
        val labels = IntArray(targetW * targetH)
        var nextLabel = 1
        val componentPoints = mutableMapOf<Int, MutableList<Point2D>>()

        val dx = intArrayOf(-1, 0, 1, -1, 1, -1, 0, 1)
        val dy = intArrayOf(-1, -1, -1, 0, 0, 1, 1, 1)

        val queueX = IntArray(targetW * targetH)
        val queueY = IntArray(targetW * targetH)

        for (y in 0 until targetH) {
            for (x in 0 until targetW) {
                val idx = y * targetW + x
                if (binMap[idx] && labels[idx] == 0) {
                    val currentLabel = nextLabel++
                    val points = mutableListOf<Point2D>()
                    var head = 0
                    var tail = 0

                    queueX[tail] = x
                    queueY[tail] = y
                    tail++
                    labels[idx] = currentLabel

                    while (head < tail) {
                        val cx = queueX[head]
                        val cy = queueY[head]
                        head++

                        points.add(Point2D(cx.toFloat(), cy.toFloat()))

                        for (d in 0 until 8) {
                            val nx = cx + dx[d]
                            val ny = cy + dy[d]

                            if (nx in 0 until targetW && ny in 0 until targetH) {
                                val nIdx = ny * targetW + nx
                                if (binMap[nIdx] && labels[nIdx] == 0) {
                                    labels[nIdx] = currentLabel
                                    queueX[tail] = nx
                                    queueY[tail] = ny
                                    tail++
                                }
                            }
                        }
                    }

                    if (points.size >= 10) {
                        componentPoints[currentLabel] = points
                    }
                }
            }
        }

        val boxes = mutableListOf<OcrBox>()
        val scaleX = origW.toFloat() / targetW
        val scaleY = origH.toFloat() / targetH

        for ((_, points) in componentPoints) {
            val hull = computeConvexHull(points)
            if (hull.size < 3) continue

            val (rectCorners, shortSide) = minAreaRect(hull)
            if (shortSide < 3.0f) continue

            var sumProb = 0f
            for (pt in points) {
                val px = pt.x.toInt().coerceIn(0, targetW - 1)
                val py = pt.y.toInt().coerceIn(0, targetH - 1)
                sumProb += probMap[py * targetW + px]
            }
            val boxScore = sumProb / points.size
            if (boxScore < 0.6f) continue

            val expandedCorners = unclipBox(rectCorners, unclipRatio = 2.0f)
            val expandedShortSide = calculateShortSide(expandedCorners)
            if (expandedShortSide < 5.0f) continue

            val originalCorners = expandedCorners.map { p ->
                val ox = (p.x * scaleX).coerceIn(0f, (origW - 1).toFloat())
                val oy = (p.y * scaleY).coerceIn(0f, (origH - 1).toFloat())
                Point2D(ox, oy)
            }

            boxes.add(OcrBox(orderPoints(originalCorners)))
            if (boxes.size >= 1000) break
        }

        return sortReadingOrder(boxes)
    }

    private fun computeConvexHull(points: List<Point2D>): List<Point2D> {
        val sorted = points.sortedWith(compareBy({ it.x }, { it.y }))
        val n = sorted.size
        if (n <= 3) return sorted

        val lower = mutableListOf<Point2D>()
        for (p in sorted) {
            while (lower.size >= 2 && crossProduct(lower[lower.size - 2], lower[lower.size - 1], p) <= 0) {
                lower.removeAt(lower.size - 1)
            }
            lower.add(p)
        }

        val upper = mutableListOf<Point2D>()
        for (i in n - 1 downTo 0) {
            val p = sorted[i]
            while (upper.size >= 2 && crossProduct(upper[upper.size - 2], upper[upper.size - 1], p) <= 0) {
                upper.removeAt(upper.size - 1)
            }
            upper.add(p)
        }

        lower.removeAt(lower.size - 1)
        upper.removeAt(upper.size - 1)
        return lower + upper
    }

    private fun crossProduct(o: Point2D, a: Point2D, b: Point2D): Float {
        return (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
    }

    private fun minAreaRect(hull: List<Point2D>): Pair<List<Point2D>, Float> {
        var minArea = Float.MAX_VALUE
        var bestBox = listOf(Point2D(0f, 0f), Point2D(0f, 0f), Point2D(0f, 0f), Point2D(0f, 0f))
        var bestShortSide = 0f

        val n = hull.size
        for (i in 0 until n) {
            val p1 = hull[i]
            val p2 = hull[(i + 1) % n]
            val edgeX = p2.x - p1.x
            val edgeY = p2.y - p1.y
            val edgeLen = hypot(edgeX, edgeY)
            if (edgeLen < 1e-4f) continue

            val uX = edgeX / edgeLen
            val uY = edgeY / edgeLen
            val vX = -uY
            val vY = uX

            var minU = Float.MAX_VALUE
            var maxU = -Float.MAX_VALUE
            var minV = Float.MAX_VALUE
            var maxV = -Float.MAX_VALUE

            for (p in hull) {
                val dx = p.x - p1.x
                val dy = p.y - p1.y
                val u = dx * uX + dy * uY
                val v = dx * vX + dy * vY

                if (u < minU) minU = u
                if (u > maxU) maxU = u
                if (v < minV) minV = v
                if (v > maxV) maxV = v
            }

            val width = maxU - minU
            val height = maxV - minV
            val area = width * height

            if (area < minArea) {
                minArea = area
                bestShortSide = min(width, height)

                val c0 = Point2D(p1.x + minU * uX + minV * vX, p1.y + minU * uY + minV * vY)
                val c1 = Point2D(p1.x + maxU * uX + minV * vX, p1.y + maxU * uY + minV * vY)
                val c2 = Point2D(p1.x + maxU * uX + maxV * vX, p1.y + maxU * uY + maxV * vY)
                val c3 = Point2D(p1.x + minU * uX + maxV * vX, p1.y + minU * uY + maxV * vY)
                bestBox = listOf(c0, c1, c2, c3)
            }
        }

        return Pair(bestBox, bestShortSide)
    }

    private fun unclipBox(box: List<Point2D>, unclipRatio: Float): List<Point2D> {
        if (box.size != 4) return box
        val ordered = orderPoints(box)
        val d01 = distance(ordered[0], ordered[1])
        val d12 = distance(ordered[1], ordered[2])
        val area = d01 * d12
        val perimeter = 2f * (d01 + d12)
        if (perimeter < 1e-4f) return ordered

        val distance = (area * unclipRatio) / perimeter

        val uX = (ordered[1].x - ordered[0].x) / max(d01, 1e-4f)
        val uY = (ordered[1].y - ordered[0].y) / max(d01, 1e-4f)
        val vX = (ordered[2].x - ordered[1].x) / max(d12, 1e-4f)
        val vY = (ordered[2].y - ordered[1].y) / max(d12, 1e-4f)

        val c0 = Point2D(ordered[0].x - distance * uX - distance * vX, ordered[0].y - distance * uY - distance * vY)
        val c1 = Point2D(ordered[1].x + distance * uX - distance * vX, ordered[1].y + distance * uY - distance * vY)
        val c2 = Point2D(ordered[2].x + distance * uX + distance * vX, ordered[2].y + distance * uY + distance * vY)
        val c3 = Point2D(ordered[3].x - distance * uX + distance * vX, ordered[3].y - distance * uY + distance * vY)

        return listOf(c0, c1, c2, c3)
    }

    private fun calculateShortSide(box: List<Point2D>): Float {
        val s1 = distance(box[0], box[1])
        val s2 = distance(box[1], box[2])
        return min(s1, s2)
    }

    private fun distance(p1: Point2D, p2: Point2D): Float {
        return hypot(p2.x - p1.x, p2.y - p1.y)
    }

    private fun orderPoints(pts: List<Point2D>): List<Point2D> {
        var minSumIdx = 0
        var maxSumIdx = 0
        var minSum = pts[0].x + pts[0].y
        var maxSum = minSum

        for (i in 1 until pts.size) {
            val sum = pts[i].x + pts[i].y
            if (sum < minSum) {
                minSum = sum
                minSumIdx = i
            }
            if (sum > maxSum) {
                maxSum = sum
                maxSumIdx = i
            }
        }

        val tl = pts[minSumIdx]
        val br = pts[maxSumIdx]

        val remaining = pts.filterIndexed { i, _ -> i != minSumIdx && i != maxSumIdx }
        val pA = remaining[0]
        val pB = remaining[1]

        val diffA = pA.y - pA.x
        val diffB = pB.y - pB.x

        val tr = if (diffA < diffB) pA else pB
        val bl = if (diffA < diffB) pB else pA

        return listOf(tl, tr, br, bl)
    }

    fun sortReadingOrder(boxes: List<OcrBox>): List<OcrBox> {
        return boxes.sortedWith { b1, b2 ->
            val y1 = b1.points.minOf { it.y }
            val y2 = b2.points.minOf { it.y }
            if (abs(y1 - y2) <= 10f) {
                val x1 = b1.points.minOf { it.x }
                val x2 = b2.points.minOf { it.x }
                x1.compareTo(x2)
            } else {
                y1.compareTo(y2)
            }
        }
    }
}
