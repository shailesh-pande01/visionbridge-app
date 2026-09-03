package com.example.visionbridge.voice

import com.example.visionbridge.data.ContextMemoryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VoiceFollowUpContextTest {

    @Before
    fun setup() {
        ContextMemoryManager.reset()
    }

    @Test
    fun `test multi-slot context storage and retrieval`() {
        ContextMemoryManager.rememberContext("reading", "Extracted text", "Margherita Pizza - Rs 250\nPasta Alfredo - Rs 320")
        ContextMemoryManager.rememberContext("surroundings", "Scene", "A bright living room with a brown couch and a coffee table")
        ContextMemoryManager.rememberContext("currency", "Currency detected", "Total amount 600 rupees: 1 note of 500, 1 note of 100")

        assertTrue(ContextMemoryManager.hasContextFor("reading"))
        assertTrue(ContextMemoryManager.hasContextFor("surroundings"))
        assertTrue(ContextMemoryManager.hasContextFor("currency"))

        val readingSlot = ContextMemoryManager.getSlot("reading")
        assertNotNull(readingSlot)
        assertEquals("Extracted text", readingSlot?.label)
        assertTrue(readingSlot?.summary?.contains("Margherita Pizza") == true)

        val surroundingsSlot = ContextMemoryManager.getSlot("surroundings")
        assertNotNull(surroundingsSlot)
        assertTrue(surroundingsSlot?.summary?.contains("brown couch") == true)

        val currencySlot = ContextMemoryManager.getSlot("currency")
        assertNotNull(currencySlot)
        assertTrue(currencySlot?.summary?.contains("600 rupees") == true)
    }

    @Test
    fun `test context replacement on consecutive captures`() {
        ContextMemoryManager.rememberContext("reading", "Menu 1", "Breakfast Menu: Eggs, Toast, Coffee")
        assertEquals("Breakfast Menu: Eggs, Toast, Coffee", ContextMemoryManager.getSlot("reading")?.summary)

        // Capture new page
        ContextMemoryManager.rememberContext("reading", "Menu 2", "Dinner Menu: Steak, Salmon, Wine")
        val updatedSlot = ContextMemoryManager.getSlot("reading")
        assertEquals("Dinner Menu: Steak, Salmon, Wine", updatedSlot?.summary)
        assertEquals("Menu 2", updatedSlot?.label)
    }

    @Test
    fun `test non-destructive screen navigation with home screen fallback`() {
        // User reads a document on reading screen
        ContextMemoryManager.setActiveScreen("reading")
        ContextMemoryManager.rememberContext("reading", "Flight Ticket", "Flight AI-101 from Mumbai to Delhi, Gate 4B, Boarding 10:30 AM")

        // User navigates back to Home screen
        ContextMemoryManager.setActiveScreen("home")

        // Memory slots must NOT be wiped
        assertTrue(ContextMemoryManager.hasContextFor("reading"))

        // When asking a follow-up from Home, payload intelligently falls back to the most recent slot
        val homePayload = ContextMemoryManager.buildContextPayload("home")
        assertEquals("home", homePayload.getString("activeFeature"))
        assertEquals("Flight Ticket", homePayload.getString("contextLabel"))
        assertTrue(homePayload.getString("contextSummary").contains("Flight AI-101"))
    }

    @Test
    fun `test rolling conversation turns capping`() {
        ContextMemoryManager.addTurn("Turn 1 question", "Turn 1 answer")
        ContextMemoryManager.addTurn("Turn 2 question", "Turn 2 answer")
        ContextMemoryManager.addTurn("Turn 3 question", "Turn 3 answer")
        ContextMemoryManager.addTurn("Turn 4 question", "Turn 4 answer")
        ContextMemoryManager.addTurn("Turn 5 question", "Turn 5 answer")

        val payload = ContextMemoryManager.toJson()
        val turns = payload.getJSONArray("recentTurns")

        // Max turns should be 4
        assertEquals(4, turns.length())

        // Turn 1 should have been evicted
        val firstTurn = turns.getJSONObject(0)
        assertEquals("Turn 2 question", firstTurn.getString("user"))
        assertEquals("Turn 2 answer", firstTurn.getString("assistant"))

        val lastTurn = turns.getJSONObject(3)
        assertEquals("Turn 5 question", lastTurn.getString("user"))
        assertEquals("Turn 5 answer", lastTurn.getString("assistant"))
    }

    @Test
    fun `test single feature clearing and reset`() {
        ContextMemoryManager.rememberContext("reading", "Menu", "Burger Rs 150")
        ContextMemoryManager.rememberContext("currency", "Cash", "500 rupees")

        ContextMemoryManager.clearFeatureContext("reading")
        assertFalse(ContextMemoryManager.hasContextFor("reading"))
        assertTrue(ContextMemoryManager.hasContextFor("currency"))

        ContextMemoryManager.reset()
        assertFalse(ContextMemoryManager.hasContextFor("currency"))
        assertEquals("home", ContextMemoryManager.activeFeature)
    }

    @Test
    fun `test serialization matches required JSON schema`() {
        ContextMemoryManager.setActiveScreen("reading")
        ContextMemoryManager.rememberContext("reading", "Book Page", "Chapter 1: The Beginning.")
        ContextMemoryManager.addTurn("What chapter is this?", "This is Chapter 1.")

        val json = ContextMemoryManager.toJson()
        assertEquals("reading", json.getString("activeFeature"))
        assertEquals("Book Page", json.getString("contextLabel"))
        assertEquals("Chapter 1: The Beginning.", json.getString("contextSummary"))
        assertTrue(json.has("contextAgeSeconds"))
        assertTrue(json.getJSONArray("recentTurns").length() == 1)
    }
}
