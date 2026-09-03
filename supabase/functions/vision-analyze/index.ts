// supabase/functions/vision-analyze/index.ts
import { serve } from 'https://deno.land/std@0.168.0/http/server.ts';
import { corsHeaders, handleCors } from '../_shared/cors.ts';
import { generateVisionJson } from '../_shared/gemini.ts';
import { languageInstruction, speech } from '../_shared/i18n.ts';

const VISION_PROMPT = `You are a real-time visual assistant speaking directly to a person with low vision. Your response will be read aloud by a screen reader — write exactly as you would speak to them, not as a description of an image.

Analyze the image and return ONLY a valid JSON object.
{
  "scene": "4 to 6 word label for the location (e.g. 'Indoor supermarket aisle', 'Outdoor pedestrian crossing')",
  "confidence": 0.95,
  "description": "2 to 3 sentences spoken directly to the user. Start with what is immediately ahead. Use 'on your left', 'on your right', 'directly ahead', 'close to you', 'further away'. If the path ahead is clear, say so. If anything blocks the way, say it first. Read any visible text on signs, screens, or labels word for word.",
  "objects": [
    "Object — direction and estimated distance (e.g. 'Dining table — directly ahead, approximately 2 steps away')"
  ],
  "obstacles": [
    "Hazard or obstacle — exact position (e.g. 'Step down — at your feet, directly ahead')"
  ],
  "lighting": "One short phrase describing lighting useful for navigation (e.g. 'Well-lit from overhead', 'Dim, proceed carefully')",
  "timeOfDay": "Estimate using natural light cues only (e.g. 'Late afternoon'), or 'Indoor — cannot determine'"
}

Rules:
1. Never use the words 'image', 'photo', 'picture', 'I can see', or 'it appears' — speak as the user's eyes.
2. Only describe what you can clearly see. If uncertain, omit it rather than guess.
3. Distances must use 'approximately' and real-world references: steps, arm's length, metres.
4. Obstacles are highest priority — include steps, stairs, blocking furniture, wet floors, uneven ground. Use empty array [] if clear.
5. If text is visible anywhere (signs, labels, menus), include it verbatim in description.
6. Description must sound natural when read aloud — fluent sentences, no bullet points.
7. Never mention colours unless identifying an important marker (e.g. red stop sign).`;

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

    const prompt = `${VISION_PROMPT}${languageInstruction(language)}`;
    const parsed = await generateVisionJson(prompt, imageBase64, mimeType, {
      temperature: 0.1,
      topP: 0.9,
      maxOutputTokens: 600,
    });

    if (!parsed) {
      return new Response(
        JSON.stringify({
          success: true,
          data: {
            scene: 'Scene detected',
            confidence: 0.5,
            description: speech('couldNotDescribe', language),
            objects: [],
            obstacles: [],
            lighting: 'Unknown',
            timeOfDay: 'Unknown',
          },
        }),
        { status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const data = {
      scene: String(parsed.scene || 'Scene detected'),
      confidence: Math.min(1.0, Math.max(0.0, Number(parsed.confidence ?? 0.85))),
      description: String(parsed.description || 'Scene observed.'),
      objects: Array.isArray(parsed.objects) ? parsed.objects.map(String) : [],
      obstacles: Array.isArray(parsed.obstacles) ? parsed.obstacles.map(String) : [],
      lighting: String(parsed.lighting || 'See description above'),
      timeOfDay: String(parsed.timeOfDay || 'See description above'),
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
