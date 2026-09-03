// supabase/functions/games-evaluate/index.ts
import { serve } from 'https://deno.land/std@0.168.0/http/server.ts';
import { corsHeaders, handleCors } from '../_shared/cors.ts';
import { generateJson } from '../_shared/gemini.ts';

function normalizeAnswer(text: string): string {
  return text
    .toLowerCase()
    .replace(/[.,/#!$%^&*;:{}=\-_`~()?"'।॥]/g, '')
    .trim();
}

serve(async (req: Request) => {
  const corsResponse = handleCors(req);
  if (corsResponse) return corsResponse;

  try {
    const { expectedAnswer = '', userAnswer = '', gameType = 'trivia', language = 'en' } = await req.json();

    if (!expectedAnswer || !userAnswer) {
      return new Response(
        JSON.stringify({ success: false, error: 'expectedAnswer and userAnswer are required' }),
        { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const normExp = normalizeAnswer(expectedAnswer);
    const normUser = normalizeAnswer(userAnswer);

    let isCorrect = false;
    if (normUser === normExp || normUser.includes(normExp) || normExp.includes(normUser)) {
      isCorrect = true;
    } else {
      try {
        const prompt = `Evaluate if the user's spoken answer matches the expected answer in meaning.
Expected: "${expectedAnswer}"
User Answer: "${userAnswer}"

Return JSON ONLY:
{
  "isCorrect": true/false
}`;
        const evalResult = await generateJson(prompt, { temperature: 0.0, maxOutputTokens: 50 });
        if (evalResult && typeof evalResult.isCorrect === 'boolean') {
          isCorrect = evalResult.isCorrect;
        }
      } catch {
        // Fallback to strict match
      }
    }

    const xpEarned = isCorrect ? 10 : 0;

    const data = {
      isCorrect,
      expectedAnswer,
      userAnswer,
      xpEarned,
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
