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
    const val RADIO_PAUSE = "RADIO_PAUSE"
    const val RADIO_RESUME = "RADIO_RESUME"
    const val RADIO_STOP = "RADIO_STOP"
    const val LIVE_RADIO_INFO = "LIVE_RADIO_INFO"
    const val STORY_PLAY = "STORY_PLAY"
    const val STORY_CONTINUE = "STORY_CONTINUE"
    const val STORY_NEXT_CHAPTER = "STORY_NEXT_CHAPTER"
    const val STORY_PREV_CHAPTER = "STORY_PREV_CHAPTER"
    const val START_GAME = "START_GAME"
    const val GET_PROGRESS = "GET_PROGRESS"
    const val UNKNOWN = "UNKNOWN"
}

object VoiceFeatures {
    const val SURROUNDINGS = "surroundings"
    const val HAZARD = "hazard"
    const val READING = "reading"
    const val CURRENCY = "currency"
    const val TRANSPORT = "transport"
    const val OBJECT_FINDER = "objectFinder"
    const val LOCATION = "location"
    const val VOLUNTEER = "volunteer"
    const val EMERGENCY = "emergency"
    const val ENTERTAINMENT = "entertainment"
    const val RADIO = "radio"
    const val LIVE_RADIO = "liveradio"
    const val STORIES = "stories"
    const val GAMES = "games"
    const val DAILY_CHALLENGE = "dailychallenge"
    const val PROGRESS = "progress"
    const val LIVE_VISION = "live_vision"
    const val VOICE_CALL = "voice_call"
    const val HOME = "home"
}

object VoiceActionRouter {

    private data class FastPathRule(
        val pattern: Pattern,
        val buildAction: (String) -> AssistantAction
    )

