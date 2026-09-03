// supabase/functions/transport-analyze/index.ts
import { serve } from 'https://deno.land/std@0.168.0/http/server.ts';
import { corsHeaders, handleCors } from '../_shared/cors.ts';
import { generateVisionJson } from '../_shared/gemini.ts';
import { languageInstruction, speech } from '../_shared/i18n.ts';

const TRANSPORT_PROMPT = `You are VisionBridge, an accessibility assistant for low-vision users.

Analyze this image.
Extract ONLY transportation and navigation-related information.

Prioritize:
- Bus number / route
- Bus destination
- Train number
- Platform number
- Metro line / station
- Building name
- Directional signs / navigation boards

Ignore advertisements and background items. If multiple signs exist, prioritize the closest/clearest one.

Return ONLY a valid JSON object:
{
  "type": "Bus, Train, Metro, Building, Sign, or None",
  "title": "e.g. Bus 102, Platform 3",
  "destination": "e.g. Shivajinagar",
  "speech": "What the screen reader will say aloud",
  "confidence": 0.95
}

If no transportation or navigation information is found, return:
{"type":"None","title":"","destination":"","speech":"No transport information found.","confidence":0.0}`;

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

    const prompt = `${TRANSPORT_PROMPT}${languageInstruction(language, { verbatimText: true })}`;
    const parsed = await generateVisionJson(prompt, imageBase64, mimeType, {
      temperature: 0.1,
      topP: 0.9,
      maxOutputTokens: 450,
    });

    if (!parsed) {
      return new Response(
        JSON.stringify({
          success: true,
          data: {
            type: 'None',
            title: '',
            destination: '',
            speech: speech('noTransportInfo', language),
            confidence: 0.0,
          },
        }),
        { status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const data = {
      type: String(parsed.type || 'None'),
      title: String(parsed.title || ''),
      destination: String(parsed.destination || ''),
      speech: String(parsed.speech || (parsed.title ? `${parsed.title} ${parsed.destination ? 'to ' + parsed.destination : ''}`.trim() : speech('noTransportInfo', language))),
      confidence: Math.min(1.0, Math.max(0.0, Number(parsed.confidence ?? 0.85))),
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
