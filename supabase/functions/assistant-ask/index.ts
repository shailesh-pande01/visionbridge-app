// supabase/functions/assistant-ask/index.ts
import { serve } from 'https://deno.land/std@0.168.0/http/server.ts';
import { corsHeaders, handleCors } from '../_shared/cors.ts';
import { generateJson } from '../_shared/gemini.ts';
import { languageInstruction, normalizeLanguage } from '../_shared/i18n.ts';

serve(async (req: Request) => {
  const corsResponse = handleCors(req);
  if (corsResponse) return corsResponse;

  try {
    const { question, context = {}, language = 'en' } = await req.json();

    if (!question) {
      return new Response(
        JSON.stringify({ success: false, error: 'question is required' }),
        { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const normalizedLang = normalizeLanguage(language);
    const readingText = typeof req.readingText === 'string' && req.readingText
      ? req.readingText
      : (typeof context.readingText === 'string' && context.readingText
        ? context.readingText
        : (typeof context.contextSummary === 'string' ? context.contextSummary : ''));

    if (!readingText) {
      return new Response(
        JSON.stringify({
          success: true,
          data: {
            action: 'ASK_CONTEXTUAL_QUESTION',
            question,
            speech: 'Please capture an image first so I can answer your question about it.',
            answer: 'Please capture an image first so I can answer your question about it.',
            confidence: 0.5,
            type: 'clarification',
          },
        }),
        { status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const prompt = `You are VisionBridge AI accessibility assistant answering a follow-up question about text previously captured by VisionBridge Smart Reading.

Use the provided reading text as the primary and authoritative source for your answer.

READING TEXT:
"""${readingText.slice(0, 10000)}"""

USER QUESTION:
"${question}"

${languageInstruction(normalizedLang)}

Instructions:
- Answer the user's question directly in the very first sentence.
- Use information from the reading text whenever available.
- Do not merely acknowledge the question. NEVER say "I'll check", "Let me check", "I can help", "Checking", or similar phrases.
- Do not pretend to perform another scan or ask the user to scan again.
- Extract specific values, names, prices, dates, quantities, addresses, and phone numbers from the text when asked.
- Perform simple reasoning/calculations over the reading text when needed:
  * If asked for the cheapest or most expensive, compare the prices found in the text and name the item and price (e.g. "Veg Pasta is the cheapest at ₹220.").
  * If asked for items under a certain price (e.g. "under 200 rupees"), list all matching items with their prices.
  * If asked for options or all items in a category (e.g. "what are the pasta options"), list them clearly with prices.
  * If asked if an item exists (e.g. "does this menu have pasta"), answer "Yes" with the items or "I don't see [item] in the captured text."
  * If asked how many items, count them.
- If the requested item or information is not present in the reading text, clearly state that it is not present in the captured text (e.g. "I don't see sushi in the menu text I captured."). Never invent or fabricate prices or facts.
- Preserve currency symbols and numeric amounts exactly as written (e.g. ₹250, $12.50).
- Keep the answer concise (1-2 sentences) and natural for text-to-speech output.

Return JSON ONLY:
{
  "answer": "Direct concise spoken answer to the question",
  "confidence": 0.95
}`;

    const parsed = await generateJson(prompt, {
      temperature: 0.1,
      topP: 0.9,
      maxOutputTokens: 300,
    });

    const answer = String(parsed?.answer || 'I could not find the answer in the captured text.');
    const confidence = Number(parsed?.confidence ?? 0.85);

    return new Response(
      JSON.stringify({
        success: true,
        data: {
          action: 'ASK_CONTEXTUAL_QUESTION',
          question,
          answer,
          speech: answer,
          confidence,
          type: 'answer',
        },
      }),
      { status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  } catch (err: any) {
    return new Response(
      JSON.stringify({ success: false, error: err.message, code: 'SERVER_ERROR' }),
      { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }
});