    private val FAST_PATH_RULES = listOf(
        // 1. Emergency SOS
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
                type = "navigation"
            )
        },

        // 2. Stop Speaking
        FastPathRule(
            Pattern.compile(
                "\\b(stop talking|be quiet|stop speaking|shut up|stop reading)\\b|(चुप हो जाओ|चुप रहो|बोलना बंद करो|बोलना बंद कर|पढ़ना बंद करो)|(गप्प बसा|गप्प बस|बोलणं थांबवा|बोलणे थांबवा|वाचणं थांबवा)|^stop$|^quiet$|^(रुको|रुक|चुप|बंद|थांब|थांबा|गप्प)$",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.STOP_SPEAKING,
                speech = "",
                type = "action"
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
                type = "action"
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
                type = "action"
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
                type = "action"
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
                type = "navigation"
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
                type = "action"
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
                type = "action"
            )
        },

        // 9. Surroundings
        FastPathRule(
            Pattern.compile(
                "\\b(describe (my |the )?surroundings|what is around me|tell me what is around me|open camera assistant|camera assistant|surroundings)\\b|(मेरे आसपास क्या है|आसपास क्या है|आसपास देखो|कैमरा असिस्टेंट)|(माझ्या आजूबाजूला काय आहे|आजूबाजूला काय आहे|कॅमेरा असिस्टंट)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.OPEN_FEATURE,
                target = VoiceFeatures.SURROUNDINGS,
                speech = "Opening Camera Assistant.",
                type = "navigation"
            )
        },

        // 10. Currency
        FastPathRule(
            Pattern.compile(
                "\\b(read (the |this |my )?money|count (this |the |my )?currency|count (this |the |my )?money|how much money( is this)?|how much currency( is this)?|currency reader|open currency reader|currency assistant|read currency|currency)\\b|(पैसे गिनो|रुपये गिनो|पैसे पढ़ो|करेंसी रीडर|पैसे देखो|यह कितने पैसे हैं|कितने रुपये हैं)|(पैसे मोजा|रुपये मोजा|पैसे वाचा|करन्सी रीडर|पैसे बघा|हे किती पैसे आहेत|किती रुपये आहेत)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.OPEN_FEATURE,
                target = VoiceFeatures.CURRENCY,
                speech = "Opening Currency Reader.",
                type = "navigation"
            )
        },

        // 11. Reading
        FastPathRule(
            Pattern.compile(
                "\\b(read this( menu)?|read the (menu|text|document|sign)|open reading assistant|reading assistant|start reading|read text|reading)\\b|(यह मेन्यू पढ़ो|यह पढ़ो|मेन्यू पढ़ो|रीडिंग असिस्टेंट|पढ़ना शुरू करो)|(हा मेन्यू वाच|हे वाच|रीडिंग असिस्टंट)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.OPEN_FEATURE,
                target = VoiceFeatures.READING,
                speech = "Opening Reading Assistant.",
                type = "navigation"
            )
        },

        // 12. Transport
        FastPathRule(
            Pattern.compile(
                "\\b(where is this bus going|public transport|open transport|transport assistant|bus assistant|train assistant|transport)\\b|(यह बस कहाँ जा रही है|पब्लिक ट्रांसपोर्ट|ट्रांसपोर्ट असिस्टेंट)|(ही बस कुठे जाते|ट्रान्सपोर्ट असिस्टंट)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.OPEN_FEATURE,
                target = VoiceFeatures.TRANSPORT,
                speech = "Opening Transport Assistant.",
                type = "navigation"
            )
        },

        // 13. Location
        FastPathRule(
            Pattern.compile(
                "\\b(where am i|check (my )?location|my location|open location assistant|location assistant|where am i location)\\b|(मैं कहाँ हूँ|मेरी लोकेशन|लोकेशन असिस्टेंट)|(मी कुठे आहे|माझं ठिकाण|लोकेशन असिस्टंट)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.OPEN_FEATURE,
                target = VoiceFeatures.LOCATION,
                speech = "Opening Where Am I.",
                type = "navigation"
            )
        },

        // 14. Hazard
        FastPathRule(
            Pattern.compile(
                "\\b(start hazard mode|hazard mode|hazard scanning|watch for hazards|monitor danger|hazard)\\b|(खतरे देखो|खतरा मोड|हज़ार्ड मोड)|(धोका मोड|धोका बघा)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.OPEN_FEATURE,
                target = VoiceFeatures.HAZARD,
                speech = "Starting Hazard Scanning.",
                type = "navigation"
            )
        },

        // 15. Volunteer
        FastPathRule(
            Pattern.compile(
                "\\b(call (a )?volunteer|connect( with)? volunteer|volunteer help|need human help|talk to volunteer|volunteer)\\b|(वॉलंटियर को कॉल करो|वॉलंटियर से जोड़ो|इंसान से बात करनी है)|(वॉलंटियरशी जोडा|वॉलंटियर मदत)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.START_VOLUNTEER_HELP,
                target = VoiceFeatures.VOLUNTEER,
                speech = "Connecting you with a volunteer.",
                type = "navigation"
            )
        },

        // 16. Gesture Help
        FastPathRule(
            Pattern.compile(
                "\\b(gesture help|how do gestures work|how to use gestures|gesture guide|gesture instructions|gesture navigation|gestures)\\b|(जेस्चर मदद|जेस्चर सहायता|जेस्चर कैसे इस्तेमाल करें|जेस्चर क्या हैं)|(जेस्चर मदत|जेस्चर कसे वापरावे|जेस्चर माहिती)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.GESTURE_HELP,
                speech = "Tap anywhere to activate voice, double-tap to read, hold to speak.",
                type = "action"
            )
        },

        // 17. Entertainment Hub
        FastPathRule(
            Pattern.compile(
                "\\b(open entertainment( hub)?|entertainment|entertainment center|show entertainment)\\b|(मनोरंजन खोलो|मनोरंजन|एंटरटेनमेंट)|(मनोरंजन उघडा|मनोरंजन)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.OPEN_FEATURE,
                target = VoiceFeatures.ENTERTAINMENT,
                speech = "Opening Entertainment Hub.",
                type = "navigation"
            )
        },

        // 18. Live Radio (Generic & Direct)
        FastPathRule(
            Pattern.compile(
                "\\b(start radio|open radio|radio|live radio|tune into radio|play some radio|turn on the radio)\\b|(रेडियो शुरू करो|रेडियो खोलो|रेडियो|लाइव रेडियो)|(रेडिओ सुरू करा|रेडिओ उघडा|रेडिओ|लाईव्ह रेडिओ)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.OPEN_FEATURE,
                target = VoiceFeatures.RADIO,
                speech = "Opening Live Radio.",
                type = "navigation"
            )
        },

        // 18b. AI Radio Query Fallback
        FastPathRule(
            Pattern.compile(
                "\\b(ai radio|start ai radio|open ai radio)\\b|(एआई रेडियो|एआय रेडिओ)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.OPEN_FEATURE,
                target = VoiceFeatures.RADIO,
                speech = "Live Radio is available. Opening Live Radio.",
                type = "navigation"
            )
        },

        // 19. Language / Local Live Radio
        FastPathRule(
            Pattern.compile(
                "\\b(play|start|find)\\s+(marathi|hindi|english|pune|mumbai|delhi|maharashtra|india)\\s+radio\\b|\\bplay\\s+radio\\s+(in|from)\\s+(marathi|hindi|english|pune|mumbai|delhi|maharashtra|india)\\b|(मराठी|हिंदी|इंग्लिश|पुणे|मुंबई|दिल्ली|महाराष्ट्र)\\s+रेडियो\\s*(चलाओ|बजाओ|शुरू करो)?|(मराठी|हिंदी|इंग्रजी|पुणे|मुंबई|दिल्ली|महाराष्ट्र)\\s+रेडिओ\\s*(लावा|सुरू करा)?",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.OPEN_FEATURE,
                target = VoiceFeatures.RADIO,
                speech = "Tuning in to radio.",
                type = "navigation"
            )
        },

        // 20. Live Local Radio Play
        FastPathRule(
            Pattern.compile(
                "\\b(start (my )?local radio|play (my )?local radio|local radio|play live radio|start live radio|live radio|play radio|resume radio|start radio|find nearby radio|show local stations|play radio near me)\\b|(लोकल रेडियो चलाओ|लोकल रेडियो|लाइव रेडियो|रेडियो बजाओ|रेडियो शुरू करो|रेडियो चालू करो|रेडियो चलाओ)|(लोकल रेडिओ लावा|लोकल रेडिओ सुरू करा|लाईव्ह रेडिओ|रेडिओ लावा|रेडिओ सुरू करा|रेडिओ चालू करा)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.OPEN_FEATURE,
                target = VoiceFeatures.RADIO,
                speech = "Starting live local radio.",
                type = "navigation"
            )
        },

        // 21. Radio Pause
        FastPathRule(
            Pattern.compile(
                "\\b(pause radio|pause the radio|pause live radio|pause stream)\\b|(रेडियो रोको|रेडियो पॉज़ करो|रेडियो पॉज करो)|(रेडिओ थांबवा|रेडिओ पॉज करा)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.RADIO_PAUSE,
                speech = "Radio paused.",
                type = "action"
            )
        },

        // 22. Radio Stop
        FastPathRule(
            Pattern.compile(
                "\\b(stop radio|stop the radio|stop live radio|turn off radio|close radio)\\b|(रेडियो बंद करो|रेडियो बंद कर|रेडियो स्टॉप करो)|(रेडिओ बंद करा|रेडिओ बंद कर|रेडिओ थांबवा)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.RADIO_STOP,
                speech = "Radio stopped.",
                type = "action"
            )
        },

        // 23. What's Playing / Radio Info
        FastPathRule(
            Pattern.compile(
                "\\b(what'?s playing|what is playing|what'?s this (radio|station|channel)|what is this (radio|station|channel)|which station is this|which radio is this|what station is this|tell me what'?s playing)\\b|(क्या बज रहा है|कौन सा स्टेशन है|कौन सा रेडियो है|यह कौन सा स्टेशन है)|(काय वाजत आहे|कोणतं स्टेशन आहे|कोणता रेडिओ आहे|हे कोणतं स्टेशन आहे)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.LIVE_RADIO_INFO,
                speech = "",
                type = "action"
            )
        },

        // 24. Radio Next Station
        FastPathRule(
            Pattern.compile(
                "\\b(next (radio )?station|next channel|change station|next stream|next radio)\\b|(अगला स्टेशन|दूसरा स्टेशन|स्टेशन बदलो|अगला चैनल)|(पुढचे स्टेशन|दुसरे स्टेशन|स्टेशन बदला|पुढचा चॅनल)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.LIVE_RADIO_NEXT,
                speech = "Playing next radio station.",
                type = "action"
            )
        },

        // 25. Radio Prev Station
        FastPathRule(
            Pattern.compile(
                "\\b(previous (radio )?station|prev station|last station|previous channel)\\b|(पिछला स्टेशन|पहले वाला स्टेशन|पिछला चैनल)|(मागील स्टेशन|आधीचे स्टेशन|मागील चॅनल)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.LIVE_RADIO_PREV,
                speech = "Playing previous radio station.",
                type = "action"
            )
        },

        // 26. Continue Story
        FastPathRule(
            Pattern.compile(
                "\\b(continue (my |the )?story|resume (my |the )?story|continue listening|keep reading story)\\b|(मेरी कहानी जारी रखो|कहानी जारी रखो|कहानी आगे बढ़ाओ)|(माझी गोष्ट पुढे चालू करा|गोष्ट पुढे ऐका)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.STORY_CONTINUE,
                target = VoiceFeatures.STORIES,
                speech = "Resuming your story.",
                type = "navigation"
            )
        },

        // 27. Show / Filter Stories
        FastPathRule(
            Pattern.compile(
                "\\b(show|open|browse|filter)\\s+(hindi|marathi|english|mystery|horror|adventure|fantasy|classics|folklore|comedy|popular)\\s+stories\\b|(हिंदी|मराठी|अंग्रेजी|रहस्यमयी|डरावनी|साहसिक|क्लासिक)\\s+कहानियाँ\\s*(दिखाओ|खोलो)|(मराठी|हिंदी|रहस्य|साहसी)\\s+गोष्टी\\s*(दाखवा|उघडा)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.OPEN_FEATURE,
                target = VoiceFeatures.STORIES,
                speech = "Showing stories.",
                type = "navigation"
            )
        },

        // 28. Open Stories
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
                type = "navigation"
            )
        },

        // 29. Next / Prev Chapter
        FastPathRule(
            Pattern.compile(
                "\\b(next chapter|skip chapter|forward chapter)\\b|(अगला अध्याय|अगला भाग|अगला चैप्टर)|(पुढचा भाग|पुढचा अध्याय|पुढचे प्रकरण)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.STORY_NEXT_CHAPTER,
                speech = "Next chapter.",
                type = "action"
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
                type = "action"
            )
        },

        // 30. Daily Challenge
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
                type = "navigation"
            )
        },

        // 31. Games (Trivia, Riddles, 20Q, Memory)
        FastPathRule(
            Pattern.compile(
                "\\b(start trivia|play trivia|trivia game|start riddles|tell me a riddle|riddle game|start 20 questions|twenty questions|play 20 questions|start memory game|audio games|open games)\\b|(पहेली पूछो|पहेलियाँ|ट्रिविया खेलो|गेम्स खोलो|खेल शुरू करो)|(कोडे सांगा|कोडी|खेळ सुरू करा)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.OPEN_FEATURE,
                target = VoiceFeatures.GAMES,
                speech = "Opening audio games.",
                type = "navigation"
            )
        },

        // 32. Progress / Streak / Achievements
        FastPathRule(
            Pattern.compile(
                "\\b(what'?s my streak|what is my streak|how many days streak|check my streak|my streak|what'?s my xp|how much xp|my score|show my achievements|my progress)\\b|(मेरा स्कोर|मेरा स्ट्रीक|मेरी प्रोग्रेस|मेरी उपलब्धियां|मेरे पॉइंट्स)|(माझा स्कोअर|माझी स्ट्रीक|माझी प्रगती|माझे यश)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.OPEN_FEATURE,
                target = VoiceFeatures.PROGRESS,
                speech = "Opening your progress.",
                type = "navigation"
            )
        },

        // 33. VisionBridge Live (Real-time camera + voice)
        FastPathRule(
            Pattern.compile(
                "\\b(open vision live|start vision live|vision live|live vision|open live vision|start live vision|realtime vision|real time vision)\\b|(विजन लाइव खोलो|विज़न लाइव खोलो|लाइव विजन|लाइव विज़न|विजन लाइव|विज़न लाइव)|(व्हिजन लाईव्ह उघडा|व्हिजन लाईव्ह|लाईव्ह व्हिजन)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.OPEN_FEATURE,
                target = VoiceFeatures.LIVE_VISION,
                speech = "Opening VisionBridge Live.",
                type = "navigation"
            )
        },

        // 34. AI Voice Call (Real-time conversational voice assistant)
        FastPathRule(
            Pattern.compile(
                "\\b(open voice call|start voice call|ai voice call|voice call|call ai|talk to ai|talk with ai|voice assistant call)\\b|(वॉयस कॉल शुरू करो|वॉयस कॉल खोलो|एआई वॉयस कॉल|वॉयस कॉल|एआई से बात करो)|(व्हॉईस कॉल सुरू करा|व्हॉईस कॉल उघडा|एआय व्हॉईस कॉल|व्हॉईस कॉल|एआयशी बोला)",
                Pattern.CASE_INSENSITIVE
            )
        ) {
            AssistantAction(
                action = VoiceActions.OPEN_FEATURE,
                target = VoiceFeatures.VOICE_CALL,
                speech = "Starting AI Voice Call.",
                type = "navigation"
            )
        }
    )

    private val OBJECT_FINDER_PATTERNS = listOf(
        Pattern.compile("^(?:find|locate|search for|where is)\\s+(?:my|the|a)?\\s*(.+)$", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^(.+)\\s+(?:ढूँढो|ढूंढो|खोजो|शोधा|कुठे आहे|कहाँ है)$", Pattern.CASE_INSENSITIVE)
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

        // 2. Check Object Finder with dynamic target object extraction
        for (pattern in OBJECT_FINDER_PATTERNS) {
            val matcher = pattern.matcher(normalized)
            if (matcher.matches()) {
                val objectName = matcher.group(1)?.trim()
                if (!objectName.isNullOrBlank()) {
                    return AssistantAction(
                        action = VoiceActions.FIND_OBJECT,
                        target = VoiceFeatures.OBJECT_FINDER,
                        objectName = objectName,
                        speech = "Looking for your $objectName.",
                        type = "navigation"
                    )
                }
            }
        }

        return null
    }

    /**
     * Maps feature target identifier to navigation route.
     */
    fun routeForTarget(target: String?, objectName: String? = null): String {
        return when (target?.lowercase()) {
            VoiceFeatures.READING -> "reading"
            VoiceFeatures.SURROUNDINGS -> "surroundings"
            VoiceFeatures.HAZARD -> "hazard"
            VoiceFeatures.CURRENCY -> "currency"
            VoiceFeatures.TRANSPORT -> "transport"
            VoiceFeatures.OBJECT_FINDER, "finder" -> {
                if (!objectName.isNullOrBlank()) "finder?target=$objectName" else "finder"
            }
            VoiceFeatures.LOCATION -> "location"
            VoiceFeatures.VOLUNTEER -> "volunteer"
            VoiceFeatures.EMERGENCY, "sos" -> "sos"
            VoiceFeatures.ENTERTAINMENT -> "entertainment"
            VoiceFeatures.RADIO, VoiceFeatures.LIVE_RADIO -> "radio"
            VoiceFeatures.STORIES -> "stories"
            VoiceFeatures.GAMES, VoiceFeatures.DAILY_CHALLENGE -> "games"
            VoiceFeatures.PROGRESS -> "progress"
            VoiceFeatures.LIVE_VISION -> "live_vision"
            VoiceFeatures.VOICE_CALL -> "voice_call"
            VoiceFeatures.HOME -> "home"
            else -> "home"
        }
    }
}
