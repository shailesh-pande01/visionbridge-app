package com.example.visionbridge.voice

import com.example.visionbridge.data.AssistantAction
import java.util.regex.Pattern

object VoiceActions {
    const val OPEN_FEATURE = "OPEN_FEATURE"
    const val CAPTURE_IMAGE = "CAPTURE_IMAGE"
    const val ASK_CONTEXTUAL_QUESTION = "ASK_CONTEXTUAL_QUESTION"
    const val FIND_OBJECT = "FIND_OBJECT"
    const val GO_HOME = "GO_HOME"
    const val START_VOLUNTEER_HELP = "START_VOLUNTEER_HELP"
    const val EMERGENCY_SOS = "EMERGENCY_SOS"
    const val CONFIRM = "CONFIRM"
    const val REPEAT_LAST = "REPEAT_LAST"
    const val STOP_SPEAKING = "STOP_SPEAKING"
    const val CANCEL = "CANCEL"
    const val GESTURE_HELP = "GESTURE_HELP"
    const val LIVE_RADIO_PLAY = "LIVE_RADIO_PLAY"
    const val LIVE_RADIO_NEXT = "LIVE_RADIO_NEXT"
    const val LIVE_RADIO_PREV = "LIVE_RADIO_PREV"
    const val LIVE_RADIO_SEARCH = "LIVE_RADIO_SEARCH"
    const val LIVE_RADIO_INFO = "LIVE_RADIO_INFO"
    const val RADIO_PAUSE = "RADIO_PAUSE"
    const val RADIO_RESUME = "RADIO_RESUME"
    const val RADIO_STOP = "RADIO_STOP"
    const val STORY_PLAY = "STORY_PLAY"
    const val STORY_PLAY_GENRE = "STORY_PLAY_GENRE"
    const val STORY_PLAY_LANGUAGE = "STORY_PLAY_LANGUAGE"
    const val STORY_FILTER_GENRE = "STORY_FILTER_GENRE"
    const val STORY_FILTER_LANGUAGE = "STORY_FILTER_LANGUAGE"
    const val STORY_CONTINUE = "STORY_CONTINUE"
    const val STORY_RESTART = "STORY_RESTART"
    const val STORY_INFO = "STORY_INFO"
    const val STORY_NEXT_CHAPTER = "STORY_NEXT_CHAPTER"
    const val STORY_PREV_CHAPTER = "STORY_PREV_CHAPTER"
    const val START_GAME = "START_GAME"
    const val GET_PROGRESS = "GET_PROGRESS"
    const val STOP_ENTERTAINMENT = "STOP_ENTERTAINMENT"
    const val CALL_CONTACT = "CALL_CONTACT"
    const val CALL_NUMBER = "CALL_NUMBER"
    const val ADD_CONTACT = "ADD_CONTACT"
    const val END_CALL = "END_CALL"
    const val RECENT_CALLS = "RECENT_CALLS"
    const val NEWS_BRIEFING = "NEWS_BRIEFING"
    const val NEWS_NEXT = "NEWS_NEXT"
    const val NEWS_PREV = "NEWS_PREV"
    const val NEWS_REPEAT = "NEWS_REPEAT"
    const val NEWS_PAUSE = "NEWS_PAUSE"
    const val NEWS_RESUME = "NEWS_RESUME"
    const val NEWS_CATEGORY = "NEWS_CATEGORY"
    const val NEWS_SOURCE = "NEWS_SOURCE"
    const val NEWS_OPEN_ORIGINAL = "NEWS_OPEN_ORIGINAL"
    const val NEWS_REFRESH = "NEWS_REFRESH"
    const val NEWS_MORE = "NEWS_MORE"
    const val NEWS_SPEED = "NEWS_SPEED"
    const val NEWS_LANG = "NEWS_LANG"
    const val UNKNOWN = "UNKNOWN"
}

object VoiceFeatures {
    const val SURROUNDINGS = "surroundings"
    const val READING = "reading"
    const val CURRENCY = "currency"
    const val TRANSPORT = "transport"
    const val OBJECT_FINDER = "objectFinder"
    const val LOCATION = "location"
    const val VOLUNTEER = "volunteer"
    const val EMERGENCY = "emergency"
    const val ENTERTAINMENT = "entertainment"
    const val RADIO = "radio"
    const val LIVE_RADIO = "liveRadio"
    const val STORIES = "stories"
    const val GAMES = "games"
    const val DAILY_CHALLENGE = "dailyChallenge"
    const val PROGRESS = "progress"
    const val LIVE_VISION = "liveVision"
    const val VOICE_CALL = "voiceCall"
    const val CALLING = "calling"
    const val NEWS = "news"
    const val HOME = "home"
}

object VoiceActionRouter {

    val ALLOWED_ACTIONS = setOf(
        VoiceActions.OPEN_FEATURE,
        VoiceActions.CAPTURE_IMAGE,
        VoiceActions.ASK_CONTEXTUAL_QUESTION,
        VoiceActions.FIND_OBJECT,
        VoiceActions.GO_HOME,
        VoiceActions.START_VOLUNTEER_HELP,
        VoiceActions.EMERGENCY_SOS,
        VoiceActions.CONFIRM,
        VoiceActions.REPEAT_LAST,
        VoiceActions.STOP_SPEAKING,
        VoiceActions.CANCEL,
        VoiceActions.GESTURE_HELP,
        VoiceActions.LIVE_RADIO_PLAY,
        VoiceActions.LIVE_RADIO_NEXT,
        VoiceActions.LIVE_RADIO_PREV,
        VoiceActions.LIVE_RADIO_SEARCH,
        VoiceActions.LIVE_RADIO_INFO,
        VoiceActions.RADIO_PAUSE,
        VoiceActions.RADIO_RESUME,
        VoiceActions.RADIO_STOP,
        VoiceActions.STORY_PLAY,
        VoiceActions.STORY_PLAY_GENRE,
        VoiceActions.STORY_PLAY_LANGUAGE,
        VoiceActions.STORY_FILTER_GENRE,
        VoiceActions.STORY_FILTER_LANGUAGE,
        VoiceActions.STORY_CONTINUE,
        VoiceActions.STORY_RESTART,
        VoiceActions.STORY_INFO,
        VoiceActions.STORY_NEXT_CHAPTER,
        VoiceActions.STORY_PREV_CHAPTER,
        VoiceActions.START_GAME,
        VoiceActions.GET_PROGRESS,
        VoiceActions.STOP_ENTERTAINMENT,
        VoiceActions.CALL_CONTACT,
        VoiceActions.CALL_NUMBER,
        VoiceActions.ADD_CONTACT,
        VoiceActions.END_CALL,
        VoiceActions.RECENT_CALLS,
        VoiceActions.NEWS_BRIEFING,
        VoiceActions.NEWS_NEXT,
        VoiceActions.NEWS_PREV,
        VoiceActions.NEWS_REPEAT,
        VoiceActions.NEWS_PAUSE,
        VoiceActions.NEWS_RESUME,
        VoiceActions.NEWS_CATEGORY,
        VoiceActions.NEWS_SOURCE,
        VoiceActions.NEWS_OPEN_ORIGINAL,
        VoiceActions.NEWS_REFRESH,
        VoiceActions.NEWS_MORE,
        VoiceActions.NEWS_SPEED,
        VoiceActions.NEWS_LANG,
        VoiceActions.UNKNOWN
    )

