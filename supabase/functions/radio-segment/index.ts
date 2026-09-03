// supabase/functions/radio-segment/index.ts
import { serve } from 'https://deno.land/std@0.168.0/http/server.ts';
import { corsHeaders, handleCors } from '../_shared/cors.ts';
import { generateJson } from '../_shared/gemini.ts';
import { languageInstruction, normalizeLanguage } from '../_shared/i18n.ts';

const RADIO_FALLBACKS: Record<string, any[]> = {
  en: [
    {
      title: 'Did You Know?',
      topic: 'facts',
      content: 'Honey never spoils. Archaeologists have discovered pots of honey in ancient Egyptian tombs that are over three thousand years old and still perfectly edible.',
      speech: 'Here is a quick fact. Honey never spoils. Archaeologists have discovered pots of honey in ancient Egyptian tombs that are over three thousand years old and still perfectly edible.',
    },
    {
      title: 'Space Snapshot',
      topic: 'science',
      content: 'A day on Venus is longer than a year on Venus. It takes Venus two hundred and forty-three Earth days to rotate once, but only two hundred and twenty-five Earth days to orbit the Sun.',
      speech: 'Here is a science moment. A day on Venus is longer than a year on Venus. It takes Venus two hundred and forty-three Earth days to rotate once, but only two hundred and twenty-five Earth days to orbit the Sun.',
    },
  ],
  hi: [
    {
      title: 'रोचक तथ्य',
      topic: 'facts',
      content: 'शहद कभी ख़राब नहीं होता। पुरातत्वविदों को प्राचीन मिस्र के मक़बरों में तीन हज़ार साल पुराना शहद मिला है जो आज भी खाने लायक है।',
      speech: 'यहाँ एक रोचक तथ्य है। शहद कभी ख़राब नहीं होता। पुरातत्वविदों को प्राचीन मिस्र के मक़बरों में तीन हज़ार साल पुराना शहद मिला है जो आज भी खाने लायक है।',
    },
  ],
  mr: [
    {
      title: 'रोचक माहिती',
      topic: 'facts',
      content: 'मध कधीही खराब होत नाही. प्राचीन इजिप्तच्या थडग्यांमध्ये तीन हजार वर्षे जुना मध सापडला आहे जो आजही खाण्यायोग्य आहे.',
      speech: 'एक रंजक माहिती. मध कधीही खराब होत नाही. प्राचीन इजिप्तच्या थडग्यांमध्ये तीन हजार वर्षे जुना मध सापडला आहे जो आजही खाण्यायोग्य आहे.',
    },
  ],
};

serve(async (req: Request) => {
  const corsResponse = handleCors(req);
  if (corsResponse) return corsResponse;

  try {
    const { topic = 'interesting facts', previousSummary = '', language = 'en' } = await req.json().catch(() => ({}));
    const normalizedLang = normalizeLanguage(language);

    const prompt = `You are the host of VisionBridge AI Radio, an accessible audio channel for low-vision listeners.
TASK: Generate ONE short, engaging, speech-friendly audio radio segment on the topic: "${topic}".
${languageInstruction(normalizedLang)}

Rules:
1. The segment must be brief: 2 to 4 sentences maximum (approx 40-70 words).
2. It will be read aloud by Text-to-Speech: write clear, conversational sentences without markdown, bullets, asterisks, or visual emojis.
${previousSummary ? `Avoid repeating previous topic: "${previousSummary}".` : ''}

Return JSON ONLY:
{
  "title": "Catchy 3-5 word segment title",
  "topic": "${topic}",
  "content": "Spoken segment text...",
  "speech": "Introductory spoken phrase followed by the segment content."
}`;

    let segment = await generateJson(prompt, {
      temperature: 0.7,
      topP: 0.9,
      maxOutputTokens: 300,
    });

    if (!segment || !segment.content) {
      const fallbackList = RADIO_FALLBACKS[normalizedLang] || RADIO_FALLBACKS.en;
      segment = fallbackList[Math.floor(Math.random() * fallbackList.length)];
    }

    const data = {
      title: segment.title || 'AI Radio Segment',
      topic: segment.topic || topic,
      content: segment.content,
      speech: segment.speech || segment.content,
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
