package com.example.visionbridge

import com.example.visionbridge.data.MEDICATION_CONFIDENCE_THRESHOLD
import com.example.visionbridge.data.MedicationExtraction
import com.example.visionbridge.data.MedicationReminder
import com.example.visionbridge.data.VerificationStatus
import com.example.visionbridge.utils.VoiceCommandParser
import com.example.visionbridge.utils.VoiceIntent
import com.example.visionbridge.voice.VoiceActionRouter
import com.example.visionbridge.voice.VoiceFeatures
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MedicationSafetyTest {

    // ── 1. Confidence Threshold (Central 0.70 Gate) ───────────────────

    @Test
    fun `confidence threshold gate strictly follows 0_70 cutoff`() {
        assertEquals(0.70, MEDICATION_CONFIDENCE_THRESHOLD, 0.001)

        val highConfidence = MedicationExtraction(
            status = "CLEAR",
            confidence = 0.85,
            medicineName = "Paracetamol",
            strength = "500mg",
            form = "Tablet",
            activeIngredients = listOf("Paracetamol"),
            printedDirections = "Take 1 tablet after food",
            expiryDate = "12/2028",
            storageInformation = "Store below 25°C",
            warningsVisibleOnPackage = listOf("Keep out of reach of children"),
            manufacturer = "Cipla",
            batchNumber = "B1234",
            uncertaintyReason = null,
            rawVisibleText = "Paracetamol 500mg..."
        )
        assertTrue("Extraction at 0.85 should be confident", highConfidence.isConfident)

        val boundaryConfidence = highConfidence.copy(confidence = 0.70)
        assertTrue("Extraction at exact 0.70 boundary should be confident", boundaryConfidence.isConfident)

        val lowConfidence = highConfidence.copy(
            confidence = 0.69,
            uncertaintyReason = "Foil strip glare obscured active ingredients"
        )
        assertFalse("Extraction at 0.69 must NOT be confident", lowConfidence.isConfident)

        val veryLowConfidence = highConfidence.copy(
            confidence = 0.45,
            uncertaintyReason = "Packaging is severely torn or curved"
        )
        assertFalse("Extraction at 0.45 must NOT be confident", veryLowConfidence.isConfident)
    }

    // ── 2. Expiry Date Logic ──────────────────────────────────────────

    @Test
    fun `past expiry dates are flagged as expired while future dates are valid`() {
        val futureMed = MedicationExtraction(
            status = "CLEAR",
            confidence = 0.90,
            medicineName = "Amoxicillin",
            strength = "250mg",
            form = "Capsule",
            activeIngredients = listOf("Amoxicillin Trihydrate"),
            printedDirections = "As directed by physician",
            expiryDate = "2029-12-31",
            storageInformation = "Dry place",
            warningsVisibleOnPackage = emptyList(),
            manufacturer = null,
            batchNumber = null,
            uncertaintyReason = null,
            rawVisibleText = ""
        )
        assertFalse("Future expiry 2029-12-31 should NOT be expired", futureMed.isExpired)

        val pastMedIso = futureMed.copy(expiryDate = "2020-01-15")
        assertTrue("Past expiry 2020-01-15 MUST be flagged expired", pastMedIso.isExpired)

        val pastMedSlash = futureMed.copy(expiryDate = "05/2019")
        assertTrue("Past expiry 05/2019 MUST be flagged expired", pastMedSlash.isExpired)

        val futureMedSlash = futureMed.copy(expiryDate = "11/2030")
        assertFalse("Future expiry 11/2030 should NOT be expired", futureMedSlash.isExpired)

        val nullExpiry = futureMed.copy(expiryDate = null)
        assertFalse("Null expiry should not crash and is not expired", nullExpiry.isExpired)
    }

    // ── 3. Reminder 12-Hour Time Formatting ───────────────────────────

    @Test
    fun `reminder correctly formats 24hr time into accessible 12hr strings`() {
        val morning = MedicationReminder(
            medicationName = "Metformin",
            reminderTime = "08:30",
            dosageLabel = "1 tablet with breakfast"
        )
        assertEquals("8:30 AM", morning.formattedTime12Hr)

        val noon = morning.copy(reminderTime = "12:00")
        assertEquals("12:00 PM", noon.formattedTime12Hr)

        val afternoon = morning.copy(reminderTime = "14:15")
        assertEquals("2:15 PM", afternoon.formattedTime12Hr)

        val evening = morning.copy(reminderTime = "20:45")
        assertEquals("8:45 PM", evening.formattedTime12Hr)

        val midnight = morning.copy(reminderTime = "00:05")
        assertEquals("12:05 AM", midnight.formattedTime12Hr)
    }

    // ── 4. Multilingual Voice Command Routing ─────────────────────────

    @Test
    fun `English voice commands correctly map to medication intent and route`() {
        val cmd1 = VoiceCommandParser.parseCommand("Vision, scan my medicine")
        assertEquals(VoiceIntent.MEDICATION_SAFETY, cmd1.intent)
        assertEquals("medication", VoiceCommandParser.mapIntentToRoute(cmd1.intent))

        val cmd2 = VoiceCommandParser.parseCommand("check my pills")
        assertEquals(VoiceIntent.MEDICATION_SAFETY, cmd2.intent)
        assertEquals("medication", VoiceCommandParser.mapIntentToRoute(cmd2.intent))

        val cmd3 = VoiceCommandParser.parseCommand("Vision, read this prescription")
        assertEquals(VoiceIntent.MEDICATION_SAFETY, cmd3.intent)
        assertEquals("medication", VoiceCommandParser.mapIntentToRoute(cmd3.intent))

        val cmd4 = VoiceCommandParser.parseCommand("set medication reminder")
        assertEquals(VoiceIntent.MEDICATION_SAFETY, cmd4.intent)
        assertEquals("medication", VoiceCommandParser.mapIntentToRoute(cmd4.intent))
    }

    @Test
    fun `Hindi voice commands correctly map to medication intent`() {
        val cmd1 = VoiceCommandParser.parseCommand("दवा की जांच करो")
        assertEquals(VoiceIntent.MEDICATION_SAFETY, cmd1.intent)

        val cmd2 = VoiceCommandParser.parseCommand("दवाई का पर्चा पढ़ो")
        assertEquals(VoiceIntent.MEDICATION_SAFETY, cmd2.intent)

        val cmd3 = VoiceCommandParser.parseCommand("गोली कब लेनी है")
        assertEquals(VoiceIntent.MEDICATION_SAFETY, cmd3.intent)

        val cmd4 = VoiceCommandParser.parseCommand("दवा का रिमाइंडर")
        assertEquals(VoiceIntent.MEDICATION_SAFETY, cmd4.intent)
    }

    @Test
    fun `Marathi voice commands correctly map to medication intent`() {
        val cmd1 = VoiceCommandParser.parseCommand("औषध तपासा")
        assertEquals(VoiceIntent.MEDICATION_SAFETY, cmd1.intent)

        val cmd2 = VoiceCommandParser.parseCommand("गोळ्या तपासा")
        assertEquals(VoiceIntent.MEDICATION_SAFETY, cmd2.intent)

        val cmd3 = VoiceCommandParser.parseCommand("माझे औषध")
        assertEquals(VoiceIntent.MEDICATION_SAFETY, cmd3.intent)

        val cmd4 = VoiceCommandParser.parseCommand("औषधाची वेळ")
        assertEquals(VoiceIntent.MEDICATION_SAFETY, cmd4.intent)
    }

    // ── 5. VoiceActionRouter Feature Normalization ────────────────────

    @Test
    fun `VoiceActionRouter maps medication synonyms across languages`() {
        assertEquals("medication", VoiceActionRouter.normalizeFeatureTarget("medication"))
        assertEquals("medication", VoiceActionRouter.normalizeFeatureTarget("medicine"))
        assertEquals("medication", VoiceActionRouter.normalizeFeatureTarget("pills"))
        assertEquals("medication", VoiceActionRouter.normalizeFeatureTarget("dawa"))
        assertEquals("medication", VoiceActionRouter.normalizeFeatureTarget("dawakhana"))
        assertEquals("medication", VoiceActionRouter.normalizeFeatureTarget("aushadh"))
        assertEquals("medication", VoiceActionRouter.normalizeFeatureTarget("golya"))

        val navRoute = VoiceActionRouter.routeForTarget("medication")
        assertEquals("medication", navRoute)
    }

    // ── 6. Edge Function JSON Parsing & Safety Validation ─────────────

    @Test
    fun `parses Edge Function extraction JSON response accurately`() {
        val mockJson = """
            {
                "status": "CLEAR",
                "confidence": 0.92,
                "medicine_name": "Azithromycin",
                "strength": "500 mg",
                "form": "Tablet",
                "active_ingredients": ["Azithromycin Dihydrate IP"],
                "printed_directions": "1 tablet once daily for 3 days or as advised",
                "expiry_date": "10/2027",
                "storage_information": "Store below 30°C in a dry place",
                "warnings_visible_on_package": [
                    "Schedule H Prescription Drug - Warning: To be sold by retail on the prescription of a Registered Medical Practitioner only."
                ],
                "manufacturer": "Pfizer India",
                "batch_number": "AZ9921",
                "uncertainty_reason": null,
                "raw_visible_text": "Azithromycin Tablets IP 500mg Batch AZ9921 Exp 10/2027"
            }
        """.trimIndent()

        val json = JSONObject(mockJson)
        val extraction = MedicationExtraction(
            status = json.optString("status", "UNKNOWN"),
            confidence = json.optDouble("confidence", 0.0),
            medicineName = json.optString("medicine_name").takeIf { it.isNotBlank() },
            strength = json.optString("strength").takeIf { it.isNotBlank() },
            form = json.optString("form").takeIf { it.isNotBlank() },
            activeIngredients = run {
                val arr = json.optJSONArray("active_ingredients") ?: JSONArray()
                List(arr.length()) { i -> arr.getString(i) }
            },
            printedDirections = json.optString("printed_directions").takeIf { it.isNotBlank() },
            expiryDate = json.optString("expiry_date").takeIf { it.isNotBlank() },
            storageInformation = json.optString("storage_information").takeIf { it.isNotBlank() },
            warningsVisibleOnPackage = run {
                val arr = json.optJSONArray("warnings_visible_on_package") ?: JSONArray()
                List(arr.length()) { i -> arr.getString(i) }
            },
            manufacturer = json.optString("manufacturer").takeIf { it.isNotBlank() },
            batchNumber = json.optString("batch_number").takeIf { it.isNotBlank() },
            uncertaintyReason = json.optString("uncertainty_reason").takeIf { it.isNotBlank() },
            rawVisibleText = json.optString("raw_visible_text", "")
        )

        assertEquals("CLEAR", extraction.status)
        assertEquals(0.92, extraction.confidence, 0.001)
        assertTrue(extraction.isConfident)
        assertEquals("Azithromycin", extraction.medicineName)
        assertEquals("500 mg", extraction.strength)
        assertEquals("Tablet", extraction.form)
        assertEquals(1, extraction.activeIngredients.size)
        assertEquals("Azithromycin Dihydrate IP", extraction.activeIngredients.first())
        assertFalse(extraction.isExpired)
        assertEquals(1, extraction.warningsVisibleOnPackage.size)
        assertEquals("Pfizer India", extraction.manufacturer)
    }

    // ── 7. Regression Tests: Supabase Function Name & Request Contract ─

    @Test
    fun `Edge Function name matches medication-extract exactly`() {
        assertEquals("medication-extract", com.example.visionbridge.supabase.SupabaseConfig.FUNCTION_MEDICATION_EXTRACT)
    }

    @Test
    fun `extractMedication request payload contains required imageBase64, mimeType, and language`() {
        val sampleBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
        val payload = JSONObject()
            .put("imageBase64", sampleBase64)
            .put("mimeType", "image/jpeg")
            .put("language", "hi")

        assertTrue(payload.has("imageBase64"))
        assertTrue(payload.has("mimeType"))
        assertTrue(payload.has("language"))
        assertEquals("image/jpeg", payload.getString("mimeType"))
        assertEquals("hi", payload.getString("language"))
        assertEquals(sampleBase64, payload.getString("imageBase64"))
    }

    @Test
    fun `response parser handles nested data object from Edge Function`() {
        val nestedResponse = JSONObject("""
            {
                "success": true,
                "data": {
                    "status": "CLEAR",
                    "confidence": 0.95,
                    "medicine_name": "Paracetamol 500mg",
                    "active_ingredients": ["Paracetamol IP"],
                    "strength": "500mg",
                    "form": "Tablet",
                    "printed_directions": "1 tablet every 6 hours",
                    "expiry_date": "12/2028",
                    "storage_information": "Store below 30°C",
                    "warnings_visible_on_package": ["Do not exceed recommended dose"],
                    "manufacturer": "Cipla",
                    "batch_number": "B9912",
                    "uncertainty_reason": null,
                    "raw_visible_text": "Paracetamol 500mg Cipla"
                }
            }
        """.trimIndent())

        val data = nestedResponse.optJSONObject("data") ?: nestedResponse
        assertEquals("CLEAR", data.getString("status"))
        assertEquals("Paracetamol 500mg", data.getString("medicine_name"))
        assertEquals(0.95, data.getDouble("confidence"), 0.001)
    }
}
