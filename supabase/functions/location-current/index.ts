// supabase/functions/location-current/index.ts
import { serve } from 'https://deno.land/std@0.168.0/http/server.ts';
import { corsHeaders, handleCors } from '../_shared/cors.ts';
import { generateText } from '../_shared/gemini.ts';
import { languageInstruction, normalizeLanguage, speech as getSpeech } from '../_shared/i18n.ts';

async function reverseGeocodeOSM(lat: number, lng: number) {
  try {
    const url = `https://nominatim.openstreetmap.org/reverse?lat=${lat}&lon=${lng}&format=json&addressdetails=1`;
    const resp = await fetch(url, {
      headers: { 'User-Agent': 'VisionBridgeApp/1.0 (contact@visionbridge.local)' },
    });
    if (!resp.ok) return null;
    const data = await resp.json();
    return {
      formattedAddress: data.display_name || '',
      addressDetails: data.address || {},
    };
  } catch {
    return null;
  }
}

async function nearbyPlacesOSM(lat: number, lng: number) {
  try {
    const query = `[out:json][timeout:10];
node(around:200,${lat},${lng})[name];
out 10;`;
    const url = `https://overpass-api.de/api/interpreter?data=${encodeURIComponent(query)}`;
    const resp = await fetch(url, {
      headers: { 'User-Agent': 'VisionBridgeApp/1.0 (contact@visionbridge.local)' },
    });
    if (!resp.ok) return [];
    const data = await resp.json();
    if (!data || !Array.isArray(data.elements)) return [];
    return data.elements.map((p: any) => ({
      name: p.tags?.name || 'Landmark',
      type: p.tags?.amenity || p.tags?.shop || p.tags?.tourism || 'place',
    }));
  } catch {
    return [];
  }
}

serve(async (req: Request) => {
  const corsResponse = handleCors(req);
  if (corsResponse) return corsResponse;

  try {
    const { latitude, longitude, language = 'en' } = await req.json();

    if (latitude == null || longitude == null) {
      return new Response(
        JSON.stringify({ success: false, error: 'latitude and longitude are required' }),
        { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const normalizedLang = normalizeLanguage(language);

    const [geocodeData, places] = await Promise.all([
      reverseGeocodeOSM(Number(latitude), Number(longitude)),
      nearbyPlacesOSM(Number(latitude), Number(longitude)),
    ]);

    let context = `Coordinates: ${latitude}, ${longitude}.\n`;
    if (geocodeData?.formattedAddress) {
      context += `Address: ${geocodeData.formattedAddress}\n`;
    }
    if (places && places.length > 0) {
      context += `Nearby places within 200m:\n`;
      places.slice(0, 4).forEach((p: any) => {
        context += `- ${p.name} (${p.type})\n`;
      });
    }

    const prompt = `You are VisionBridge location assistant. Describe the user's current location in 2 to 3 short sentences for low-vision audio.

${context}

Rules:
1. Start with "You are at..." or natural equivalent.
2. Mention primary street/locality and 1-2 notable nearby landmarks.
3. No bullet points or raw coordinates.
4. Keep place names exact.
${languageInstruction(normalizedLang)}

Spoken description:`;

    const summaryText = await generateText(prompt, {
      temperature: 0.2,
      topP: 0.9,
      maxOutputTokens: 350,
    });

    const summary = summaryText?.trim() || getSpeech('locationUnavailable', normalizedLang);

    const data = {
      summary,
      address: geocodeData?.formattedAddress || null,
      landmarks: places.slice(0, 5).map((p: any) => p.name),
      latitude: Number(latitude),
      longitude: Number(longitude),
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