    val ALLOWED_FEATURES = setOf(
        VoiceFeatures.SURROUNDINGS,
        VoiceFeatures.READING,
        VoiceFeatures.CURRENCY,
        VoiceFeatures.TRANSPORT,
        VoiceFeatures.OBJECT_FINDER,
        VoiceFeatures.LOCATION,
        VoiceFeatures.VOLUNTEER,
        VoiceFeatures.EMERGENCY,
        VoiceFeatures.ENTERTAINMENT,
        VoiceFeatures.RADIO,
        VoiceFeatures.LIVE_RADIO,
        VoiceFeatures.STORIES,
        VoiceFeatures.GAMES,
        VoiceFeatures.DAILY_CHALLENGE,
        VoiceFeatures.PROGRESS,
        VoiceFeatures.LIVE_VISION,
        VoiceFeatures.VOICE_CALL,
        VoiceFeatures.CALLING,
        VoiceFeatures.NEWS,
        VoiceFeatures.HOME
    )

    private data class FastPathRule(
        val pattern: Pattern,
        val buildAction: (String) -> AssistantAction
    )

    private val FAST_PATH_RULES = listOf(
        // 1. Emergency SOS (Highest Priority)
        FastPathRule(
            Pattern.compile(
                "\\b(emergency|s\\.?o\\.?s\\.?|i'?m in danger|im in danger|i am in danger|call for help now)\\b|^help me$|(इमरजेंसी|इमर्जेंसी|इमर्जन्सी|आपातकाल|आपत्काल|आपत्कालीन|एसओएस)|(मैं ख़तरे में हूँ|मैं खतरे में हूं|मी धोक्यात आहे)|^(बचाओ|मुझे बचाओ|वाचवा|मला वाचवा)$",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.EMERGENCY_SOS,
                target = VoiceFeatures.EMERGENCY,
                speech = "Starting Emergency SOS countdown.",
                type = "navigation",
                confidence = 0.99
            )
        },

        // 2. Stop Speaking / Barge-in
        FastPathRule(
            Pattern.compile(
                "\\b(stop talking|be quiet|stop speaking|shut up|stop reading)\\b|(चुप हो जाओ|चुप रहो|बोलना बंद करो|बोलना बंद कर|पढ़ना बंद करो)|(गप्प बसा|गप्प बस|बोलणं थांबवा|बोलणे थांबवा|वाचणं थांबवा)|^stop$|^quiet$|^(रुको|रुक|चुप|बंद|थांब|थांबा|गप्प)$",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.STOP_SPEAKING,
                speech = "",
                type = "action",
                confidence = 0.98
            )
        },

        // 3. Cancel Request (Volunteer)
        FastPathRule(
            Pattern.compile(
                "\\b(cancel (the |my |this )?(volunteer |help )?request|request cancel)\\b|(अनुरोध\\s*(रद्द|कैंसिल)|रिक्वेस्ट\\s*(रद्द|कैंसिल))|(विनंती\\s*(रद्द|कँसिल))",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.CANCEL,
                speech = "Volunteer help request cancelled.",
                type = "action",
                confidence = 0.97
            )
        },

        // 4. Cancel
        FastPathRule(
            Pattern.compile(
                "^(cancel|never ?mind|forget it|no thanks|no)$|^(रद्द|रद्द करो|कैंसिल|रहने दो|छोड़ो|नहीं|नही|ना)$|^(रद्द करा|नको|जाऊ द्या|राहू द्या|नाही)$",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.CANCEL,
                speech = "Cancelled.",
                type = "action",
                confidence = 0.96
            )
        },

        // 5. Confirm / Yes
        FastPathRule(
            Pattern.compile(
                "^(yes|yeah|yep|confirm|send it|send the request|go ahead|do it|ok|okay)$|^(हाँ|हां|जी|जी हाँ|हाँ जी|ठीक है|भेज दो|भेजो|कर दो|करो|ओके)$|^(हो|होय|हो जी|ठीक आहे|पाठवा|पाठव|करा|कर)$",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.CONFIRM,
                speech = "",
                type = "action",
                confidence = 0.97
            )
        },

        // 6. Go Home
        FastPathRule(
            Pattern.compile(
                "\\b(go home|take me home|home screen|main menu|go back home)\\b|(घर चलो|होम पर जाओ|होम पे जाओ|होम स्क्रीन|मुख्य मेन्यू|मुख्य मेनू|वापस होम)|(होमवर जा|घरी चल|मुख्य पान|मुख्य मेनूवर जा)|^home$",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.GO_HOME,
                target = VoiceFeatures.HOME,
                speech = "Going home.",
                type = "navigation",
                confidence = 0.96
            )
        },

        // 7. Capture Image
        FastPathRule(
            Pattern.compile(
                "\\b(capture|take a (photo|picture)|scan (this|it|now)|snap (this|it))\\b|(कैप्चर|फोटो लो|फ़ोटो लो|तस्वीर लो|फोटो खींचो|फ़ोटो खींचो|स्कैन करो|स्कैन कर)|(फोटो घे|फोटो काढ|स्कॅन कर|कॅप्चर)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.CAPTURE_IMAGE,
                speech = "Capturing.",
                type = "action",
                confidence = 0.97
            )
        },

        // 8. Repeat Last
        FastPathRule(
            Pattern.compile(
                "\\b(say (that )?again|repeat that|repeat it|what did you say)\\b|(फिर से बोलो|फिर से कहो|दोबारा बोलो|दोबारा कहो|क्या कहा|फिर बोलो)|(पुन्हा सांग|पुन्हा बोला|पुन्हा म्हणा|काय म्हणालात|काय म्हणाला)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.REPEAT_LAST,
                speech = "",
                type = "action",
                confidence = 0.96
            )
        },

        // 9. VisionBridge Live
        FastPathRule(
            Pattern.compile(
                "\\b(open vision live|start vision live|vision live|live vision|open live vision|start live vision|open live camera|start live camera|real ?time vision|start real ?time vision|live assistant)\\b|(लाइव विज़न|लाइव कैमरा|विज़न लाइव|लाइव मोड|रियल टाइम कैमरा|लाइव शुरू करो|विजन लाइव)|(लाईव्ह व्हिजन|लाईव्ह कॅमेरा|व्हिजन लाईव्ह|लाईव्ह मोड|थेट कॅमेरा|लाईव्ह सुरू करा)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.OPEN_FEATURE,
                target = VoiceFeatures.LIVE_VISION,
                speech = "Opening VisionBridge Live.",
                type = "navigation",
                confidence = 0.96
            )
        },

        // 10. AI Voice Call
        FastPathRule(
            Pattern.compile(
                "\\b(start (a )?(voice |ai )?call|open (voice |ai )?call|voice call|ai call|ai voice call|call vision|call ai|talk to vision|talk to ai|let'?s talk|start (an? )?(ai )?voice chat|chat with ai)\\b|(वॉइस कॉल|एआई कॉल|एआई से बात करो|विज़न से बात करो|बातचीत शुरू करो)|(व्हॉईस कॉल|एआय कॉल|एआयशी बोला|व्हिजनशी बोला)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.OPEN_FEATURE,
                target = VoiceFeatures.VOICE_CALL,
                speech = "Starting AI Voice Call.",
                type = "navigation",
                confidence = 0.96
            )
        },

        // 11. End Phone Call
        FastPathRule(
            Pattern.compile(
                "\\b(end (the |my |this )?call|hang up|disconnect (the )?call|stop (the )?call|terminate (the )?call|cut (the )?call)\\b|(कॉल काटो|फोन काटो|कॉल समाप्त करो|कॉल बंद करो)|(फोन ठेव|कॉल बंद करा|कॉल संपवा|कॉल थांबवा)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.END_CALL,
                speech = "Call ended.",
                type = "action",
                confidence = 0.98
            )
        },

        // 12. Recent Phone Calls
        FastPathRule(
            Pattern.compile(
                "\\b(who did i call recently|call history|recent calls|call the last person( i called)?|show (my )?recent calls|last call)\\b|(हाल के कॉल|कॉल हिस्ट्री|आखिरी कॉल किसे किया|हाल में किसे कॉल किया)|(नुकतेच केलेले कॉल|कॉल हिस्टरी|शेवटचा कॉल कोणाला केला)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.RECENT_CALLS,
                target = VoiceFeatures.CALLING,
                speech = "Checking recent calls.",
                type = "navigation",
                confidence = 0.96
            )
        },

        // 13. Add New Contact
        FastPathRule(
            Pattern.compile(
                "\\b(add (a )?(new )?contact|save (a )?(new )?contact|create (a )?(new )?contact|new contact)\\b|(कांटेक्ट जोड़ो|नया कांटेक्ट बनाओ|नया कांटेक्ट जोड़ो|संपर्क जोड़ो|कांटेक्ट सेव करो)|(संपर्क जोडा|नवीन संपर्क तयार करा|नवीन संपर्क जोडा|कांटेक्ट सेव्ह करा)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.ADD_CONTACT,
                target = VoiceFeatures.CALLING,
                speech = "Adding a new contact.",
                type = "navigation",
                confidence = 0.95
            )
        },

        // 14. Phone Calling / Dialer Assistant
        FastPathRule(
            Pattern.compile(
                "\\b(open (the )?(phone|dialer|calling assistant)|phone assistant|open dialer|phone dialer|open phone|calling assistant|phone calling|phone)\\b|(फोन खोलो|डायलर खोलो|कॉलिंग खोलो|फोन डायलर)|(फोन उघडा|डायलर उघडा|कॉलिंग उघडा)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.OPEN_FEATURE,
                target = VoiceFeatures.CALLING,
                speech = "Opening Calling Assistant.",
                type = "navigation",
                confidence = 0.96
            )
        },

        // 15. Surroundings / Camera Assistant (Expanded Variations)
        FastPathRule(
            Pattern.compile(
                "\\b(describe (my |the )?surroundings|what is around me|what'?s around me|tell me what is around me|tell me what'?s around me|what do you see|what do you see around me|describe this scene|what is in front of me|what'?s in front of me|look around|open camera assistant|camera assistant|surroundings)\\b|(मेरे आसपास क्या है|आसपास क्या है|आसपास देखो|सामने क्या है|सामने क्या दिख रहा है|दृश्य बताओ|क्या दिख रहा है|कैमरा असिस्टेंट)|(माझ्या आजूबाजूला काय आहे|आजूबाजूला काय आहे|समोर काय आहे|परिसर सांगा|कॅमेरा असिस्टंट|काय दिसत आहे)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.OPEN_FEATURE,
                target = VoiceFeatures.SURROUNDINGS,
                speech = "Opening Camera Assistant.",
                type = "navigation",
                confidence = 0.96
            )
        },

        // 16. Currency Reader (Expanded Variations)
        FastPathRule(
            Pattern.compile(
                "\\b(read (the |this |my )?money|count (this |the |my )?currency|count (this |the |my )?money|count (the |this |my )?cash|how much money( is this)?|how much currency( is this)?|how much cash( is this)?|currency reader|open currency reader|currency assistant|read currency|count notes|identify notes|currency)\\b|(पैसे गिनो|रुपये गिनो|पैसे पढ़ो|करेंसी रीडर|पैसे देखो|यह कितने पैसे हैं|कितने रुपये हैं|नोट गिनो|कैश गिनो)|(पैसे मोजा|रुपये मोजा|पैसे वाचा|करन्सी रीडर|पैसे बघा|हे किती पैसे आहेत|किती रुपये आहेत|नोट मोजा|कॅश मोजा)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.OPEN_FEATURE,
                target = VoiceFeatures.CURRENCY,
                speech = "Opening Currency Reader.",
                type = "navigation",
                confidence = 0.96
            )
        },

        // 17. Smart Reading Assistant (Extensively Expanded Variations)
        FastPathRule(
            Pattern.compile(
                "\\b(read this( menu)?|read the (menu|text|document|sign|page|book|label|paper)|read this text|read what'?s written( here)?|read what is written( here)?|read the text|read this for me|can you read this|read the page|read this document|what does this say|tell me what'?s written( here)?|tell me what is written( here)?|open reading assistant|reading assistant|start reading|open reading|read text|reading)\\b|(यह मेन्यू पढ़ो|यह पढ़ो|मेन्यू पढ़ो|रीडिंग असिस्टेंट|पढ़ना शुरू करो|क्या लिखा है|यहाँ क्या लिखा है|यह क्या लिखा है|इसे पढ़ो|पढ़कर बताओ|किताब पढ़ो|दस्तावेज पढ़ो|टेक्स्ट पढ़ो)|(हा मेन्यू वाच|हे वाच|रीडिंग असिस्टंट|वाचायला सुरू करा|काय लिहिलं आहे|हे काय लिहिलं आहे|वाचून दाखवा|पुस्तक वाच|मजकूर वाच)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.OPEN_FEATURE,
                target = VoiceFeatures.READING,
                speech = "Opening Reading Assistant.",
                type = "navigation",
                confidence = 0.96
            )
        },

        // 18. Public Transport & Signboards
        FastPathRule(
            Pattern.compile(
                "\\b(where is this bus going|public transport|open transport|transport assistant|bus assistant|train assistant|train platform|which platform|bus number|which bus is this|check platform|transport)\\b|(यह बस कहाँ जा रही है|पब्लिक ट्रांसपोर्ट|ट्रांसपोर्ट असिस्टेंट|बस नंबर क्या है|ट्रेन कहाँ जाएगी|प्लेटफॉर्म कौन सा है)|(ही बस कुठे जाते|ट्रान्सपोर्ट असिस्टंट|बसचा नंबर काय आहे|प्लॅटफॉर्म कोणता आहे|ही ट्रेन कुठे जाते)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.OPEN_FEATURE,
                target = VoiceFeatures.TRANSPORT,
                speech = "Opening Transport Assistant.",
                type = "navigation",
                confidence = 0.95
            )
        },

        // 19. Location / Where Am I
        FastPathRule(
            Pattern.compile(
                "\\b(where am i|check (my )?location|my location|open location assistant|location assistant|where are we|what place is this|locate me|current location)\\b|(मैं कहाँ हूँ|मेरी लोकेशन|लोकेशन असिस्टेंट|हम कहाँ हैं|यह कौन सी जगह है|मेरा स्थान बताओ)|(मी कुठे आहे|माझं ठिकाण|लोकेशन असिस्टंट|आम्ही कुठे आहोत|ही कोणती जागा आहे|माझे स्थान सांगा)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.OPEN_FEATURE,
                target = VoiceFeatures.LOCATION,
                speech = "Opening Where Am I.",
                type = "navigation",
                confidence = 0.96
            )
        },

        // 20. Volunteer Help
        FastPathRule(
            Pattern.compile(
                "\\b(call (a )?volunteer|connect( with)? volunteer|volunteer help|need human help|talk to volunteer|connect to person|human help|volunteer)\\b|(वॉलंटियर को कॉल करो|वॉलंटियर से जोड़ो|इंसान से बात करनी है|सहायक से बात करो)|(वॉलंटियरशी जोडा|वॉलंटियर मदत|मदतनीसाशी बोला)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.START_VOLUNTEER_HELP,
                target = VoiceFeatures.VOLUNTEER,
                speech = "Connecting you with a volunteer.",
                type = "navigation",
                confidence = 0.96
            )
        },

        // 21. Gesture Help
        FastPathRule(
            Pattern.compile(
                "\\b(gesture help|how do gestures work|how to use gestures|gesture guide|gesture instructions|gesture navigation|gestures)\\b|(जेस्चर मदद|जेस्चर सहायता|जेस्चर कैसे इस्तेमाल करें|जेस्चर क्या हैं)|(जेस्चर मदत|जेस्चर कसे वापरावे|जेस्चर माहिती)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.GESTURE_HELP,
                speech = "Tap anywhere to activate voice, double-tap to read, hold to speak.",
                type = "action",
                confidence = 0.95
            )
        },

        // 22. Local / National / World News Categories (Higher specificity than generic briefing)
        FastPathRule(
            Pattern.compile(
                "\\b(read |tell me |give me |show |open )?(local news|nearby news|city news|state news)\\b|(लोकल समाचार|स्थानीय खबरें|स्थानीय समाचार|लोकल खबरें|शहर की खबरें)|(स्थानिक बातम्या|लोकल बातम्या|शहरातील बातम्या)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.NEWS_CATEGORY,
                target = VoiceFeatures.NEWS,
                newsCategory = "local",
                speech = "Reading local news.",
                type = "action",
                confidence = 0.96
            )
        },
        FastPathRule(
            Pattern.compile(
                "\\b(read |tell me |give me |show |open )?(national news|country news|india news|nation news)\\b|(राष्ट्रीय समाचार|राष्ट्रीय खबरें|देश की खबरें|भारत की खबरें)|(राष्ट्रीय बातम्या|देशातील बातम्या|भारतातील बातम्या)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.NEWS_CATEGORY,
                target = VoiceFeatures.NEWS,
                newsCategory = "national",
                speech = "Reading national news.",
                type = "action",
                confidence = 0.96
            )
        },
        FastPathRule(
            Pattern.compile(
                "\\b(read |tell me |give me |show |open )?(world news|global news|international news)\\b|(विश्व समाचार|दुनिया की खबरें|अंतरराष्ट्रीय खबरें|ग्लोबल समाचार|विदेश की खबरें)|(जागतिक बातम्या|जगातील बातम्या|आंतरराष्ट्रीय बातम्या)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.NEWS_CATEGORY,
                target = VoiceFeatures.NEWS,
                newsCategory = "global",
                speech = "Reading global news.",
                type = "action",
                confidence = 0.96
            )
        },

        // 23. Daily News Briefing
        FastPathRule(
            Pattern.compile(
                "\\b(tell me (the |today'?s |today )?news|what'?s the news|what is the news|give me (the |today'?s |today )?news|read (the |today'?s |today )?news|read today'?s headlines|what('?s| is) happening today|give me (the )?latest news|what('?s| is) happening in the world|give me today'?s headlines|open news|news assistant|daily news|today'?s news|news briefing|news)\\b|(मुझे\\s*)?(आज की\\s*)?(खबरें|समाचार|ताज़ा खबरें|ताजा समाचार|न्यूज़)\\s*(सुनाओ|बताओ|दिखाओ|पढ़ो|खोलो|पढो)|(आज क्या हुआ|देश दुनिया की खबरें|आज की मुख्य खबरें)|(मला\\s*)?(आजच्या\\s*)?(बातम्या|ताज्या बातम्या|बातमी|न्यूज)\\s*(सांगा|सांग|वाचा|दाखवा|उघडा)|(आज काय घडलं|देश विदेशातील बातम्या|आजच्या महत्त्वाच्या बातम्या)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.NEWS_BRIEFING,
                target = VoiceFeatures.NEWS,
                speech = "Getting today's news for you.",
                type = "navigation",
                confidence = 0.98
            )
        },

        // 24. News Controls: Next / Prev / Repeat
        FastPathRule(
            Pattern.compile(
                "\\b(read (that|it|this) again|repeat (the |this |that )?(news|story|headline)|replay (the |this )?(news|story))\\b|(यह खबर फिर से पढ़ो|फिर से सुनाओ|दोबारा सुनाओ|खबर दोहराओ|समाचार दोहराओ)|(ही बातमी पुन्हा वाचा|बातमी पुन्हा सांगा|पुन्हा ऐकवा)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.NEWS_REPEAT,
                speech = "",
                type = "action",
                confidence = 0.97
            )
        },
        FastPathRule(
            Pattern.compile(
                "\\b(next (news|story|headline|article)|skip (news|story))\\b|(अगली खबर|अगला समाचार|अगली स्टोरी|अगला लेख)|(पुढची बातमी|पुढील बातमी|पुढचा समाचार)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.NEWS_NEXT,
                speech = "Playing next story.",
                type = "action",
                confidence = 0.97
            )
        },
        FastPathRule(
            Pattern.compile(
                "\\b(previous (news|story|headline|article)|prev (news|story)|last (news|story)|go back a story)\\b|(पिछली खबर|पिछला समाचार|पिछली स्टोरी)|(मागची बातमी|मागील बातमी|मागील समाचार)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.NEWS_PREV,
                speech = "Playing previous story.",
                type = "action",
                confidence = 0.97
            )
        },

        // 25. News Source & Original Article
        FastPathRule(
            Pattern.compile(
                "\\b(what('?s| is) (the |this )?source|who reported this|source of this news|where is this (news|story) from)\\b|(स्रोत क्या है|यह खबर कहाँ से है|यह किसने रिपोर्ट की|सोर्स क्या है)|(स्रोत काय आहे|ही बातमी कुठून आली|सोर्स काय आहे)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.NEWS_SOURCE,
                speech = "",
                type = "action",
                confidence = 0.96
            )
        },
        FastPathRule(
            Pattern.compile(
                "\\b(open (this |the |original )?(article|link|news)|read (the )?full article|open original article)\\b|(पूरा आर्टिकल|मूल समाचार|मूल खबर|लिंक खोलो|आर्टिकल खोलो)|(पूर्ण बातमी|मूळ बातमी|लिंक उघडा|आर्टिकल उघडा)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.NEWS_OPEN_ORIGINAL,
                speech = "Opening original article.",
                type = "action",
                confidence = 0.96
            )
        },

        // 26. Refresh News & More Stories
        FastPathRule(
            Pattern.compile(
                "\\b(get (the )?latest news|refresh (the )?news|reload news|update news)\\b|(ताज़ा खबरें लाओ|ताजा समाचार लाओ|समाचार रिफ्रेश करो|ताज़ा समाचार लाओ)|(ताज्या बातम्या आणा|बातम्या रिफ्रेश करा|ताज्या घडामोडी आणा)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.NEWS_REFRESH,
                target = VoiceFeatures.NEWS,
                speech = "Getting the latest news.",
                type = "action",
                confidence = 0.96
            )
        },
        FastPathRule(
            Pattern.compile(
                "\\b(more news|give me more( news)?|what'?s next( in the news)?|read more( news)?|more stories|additional news)\\b|(और खबरें|और समाचार|आगे की खबरें|और सुनाओ|और बताओ)|(आणखी बातम्या|पुढील बातम्या सांगा|अजून बातम्या|आणखी ऐकवा)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.NEWS_MORE,
                target = VoiceFeatures.NEWS,
                speech = "Reading more stories from today.",
                type = "action",
                confidence = 0.96
            )
        },

        // 27. Entertainment Hub
        FastPathRule(
            Pattern.compile(
                "\\b(open entertainment( hub)?|entertainment|entertainment center|show entertainment|i'?m bored|im bored|i am bored)\\b|(मनोरंजन खोलो|मनोरंजन|एंटरटेनमेंट|बोर हो रहा हूँ|बोर हो रहा हूं)|(मनोरंजन उघडा|मनोरंजन|बोर होत आहे)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.OPEN_FEATURE,
                target = VoiceFeatures.ENTERTAINMENT,
                speech = "Opening Entertainment Hub.",
                type = "navigation",
                confidence = 0.96
            )
        },

        // 28. Live Radio (Play, Pause, Stop, Info, Next, Prev)
        FastPathRule(
            Pattern.compile(
                "\\b(start (my )?local radio|play (my )?local radio|local radio|play live radio|start live radio|live radio|play radio|resume radio|start radio|find nearby radio|show local stations|play radio near me)\\b|(लोकल रेडियो चलाओ|लोकल रेडियो|लाइव रेडियो|रेडियो बजाओ|रेडियो शुरू करो|रेडियो चालू करो|रेडियो चलाओ)|(लोकल रेडिओ लावा|लोकल रेडिओ सुरू करा|लाईव्ह रेडिओ|रेडिओ लावा|रेडिओ सुरू करा|रेडिओ चालू करा)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.LIVE_RADIO_PLAY,
                target = VoiceFeatures.RADIO,
                speech = "Starting live local radio.",
                type = "navigation",
                confidence = 0.96
            )
        },
        FastPathRule(
            Pattern.compile(
                "\\b(pause radio|pause the radio|pause live radio|pause stream)\\b|(रेडियो रोको|रेडियो पॉज़ करो|रेडियो पॉज करो)|(रेडिओ थांबवा|रेडिओ पॉज करा)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.RADIO_PAUSE,
                speech = "Radio paused.",
                type = "action",
                confidence = 0.96
            )
        },
        FastPathRule(
            Pattern.compile(
                "\\b(stop radio|stop the radio|stop live radio|turn off radio|close radio)\\b|(रेडियो बंद करो|रेडियो बंद कर|रेडियो स्टॉप करो)|(रेडिओ बंद करा|रेडिओ बंद कर|रेडिओ थांबवा)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.RADIO_STOP,
                speech = "Radio stopped.",
                type = "action",
                confidence = 0.96
            )
        },
        FastPathRule(
            Pattern.compile(
                "\\b(what'?s playing|what is playing|what'?s this (radio|station|channel)|what is this (radio|station|channel)|which station is this|which radio is this|what station is this|tell me what'?s playing)\\b|(क्या बज रहा है|कौन सा स्टेशन है|कौन सा रेडियो है|यह कौन सा स्टेशन है)|(काय वाजत आहे|कोणतं स्टेशन आहे|कोणता रेडिओ आहे|हे कोणतं स्टेशन आहे)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.LIVE_RADIO_INFO,
                speech = "",
                type = "action",
                confidence = 0.96
            )
        },
        FastPathRule(
            Pattern.compile(
                "\\b(next (radio )?station|next channel|change station|next stream|next radio|give me another (station|channel)|another station)\\b|(अगला स्टेशन|दूसरा स्टेशन|स्टेशन बदलो|अगला चैनल|कुछ और बजाओ)|(पुढचे स्टेशन|दुसरे स्टेशन|स्टेशन बदला|पुढचा चॅनल|काहीतरी दुसरं लावा)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.LIVE_RADIO_NEXT,
                speech = "Playing next radio station.",
                type = "action",
                confidence = 0.96
            )
        },
        FastPathRule(
            Pattern.compile(
                "\\b(previous (radio )?station|prev station|last station|previous channel|last channel)\\b|(पिछला स्टेशन|पहले वाला स्टेशन|पिछला चैनल)|(मागील स्टेशन|आधीचे स्टेशन|मागील चॅनल)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.LIVE_RADIO_PREV,
                speech = "Playing previous radio station.",
                type = "action",
                confidence = 0.96
            )
        },

        // 29. Stories (Open, Continue, Chapter navigation)
        FastPathRule(
            Pattern.compile(
                "\\b(tell (me )?a story|start a story|open stories|story library|browse stories|show stories|stories hub|listen to stories|audio stories)\\b|(कहानी सुनाओ|मुझे कहानी सुनाओ|कोई कहानी बताओ|कहानियाँ खोलो|कहानी लाइब्रेरी|कहानियां दिखाओ)|(गोष्ट सांगा|मला गोष्ट सांगा|गोष्टी उघडा|गोष्टींची लायब्ररी|गोष्टी दाखवा)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.OPEN_FEATURE,
                target = VoiceFeatures.STORIES,
                speech = "Opening stories library.",
                type = "navigation",
                confidence = 0.96
            )
        },
        FastPathRule(
            Pattern.compile(
                "\\b(continue (my |the )?story|resume (my |the )?story|continue listening|keep reading story|resume where i stopped)\\b|(मेरी कहानी जारी रखो|कहानी जारी रखो|कहानी आगे बढ़ाओ)|(माझी गोष्ट पुढे चालू करा|गोष्ट पुढे ऐका|गोष्ट पुढे सांगा)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.STORY_CONTINUE,
                target = VoiceFeatures.STORIES,
                speech = "Resuming your story.",
                type = "navigation",
                confidence = 0.96
            )
        },
        FastPathRule(
            Pattern.compile(
                "\\b(next chapter|skip chapter|forward chapter)\\b|(अगला अध्याय|अगला भाग|अगला चैप्टर)|(पुढचा भाग|पुढचा अध्याय|पुढचे प्रकरण)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.STORY_NEXT_CHAPTER,
                speech = "Next chapter.",
                type = "action",
                confidence = 0.96
            )
        },
        FastPathRule(
            Pattern.compile(
                "\\b(previous chapter|prev chapter|back chapter)\\b|(पिछला अध्याय|पिछला भाग|पिछला चैप्टर)|(मागील भाग|मागील अध्याय|आधीचे प्रकरण)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.STORY_PREV_CHAPTER,
                speech = "Previous chapter.",
                type = "action",
                confidence = 0.96
            )
        },

        // 30. Audio Games & Daily Challenge
        FastPathRule(
            Pattern.compile(
                "\\b(start trivia|play trivia|trivia game|start riddles|tell me a riddle|riddle game|start 20 questions|twenty questions|play 20 questions|start memory game|audio games|open games|let'?s play a game)\\b|(पहेली पूछो|पहेलियाँ|ट्रिविया खेलो|गेम्स खोलो|खेल शुरू करो|गेम खेलो)|(कोडे सांगा|कोडी|खेळ सुरू करा|गेम्स उघडा)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.OPEN_FEATURE,
                target = VoiceFeatures.GAMES,
                speech = "Opening audio games.",
                type = "navigation",
                confidence = 0.96
            )
        },
        FastPathRule(
            Pattern.compile(
                "\\b(start (today'?s |daily )?challenge|daily challenge|today'?s challenge)\\b|(दैनिक चुनौती|आज की चुनौती शुरू करो|दैनिक चैलेंज)|(दैनिक आव्हान|आजचे आव्हान सुरू करा)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.OPEN_FEATURE,
                target = VoiceFeatures.GAMES,
                speech = "Starting today's challenge.",
                type = "navigation",
                confidence = 0.96
            )
        },

        // 31. Progress / Streak / Score
        FastPathRule(
            Pattern.compile(
                "\\b(what('?s| is) my (score|streak|xp|progress)|how many days streak|check (my )?streak|my streak|how much xp|my score|show (my )?achievements|my progress)\\b|(मेरा स्कोर क्या है|मेरी स्ट्रीक क्या है|मेरी प्रगति|उपलब्धियां दिखाओ|मेरा स्कोर|मेरी प्रोग्रेस)|(माझा स्कोर काय आहे|माझी स्ट्रीक काय आहे|माझी प्रगती|माझे यश|माझा स्कोअर)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.OPEN_FEATURE,
                target = VoiceFeatures.PROGRESS,
                speech = "Opening your progress.",
                type = "navigation",
                confidence = 0.95
            )
        }
    )

