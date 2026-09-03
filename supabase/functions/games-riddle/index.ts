// supabase/functions/games-riddle/index.ts
import { serve } from 'https://deno.land/std@0.168.0/http/server.ts';
import { corsHeaders, handleCors } from '../_shared/cors.ts';
import { normalizeLanguage } from '../_shared/i18n.ts';

const RIDDLES_BANK: Record<string, any[]> = {
  en: [
    { riddle: 'I have keys, but no locks. I have space, but no room. You can enter, but you cannot go outside. What am I?', answer: 'Keyboard', clue: 'You use it to type words on a computer.' },
    { riddle: 'What gets wetter the more it dries?', answer: 'Towel', clue: 'You use it after taking a shower.' },
    { riddle: 'What has hands, but cannot clap?', answer: 'Clock', clue: 'It tells you what time it is.' },
    { riddle: 'What has a head, a tail, is brown, and has no legs?', answer: 'Penny', clue: 'It is a small coin.' },
  ],
  hi: [
    { riddle: 'ऐसी कौन सी चीज़ है जिसके पास चाबियाँ हैं लेकिन ताले नहीं, स्पेस है लेकिन कमरा नहीं, और आप एंटर कर सकते हैं लेकिन बाहर नहीं जा सकते?', answer: 'कीबोर्ड', clue: 'यह कंप्यूटर से जुड़ा होता है।' },
    { riddle: 'वह क्या है जो सुखाते समय खुद गीला हो जाता है?', answer: 'तौलिया', clue: 'नहाने के बाद इसका उपयोग होता है।' },
    { riddle: 'ऐसी कौन सी चीज़ है जिसके दो हाथ हैं पर वह ताली नहीं बजा सकती?', answer: 'घड़ी', clue: 'यह समय बताती है।' },
  ],
  mr: [
    { riddle: 'अशी कोणती गोष्ट आहे ज्याच्याकडे कळा आहेत पण कुलूप नाही, स्पेस आहे पण जागा नाही, आणि तुम्ही एंटर करू शकता पण बाहेर जाऊ शकत नाही?', answer: 'कीबोर्ड', clue: 'याचा वापर संगणकावर लिहिण्यासाठी होतो.' },
    { riddle: 'अशी कोणती वस्तू आहे जी दुसऱ्याला कोरडं करताना स्वतः ओली होते?', answer: 'टॉवेल', clue: 'अंघोळीनंतर आपण वापरतो.' },
    { riddle: 'अशी कोणती गोष्ट आहे जिला दोन हात असतात पण ती टाळी वाजवू शकत नाही?', answer: 'घड्याळ', clue: 'ती आपल्याला वेळ दाखवते.' },
  ],
};

serve(async (req: Request) => {
  const corsResponse = handleCors(req);
  if (corsResponse) return corsResponse;

  try {
    const { language = 'en' } = await req.json().catch(() => ({}));
    const code = normalizeLanguage(language);
    const bank = RIDDLES_BANK[code] || RIDDLES_BANK.en;
    const randomRiddle = bank[Math.floor(Math.random() * bank.length)];

    return new Response(JSON.stringify({ success: true, data: randomRiddle }), {
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
