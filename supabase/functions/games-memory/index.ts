// supabase/functions/games-memory/index.ts
import { serve } from 'https://deno.land/std@0.168.0/http/server.ts';
import { corsHeaders, handleCors } from '../_shared/cors.ts';
import { normalizeLanguage } from '../_shared/i18n.ts';

const MEMORY_WORDS_BANK: Record<string, string[][]> = {
  en: [
    ['Apple', 'River', 'Clock', 'Mountain'],
    ['Sun', 'Guitar', 'Ocean', 'Train', 'Book'],
    ['Diamond', 'Falcon', 'Forest', 'Bridge', 'Star', 'Candle'],
  ],
  hi: [
    ['सेब', 'नदी', 'घड़ी', 'पहाड़'],
    ['सूरज', 'गिटार', 'समुद्र', 'ट्रेन', 'किताब'],
  ],
  mr: [
    ['सफरचंद', 'नदी', 'घड्याळ', 'पर्वत'],
    ['सूर्य', 'गिटार', 'समुद्र', 'गाडी', 'पुस्तक'],
  ],
};

serve(async (req: Request) => {
  const corsResponse = handleCors(req);
  if (corsResponse) return corsResponse;

  try {
    const { level = 1, language = 'en' } = await req.json().catch(() => ({}));
    const code = normalizeLanguage(language);
    const bank = MEMORY_WORDS_BANK[code] || MEMORY_WORDS_BANK.en;
    const safeLevel = Math.min(3, Math.max(1, Number(level) || 1));
    const sequence = bank[(safeLevel - 1) % bank.length];

    const data = {
      level: safeLevel,
      words: sequence,
      prompt: sequence.join(', '),
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
