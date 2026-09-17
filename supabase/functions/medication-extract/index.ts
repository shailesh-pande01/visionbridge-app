// supabase/functions/medication-extract/index.ts
import { serve } from 'https://deno.land/std@0.168.0/http/server.ts';
import { corsHeaders, handleCors } from '../_shared/cors.ts';
import { generateVisionJson } from '../_shared/gemini.ts';

const MEDICATION_SAFETY_PROMPT = `You are a specialized Medication Information & Safety Accessibility Companion for persons with low vision or blindness.

YOUR STRICT DIRECTIVES:
1. Extract ONLY information that is ACTUALLY and CLEARLY readable on the medicine package, strip, box, bottle, or printed label.
2. Return ONLY a valid JSON object matching the exact schema below.
3. NEVER infer, deduce, or hallucinate missing information.
4. NEVER guess medicine names. If the name is partly torn, blurry, or ambiguous between multiple medicines, set "status": "UNCERTAIN", "medicine_name": null, and document the uncertainty.
5. NEVER invent active ingredients, strengths, or dosages.
6. NEVER diagnose diseases, prescribe medications, or recommend changing dosage.
7. NEVER recommend which medicine a person should take or claim that a medicine is safe or appropriate for any disease.
8. If a field cannot be confidently read from the image, leave it as null (or empty array) and list its field name in "fields_needing_verification". For example, if expiry date is blurry or not visible, "expiry_date": null, and add "expiry_date" to "fields_needing_verification".
9. For "printed_directions", copy ONLY the verbatim printed usage instructions visible on the packaging (e.g. "Take 1 tablet after meals"). Do NOT rephrase into advice (do NOT say "You should take...").
10. Assign a conservative confidence score between 0.00 and 1.00 reflecting the visual legibility and certainty of the visible text. If unreadable, ambiguous, or no medicine is present, set confidence below 0.50.

JSON SCHEMA:
{
  "status": "CLEAR" | "UNCERTAIN" | "NO_MEDICATION_TEXT",
  "medicine_name": string | null,
  "active_ingredients": string[],
  "strength": string | null,
  "form": string | null,
  "printed_directions": string | null,
  "expiry_date": string | null,
  "storage_information": string | null,
  "warnings_visible_on_package": string[],
  "manufacturer": string | null,
  "batch_number": string | null,
  "raw_visible_text": string,
  "confidence": number,
  "uncertainty_reason": string | null,
  "fields_needing_verification": string[]
}

CRITICAL: Pharmaceutical product names and active ingredients must retain their standard international/Latin names (e.g. "Paracetamol", "Amoxicillin", "Metformin 500mg") even if the user interface language is Hindi or Marathi, so the name is never mistranslated.`;

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

    const langInstruction = language === 'hi' 
      ? '\nRespond with JSON. Provide instructions or notes with clear language context, keeping medicine names in recognizable pharmaceutical format.'
      : language === 'mr'
      ? '\nRespond with JSON. Provide instructions or notes with clear language context, keeping medicine names in recognizable pharmaceutical format.'
      : '\nRespond with JSON.';

    const prompt = `${MEDICATION_SAFETY_PROMPT}${langInstruction}`;

    const parsed = await generateVisionJson(prompt, imageBase64, mimeType, {
      temperature: 0.1,
      topP: 0.85,
      maxOutputTokens: 1200,
    });

    if (!parsed) {
      return new Response(
        JSON.stringify({
          success: true,
          data: {
            status: 'UNCERTAIN',
            medicine_name: null,
            active_ingredients: [],
            strength: null,
            form: null,
            printed_directions: null,
            expiry_date: null,
            storage_information: null,
            warnings_visible_on_package: [],
            manufacturer: null,
            batch_number: null,
            raw_visible_text: '',
            confidence: 0.0,
            uncertainty_reason: 'Could not process or read the image clearly.',
            fields_needing_verification: ['medicine_name', 'strength', 'expiry_date']
          },
        }),
        { status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    // Sanitize and validate fields
    const status = String(parsed.status || 'UNCERTAIN').toUpperCase();
    const rawName = parsed.medicine_name ? String(parsed.medicine_name).trim() : null;
    const medicineName = (rawName && rawName.length <= 100) ? rawName : null;
    const strength = parsed.strength ? String(parsed.strength).trim().slice(0, 50) : null;
    const form = parsed.form ? String(parsed.form).trim().slice(0, 50) : null;
    const printedDirections = parsed.printed_directions ? String(parsed.printed_directions).trim().slice(0, 500) : null;
    const expiryDate = parsed.expiry_date ? String(parsed.expiry_date).trim().slice(0, 50) : null;
    const storageInfo = parsed.storage_information ? String(parsed.storage_information).trim().slice(0, 250) : null;
    const manufacturer = parsed.manufacturer ? String(parsed.manufacturer).trim().slice(0, 100) : null;
    const batchNumber = parsed.batch_number ? String(parsed.batch_number).trim().slice(0, 50) : null;
    const rawVisibleText = parsed.raw_visible_text ? String(parsed.raw_visible_text).trim().slice(0, 1000) : '';

    const activeIngredients = Array.isArray(parsed.active_ingredients)
      ? parsed.active_ingredients.map((i: any) => String(i).trim().slice(0, 100)).filter(Boolean)
      : [];

    const warnings = Array.isArray(parsed.warnings_visible_on_package)
      ? parsed.warnings_visible_on_package.map((w: any) => String(w).trim().slice(0, 200)).filter(Boolean)
      : [];

    const fieldsNeedingVerification = Array.isArray(parsed.fields_needing_verification)
      ? parsed.fields_needing_verification.map((f: any) => String(f).trim()).filter(Boolean)
      : [];

    const rawConfidence = Number(parsed.confidence);
    const confidence = isNaN(rawConfidence) ? 0.0 : Math.min(1.0, Math.max(0.0, rawConfidence));

    const uncertaintyReason = parsed.uncertainty_reason ? String(parsed.uncertainty_reason).trim() : null;

    return new Response(
      JSON.stringify({
        success: true,
        data: {
          status: status === 'CLEAR' && medicineName ? 'CLEAR' : status === 'NO_MEDICATION_TEXT' ? 'NO_MEDICATION_TEXT' : 'UNCERTAIN',
          medicine_name: medicineName,
          active_ingredients: activeIngredients,
          strength: strength,
          form: form,
          printed_directions: printedDirections,
          expiry_date: expiryDate,
          storage_information: storageInfo,
          warnings_visible_on_package: warnings,
          manufacturer: manufacturer,
          batch_number: batchNumber,
          raw_visible_text: rawVisibleText,
          confidence: confidence,
          uncertainty_reason: uncertaintyReason,
          fields_needing_verification: fieldsNeedingVerification
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
