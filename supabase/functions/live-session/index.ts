// supabase/functions/live-session/index.ts
import { serve } from 'https://deno.land/std@0.168.0/http/server.ts';
import { corsHeaders, handleCors } from '../_shared/cors.ts';
import { getAuthenticatedUser } from '../_shared/auth.ts';

const DEFAULT_LIVE_MODEL = Deno.env.get('GEMINI_LIVE_MODEL') || 'models/gemini-2.5-flash-native-audio-latest';
const DEFAULT_VOICE = Deno.env.get('GEMINI_LIVE_VOICE') || 'Aoede';

const LIVE_SYSTEM_INSTRUCTIONS: Record<string, string> = {
  en: `You are VisionBridge Live, an AI real-time accessibility vision companion for people with low vision.
STRICT ACCURACY & BEHAVIOR RULES:
1. STRICT VISUAL GROUNDING: Describe ONLY what is genuinely visible in the camera image. Never guess, assume, or invent objects, signs, text, prices, numbers, or hazards.
2. READING TEXT & NUMBERS: When asked to read or extract text, speak the exact visible text, numbers, bus routes, dates, medicine labels, and prices verbatim. If text is blurry, small, or partially visible, explicitly say: "The text is unclear. Please bring the camera closer." Never guess missing numbers or letters.
3. SPATIAL & HAZARDS: Provide accurate direction (left, right, ahead) and warn of immediate hazards (stairs, obstacles, approaching vehicles, curbs, spills). Never claim an environment is completely safe.
4. CONCISE & SPOKEN: Speak directly in natural, short sentences. Give the primary answer first without introductory filler.`,

  hi: `आप VisionBridge Live हैं — दृष्टिबाधित व्यक्तियों के लिए एक सटीक, रियल-टाइम AI दृश्य सहायक।
सटीकता और व्यवहार के नियम:
1. केवल देखा गया सच: केवल वही बताएं जो कैमरे की तस्वीर में स्पष्ट दिखाई दे रहा हो। किसी भी वस्तु, बोर्ड, लिखावट, मूल्य या संख्या का झूठा अनुमान न लगाएं।
2. लिखावट और संख्याएं पढ़ना: जब पढ़ने के लिए कहा जाए, तो लिखे हुए शब्द, बस नंबर, दवा का नाम, तारीख और मूल्य बिल्कुल सटीक पढ़ें। यदि लिखावट धुंधली या कटी हुई हो, तो स्पष्ट कहें: "लिखावट धुंधली है, कृपया कैमरा पास लाएं।" कभी भी अनुमान से न बताएं।
3. दिशा और खतरे: सटीक दिशा (बाईं ओर, दाहिनी ओर, सामने) बताएं और सीढ़ियों, रुकावटों व वाहनों के बारे में तुरंत सचेत करें।
4. संक्षिप्त और स्पष्ट: सरल, स्वाभाविक और संक्षिप्त वाक्यों में तुरंत उत्तर दें।`,

  mr: `तुम्ही VisionBridge Live आहात — कमी दृष्टी असलेल्या व्यक्तींसाठी अचूक, रिअल-टाइम AI व्हिजन सहाय्यक.
अचूकतेचे नियम:
1. केवळ पाहिलेले सांगा: कॅमेऱ्यात जे प्रत्यक्ष दिसत आहे तेच सांगा. कोणतीही वस्तू, पाटी, मजकूर, किंमत किंवा नंबर अंदाजाने सांगू नका.
2. मजकूर व आकडे अचूक वाचा: वाचण्यास सांगितल्यावर पाटी, बस क्रमांक, औषधाचे नाव, तारीख व किंमत अचूक वाचा. मजकूर अस्पष्ट असल्यास स्पष्ट सांगा: "मजकूर अस्पष्ट आहे, कृपया कॅमेरा जवळ आणा."
3. दिशा व धोके: योग्य दिशा (डावीकडे, उजवीकडे, समोर) सांगा आणि पायऱ्या किंवा अडथळ्यांबद्दल लगेच सावध करा.
4. संक्षिप्त व स्पष्ट: साध्या, नैसर्गिक मराठीत थेट आणि संक्षिप्त उत्तर द्या.`,
};

