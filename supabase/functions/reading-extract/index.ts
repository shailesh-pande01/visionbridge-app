// supabase/functions/reading-extract/index.ts
import { serve } from 'https://deno.land/std@0.168.0/http/server.ts';
import { corsHeaders, handleCors } from '../_shared/cors.ts';
import { generateVisionJson } from '../_shared/gemini.ts';
import { languageInstruction, speech } from '../_shared/i18n.ts';

const READING_PROMPT = `You are a text-reading assistant for a person with low vision. Your ONLY job is to extract the text visible in the image and evaluate your confidence.

Rules you MUST follow:
1. Return ONLY a valid JSON object.
{
  "answer": "The extracted text goes here...",
  "confidence": 0.95
}
2. Preserve natural reading order (top to bottom, left to right).
3. If a word is partially obscured but clearly inferable, include it. If not, skip it.
4. If no text is visible or completely unreadable, return:
{
  "answer": "NO_TEXT_FOUND",
  "confidence": 0.0
}`;

serve(async (req: Request) => {
  const corsResponse = handleCors(req);
  if (corsResponse) return corsResponse;

  try {
    const { imageBase64, mimeType = 'image/jpeg', language = 'en' } = await req.json();

    if (!imageBase64) {
      return new Response(
        JSON.stringify({ success: false, error: 'imageBase64 is required' }),
        { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const prompt = `${READING_PROMPT}${languageInstruction(language, { verbatimText: true })}
5. "NO_TEXT_FOUND" is a fixed marker, not prose — return it in Latin letters exactly as written.`;

    const parsed = await generateVisionJson(prompt, imageBase64, mimeType, {
      temperature: 0.1,
      topP: 0.9,
      maxOutputTokens: 1024,
    });

    if (!parsed) {
      return new Response(
        JSON.stringify({
          success: true,
          data: {
            extractedText: null,
            message: speech('couldNotRead', language),
          },
        }),
        { status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    let answer = String(parsed.answer || '').trim();
    if (answer === 'NO_TEXT_FOUND' || answer.length === 0) {
      return new Response(
        JSON.stringify({
          success: true,
          data: {
            extractedText: null,
            confidence: 0.0,
            message: speech('noReadableText', language),
          },
        }),
        { status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const confidence = Math.min(1.0, Math.max(0.0, Number(parsed.confidence ?? 0.85)));

    return new Response(
      JSON.stringify({
        success: true,
        data: { extractedText: answer, confidence },
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
