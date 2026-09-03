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
    const summary = typeof context.contextSummary === 'string' ? context.contextSummary : '';

    if (!summary) {
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

    const prompt = `You are VisionBridge AI assistant.
Answer the user's follow-up question directly and concisely from the stored context.

Stored context from user's active screen:
"""${summary.slice(0, 1500)}"""

User question: "${question}"
${languageInstruction(normalizedLang)}

Return JSON ONLY:
{
  "answer": "Concise spoken answer to the question",
  "confidence": 0.95
}`;

    const parsed = await generateJson(prompt, {
      temperature: 0.1,
      topP: 0.9,
      maxOutputTokens: 300,
    });

    const answer = String(parsed?.answer || 'I could not find the answer in the current view.');
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