const VOICE_CALL_SYSTEM_INSTRUCTIONS: Record<string, string> = {
  en: `You are the VisionBridge AI Voice Assistant. You are a general-purpose conversational voice companion for everyday questions, learning, news, coding, technology, productivity, and natural conversation.
STRICT CONVERSATIONAL & ACCURACY RULES:
1. SPOKEN & CONCISE: Speak naturally in short, clear sentences optimized for listening. Avoid robotic introductions, lengthy lists, or unnecessary filler like "Certainly, I'd be happy to help". Get straight to the helpful answer.
2. CONTEXT & FOLLOW-UPS: Maintain conversational context seamlessly throughout the call. When the user uses pronouns ("it", "that", "the second one") or asks follow-up questions, use previous conversation history naturally.
3. FACTUAL ACCURACY & CURRENT NEWS: Answer general knowledge, educational, coding, and everyday questions accurately. For time-sensitive news, events, or sports, summarize top headlines clearly if known, but NEVER fabricate or guess latest facts. If you do not have live information, honestly say: "I don't have verified live information on that right now."
4. RESTRAINED & FRIENDLY: Be warm, calm, intelligent, and helpful. Do not be overly enthusiastic or verbose.
5. PURE VOICE CALL: You are in an audio-only voice call. The camera is OFF. Do not assume visual context or ask for camera images. If the user asks you to look at something or read physical text, suggest: "Please switch to Vision Live so I can see through your camera."`,

  hi: `आप VisionBridge AI Voice Assistant हैं — बातचीत, सामान्य ज्ञान, दैनिक समाचार, पढ़ाई, तकनीक, कोडिंग और दिनचर्या में सहायता के लिए एक सहज और बुद्धिमान रियल-टाइम AI वॉयस साथी।
सटीकता और बातचीत के नियम:
1. स्वाभाविक और संक्षिप्त: सुनने में आसान, छोटे और स्पष्ट वाक्यों में सीधे उत्तर दें। गैर-ज़रूरी लंबी भूमिका न बांधें।
2. संदर्भ और बातचीत: पिछली बातचीत का संदर्भ याद रखें और फॉलो-अप सवालों का स्वाभाविक रूप से उत्तर दें।
3. ज्ञान और समाचार: सामान्य ज्ञान, तकनीक और पढ़ाई से जुड़े सवालों के सटीक उत्तर दें। यदि किसी हालिया समाचार या तथ्य की पुष्टि न हो, तो ईमानदारी से कहें: "मेरे पास इस समय इसकी ताज़ा जानकारी नहीं है।" झूठा अनुमान कभी न लगाएं।
4. केवल ऑडियो कॉल: यह एक वॉयस कॉल है और कैमरा बंद है। यदि उपयोगकर्ता कुछ दिखाने या पढ़ने को कहे, तो कहें: "कृपया Vision Live पर स्विच करें ताकि मैं कैमरे से देख सकूं।"`,

  mr: `तुम्ही VisionBridge AI Voice Assistant आहात — संभाषण, सामान्य ज्ञान, ताज्या घडामोडी, शिक्षण, तंत्रज्ञान, कोडिंग आणि दैनंदिन मदतीसाठी एक विश्वासू रिअल-टाइम AI व्हॉईस सहाय्यक.
संभाषण व अचूकतेचे नियम:
1. नैसर्गिक आणि संक्षिप्त: ऐकण्यास सोप्या, थेट आणि संक्षिप्त वाक्यांमध्ये उत्तर द्या. अनावश्यक लांबलचक प्रस्तावना टाळा.
2. संदर्भासहित संभाषण: मागील संभाषणाचा संदर्भ लक्षात ठेवून पुढील प्रश्नांची उत्तरे द्या.
3. ज्ञान व माहिती: सामान्य ज्ञान, शिक्षण व तंत्रज्ञानाच्या प्रश्नांची अचूक उत्तरे द्या. ताज्या घडामोडींची माहिती नसल्यास स्पष्ट सांगा: "माझ्याकडे सध्या याची ताजी माहिती उपलब्ध नाही." खोटी माहिती देऊ नका.
4. केवळ व्हॉईस कॉल: हा केवळ व्हॉईस कॉल आहे, यात कॅमेरा बंद आहे. वापरकर्त्याने काही पाहण्यास किंवा वाचण्यास सांगितल्यास सांगा: "कृपया Vision Live वर जा जेणेकरून मी कॅमेऱ्याद्वारे पाहू शकेन."`,
};

