// supabase/functions/games-twenty-questions/index.ts
import { serve } from 'https://deno.land/std@0.168.0/http/server.ts';
import { corsHeaders, handleCors } from '../_shared/cors.ts';
import { generateJson } from '../_shared/gemini.ts';
import { languageInstruction, normalizeLanguage } from '../_shared/i18n.ts';

const TWENTY_QUESTIONS_OBJECTS: Record<string, string[]> = {
  en: ['Guitar', 'Refrigerator', 'Bicycle', 'Umbrella', 'Coffee Mug', 'Clock', 'Smartphone', 'Backpack'],
  hi: ['गिटार', 'फ्रिज', 'साइकिल', 'छाता', 'कॉफी मग', 'घड़ी', 'स्मार्टफोन'],
  mr: ['गिटार', 'फ्रिज', 'सायकल', 'छत्री', 'घड्याळ', 'स्मार्टफोन'],
};

serve(async (req: Request) => {
  const corsResponse = handleCors(req);
  if (corsResponse) return corsResponse;

  try {
    const body = await req.json().catch(() => ({}));
    const { secretObject, question, language = 'en' } = body;
    const code = normalizeLanguage(language);

    // If no question is provided, start a new session by picking a secret object
    if (!question) {
      const bank = TWENTY_QUESTIONS_OBJECTS[code] || TWENTY_QUESTIONS_OBJECTS.en;
      const pickedObject = bank[Math.floor(Math.random() * bank.length)];

      return new Response(
        JSON.stringify({
          success: true,
          data: {
            secretObject: pickedObject,
            maxQuestions: 20,
          },
        }),
        { status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    if (!secretObject) {
      return new Response(
        JSON.stringify({ success: false, error: 'secretObject is required when asking a question' }),
        { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    // Evaluate question or guess
    const normGuess = question.toLowerCase().trim();
    const normTarget = secretObject.toLowerCase().trim();

    if (normGuess.includes(normTarget) || normTarget.includes(normGuess)) {
      return new Response(
        JSON.stringify({
          success: true,
          data: {
            isGuess: true,
            isCorrect: true,
            answer: code === 'hi' ? 'बिल्कुल सही! आपने सही अनुमान लगाया।' : code === 'mr' ? 'अगदी बरोबर! तुम्ही योग्य ओळखलं.' : 'Correct! You guessed the secret object!',
          },
        }),
        { status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const prompt = `You are hosting a 20 Questions game.
The secret object is: "${secretObject}".
The user asks: "${question}".
${languageInstruction(code)}

Evaluate whether the answer to the user's question about the secret object is Yes, No, Sometimes, or if they made a direct incorrect guess.
Keep the answer under 10 words.

Return JSON ONLY:
{
  "isGuess": false,
  "isCorrect": false,
  "answer": "Yes / No / Sometimes / etc."
}`;

    const parsed = await generateJson(prompt, { temperature: 0.1, maxOutputTokens: 100 });

    const data = parsed || {
      isGuess: false,
      isCorrect: false,
      answer: 'I cannot tell for sure. Try another question!',
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
