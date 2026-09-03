// supabase/functions/object-finder/index.ts
import { serve } from 'https://deno.land/std@0.168.0/http/server.ts';
import { corsHeaders, handleCors } from '../_shared/cors.ts';
import { generateVisionJson } from '../_shared/gemini.ts';
import { languageInstruction, speech } from '../_shared/i18n.ts';

serve(async (req: Request) => {
  const corsResponse = handleCors(req);
  if (corsResponse) return corsResponse;

  try {
    const { imageBase64, objectName, mimeType = 'image/jpeg', language = 'en' } = await req.json();

    if (!imageBase64 || !objectName) {
      return new Response(
        JSON.stringify({ success: false, error: 'imageBase64 and objectName are required' }),
        { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const prompt = `You are VisionBridge, an AI assistant for low-vision users.

The user is looking for:
"${objectName}"

Analyze ONLY the current image.
Determine:
- Is the object visible?
- Direction relative to user (Left, Right, Center, Ahead)
- Estimated distance (e.g. About one meter, arm's length)
- Nearby reference object (e.g. On the wooden table)

Return ONLY a valid JSON object:
{
  "found": true,
  "object": "${objectName}",
  "direction": "Left, Right, Center, or empty",
  "distance": "e.g. About one meter, or empty",
  "reference": "e.g. On the wooden table, or empty",
  "speech": "Spoken feedback for user",
  "confidence": 0.95
}${languageInstruction(language, { verbatimText: true })}`;

    const parsed = await generateVisionJson(prompt, imageBase64, mimeType, {
      temperature: 0.1,
      topP: 0.9,
      maxOutputTokens: 400,
    });

    if (!parsed) {
      return new Response(
        JSON.stringify({
          success: true,
          data: {
            found: false,
            object: objectName,
            direction: '',
            distance: '',
            reference: '',
            speech: speech('finderNotVisible', language, { object: objectName }),
            confidence: 0.0,
          },
        }),
        { status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const found = Boolean(parsed.found);
    const data = {
      found,
      object: String(parsed.object || objectName),
      direction: String(parsed.direction || ''),
      distance: String(parsed.distance || ''),
      reference: String(parsed.reference || ''),
      speech: String(parsed.speech || (found ? `Your ${objectName} was found.` : `I couldn't find your ${objectName} in view.`)),
      confidence: Math.min(1.0, Math.max(0.0, Number(parsed.confidence ?? (found ? 0.9 : 0.5)))),
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
