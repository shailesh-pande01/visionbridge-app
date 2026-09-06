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

        val hiDanger = VoiceActionRouter.matchFastPath("i am in danger")
        assertNotNull(hiDanger)
        assertEquals(VoiceActions.EMERGENCY_SOS, hiDanger?.action)

        val hiSos = VoiceActionRouter.matchFastPath("बचाओ")
        assertNotNull(hiSos)
        assertEquals(VoiceActions.EMERGENCY_SOS, hiSos?.action)

        val mrSos = VoiceActionRouter.matchFastPath("मला वाचवा")
        assertNotNull(mrSos)
        assertEquals(VoiceActions.EMERGENCY_SOS, mrSos?.action)
    }

    @Test
    fun `test Ambiguous help me command triggers clarification instead of SOS`() {
        val enHelpMe = VoiceActionRouter.matchFastPath("help me")
        assertNotNull(enHelpMe)
        assertEquals(VoiceActions.UNKNOWN, enHelpMe?.action)
        assertEquals("clarification", enHelpMe?.type)
        assertTrue(enHelpMe?.speech?.contains("Do you want me to describe your surroundings, read text, or find an object?") == true)

        val hiHelpMe = VoiceActionRouter.matchFastPath("मदद करो")
        assertNotNull(hiHelpMe)
        assertEquals(VoiceActions.UNKNOWN, hiHelpMe?.action)
        assertEquals("clarification", hiHelpMe?.type)
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

    @Test
    fun `test Smart Reading expanded variations in English, Hindi, and Marathi`() {
        val read1 = VoiceActionRouter.matchFastPath("read what's written here")
        assertNotNull(read1)
        assertEquals(VoiceActions.OPEN_FEATURE, read1?.action)
        assertEquals(VoiceFeatures.READING, read1?.target)

        val read2 = VoiceActionRouter.matchFastPath("can you read this")
        assertNotNull(read2)
        assertEquals(VoiceActions.OPEN_FEATURE, read2?.action)
        assertEquals(VoiceFeatures.READING, read2?.target)

        val readHindi1 = VoiceActionRouter.matchFastPath("क्या लिखा है")
        assertNotNull(readHindi1)
        assertEquals(VoiceActions.OPEN_FEATURE, readHindi1?.action)
        assertEquals(VoiceFeatures.READING, readHindi1?.target)

        val readMarathi1 = VoiceActionRouter.matchFastPath("काय लिहिलं आहे")
        assertNotNull(readMarathi1)
        assertEquals(VoiceActions.OPEN_FEATURE, readMarathi1?.action)
        assertEquals(VoiceFeatures.READING, readMarathi1?.target)

        val readHindi2 = VoiceActionRouter.matchFastPath("यहाँ क्या लिखा है")
        assertNotNull(readHindi2)
        assertEquals(VoiceActions.OPEN_FEATURE, readHindi2?.action)
        assertEquals(VoiceFeatures.READING, readHindi2?.target)
    }

    @Test
    fun `test VisionBridge Live and AI Voice Call fast paths`() {
        val live = VoiceActionRouter.matchFastPath("open live vision")
        assertNotNull(live)
        assertEquals(VoiceActions.OPEN_FEATURE, live?.action)
        assertEquals(VoiceFeatures.LIVE_VISION, live?.target)

        val liveHindi = VoiceActionRouter.matchFastPath("लाइव विज़न")
        assertNotNull(liveHindi)
        assertEquals(VoiceActions.OPEN_FEATURE, liveHindi?.action)
        assertEquals(VoiceFeatures.LIVE_VISION, liveHindi?.target)

        val voiceCall = VoiceActionRouter.matchFastPath("start voice call")
        assertNotNull(voiceCall)
        assertEquals(VoiceActions.OPEN_FEATURE, voiceCall?.action)
        assertEquals(VoiceFeatures.VOICE_CALL, voiceCall?.target)

        val voiceCallHindi = VoiceActionRouter.matchFastPath("एआई से बात करो")
        assertNotNull(voiceCallHindi)
        assertEquals(VoiceActions.OPEN_FEATURE, voiceCallHindi?.action)
        assertEquals(VoiceFeatures.VOICE_CALL, voiceCallHindi?.target)
    }

    @Test
    fun `test Phone Calling dynamic extraction and controls`() {
        val callMom = VoiceActionRouter.matchFastPath("call Mom")
        assertNotNull(callMom)
        assertEquals(VoiceActions.CALL_CONTACT, callMom?.action)
        assertEquals("mom", callMom?.contactName?.lowercase())

        val callRahul = VoiceActionRouter.matchFastPath("Rahul को कॉल करो")
        assertNotNull(callRahul)
        assertEquals(VoiceActions.CALL_CONTACT, callRahul?.action)
        assertEquals("rahul", callRahul?.contactName?.lowercase())

        val dialNum = VoiceActionRouter.matchFastPath("dial 9876543210")
        assertNotNull(dialNum)
        assertEquals(VoiceActions.CALL_NUMBER, dialNum?.action)
        assertEquals("9876543210", dialNum?.phoneNumber)

        val endCall = VoiceActionRouter.matchFastPath("hang up")
        assertNotNull(endCall)
        assertEquals(VoiceActions.END_CALL, endCall?.action)

        val recentCalls = VoiceActionRouter.matchFastPath("who did I call recently")
        assertNotNull(recentCalls)
        assertEquals(VoiceActions.RECENT_CALLS, recentCalls?.action)
    }

    @Test
    fun `test Daily News Briefing and Controls`() {
        val news = VoiceActionRouter.matchFastPath("tell me the news")
        assertNotNull(news)
        assertEquals(VoiceActions.NEWS_BRIEFING, news?.action)

        val newsHindi = VoiceActionRouter.matchFastPath("आज की खबरें सुनाओ")
        assertNotNull(newsHindi)
        assertEquals(VoiceActions.NEWS_BRIEFING, newsHindi?.action)

        val nextNews = VoiceActionRouter.matchFastPath("next story")
        assertNotNull(nextNews)
        assertEquals(VoiceActions.NEWS_NEXT, nextNews?.action)

        val prevNews = VoiceActionRouter.matchFastPath("previous story")
        assertNotNull(prevNews)
        assertEquals(VoiceActions.NEWS_PREV, prevNews?.action)

        val localNews = VoiceActionRouter.matchFastPath("read local news")
        assertNotNull(localNews)
        assertEquals(VoiceActions.NEWS_CATEGORY, localNews?.action)
        assertEquals("local", localNews?.newsCategory)
    }

    @Test
    fun `test Entertainment Live Radio and Stories fast paths`() {
        val radio = VoiceActionRouter.matchFastPath("start live radio")
        assertNotNull(radio)
        assertEquals(VoiceActions.LIVE_RADIO_PLAY, radio?.action)

        val radioPause = VoiceActionRouter.matchFastPath("pause radio")
        assertNotNull(radioPause)
        assertEquals(VoiceActions.RADIO_PAUSE, radioPause?.action)

        val radioResume = VoiceActionRouter.matchFastPath("रेडियो रोको")
        assertNotNull(radioResume)
        assertEquals(VoiceActions.RADIO_PAUSE, radioResume?.action)

        val storyAlice = VoiceActionRouter.matchFastPath("play Alice in Wonderland")
        assertNotNull(storyAlice)
        assertEquals(VoiceActions.STORY_PLAY, storyAlice?.action)

        val storyMystery = VoiceActionRouter.matchFastPath("tell me a mystery story")
        assertNotNull(storyMystery)
        assertEquals(VoiceActions.STORY_PLAY_GENRE, storyMystery?.action)
        assertEquals("mystery", storyMystery?.genre)
    }

    @Test
    fun `test Allowlist validation and confidence safety`() {
        val valid = VoiceActionRouter.validateAction(
            com.example.visionbridge.data.AssistantAction(
                action = VoiceActions.OPEN_FEATURE,
                target = VoiceFeatures.READING,
                confidence = 0.95
            )
        )
        assertEquals(VoiceActions.OPEN_FEATURE, valid.action)
        assertEquals(VoiceFeatures.READING, valid.target)

        val lowConfidence = VoiceActionRouter.validateAction(
            com.example.visionbridge.data.AssistantAction(
                action = VoiceActions.OPEN_FEATURE,
                target = VoiceFeatures.READING,
                confidence = 0.50
            )
        )
        assertEquals(VoiceActions.UNKNOWN, lowConfidence.action)
        assertEquals("clarification", lowConfidence.type)

        val invalidAction = VoiceActionRouter.validateAction(
            com.example.visionbridge.data.AssistantAction(
                action = "MALICIOUS_ACTION",
                target = VoiceFeatures.READING,
                confidence = 0.95
            )
        )
        assertEquals(VoiceActions.UNKNOWN, invalidAction.action)

        val synonymTarget = VoiceActionRouter.validateAction(
            com.example.visionbridge.data.AssistantAction(
                action = VoiceActions.OPEN_FEATURE,
                target = "camera",
                confidence = 0.95
            )
        )
        assertEquals(VoiceActions.OPEN_FEATURE, synonymTarget.action)
        assertEquals(VoiceFeatures.SURROUNDINGS, synonymTarget.target)

        val invalidTarget = VoiceActionRouter.validateAction(
            com.example.visionbridge.data.AssistantAction(
                action = VoiceActions.OPEN_FEATURE,
                target = "non_existent_feature",
                confidence = 0.95
            )
        )
        assertEquals(VoiceActions.UNKNOWN, invalidTarget.action)
    }

    @Test
    fun `test natural language conversational commands with polite fillers`() {
        val politeRead = VoiceActionRouter.matchFastPath("could you please read this document")
        assertNotNull(politeRead)
        assertEquals(VoiceActions.OPEN_FEATURE, politeRead?.action)
        assertEquals(VoiceFeatures.READING, politeRead?.target)

        val longCamera = VoiceActionRouter.matchFastPath("open the camera and help me understand what is in front of me")
        assertNotNull(longCamera)
        assertEquals(VoiceActions.OPEN_FEATURE, longCamera?.action)
        assertEquals(VoiceFeatures.SURROUNDINGS, longCamera?.target)

        val politeObjects = VoiceActionRouter.matchFastPath("can you please tell me what objects are around me")
        assertNotNull(politeObjects)
        assertEquals(VoiceActions.OPEN_FEATURE, politeObjects?.action)
        assertEquals(VoiceFeatures.SURROUNDINGS, politeObjects?.target)

        val hazardCheck = VoiceActionRouter.matchFastPath("check for hazards")
        assertNotNull(hazardCheck)
        assertEquals(VoiceActions.OPEN_FEATURE, hazardCheck?.action)
        assertEquals(VoiceFeatures.SURROUNDINGS, hazardCheck?.target)
    }

    @Test
    fun `test Object Finder generic open vs dynamic target extraction`() {
        val genericOpen = VoiceActionRouter.matchFastPath("open object finder")
        assertNotNull(genericOpen)
        assertEquals(VoiceActions.OPEN_FEATURE, genericOpen?.action)
        assertEquals(VoiceFeatures.OBJECT_FINDER, genericOpen?.target)

        val genericFindObjects = VoiceActionRouter.matchFastPath("find objects")
        assertNotNull(genericFindObjects)
        assertEquals(VoiceActions.OPEN_FEATURE, genericFindObjects?.action)
        assertEquals(VoiceFeatures.OBJECT_FINDER, genericFindObjects?.target)

        val dynamicFind = VoiceActionRouter.matchFastPath("find my white cane")
        assertNotNull(dynamicFind)
        assertEquals(VoiceActions.FIND_OBJECT, dynamicFind?.action)
        assertEquals("white cane", dynamicFind?.objectName)

        val dynamicHindi = VoiceActionRouter.matchFastPath("मेरा चश्मा ढूँढो")
        assertNotNull(dynamicHindi)
        assertEquals(VoiceActions.FIND_OBJECT, dynamicHindi?.action)
        assertEquals("चश्मा", dynamicHindi?.objectName)
    }

    @Test
    fun `test routeForTarget synonym normalization`() {
        assertEquals("surroundings", VoiceActionRouter.routeForTarget("camera"))
        assertEquals("surroundings", VoiceActionRouter.routeForTarget("vision"))
        assertEquals("surroundings", VoiceActionRouter.routeForTarget("hazard"))
        assertEquals("reading", VoiceActionRouter.routeForTarget("read"))
        assertEquals("reading", VoiceActionRouter.routeForTarget("document"))
        assertEquals("currency", VoiceActionRouter.routeForTarget("money"))
        assertEquals("currency", VoiceActionRouter.routeForTarget("cash"))
        assertEquals("finder?target=keys", VoiceActionRouter.routeForTarget("object_finder", "keys"))
        assertEquals("finder", VoiceActionRouter.routeForTarget("finder"))
        assertEquals("sos", VoiceActionRouter.routeForTarget("danger"))
        assertEquals("calling", VoiceActionRouter.routeForTarget("phone"))
        assertEquals("voice_call", VoiceActionRouter.routeForTarget("assistant"))
    }

    @Test
    fun `test Hindi and Marathi conversational commands with politeness prefixes`() {
        val politeHindi = VoiceActionRouter.matchFastPath("कृपया यह मेन्यू पढ़ो")
        assertNotNull(politeHindi)
        assertEquals(VoiceActions.OPEN_FEATURE, politeHindi?.action)
        assertEquals(VoiceFeatures.READING, politeHindi?.target)

        val politeMarathi = VoiceActionRouter.matchFastPath("कृपया हे वाचा")
        assertNotNull(politeMarathi)
        assertEquals(VoiceActions.OPEN_FEATURE, politeMarathi?.action)
        assertEquals(VoiceFeatures.READING, politeMarathi?.target)
    }
}
