// supabase/functions/vision-hazard/index.ts
import { serve } from 'https://deno.land/std@0.168.0/http/server.ts';
import { corsHeaders, handleCors } from '../_shared/cors.ts';
import { generateVisionJson } from '../_shared/gemini.ts';
import { languageInstruction } from '../_shared/i18n.ts';

const HAZARD_PROMPT = `You are VisionBridge, an AI assistant for low-vision users.

Previous Scene:
{sceneMemory}

Analyze the CURRENT image.
Ignore objects that have not changed.
Only mention:
- New hazards
- Moving obstacles
- Changes in the environment
- Navigation warnings
- Important safety information
- Objects suddenly moving closer or into the frame

If nothing important changed, simply reply with exactly this English string: "No significant change."
That one sentinel value is never translated, whatever language is requested below — the application
compares against it to decide whether to stay silent. Every OTHER value you produce follows the
language rule below.

At the end of every response, generate a NEW scene summary in ONE SHORT SENTENCE.

Return JSON only:
{
  "speech": "...",
  "sceneSummary": "..."
}`;

const NO_CHANGE_ALIASES = [
  'no significant change',
  'कोई ख़ास बदलाव नहीं',
  'कोई खास बदलाव नहीं',
  'कोई बदलाव नहीं',
  'विशेष बदल नाही',
  'काही विशेष बदल नाही',
  'कोणताही बदल नाही',
];

function normalizeNoChange(speechText: string): string {
  const normalized = String(speechText || '')
    .toLowerCase()
    .replace(/[^\p{L}\p{N} ]/gu, ' ')
    .replace(/\s+/g, ' ')
    .trim();

  const isNoChange = NO_CHANGE_ALIASES.some((alias) => {
    const cleanAlias = alias.toLowerCase().replace(/[^\p{L}\p{N} ]/gu, ' ').replace(/\s+/g, ' ').trim();
    return normalized.includes(cleanAlias);
  });

  return isNoChange ? 'No significant change.' : speechText;
}

serve(async (req: Request) => {
  const corsResponse = handleCors(req);
  if (corsResponse) return corsResponse;

  try {
    const { imageBase64, mimeType = 'image/jpeg', sceneMemory = '', language = 'en' } = await req.json();

    if (!imageBase64) {
      return new Response(
        JSON.stringify({ success: false, error: 'imageBase64 is required' }),
        { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const prompt = HAZARD_PROMPT.replace('{sceneMemory}', sceneMemory || 'No previous context.') +
      languageInstruction(language);

    const parsed = await generateVisionJson(prompt, imageBase64, mimeType, {
      temperature: 0.1,
      topP: 0.9,
      maxOutputTokens: 300,
    });

    const data = {
      speech: normalizeNoChange(String(parsed?.speech || 'No significant change.')),
      sceneSummary: String(parsed?.sceneSummary || sceneMemory || 'Scene unchanged.'),
      timestamp: Date.now(),
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
