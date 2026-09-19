package com.example.visionbridge.ocr

data class Point2D(
    val x: Float,
    val y: Float
)

data class OcrBox(
    val points: List<Point2D>
)

data class OcrLine(
    val text: String,
    val score: Float,
    val box: OcrBox
)

data class OcrOutput(
    val lines: List<OcrLine>,
    val fullText: String,
    val confidence: Double,
    val timingsMs: Map<String, Long>,
    val modelId: String
)
