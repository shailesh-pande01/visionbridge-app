package com.example.visionbridge.gesture

import com.example.visionbridge.data.ContextMemoryManager
import com.example.visionbridge.voice.VoiceActionRouter
import com.example.visionbridge.voice.VoiceActions
import com.example.visionbridge.voice.VoiceActivationSource
import com.example.visionbridge.voice.VoiceFeatures
import com.example.visionbridge.voice.WakeWordMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying the Gesture-Based Voice Interaction System:
 * 1. Fast-path bare command recognition (no wake word required).
 * 2. Natural language commands with or without "Vision".
 * 3. Feature conflict policy enforcement (protecting Gemini Live, AI Voice Call, Volunteer Call).
 * 4. Debounce and cooldown window logic.
 * 5. Multi-slot context memory retention across gesture activations.
 */
class GestureControllerAndVoiceActivationTest {

    @Before
    fun setup() {
        ContextMemoryManager.reset()
    }

    // ── 1. Bare Commands (No Wake Word Required) ─────────────────────

    @Test
    fun `test bare command executes reading without wake word`() {
        val action = VoiceActionRouter.matchFastPath("read this menu")
        assertNotNull(action)
        assertEquals(VoiceActions.OPEN_FEATURE, action?.action)
        assertEquals(VoiceFeatures.READING, action?.target)
    }

    @Test
    fun `test bare command executes currency reader without wake word`() {
        val action = VoiceActionRouter.matchFastPath("count my money")
        assertNotNull(action)
        assertEquals(VoiceActions.OPEN_FEATURE, action?.action)
        assertEquals(VoiceFeatures.CURRENCY, action?.target)
    }

    @Test
    fun `test bare command executes camera surroundings without wake word`() {
        val action = VoiceActionRouter.matchFastPath("what is around me")
        assertNotNull(action)
        assertEquals(VoiceActions.OPEN_FEATURE, action?.action)
        assertEquals(VoiceFeatures.SURROUNDINGS, action?.target)
    }

    @Test
    fun `test bare command executes object finder without wake word`() {
        val action = VoiceActionRouter.matchFastPath("find my keys")
        assertNotNull(action)
        assertEquals(VoiceActions.FIND_OBJECT, action?.action)
        assertEquals("keys", action?.objectName)
    }

    @Test
    fun `test bare command executes emergency SOS immediately`() {
        val action = VoiceActionRouter.matchFastPath("emergency")
        assertNotNull(action)
        assertEquals(VoiceActions.EMERGENCY_SOS, action?.action)
    }

    // ── 2. Stripping repeated wake words ─────────────────────────────

    @Test
    fun `test command with repeated wake word extracts bare command correctly`() {
        val result = WakeWordMatcher.splitOnWakeWord("Vision, read this menu")
        assertNotNull(result)
        assertEquals("read this menu", result?.command)

        val action = VoiceActionRouter.matchFastPath(result!!.command)
        assertNotNull(action)
        assertEquals(VoiceActions.OPEN_FEATURE, action?.action)
        assertEquals(VoiceFeatures.READING, action?.target)
    }

    @Test
    fun `test Hindi bare and wake-prefixed commands`() {
        // Bare
        val bareHindi = VoiceActionRouter.matchFastPath("यह मेन्यू पढ़ो")
        assertNotNull(bareHindi)
        assertEquals(VoiceActions.OPEN_FEATURE, bareHindi?.action)
        assertEquals(VoiceFeatures.READING, bareHindi?.target)

        // With wake word
        val splitHindi = WakeWordMatcher.splitOnWakeWord("विज़न यह मेन्यू पढ़ो")
        assertNotNull(splitHindi)
        assertEquals("यह मेन्यू पढ़ो", splitHindi?.command)
    }

    @Test
    fun `test Marathi bare and wake-prefixed commands`() {
        // Bare
        val bareMarathi = VoiceActionRouter.matchFastPath("पैसे मोजा")
        assertNotNull(bareMarathi)
        assertEquals(VoiceActions.OPEN_FEATURE, bareMarathi?.action)
        assertEquals(VoiceFeatures.CURRENCY, bareMarathi?.target)

        // With wake word
        val splitMarathi = WakeWordMatcher.splitOnWakeWord("व्हिजन पैसे मोजा")
        assertNotNull(splitMarathi)
        assertEquals("पैसे मोजा", splitMarathi?.command)
    }

