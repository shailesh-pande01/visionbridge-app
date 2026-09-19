package com.example.visionbridge.ocr

import org.junit.Assert.*
import org.junit.Test

class OcrUnitTest {

    @Test
    fun ctcDecoderConstructsLabelsProperly() {
        val dict = listOf("A", "B", "C", "D")
        val decoder = CtcDecoder(dict)

        assertEquals("Total classes should be dict size + 2", 6, decoder.classCount)
        assertEquals("First label must be blank", "blank", decoder.labels[0])
        assertEquals("Index 1 should be A", "A", decoder.labels[1])
        assertEquals("Index 2 should be B", "B", decoder.labels[2])
        assertEquals("Index 3 should be C", "C", decoder.labels[3])
        assertEquals("Index 4 should be D", "D", decoder.labels[4])
        assertEquals("Last label must be space", " ", decoder.labels[5])
    }

    @Test
    fun ctcDecoderDecodesGreedySequenceAndCollapsesRepeats() {
        val dict = listOf("A", "B", "C")
        val decoder = CtcDecoder(dict)
        // 5 classes: 0=blank, 1=A, 2=B, 3=C, 4=space
        val numClasses = 5
        val timeSteps = 6

        // Sequence: blank (0), A (1), A (1), blank (0), B (2), B (2) -> Expected: "AB"
        val probs = FloatArray(timeSteps * numClasses) { 0f }

        fun setStep(t: Int, targetClass: Int, prob: Float) {
            probs[t * numClasses + targetClass] = prob
        }

        setStep(0, 0, 0.99f) // blank
        setStep(1, 1, 0.90f) // A
        setStep(2, 1, 0.80f) // A (collapsed repeat)
        setStep(3, 0, 0.95f) // blank
        setStep(4, 2, 0.85f) // B
        setStep(5, 2, 0.75f) // B (collapsed repeat)

        val result = decoder.decode(probs, timeSteps, numClasses)
        assertEquals("AB", result.text)
        // Average score of kept characters: kept A at t=1 (0.90) and B at t=4 (0.85)
        assertEquals((0.90f + 0.85f) / 2f, result.score, 0.01f)
    }

    @Test(expected = IllegalStateException::class)
    fun ctcDecoderThrowsOnClassCountMismatch() {
        val dict = listOf("A", "B")
        val decoder = CtcDecoder(dict) // expects 4 classes
        val probs = FloatArray(10)
        decoder.decode(probs, timeSteps = 2, numClasses = 5) // passes 5 classes -> throws
    }

    @Test
    fun readingOrderSortsTopToBottomAndLeftToRight() {
        val boxRow1Col1 = OcrBox(listOf(Point2D(10f, 20f), Point2D(50f, 20f), Point2D(50f, 40f), Point2D(10f, 40f)))
        val boxRow1Col2 = OcrBox(listOf(Point2D(70f, 25f), Point2D(120f, 25f), Point2D(120f, 45f), Point2D(70f, 45f))) // within 5px vertically of Col1
        val boxRow2Col1 = OcrBox(listOf(Point2D(15f, 100f), Point2D(60f, 100f), Point2D(60f, 120f), Point2D(15f, 120f))) // >10px below Row 1

        val input = listOf(boxRow2Col1, boxRow1Col2, boxRow1Col1)
        val sorted = sortReadingOrderForTest(input)

        assertEquals("First box should be Row1Col1", boxRow1Col1, sorted[0])
        assertEquals("Second box should be Row1Col2", boxRow1Col2, sorted[1])
        assertEquals("Third box should be Row2Col1", boxRow2Col1, sorted[2])
    }

    private fun sortReadingOrderForTest(boxes: List<OcrBox>): List<OcrBox> {
        return boxes.sortedWith { b1, b2 ->
            val y1 = b1.points.minOf { it.y }
            val y2 = b2.points.minOf { it.y }
            if (kotlin.math.abs(y1 - y2) <= 10f) {
                val x1 = b1.points.minOf { it.x }
                val x2 = b2.points.minOf { it.x }
                x1.compareTo(x2)
            } else {
                y1.compareTo(y2)
            }
        }
    }
}
