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

    @Test
    fun `test ReadingSession lifecycle and replacement on new capture`() {
        val session1 = com.example.visionbridge.data.ReadingSession(
            sessionId = "session-1",
            extractedText = "Restaurant Menu Page 1: Soup Rs 100",
            language = "en"
        )
        ContextMemoryManager.setActiveReadingSession(session1)
        assertTrue(ContextMemoryManager.hasActiveReadingSession())
        assertEquals("Restaurant Menu Page 1: Soup Rs 100", ContextMemoryManager.getActiveReadingSession()?.extractedText)

        // New document scanned -> session replaced
        val session2 = com.example.visionbridge.data.ReadingSession(
            sessionId = "session-2",
            extractedText = "Prescription: Take 1 tablet after meals",
            language = "en"
        )
        ContextMemoryManager.setActiveReadingSession(session2)
        assertEquals("session-2", ContextMemoryManager.getActiveReadingSession()?.sessionId)
        assertEquals("Prescription: Take 1 tablet after meals", ContextMemoryManager.getActiveReadingSession()?.extractedText)

        ContextMemoryManager.clearReadingSession()
        assertFalse(ContextMemoryManager.hasActiveReadingSession())
        assertNull(ContextMemoryManager.getActiveReadingSession())
    }

    @Test
    fun `test buildContextPayloadForCommand isolates Home commands from stale Reading context`() {
        // User captured text on reading screen
        ContextMemoryManager.setActiveScreen("reading")
        ContextMemoryManager.rememberContext("reading", "Menu", "Burger Rs 150, Fries Rs 80")

        // User navigates back to Home
        ContextMemoryManager.setActiveScreen("home")

        // 1. General command on Home -> contextSummary MUST BE EMPTY so Gemini does not assume it's a question about the menu
        val cmdPayload = ContextMemoryManager.buildContextPayloadForCommand("open camera")
        assertEquals("home", cmdPayload.getString("activeFeature"))
        assertEquals("", cmdPayload.getString("contextSummary"))

        // 2. Explicit follow-up question asked from Home -> allows accessing previous context
        val followUpPayload = ContextMemoryManager.buildContextPayloadForCommand("how much was the burger?")
        assertEquals("home", followUpPayload.getString("activeFeature"))
        assertTrue(followUpPayload.getString("contextSummary").contains("Burger Rs 150"))
    }

    @Test
    fun `test isFollowUpInquiry detects questions accurately in English Hindi and Marathi`() {
        // English questions
        assertTrue(ContextMemoryManager.isFollowUpInquiry("what is the price of the coffee?"))
        assertTrue(ContextMemoryManager.isFollowUpInquiry("how much does the pizza cost"))
        assertTrue(ContextMemoryManager.isFollowUpInquiry("summarize this document"))
        assertTrue(ContextMemoryManager.isFollowUpInquiry("can you explain the dosage"))
        assertTrue(ContextMemoryManager.isFollowUpInquiry("read it again"))

        // Hindi questions
        assertTrue(ContextMemoryManager.isFollowUpInquiry("इसकी कीमत क्या है?"))
        assertTrue(ContextMemoryManager.isFollowUpInquiry("दवाई कब लेनी है"))
        assertTrue(ContextMemoryManager.isFollowUpInquiry("इसका मतलब क्या है"))

        // Marathi questions
        assertTrue(ContextMemoryManager.isFollowUpInquiry("याची किंमत काय आहे?"))
        assertTrue(ContextMemoryManager.isFollowUpInquiry("औषध कसे घ्यायचे"))

        // Extended questions
        assertTrue(ContextMemoryManager.isFollowUpInquiry("does this menu have pasta?"))
        assertTrue(ContextMemoryManager.isFollowUpInquiry("is there anything under 200 rupees?"))
        assertTrue(ContextMemoryManager.isFollowUpInquiry("which pasta is the cheapest?"))
        assertTrue(ContextMemoryManager.isFollowUpInquiry("how much is it?"))
        assertTrue(ContextMemoryManager.isFollowUpInquiry("what about that one?"))
        assertTrue(ContextMemoryManager.isFollowUpInquiry("can you tell me the price of pasta?"))
        assertTrue(ContextMemoryManager.isFollowUpInquiry("price of pasta"))

        // Explicit navigation commands must NOT be identified as follow-up inquiries
        assertFalse(ContextMemoryManager.isFollowUpInquiry("open camera"))
        assertFalse(ContextMemoryManager.isFollowUpInquiry("count money"))
        assertFalse(ContextMemoryManager.isFollowUpInquiry("go home"))
        assertFalse(ContextMemoryManager.isFollowUpInquiry("call a volunteer"))
        assertFalse(ContextMemoryManager.isFollowUpInquiry("emergency"))
    }

    @Test
    fun `test SmartReadingGroundedQa restaurant menu all 7 examples and reasoning`() {
        val menuText = """
            SUNSET RESTAURANT

            Starters
            Veg Spring Roll - ₹120
            Paneer Tikka - ₹180

            Main Course
            Pasta Alfredo - ₹250
            Penne Arrabbiata - ₹280
            Veg Pasta - ₹220

            Desserts
            Chocolate Cake - ₹150
        """.trimIndent()

        // Example 1: Direct lookup for Pasta Alfredo
        val res1 = SmartReadingGroundedQa.answer(menuText, "What is the price of Pasta Alfredo?")
        assertTrue(res1.answered)
        assertEquals("Pasta Alfredo costs ₹250.", res1.answer)
        assertFalse("Must never acknowledge", res1.answer.contains("I'll check", ignoreCase = true))

        // Example 2: How much is the veg pasta
        val res2 = SmartReadingGroundedQa.answer(menuText, "How much is the veg pasta?")
        assertTrue(res2.answered)
        assertEquals("Veg Pasta costs ₹220.", res2.answer)

        // Example 3: Pasta options
        val res3 = SmartReadingGroundedQa.answer(menuText, "What are the pasta options?")
        assertTrue(res3.answered)
        assertTrue("Must mention Pasta Alfredo ₹250", res3.answer.contains("Pasta Alfredo for ₹250"))
        assertTrue("Must mention Penne Arrabbiata for ₹280", res3.answer.contains("Penne Arrabbiata for ₹280"))
        assertTrue("Must mention Veg Pasta for ₹220", res3.answer.contains("Veg Pasta for ₹220"))

        // Example 4: Cheapest pasta calculation
        val res4 = SmartReadingGroundedQa.answer(menuText, "Which pasta is the cheapest?")
        assertTrue(res4.answered)
        assertEquals("Veg Pasta is the cheapest at ₹220.", res4.answer)

        // Example 5: Most expensive item on menu
        val res5 = SmartReadingGroundedQa.answer(menuText, "What is the most expensive item on the menu?")
        assertTrue(res5.answered)
        assertEquals("Penne Arrabbiata is the most expensive at ₹280.", res5.answer)

        // Example 6: Existence of pasta
        val res6 = SmartReadingGroundedQa.answer(menuText, "Does this menu have pasta?")
        assertTrue(res6.answered)
        assertTrue("Must confirm pasta existence", res6.answer.startsWith("Yes. It has"))
        assertTrue("Must list Pasta Alfredo", res6.answer.contains("Pasta Alfredo"))
        assertTrue("Must list Penne Arrabbiata", res6.answer.contains("Penne Arrabbiata"))
        assertTrue("Must list Veg Pasta", res6.answer.contains("Veg Pasta"))

        // Example 7: Price threshold filtering under 200 rupees
        val res7 = SmartReadingGroundedQa.answer(menuText, "Is there anything under 200 rupees?")
        assertTrue(res7.answered)
        assertTrue("Must start with Yes", res7.answer.startsWith("Yes."))
        assertTrue("Must include Veg Spring Roll", res7.answer.contains("Veg Spring Roll is ₹120"))
        assertTrue("Must include Paneer Tikka", res7.answer.contains("Paneer Tikka is ₹180"))
        assertTrue("Must include Chocolate Cake", res7.answer.contains("Chocolate Cake is ₹150"))
        assertFalse("Must NOT include items >= 200", res7.answer.contains("Pasta Alfredo"))

        // Counting query
        val resCount = SmartReadingGroundedQa.answer(menuText, "How many pasta dishes are there?")
        assertTrue(resCount.answered)
        assertEquals("There are three pasta dishes.", resCount.answer)

        // Missing item lookup (Sushi)
        val resMissing = SmartReadingGroundedQa.answer(menuText, "What is the price of sushi?")
        assertTrue(resMissing.answered)
        assertEquals("I don't see sushi in the text I captured.", resMissing.answer)
    }

    @Test
    fun `test SmartReadingGroundedQa receipt and phone number lookups`() {
        val receiptText = """
            CAFE MOCHA
            Coffee ₹80
            Sandwich ₹120
            Total ₹200
        """.trimIndent()

        val sandwichRes = SmartReadingGroundedQa.answer(receiptText, "How much was the sandwich?")
        assertTrue(sandwichRes.answered)
        assertTrue(sandwichRes.answer.contains("₹120"))

        val totalRes = SmartReadingGroundedQa.answer(receiptText, "What was the total?")
        assertTrue(totalRes.answered)
        assertEquals("The total was ₹200.", totalRes.answer)

        val docWithPhone = """
            Customer Service Center
            Please call our helpline: 9876543210
            Office Hours: 9 AM to 6 PM
        """.trimIndent()

        val phoneRes = SmartReadingGroundedQa.answer(docWithPhone, "What is the phone number?")
        assertTrue(phoneRes.answered)
        assertTrue(phoneRes.answer.contains("9876543210"))
    }

    @Test
    fun `test AssistantApi acknowledgment interceptor identifies intermediate statuses`() {
        assertTrue(com.example.visionbridge.api.AssistantApi.isAcknowledgment("I'll check the price of pasta."))
        assertTrue(com.example.visionbridge.api.AssistantApi.isAcknowledgment("I'll check the price of Pasta Alfredo."))
        assertTrue(com.example.visionbridge.api.AssistantApi.isAcknowledgment("Let me check that for you."))
        assertTrue(com.example.visionbridge.api.AssistantApi.isAcknowledgment("Checking the document..."))
        assertTrue(com.example.visionbridge.api.AssistantApi.isAcknowledgment("One moment please."))
        assertTrue(com.example.visionbridge.api.AssistantApi.isAcknowledgment("I can check that."))
        assertTrue(com.example.visionbridge.api.AssistantApi.isAcknowledgment("मैं देखता हूँ।"))
        assertTrue(com.example.visionbridge.api.AssistantApi.isAcknowledgment("मी तपासतो."))

        // Real answers must NOT be flagged as acknowledgments
        assertFalse(com.example.visionbridge.api.AssistantApi.isAcknowledgment("Pasta Alfredo costs ₹250."))
        assertFalse(com.example.visionbridge.api.AssistantApi.isAcknowledgment("Veg Pasta is the cheapest at ₹220."))
        assertFalse(com.example.visionbridge.api.AssistantApi.isAcknowledgment("The total was ₹200."))
        assertFalse(com.example.visionbridge.api.AssistantApi.isAcknowledgment("I don't see sushi in the text I captured."))
    }
}