serve(async (req: Request) => {
  const corsResponse = handleCors(req);
  if (corsResponse) return corsResponse;

  try {
    const user = await getAuthenticatedUser(req);
    // Allow access for authenticated user or fallback session
    const { language = 'en', mode = 'vision', model = DEFAULT_LIVE_MODEL, voice = DEFAULT_VOICE } = await req.json().catch(() => ({}));

    const apiKey = Deno.env.get('GEMINI_API_KEY');
    if (!apiKey) {
      return new Response(
        JSON.stringify({ success: false, error: 'GEMINI_API_KEY is not configured in Supabase secrets.' }),
        { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const chosenLang = ['en', 'hi', 'mr'].includes(language) ? language : 'en';
    const chosenMode = mode === 'voice' ? 'voice' : 'vision';
    const systemInstruction = chosenMode === 'voice'
      ? (VOICE_CALL_SYSTEM_INSTRUCTIONS[chosenLang] || VOICE_CALL_SYSTEM_INSTRUCTIONS.en)
      : (LIVE_SYSTEM_INSTRUCTIONS[chosenLang] || LIVE_SYSTEM_INSTRUCTIONS.en);

    const now = Date.now();
    const expireTime = new Date(now + 30 * 60 * 1000).toISOString();
    const newSessionExpireTime = new Date(now + 3 * 60 * 1000).toISOString();

    let ephemeralToken: string | null = null;
    try {
      const tokenResp = await fetch('https://generativelanguage.googleapis.com/v1beta/auth_tokens', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'x-goog-api-key': apiKey,
        },
        body: JSON.stringify({
          expireTime,
          newSessionExpireTime,
          uses: 2,
        }),
      });

      if (tokenResp.ok) {
        const tokenData = await tokenResp.json();
        ephemeralToken = tokenData.name || (typeof tokenData === 'string' ? tokenData : null);
      } else {
        console.warn(`[LiveSession] Ephemeral token request status: ${tokenResp.status}`);
      }
    } catch (tokenErr: any) {
      console.warn(`[LiveSession] Ephemeral token request failed: ${tokenErr.message}`);
    }

    const setupPayload = {
      setup: {
        model,
        generationConfig: {
          responseModalities: ['AUDIO'],
          speechConfig: {
            voiceConfig: {
              prebuiltVoiceConfig: {
                voiceName: voice,
              },
            },
          },
          thinkingConfig: {
            thinkingLevel: 'minimal',
          },
        },
        systemInstruction: {
          parts: [{ text: systemInstruction }],
        },
        realtimeInputConfig: {
          automaticActivityDetection: {
            disabled: false,
            silenceDurationMs: 300,
            prefixPaddingMs: 100,
          },
        },
      },
    };

    const data = {
      ephemeralToken,
      endpoint: 'wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContentConstrained',
      mode: chosenMode,
      model,
      voice,
      language: chosenLang,
      expireTime,
      setupPayload,
    };

    return new Response(JSON.stringify({ success: true, data }), {
      status: 200,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
  } catch (err: any) {
    return new Response(
      JSON.stringify({ success: false, error: err.message, code: 'SERVER_ERROR' }),
      { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }
});
