package com.example.visionbridge

import com.example.visionbridge.utils.VoiceCommandParser
import com.example.visionbridge.utils.VoiceIntent
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceCommandRoutingTest {

    private fun route(transcript: String): String? {
        val parsed = VoiceCommandParser.parseCommand(transcript)
        return VoiceCommandParser.mapIntentToRoute(parsed.intent)
    }

    private fun intent(transcript: String): VoiceIntent {
        return VoiceCommandParser.parseCommand(transcript).intent
    }

    @Test
    fun `documented English commands map to the expected feature`() {
        assertEquals(VoiceIntent.HOME, intent("Vision, go home."))
        assertEquals("reading", route("Vision, read this menu."))
        assertEquals("surroundings", route("Vision, describe my surroundings."))
        assertEquals("currency", route("Vision, count my currency."))
        assertEquals("transport", route("Vision, what bus is this?"))
        assertEquals("finder", route("Vision, find my wallet."))
        assertEquals("location", route("Vision, where am I?"))
        assertEquals("volunteer", route("Vision, I need a volunteer."))
        assertEquals("sos", route("Vision, emergency."))
    }

    @Test
    fun `target object extraction for smart object finder`() {
        val parsed = VoiceCommandParser.parseCommand("Vision, find my wallet")
        assertEquals(VoiceIntent.SMART_OBJECT_FINDER, parsed.intent)
        assertEquals("wallet", parsed.targetObject)

        val parsedKeys = VoiceCommandParser.parseCommand("find my car keys")
        assertEquals(VoiceIntent.SMART_OBJECT_FINDER, parsedKeys.intent)
        assertEquals("car keys", parsedKeys.targetObject)
    }

    @Test
    fun `Hindi voice commands map to correct features`() {
        assertEquals(VoiceIntent.SMART_READING, intent("Vision, यह मेन्यू पढ़ो"))
        assertEquals(VoiceIntent.AI_SURROUNDINGS, intent("आसपास क्या है बताओ"))
        assertEquals(VoiceIntent.CURRENCY_READER, intent("पैसे गिनो"))
        assertEquals(VoiceIntent.PUBLIC_TRANSPORT, intent("बस नंबर क्या है"))
        assertEquals(VoiceIntent.WHERE_AM_I, intent("मैं कहाँ हूँ"))
        assertEquals(VoiceIntent.VOLUNTEER_HELP, intent("वॉलंटियर से बात कराओ"))
        assertEquals(VoiceIntent.EMERGENCY_SOS, intent("आपातकाल मदद चाहिए"))
    }

    @Test
    fun `Marathi voice commands map to correct features`() {
        assertEquals(VoiceIntent.SMART_READING, intent("Vision, हे वाचा"))
        assertEquals(VoiceIntent.AI_SURROUNDINGS, intent("माझ्या समोर काय आहे"))
        assertEquals(VoiceIntent.CURRENCY_READER, intent("पैसे मोजा"))
        assertEquals(VoiceIntent.PUBLIC_TRANSPORT, intent("बस फलाट नंबर सांगा"))
        assertEquals(VoiceIntent.WHERE_AM_I, intent("मी कुठे आहे"))
        assertEquals(VoiceIntent.VOLUNTEER_HELP, intent("स्वयंसेवकाशी जोडा"))
        assertEquals(VoiceIntent.EMERGENCY_SOS, intent("संकट मदत करा"))
    }
}