    // Dynamic Entity Extraction Patterns
    private val PHONE_NUMBER_PATTERN = Pattern.compile(
        "\\b(?:call|dial|phone)\\s+(\\+?\\d[\\d\\s-]{3,})\\b|(\\+?\\d[\\d\\s-]{3,})\\s*(?:पर\\s*)?(?:कॉल करो|फोन लगाओ|फोन करो|डायल करो)|(\\+?\\d[\\d\\s-]{3,})\\s*(?:वर\\s*)?(?:कॉल करा|फोन करा|डायल करा)",
        Pattern.CASE_INSENSITIVE
    )

    private val CALL_CONTACT_PATTERN = Pattern.compile(
        "\\b(?:please\\s+)?(?:call|phone|dial|ring|i want to call|make a call to)\\s+(.+)\\b|([a-zA-Z\\u0900-\\u097F\\s]+?)\\s*(?:को|ला|ना)?\\s*(?:कॉल करो|फोन करो|फोन लगाओ|कॉल लगाओ|कॉल करना है|फोन लगाना है|कॉल करा|कॉल कर|फोन कर|फोन करा|फोन लावायचा आहे|कॉल करायचा आहे)",
        Pattern.CASE_INSENSITIVE
    )

    private val OBJECT_FINDER_PATTERNS = listOf(
        Pattern.compile("^(?:find|locate|search for|where is|where are|help me find)\\s+(?:my|the|a)?\\s*(.+)$", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^(.+)\\s+(?:ढूँढो|ढूंढो|खोजो|शोधा|कुठे आहे|कहाँ है)$", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^(?:मेरी|मेरा|माझा|माझी|माझे)\\s+(.+)\\s+(?:ढूँढो|ढूंढो|खोजो|शोधा|कहाँ है|कुठे आहे)$", Pattern.CASE_INSENSITIVE)
    )

