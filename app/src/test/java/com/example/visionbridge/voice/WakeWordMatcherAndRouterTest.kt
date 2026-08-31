package com.example.visionbridge.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordMatcherAndRouterTest {

    // ── 1. Wake Word Matcher Tests ───────────────────────────────────

    @Test
    fun `test wake word alone matches with empty command`() {
        val result = WakeWordMatcher.splitOnWakeWord("Vision")
        assertNotNull(result)
        assertEquals("", result?.command)

        val resultLower = WakeWordMatcher.splitOnWakeWord("vision")
        assertNotNull(resultLower)
        assertEquals("", resultLower?.command)

        val resultUpper = WakeWordMatcher.splitOnWakeWord("VISION")
        assertNotNull(resultUpper)
        assertEquals("", resultUpper?.command)
    }

    @Test
    fun `test wake word with politeness prefixes`() {
        val heyVision = WakeWordMatcher.splitOnWakeWord("Hey Vision")
        assertNotNull(heyVision)
        assertEquals("", heyVision?.command)

        val okVision = WakeWordMatcher.splitOnWakeWord("Ok Vision")
        assertNotNull(okVision)
        assertEquals("", okVision?.command)

        val hindiHey = WakeWordMatcher.splitOnWakeWord("हे विजन")
        assertNotNull(hindiHey)
        assertEquals("", hindiHey?.command)
    }

    @Test
    fun `test single utterance wake word plus command extraction`() {
        val res1 = WakeWordMatcher.splitOnWakeWord("Vision, read this menu.")
        assertNotNull(res1)
        assertEquals("read this menu", res1?.command)

        val res2 = WakeWordMatcher.splitOnWakeWord("Hey Vision, where am I?")
        assertNotNull(res2)
        assertEquals("where am I", res2?.command)

        val res3 = WakeWordMatcher.splitOnWakeWord("Vision, emergency!")
        assertNotNull(res3)
        assertEquals("emergency", res3?.command)

        val res4 = WakeWordMatcher.splitOnWakeWord("Vision count my money")
        assertNotNull(res4)
        assertEquals("count my money", res4?.command)
    }

    @Test
    fun `test Devanagari wake word matching and command extraction`() {
        val hindi1 = WakeWordMatcher.splitOnWakeWord("विजन यह मेन्यू पढ़ो")
        assertNotNull(hindi1)
        assertEquals("यह मेन्यू पढ़ो", hindi1?.command)

        val marathi1 = WakeWordMatcher.splitOnWakeWord("व्हिजन पैसे मोजा")
        assertNotNull(marathi1)
        assertEquals("पैसे मोजा", marathi1?.command)

        val hindi2 = WakeWordMatcher.splitOnWakeWord("विज़न आसपास क्या है")
        assertNotNull(hindi2)
        assertEquals("आसपास क्या है", hindi2?.command)
    }

    @Test
    fun `test non-wake speech is ignored`() {
        assertNull(WakeWordMatcher.splitOnWakeWord("Hello, can you help me?"))
        assertNull(WakeWordMatcher.splitOnWakeWord("What is the time right now"))
        assertNull(WakeWordMatcher.splitOnWakeWord("Good morning"))
    }

    @Test
    fun `test false positive protection for words containing vision`() {
        assertNull(WakeWordMatcher.splitOnWakeWord("I watched television today"))
        assertNull(WakeWordMatcher.splitOnWakeWord("division of labor"))
        assertNull(WakeWordMatcher.splitOnWakeWord("provision store"))
    }

    // ── 2. Voice Action Router Fast Path Tests ───────────────────────

    @Test
    fun `test Emergency SOS fast paths in English, Hindi, and Marathi`() {
        val enSos = VoiceActionRouter.matchFastPath("emergency")
        assertNotNull(enSos)
        assertEquals(VoiceActions.EMERGENCY_SOS, enSos?.action)

        val enHelpMe = VoiceActionRouter.matchFastPath("help me")
        assertNotNull(enHelpMe)
        assertEquals(VoiceActions.EMERGENCY_SOS, enHelpMe?.action)

        val hiSos = VoiceActionRouter.matchFastPath("बचाओ")
        assertNotNull(hiSos)
        assertEquals(VoiceActions.EMERGENCY_SOS, hiSos?.action)

        val mrSos = VoiceActionRouter.matchFastPath("मला वाचवा")
        assertNotNull(mrSos)
        assertEquals(VoiceActions.EMERGENCY_SOS, mrSos?.action)
    }

    @Test
    fun `test Stop and Cancel fast paths`() {
        val stop = VoiceActionRouter.matchFastPath("stop talking")
        assertNotNull(stop)
        assertEquals(VoiceActions.STOP_SPEAKING, stop?.action)

        val quiet = VoiceActionRouter.matchFastPath("रुको")
        assertNotNull(quiet)
        assertEquals(VoiceActions.STOP_SPEAKING, quiet?.action)

        val cancel = VoiceActionRouter.matchFastPath("cancel")
        assertNotNull(cancel)
        assertEquals(VoiceActions.CANCEL, cancel?.action)

        val cancelReq = VoiceActionRouter.matchFastPath("cancel volunteer request")
        assertNotNull(cancelReq)
        assertEquals(VoiceActions.CANCEL, cancelReq?.action)
    }

    @Test
    fun `test feature navigation fast paths`() {
        val read = VoiceActionRouter.matchFastPath("read this menu")
        assertNotNull(read)
        assertEquals(VoiceActions.OPEN_FEATURE, read?.action)
        assertEquals(VoiceFeatures.READING, read?.target)

        val surroundings = VoiceActionRouter.matchFastPath("describe my surroundings")
        assertNotNull(surroundings)
        assertEquals(VoiceActions.OPEN_FEATURE, surroundings?.action)
        assertEquals(VoiceFeatures.SURROUNDINGS, surroundings?.target)

        val currency = VoiceActionRouter.matchFastPath("count my currency")
        assertNotNull(currency)
        assertEquals(VoiceActions.OPEN_FEATURE, currency?.action)
        assertEquals(VoiceFeatures.CURRENCY, currency?.target)

        val transport = VoiceActionRouter.matchFastPath("where is this bus going")
        assertNotNull(transport)
        assertEquals(VoiceActions.OPEN_FEATURE, transport?.action)
        assertEquals(VoiceFeatures.TRANSPORT, transport?.target)

        val location = VoiceActionRouter.matchFastPath("where am I")
        assertNotNull(location)
        assertEquals(VoiceActions.OPEN_FEATURE, location?.action)
        assertEquals(VoiceFeatures.LOCATION, location?.target)

        val hazard = VoiceActionRouter.matchFastPath("start hazard mode")
        assertNotNull(hazard)
        assertEquals(VoiceActions.OPEN_FEATURE, hazard?.action)
        assertEquals(VoiceFeatures.HAZARD, hazard?.target)

        val volunteer = VoiceActionRouter.matchFastPath("call a volunteer")
        assertNotNull(volunteer)
        assertEquals(VoiceActions.START_VOLUNTEER_HELP, volunteer?.action)
        assertEquals(VoiceFeatures.VOLUNTEER, volunteer?.target)

        val home = VoiceActionRouter.matchFastPath("go home")
        assertNotNull(home)
        assertEquals(VoiceActions.GO_HOME, home?.action)
    }

    @Test
    fun `test Object Finder dynamic object target extraction`() {
        val findWallet = VoiceActionRouter.matchFastPath("find my wallet")
        assertNotNull(findWallet)
        assertEquals(VoiceActions.FIND_OBJECT, findWallet?.action)
        assertEquals("wallet", findWallet?.objectName)

        val findKeys = VoiceActionRouter.matchFastPath("where is my car keys")
        assertNotNull(findKeys)
        assertEquals(VoiceActions.FIND_OBJECT, findKeys?.action)
        assertEquals("car keys", findKeys?.objectName)

        val findHindi = VoiceActionRouter.matchFastPath("चाबी ढूँढो")
        assertNotNull(findHindi)
        assertEquals(VoiceActions.FIND_OBJECT, findHindi?.action)
        assertEquals("चाबी", findHindi?.objectName)
    }

    @Test
    fun `test Camera capture fast path`() {
        val capture = VoiceActionRouter.matchFastPath("capture")
        assertNotNull(capture)
        assertEquals(VoiceActions.CAPTURE_IMAGE, capture?.action)

        val takePhoto = VoiceActionRouter.matchFastPath("take a photo")
        assertNotNull(takePhoto)
        assertEquals(VoiceActions.CAPTURE_IMAGE, takePhoto?.action)

        val photoLo = VoiceActionRouter.matchFastPath("फोटो लो")
        assertNotNull(photoLo)
        assertEquals(VoiceActions.CAPTURE_IMAGE, photoLo?.action)
    }
}
