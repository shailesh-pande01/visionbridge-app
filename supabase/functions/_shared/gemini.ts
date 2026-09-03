// supabase/functions/_shared/gemini.ts

export const MODEL_CANDIDATES = [
  Deno.env.get('GEMINI_MODEL'),
  'gemini-2.5-flash-lite',
  'gemini-2.5-flash',
  'gemini-3.5-flash-lite',
  'gemini-flash-latest',
].filter(Boolean) as string[];

export interface GenerationConfig {
  temperature?: number;
  topP?: number;
  maxOutputTokens?: number;
  responseMimeType?: string;
}

export function extractFirstJsonObject(text: string): Record<string, any> | null {
  const start = text.indexOf('{');
  if (start === -1) return null;

  let depth = 0;
  let inString = false;
  let escaped = false;

  for (let i = start; i < text.length; i += 1) {
    const char = text[i];

    if (inString) {
      if (escaped) {
        escaped = false;
      } else if (char === '\\') {
        escaped = true;
      } else if (char === '"') {
        inString = false;
      }
      continue;
    }

    if (char === '"') {
      inString = true;
      continue;
    }

    if (char === '{') depth += 1;
    if (char === '}') {
      depth -= 1;
      if (depth === 0) {
        const candidate = text.slice(start, i + 1);
        try {
          return JSON.parse(candidate);
        } catch {
          return null;
        }
      }
    }
  }

  return null;
}

export async function generateVisionJson(
  prompt: string,
  imageBase64: string,
  mimeType: string = 'image/jpeg',
  config: GenerationConfig = {}
): Promise<Record<string, any> | null> {
  const apiKey = Deno.env.get('GEMINI_API_KEY');
  if (!apiKey) {
    throw new Error('GEMINI_API_KEY is not configured in Supabase secrets.');
  }

  // Strip prefix if included
  const cleanBase64 = imageBase64.replace(/^data:[^;]+;base64,/, '').trim();

  for (const model of MODEL_CANDIDATES) {
    try {
      const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${apiKey}`;
      const payload = {
        contents: [
          {
            parts: [
              { text: prompt },
              {
                inline_data: {
                  mime_type: mimeType,
                  data: cleanBase64,
                },
              },
            ],
          },
        ],
        generationConfig: {
          temperature: config.temperature ?? 0.1,
          topP: config.topP ?? 0.9,
          maxOutputTokens: config.maxOutputTokens ?? 600,
          responseMimeType: config.responseMimeType ?? 'application/json',
        },
      };

      const response = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      if (!response.ok) {
        const errText = await response.text();
        console.warn(`[Gemini ${model}] HTTP ${response.status}: ${errText}`);
        continue;
      }

      const result = await response.json();
      const candidate = result.candidates?.[0];
      if (!candidate || candidate.finishReason === 'SAFETY') {
        console.warn(`[Gemini ${model}] No candidate or blocked by safety filter`);
        continue;
      }

      const text = candidate.content?.parts?.[0]?.text;
      if (!text) continue;

      try {
        return JSON.parse(text);
      } catch {
        const extracted = extractFirstJsonObject(text);
        if (extracted) return extracted;
      }
    } catch (err: any) {
      console.warn(`[Gemini ${model}] Exception: ${err.message}`);
    }
  }

  return null;
}

export async function generateText(
  prompt: string,
  config: GenerationConfig = {}
): Promise<string | null> {
  const apiKey = Deno.env.get('GEMINI_API_KEY');
  if (!apiKey) {
    throw new Error('GEMINI_API_KEY is not configured in Supabase secrets.');
  }

  for (const model of MODEL_CANDIDATES) {
    try {
      const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${apiKey}`;
      const payload = {
        contents: [{ parts: [{ text: prompt }] }],
        generationConfig: {
          temperature: config.temperature ?? 0.2,
          topP: config.topP ?? 0.9,
          maxOutputTokens: config.maxOutputTokens ?? 400,
          responseMimeType: config.responseMimeType,
        },
      };

      const response = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      if (!response.ok) {
        continue;
      }

      const result = await response.json();
      const candidate = result.candidates?.[0];
      if (!candidate || candidate.finishReason === 'SAFETY') continue;

      return candidate.content?.parts?.[0]?.text || null;
    } catch (err: any) {
      console.warn(`[Gemini ${model}] Exception: ${err.message}`);
    }
  }

  return null;
}

export async function generateJson(
  prompt: string,
  config: GenerationConfig = {}
): Promise<Record<string, any> | null> {
  const text = await generateText(prompt, { ...config, responseMimeType: 'application/json' });
  if (!text) return null;

  try {
    return JSON.parse(text);
  } catch {
    return extractFirstJsonObject(text);
  }
}