    private val STORY_PLAY_PATTERN = Pattern.compile(
        "\\b(play|start|listen to)\\s+(alice'?s?\\s+(adventures\\s+in\\s+)?wonderland|alice|sherlock(\\s+holmes)?|scandal\\s+in\\s+bohemia|time\\s+machine|the\\s+time\\s+machine|dracula|jekyll(\\s+and\\s+hyde)?|dr\\s+jekyll|wizard\\s+of\\s+oz|secret\\s+garden|the\\s+secret\\s+garden|christmas\\s+carol|a\\s+christmas\\s+carol|sleepy\\s+hollow|tom\\s+sawyer|panchatantra|akbar(\\s+and\\s+)?birbal|birbal|idgah|do\\s+bailon\\s+ki\\s+katha|panch\\s+parmeshwar|shyamchi\\s+aai|sant\\s+tukaram)\\b|(एलिस|शरलॉक|टाइम मशीन|ड्रैकुला|विज़ार्ड|सीक्रेट गार्डन|क्रिसमस कैरोल|स्लीपी हॉलो|टॉम सॉयर|पंचतंत्र|अकबर बीरबल|बीरबल|ईदगाह|दो बैलों की कथा|पंच परमेश्वर)\\s*(सुनाओ|चलाओ|लगाओ|बजाओ)?|(एलिस|शेरलॉक|टाइम मशीन|ड्रॅक्युल|पंचतंत्र|अकबर बिरबल|बिरबल|श्यामची आई|संत तुकाराम)\\s*(गोष्ट)?\\s*(सांगा|लावा|ऐकवा)",
        Pattern.CASE_INSENSITIVE
    )

