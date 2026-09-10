package com.example.visionbridge.ai

import org.junit.Assert.*
import org.junit.Test

class ConfidenceEvaluatorTest {

    @Test
    fun testHighConfidenceEvaluation() {
        val level = ConfidenceEvaluator.evaluate(0.95)
        assertEquals(ConfidenceLevel.HIGH, level)
        assertTrue(ConfidenceEvaluator.isReliable(0.95))
    }

    @Test
    fun testHighConfidenceBoundary() {
        val level = ConfidenceEvaluator.evaluate(0.85)
        assertEquals(ConfidenceLevel.HIGH, level)
        assertTrue(ConfidenceEvaluator.isReliable(0.85))
    }

    @Test
    fun testMediumConfidenceEvaluation() {
        val level = ConfidenceEvaluator.evaluate(0.78)
        assertEquals(ConfidenceLevel.MEDIUM, level)
        assertTrue(ConfidenceEvaluator.isReliable(0.78))
    }

    @Test
    fun testMediumConfidenceBoundary() {
        val level = ConfidenceEvaluator.evaluate(0.70)
        assertEquals(ConfidenceLevel.MEDIUM, level)
        assertTrue(ConfidenceEvaluator.isReliable(0.70))
    }

    @Test
    fun testLowConfidenceEvaluation() {
        val level = ConfidenceEvaluator.evaluate(0.69)
        assertEquals(ConfidenceLevel.LOW, level)
        assertFalse(ConfidenceEvaluator.isReliable(0.69))
    }

    @Test
    fun testSafetyFallbackEvaluation() {
        // Web app and Gemini fallback return 0.50
        val level = ConfidenceEvaluator.evaluate(0.50)
        assertEquals(ConfidenceLevel.LOW, level)
        assertFalse(ConfidenceEvaluator.isReliable(0.50))
    }

    @Test
    fun testNullConfidenceDefaultsToLow() {
        val level = ConfidenceEvaluator.evaluate(null)
        assertEquals(ConfidenceLevel.LOW, level)
        assertFalse(ConfidenceEvaluator.isReliable(null))
    }

    @Test
    fun testScoreClamping() {
        assertEquals(ConfidenceLevel.LOW, ConfidenceEvaluator.evaluate(-0.5))
        assertEquals(ConfidenceLevel.HIGH, ConfidenceEvaluator.evaluate(1.5))
    }

    @Test
    fun testHumanFallbackCoordinatorDeclinedSuppression() {
        val coordinator = HumanFallbackCoordinator("Describe Surroundings")
        val signature = "Dining table_Scene with low lighting"

        // 1. First low-confidence result -> should offer fallback
        assertTrue(coordinator.shouldOfferFallback(0.45, signature))

        // 2. User declines fallback -> record it
        coordinator.markFallbackDeclined(signature)

        // 3. Repeating same result in session -> should NOT offer fallback again (avoids frustrating loop)
        assertFalse(coordinator.shouldOfferFallback(0.45, signature))

        // 4. A different scene result arrives -> should offer fallback for the new scene
        val newSignature = "Hallway_Different scene"
        assertTrue(coordinator.shouldOfferFallback(0.40, newSignature))

        // 5. Session reset (e.g. user triggers fresh capture flow) -> should offer again
        coordinator.resetSession()
        assertTrue(coordinator.shouldOfferFallback(0.45, signature))
    }

    @Test
    fun testFallbackContextFormatting() {
        val coordinator = HumanFallbackCoordinator("Describe Surroundings")
        val context = coordinator.buildFallbackContext(
            confidence = 0.52,
            reason = "Scene unclear",
            originalPrompt = "Kitchen counter"
        )

        assertEquals("Describe Surroundings", context.sourceFeature)
        assertEquals(0.52, context.confidence ?: 0.0, 0.001)
        assertEquals(ConfidenceLevel.LOW, context.level)

        val desc = context.toHelpDescription()
        assertTrue(desc.contains("[AI Fallback - Describe Surroundings]"))
        assertTrue(desc.contains("52%"))
        assertTrue(desc.contains("Scene unclear"))
    }
}
