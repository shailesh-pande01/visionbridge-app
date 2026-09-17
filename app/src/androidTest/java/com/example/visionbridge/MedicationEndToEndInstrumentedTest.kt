package com.example.visionbridge

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.visionbridge.api.ApiResult
import com.example.visionbridge.data.MedicationRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end integration test executed ON THE REAL ANDROID DEVICE:
 * Loads a real medicine package image, calls MedicationRepository.extractMedication,
 * sends the request over the live network to the deployed Supabase Edge Function (medication-extract),
 * reaches Gemini, parses the returned JSON, and verifies field extraction and confidence.
 * NOTHING IS MOCKED.
 */
@RunWith(AndroidJUnit4::class)
class MedicationEndToEndInstrumentedTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private val sampleMedicineJpeg: ByteArray
        get() = InstrumentationRegistry.getInstrumentation().context.assets
            .open("medicine-sample.jpg")
            .use { it.readBytes() }

    @Test
    fun realDeviceEndToEndMedicationScanFlow() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = MedicationRepository(appContext)

        val imageBytes = sampleMedicineJpeg
        assertTrue("Sample medicine image should not be empty", imageBytes.isNotEmpty())

        val result = runBlocking {
            repository.extractMedication(imageBytes, rotationDegrees = 0, language = "en")
        }

        when (result) {
            is ApiResult.Failure -> {
                fail("Live medication extraction failed: userMessage=${result.error.userMessage}, technicalDetail=${result.error.technicalDetail}")
            }
            is ApiResult.Success -> {
                val extraction = result.value
                assertEquals("CLEAR", extraction.status)
                assertTrue("Confidence should meet or exceed 0.70 threshold", extraction.confidence >= 0.70)
                assertTrue("Extraction must be flagged confident", extraction.isConfident)
                assertNotNull("Medicine name must be extracted", extraction.medicineName)
                assertTrue(
                    "Medicine name should contain Paracetamol",
                    extraction.medicineName!!.contains("Paracetamol", ignoreCase = true)
                )
                assertTrue(
                    "Active ingredients should contain Paracetamol",
                    extraction.activeIngredients.any { it.contains("Paracetamol", ignoreCase = true) }
                )
                assertFalse("Medication with expiry in 2028 should not be expired", extraction.isExpired)
                assertNotNull("Printed directions must be extracted", extraction.printedDirections)
            }
        }
    }
}