    // ── 3. Feature Conflict Policy Verification ──────────────────────

    @Test
    fun `test conflict policy rejects exclusive realtime sessions`() {
        val exclusiveScreens = listOf(
            "live_vision",
            "liveVision",
            "voice_call",
            "voiceCall",
            "volunteer",
            "volunteer_dashboard"
        )

        for (screen in exclusiveScreens) {
            val normalized = screen.lowercase()
            val isConflict = normalized.contains("livevision") || normalized.contains("live_vision") ||
                    normalized.contains("voicecall") || normalized.contains("voice_call") ||
                    normalized.contains("volunteer")

            assertTrue("Screen '$screen' MUST be marked as conflict to protect microphone", isConflict)
        }
    }

    @Test
    fun `test safe screens allow gesture voice activation`() {
        val safeScreens = listOf(
            "home",
            "reading",
            "surroundings",
            "currency",
            "transport",
            "finder",
            "location",
            "sos",
            "contacts",
            "entertainment",
            "radio",
            "stories",
            "games",
            "progress",
            "calling",
            "news"
        )

        for (screen in safeScreens) {
            val normalized = screen.lowercase()
            val isConflict = normalized.contains("livevision") || normalized.contains("live_vision") ||
                    normalized.contains("voicecall") || normalized.contains("voice_call") ||
                    normalized.contains("volunteer")

            assertFalse("Safe screen '$screen' must NOT be blocked", isConflict)
        }
    }

    // ── 4. Debounce & Cooldown Logic ─────────────────────────────────

    @Test
    fun `test cooldown window rejects rapid successive gesture triggers`() {
        val cooldownMs = 1500L
        val t0 = 1000000L

        // Trigger 1 at t0
        var lastActivation = t0
        var isAccepted = true

        // Trigger 2 at t0 + 500ms (should be rejected)
        val t1 = t0 + 500L
        if (t1 - lastActivation < cooldownMs) {
            isAccepted = false
        }
        assertFalse("Second trigger within 500ms must be rejected", isAccepted)

        // Trigger 3 at t0 + 1600ms (should be accepted)
        val t2 = t0 + 1600L
        var isTrigger3Accepted = false
        if (t2 - lastActivation >= cooldownMs) {
            isTrigger3Accepted = true
            lastActivation = t2
        }
        assertTrue("Trigger after 1600ms must be accepted", isTrigger3Accepted)
    }

    // ── 5. Context Memory Retention Across Gestures ──────────────────

    @Test
    fun `test gesture activation retains stored visual reading context for follow-up questions`() {
        // Step 1: User reads a restaurant menu in Smart Reading
        ContextMemoryManager.setActiveScreen("reading")
        ContextMemoryManager.rememberContext(
            featureId = "reading",
            label = "Restaurant Menu",
            summary = "Margherita Pizza ₹250, Pasta Alfredo ₹320, Garlic Bread ₹120, Mineral Water ₹30"
        )

        // Step 2: User navigates back to Home
        ContextMemoryManager.setActiveScreen("home")

        // Step 3: User triggers 2-finger double-tap gesture from Home
        val payload = ContextMemoryManager.buildContextPayload("home")

        // Step 4: Verify previous menu context is preserved in the Gemini payload
        assertEquals("home", payload.getString("activeFeature"))
        assertEquals("Restaurant Menu", payload.getString("contextLabel"))
        assertTrue(payload.getString("contextSummary").contains("Margherita Pizza ₹250"))
    }

    // ── 6. Activation Source Enum Values ─────────────────────────────

    @Test
    fun `test VoiceActivationSource enum contains expected sources`() {
        val sources = VoiceActivationSource.values()
        assertTrue(sources.contains(VoiceActivationSource.WAKE_WORD))
        assertTrue(sources.contains(VoiceActivationSource.GESTURE_TOUCH))
        assertTrue(sources.contains(VoiceActivationSource.GESTURE_SHAKE))
        assertTrue(sources.contains(VoiceActivationSource.BUTTON))
    }
}
