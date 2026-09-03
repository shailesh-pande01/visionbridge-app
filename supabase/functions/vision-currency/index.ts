// supabase/functions/vision-currency/index.ts
import { serve } from 'https://deno.land/std@0.168.0/http/server.ts';
import { corsHeaders, handleCors } from '../_shared/cors.ts';
import { generateVisionJson } from '../_shared/gemini.ts';
import { languageInstruction } from '../_shared/i18n.ts';

const CURRENCY_PROMPT = `You are VisionBridge Currency Reader for low-vision users.

Analyze the current image ONLY for visible currency notes and coins.
Identify each clearly visible denomination, count the quantity of each note/coin, and calculate the total amount only when the denomination can be determined with reasonable confidence.
Do not guess.
Ignore unrelated objects and non-currency text.

For Indian currency, prioritize INR / ₹ denominations such as:
₹10, ₹20, ₹50, ₹100, ₹200, ₹500, ₹2000.
Do not assume ₹2000 is present unless it is actually clearly visible.
If other world currencies are present (e.g. USD, EUR, GBP), identify them accurately.

If the image is unclear, blurry, or no currency is visible, or a denomination cannot be identified reliably, explicitly say so and assign low confidence.

Return ONLY a valid JSON object:
{
  "currency": "INR",
  "symbol": "₹",
  "items": [
    {
      "denomination": 500,
      "quantity": 1,
      "confidence": 0.96
    }
  ],
  "total": 500,
  "confidence": 0.95,
  "speech": "You have 500 rupees."
}

If no currency is visible or currency cannot be determined reliably:
{
  "currency": "INR",
  "symbol": "₹",
  "items": [],
  "total": null,
  "confidence": 0.30,
  "speech": "I couldn't confidently identify the currency amount."
}`;

function getCurrencySymbol(currencyCode: string): string {
  switch ((currencyCode || '').toUpperCase()) {
    case 'INR': return '₹';
    case 'USD': return '$';
    case 'EUR': return '€';
    case 'GBP': return '£';
    case 'JPY': return '¥';
    case 'CAD': return 'CA$';
    case 'AUD': return 'A$';
    default:    return '₹';
  }
}

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

    const prompt = `${CURRENCY_PROMPT}${languageInstruction(language)}`;
    const parsed = await generateVisionJson(prompt, imageBase64, mimeType, {
      temperature: 0.1,
      topP: 0.9,
      maxOutputTokens: 500,
    });

    if (!parsed) {
      return new Response(
        JSON.stringify({
          success: true,
          data: {
            currency: 'INR',
            symbol: '₹',
            items: [],
            total: null,
            confidence: 0.30,
            speech: "I couldn't confidently identify any currency in this image.",
          },
        }),
        { status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const currency = String(parsed.currency || 'INR').trim().toUpperCase();
    const symbol = String(parsed.symbol || getCurrencySymbol(currency)).trim();
    const rawItems = Array.isArray(parsed.items) ? parsed.items : [];

    const items = rawItems
      .filter((item: any) => item && Number.isFinite(Number(item.denomination)) && Number(item.denomination) > 0)
      .map((item: any) => ({
        denomination: Number(item.denomination),
        quantity: Math.max(1, Math.round(Number(item.quantity) || 1)),
        confidence: Math.min(1.0, Math.max(0.0, Number(item.confidence ?? 0.85))),
      }));

    let total: number | null = null;
    if (parsed.total !== null && parsed.total !== undefined && Number.isFinite(Number(parsed.total))) {
      total = Number(parsed.total);
    } else if (items.length > 0) {
      total = items.reduce((sum: number, item: any) => sum + item.denomination * item.quantity, 0);
    }

    const confidence = Math.min(1.0, Math.max(0.0, Number(parsed.confidence ?? (items.length > 0 ? 0.85 : 0.30))));

    let speechText = String(parsed.speech || '').trim();
    if (!speechText) {
      if (total !== null && total > 0) {
        speechText = `You have ${total} ${currency === 'INR' ? 'rupees' : currency}.`;
      } else {
        speechText = "I couldn't confidently identify any currency in this image.";
      }
    }

    const data = {
      currency,
      symbol,
      items,
      total,
      confidence,
      speech: speechText,
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
