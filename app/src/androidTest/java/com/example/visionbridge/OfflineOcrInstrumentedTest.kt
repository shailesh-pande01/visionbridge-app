package com.example.visionbridge

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.visionbridge.ocr.BitmapDecoder
import com.example.visionbridge.ocr.OfflineOcrEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineOcrInstrumentedTest {

    private val sampleJpeg: ByteArray
        get() = InstrumentationRegistry.getInstrumentation().context.assets
            .open("reading-sample.jpg")
            .use { it.readBytes() }

    @Test
    fun runsOfflineOcrOnSampleImageAndFindsParacetamol() = runBlocking {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val sampleBytes = sampleJpeg

        assertTrue("Sample JPEG should not be empty", sampleBytes.isNotEmpty())

        val decodeStart = System.currentTimeMillis()
        val bitmap = BitmapDecoder.decodeForOcr(sampleBytes, rotationDegrees = 0)
        val decodeElapsed = System.currentTimeMillis() - decodeStart

        assertNotNull("Bitmap must decode successfully", bitmap)
        Log.i(TAG, "Sample bitmap decoded in ${decodeElapsed}ms (${bitmap.width}x${bitmap.height})")

        val engine = OfflineOcrEngine.getInstance(targetContext)
        engine.warmUp()

        val output = engine.read(bitmap)
        bitmap.recycle()

        Log.i(
            TAG,
            "Offline OCR Test Complete: confidence=${output.confidence}, lines=${output.lines.size}, " +
                    "timings=${output.timingsMs}"
        )

        assertFalse("Extracted full text should not be empty", output.fullText.isBlank())
        assertTrue(
            "Extracted text should contain 'PARACETAMOL', got: '${output.fullText}'",
            output.fullText.contains("PARACETAMOL", ignoreCase = true)
        )
    }

    companion object {
        private const val TAG = "VB-OfflineOcr"
    }
}
