// supabase/functions/games-trivia/index.ts
import { serve } from 'https://deno.land/std@0.168.0/http/server.ts';
import { corsHeaders, handleCors } from '../_shared/cors.ts';
import { normalizeLanguage } from '../_shared/i18n.ts';

const TRIVIA_BANK: Record<string, any[]> = {
  en: [
    { category: 'Science', question: 'Which planet is known as the Red Planet?', answer: 'Mars', options: ['Mars', 'Jupiter', 'Venus', 'Saturn'] },
    { category: 'Nature', question: 'What is the largest mammal on Earth?', answer: 'Blue Whale', options: ['Blue Whale', 'Elephant', 'Giraffe', 'Hippopotamus'] },
    { category: 'Geography', question: 'What is the capital city of France?', answer: 'Paris', options: ['Paris', 'London', 'Rome', 'Berlin'] },
    { category: 'Technology', question: 'What does AI stand for?', answer: 'Artificial Intelligence', options: ['Artificial Intelligence', 'Automated Interface', 'Applied Information', 'Audio Input'] },
    { category: 'General', question: 'How many days are there in a leap year?', answer: '366', options: ['366', '365', '364', '360'] },
  ],
  hi: [
    { category: 'विज्ञान', question: 'किस ग्रह को लाल ग्रह के नाम से जाना जाता है?', answer: 'मंगल', options: ['मंगल', 'बृहस्पति', 'शुक्र', 'शनि'] },
    { category: 'प्रकृति', question: 'पृथ्वी पर सबसे बड़ा स्तनपायी जीव कौन सा है?', answer: 'ब्लू व्हेल', options: ['ब्लू व्हेल', 'हाथी', 'जिराफ़', 'शेर'] },
    { category: 'सामान्य', question: 'एक लीप वर्ष में कितने दिन होते हैं?', answer: '366', options: ['366', '365', '364', '360'] },
  ],
  mr: [
    { category: 'विज्ञान', question: 'कोणत्या ग्रहाला लाल ग्रह म्हणतात?', answer: 'मंगळ', options: ['मंगळ', 'गुरु', 'शुक्र', 'शनी'] },
    { category: 'निसर्ग', question: 'पृथ्वीवरील सर्वात मोठा सस्तन प्राणी कोणता आहे?', answer: 'ब्लू व्हेल', options: ['ब्लू व्हेल', 'हत्ती', 'जिराफ', 'सिंह'] },
    { category: 'सामान्य', question: 'लीप वर्षात किती दिवस असतात?', answer: '366', options: ['366', '365', '364', '360'] },
  ],
};

serve(async (req: Request) => {
  const corsResponse = handleCors(req);
  if (corsResponse) return corsResponse;

  try {
    const { language = 'en' } = await req.json().catch(() => ({}));
    const code = normalizeLanguage(language);
    const bank = TRIVIA_BANK[code] || TRIVIA_BANK.en;
    const randomQuestion = bank[Math.floor(Math.random() * bank.length)];

    return new Response(JSON.stringify({ success: true, data: randomQuestion }), {
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
