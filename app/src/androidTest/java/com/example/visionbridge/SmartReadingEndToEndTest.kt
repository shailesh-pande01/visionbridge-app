package com.example.visionbridge

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.visionbridge.api.ApiResult
import com.example.visionbridge.api.BackendLocator
import com.example.visionbridge.data.SmartReadingRepository
import com.example.visionbridge.utils.ImageHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the real Smart Reading path on a real device: optimize a JPEG, POST it to
 * the running VisionBridge MERN backend, let the backend's Gemini call run, and parse
 * what comes back. Nothing here is mocked.
 *
 * The backend must be reachable from the device before running:
 *   · `node server.js` in the web project's server folder
 *   · emulator → nothing else needed (10.0.2.2)
 *   · USB device → `adb reverse tcp:5000 tcp:5000`
 *   · Wi-Fi device → set visionbridge.lanHost in gradle.properties
 */
@RunWith(AndroidJUnit4::class)
class SmartReadingEndToEndTest {

    /**
     * Keeps a real Activity in front while the tests run. Without it the instrumentation
     * process has no foreground component, and the platform stalls the reply to a request
     * that takes tens of seconds — the socket simply never delivers. The shipped screen
     * always has an Activity, so this makes the test match how the feature actually runs.
     */
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private val sampleJpeg: ByteArray
        get() = InstrumentationRegistry.getInstrumentation().context.assets
            .open("reading-sample.jpg")
            .use { it.readBytes() }

    @Test
    fun backendIsReachableFromThisDevice() {
        when (val resolution = BackendLocator.resolve()) {
            is BackendLocator.Resolution.Found ->
                assertTrue("Resolved a usable base URL", resolution.baseUrl.startsWith("http://"))

            is BackendLocator.Resolution.NotFound ->
                fail("No VisionBridge backend answered. Attempts: ${resolution.attempts}")
        }
    }

    @Test
    fun optimizationMatchesTheWebClientBudget() {
        val optimized = ImageHelper.optimizeAndEncode(sampleJpeg, rotationDegrees = 0)

        assertEquals("image/jpeg", optimized.mimeType)
        assertTrue(
            "Longest side should be capped at 1024 like the web client, was " +
                    "${optimized.width}x${optimized.height}",
            maxOf(optimized.width, optimized.height) <= 1024
        )
        assertTrue("Base64 payload should not be empty", optimized.base64.isNotEmpty())
        assertTrue(
            "Payload must stay well under the backend's 14,000,000 char limit, was " +
                    "${optimized.base64.length}",
            optimized.base64.length < 14_000_000
        )
    }

    /**
     * Diagnostic: same payload size, but an invalid mimeType so the backend's
     * validateReadingRequest rejects it immediately with 400. Isolates "large upload"
     * from "slow response" when the transport is under suspicion.
     */
    @Test
    fun largeUploadWithAnImmediateRejectionRoundTrips() {
        val optimized = ImageHelper.optimizeAndEncode(sampleJpeg, rotationDegrees = 0)
        val started = System.currentTimeMillis()

        val result = com.example.visionbridge.api.SmartReadingApi()
            .extractText(optimized.base64, "image/bmp")
        val elapsed = System.currentTimeMillis() - started

        when (result) {
            is ApiResult.Success<*> -> fail("Backend should have rejected mimeType image/bmp")
            is ApiResult.Failure -> assertTrue(
                "Expected an immediate 400, got ${result.error.kind} after ${elapsed}ms: " +
                        result.error.technicalDetail,
                result.error.technicalDetail.contains("HTTP 400")
            )
        }
    }

    @Test
    fun extractsRealTextFromTheRunningBackend() = runBlocking {
        val result = SmartReadingRepository().extractText(sampleJpeg, rotationDegrees = 0)

        when (result) {
            is ApiResult.Success -> {
                val text = result.value.extractedText
                assertTrue(
                    "Backend returned no text. message=${result.value.message}",
                    !text.isNullOrBlank()
                )
                assertTrue(
                    "Extracted text should contain the sample's heading, got: $text",
                    text!!.contains("PARACETAMOL", ignoreCase = true)
                )
            }

            is ApiResult.Failure -> fail(
                "Extraction failed: ${result.error.kind} — ${result.error.userMessage} " +
                        "(${result.error.technicalDetail})"
            )
        }
    }
}