    private val STORY_GENRE_PATTERN = Pattern.compile(
        "\\b(play|start|tell me)\\s+(a\\s+)?(mystery|horror|scary|adventure|fantasy|classic|classics|detective|children|funny|comedy|folklore)\\s+story\\b|\\bplay\\s+something\\s+(scary|adventurous|mysterious|fun|classic)\\b|(रहस्यमयी|डरावनी|साहसिक|जादुई|बच्चों की|मजेदार)\\s+कहानी\\s*(सुनाओ|चलाओ|शुरू करो)|(रहस्य|भीतीदायक|साहसी|मुलांची|गंमत)\\s+गोष्ट\\s*(सांगा|लावा)",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Matches a command against local fast-path rules without making a network call.
     * Returns null if no fast path matched.
     */
    fun matchFastPath(command: String?): AssistantAction? {
        if (command.isNullOrBlank()) return null

        val normalized = command.lowercase()
            .replace(Regex("[.,!?;:।॥]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        // 1. Check direct fast paths
        for (rule in FAST_PATH_RULES) {
            if (rule.pattern.matcher(normalized).find()) {
                return rule.buildAction(normalized)
            }
        }

        // 2. Direct Phone Number Call
        val phoneMatcher = PHONE_NUMBER_PATTERN.matcher(normalized)
        if (phoneMatcher.find()) {
            val rawNum = phoneMatcher.group(1) ?: phoneMatcher.group(2) ?: phoneMatcher.group(3) ?: ""
            val cleanNum = rawNum.replace(Regex("[^\\d+]"), "")
            if (cleanNum.isNotBlank()) {
                return AssistantAction(
                    action = VoiceActions.CALL_NUMBER,
                    target = VoiceFeatures.CALLING,
                    phoneNumber = cleanNum,
                    speech = "Calling $cleanNum.",
                    type = "navigation",
                    confidence = 0.96
                )
            }
        }

        // 3. Specific Story Play by Name
        val storyMatcher = STORY_PLAY_PATTERN.matcher(normalized)
        if (storyMatcher.find()) {
            val query = storyMatcher.group(0)?.trim() ?: ""
            return AssistantAction(
                action = VoiceActions.STORY_PLAY,
                target = VoiceFeatures.STORIES,
                storyQuery = query,
                speech = "Playing story.",
                type = "action",
                confidence = 0.96
            )
        }

        // 4. Story Play by Genre
        val genreMatcher = STORY_GENRE_PATTERN.matcher(normalized)
        if (genreMatcher.find()) {
            var genre = "popular"
            if (normalized.contains("horror") || normalized.contains("scary") || normalized.contains("डरावनी") || normalized.contains("भीतीदायक")) {
                genre = "horror"
            } else if (normalized.contains("mystery") || normalized.contains("detective") || normalized.contains("रहस्य")) {
                genre = "mystery"
            } else if (normalized.contains("adventure") || normalized.contains("साहस")) {
                genre = "adventure"
            } else if (normalized.contains("fantasy") || normalized.contains("जादुई")) {
                genre = "fantasy"
            } else if (normalized.contains("children") || normalized.contains("बच्चों") || normalized.contains("मुलांची")) {
                genre = "children"
            }
            return AssistantAction(
                action = VoiceActions.STORY_PLAY_GENRE,
                target = VoiceFeatures.STORIES,
                genre = genre,
                speech = "Playing a $genre story.",
                type = "action",
                confidence = 0.95
            )
        }

        // 5. Calling Contact by Name
        val contactMatcher = CALL_CONTACT_PATTERN.matcher(normalized)
        if (contactMatcher.matches()) {
            val rawName = contactMatcher.group(1) ?: contactMatcher.group(2) ?: contactMatcher.group(3) ?: ""
            val cleanName = rawName
                .replace(Regex("^(my|a|the|my\\s+dear|mera|meri|mere|majha|majhi|majhe)\\s+", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s*(?:को|ला|ना)$"), "")
                .replace(Regex("[.!?]"), "")
                .trim()

            if (cleanName.isNotBlank() && cleanName.length > 1 && !cleanName.equals("vision", ignoreCase = true) && !cleanName.equals("ai", ignoreCase = true)) {
                // If it looks like pure digits, return as CALL_NUMBER
                if (cleanName.replace(Regex("\\s+"), "").matches(Regex("^\\+?\\d+$"))) {
                    val phoneNum = cleanName.replace(Regex("[^\\d+]"), "")
                    return AssistantAction(
                        action = VoiceActions.CALL_NUMBER,
                        target = VoiceFeatures.CALLING,
                        phoneNumber = phoneNum,
                        speech = "Calling $phoneNum.",
                        type = "navigation",
                        confidence = 0.96
                    )
                }
                return AssistantAction(
                    action = VoiceActions.CALL_CONTACT,
                    target = VoiceFeatures.CALLING,
                    contactName = cleanName,
                    speech = "Calling $cleanName.",
                    type = "navigation",
                    confidence = 0.96
                )
            }
        }

        // 6. Object Finder with dynamic target object extraction
        for (pattern in OBJECT_FINDER_PATTERNS) {
            val matcher = pattern.matcher(normalized)
            if (matcher.matches()) {
                val rawObj = matcher.group(1)?.trim() ?: ""
                val cleanObj = rawObj
                    .replace(Regex("^(my|the|a|mera|meri|mere|majha|majhi|majhe)\\s+", RegexOption.IGNORE_CASE), "")
                    .trim()

                if (cleanObj.isNotBlank() && cleanObj.length > 1) {
                    return AssistantAction(
                        action = VoiceActions.FIND_OBJECT,
                        target = VoiceFeatures.OBJECT_FINDER,
                        objectName = cleanObj,
                        speech = "Looking for your $cleanObj.",
                        type = "navigation",
                        confidence = 0.95
                    )
                }
            }
        }

        return null
    }

    /**
     * Validates that an action produced by Gemini is on the allowlist.
     */
    fun isAllowedAction(action: String?): Boolean {
        if (action.isNullOrBlank()) return false
        return ALLOWED_ACTIONS.contains(action.trim().uppercase())
    }

    /**
     * Validates that a feature target is on the allowlist.
     */
    fun isAllowedFeatureTarget(target: String?): Boolean {
        if (target.isNullOrBlank()) return false
        return ALLOWED_FEATURES.contains(target.trim())
    }

    /**
     * Sanitizes and validates an action object.
     */
    fun validateAction(action: AssistantAction?): AssistantAction {
        if (action == null) return AssistantAction(action = VoiceActions.UNKNOWN)

        val normalizedAction = action.action.trim().uppercase()
        if (!ALLOWED_ACTIONS.contains(normalizedAction)) {
            return AssistantAction(action = VoiceActions.UNKNOWN, speech = action.speech)
        }

        val target = action.target?.trim()
        val validTarget = if (target != null && ALLOWED_FEATURES.contains(target)) target else null

        if (normalizedAction == VoiceActions.OPEN_FEATURE && validTarget == null) {
            return AssistantAction(action = VoiceActions.UNKNOWN, speech = action.speech)
        }

        return action.copy(
            action = normalizedAction,
            target = validTarget
        )
    }

    /**
     * Maps feature target identifier to navigation route.
     */
    fun routeForTarget(target: String?, objectName: String? = null): String {
        return when (target?.lowercase()) {
            VoiceFeatures.READING.lowercase() -> "reading"
            VoiceFeatures.SURROUNDINGS.lowercase() -> "surroundings"
            VoiceFeatures.CURRENCY.lowercase() -> "currency"
            VoiceFeatures.TRANSPORT.lowercase() -> "transport"
            VoiceFeatures.OBJECT_FINDER.lowercase(), "finder" -> {
                if (!objectName.isNullOrBlank()) "finder?target=$objectName" else "finder"
            }
            VoiceFeatures.LOCATION.lowercase() -> "location"
            VoiceFeatures.VOLUNTEER.lowercase() -> "volunteer"
            VoiceFeatures.EMERGENCY.lowercase(), "sos" -> "sos"
            VoiceFeatures.ENTERTAINMENT.lowercase() -> "entertainment"
            VoiceFeatures.RADIO.lowercase(), VoiceFeatures.LIVE_RADIO.lowercase() -> "radio"
            VoiceFeatures.STORIES.lowercase() -> "stories"
            VoiceFeatures.GAMES.lowercase(), VoiceFeatures.DAILY_CHALLENGE.lowercase() -> "games"
            VoiceFeatures.PROGRESS.lowercase() -> "progress"
            VoiceFeatures.LIVE_VISION.lowercase(), "live_vision" -> "live_vision"
            VoiceFeatures.VOICE_CALL.lowercase(), "voice_call" -> "voice_call"
            VoiceFeatures.CALLING.lowercase(), "phone", "dialer" -> "calling"
            VoiceFeatures.NEWS.lowercase(), "headlines" -> "news"
            VoiceFeatures.HOME.lowercase() -> "home"
            else -> "home"
        }
    }
}
