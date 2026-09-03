// supabase/functions/_shared/i18n.ts
export const SUPPORTED_LANGUAGES = ['en', 'hi', 'mr'];
export const DEFAULT_LANGUAGE = 'en';

export const LANGUAGE_NAMES: Record<string, string> = {
  en: 'English',
  hi: 'Hindi (हिन्दी), written in the Devanagari script',
  mr: 'Marathi (मराठी), written in the Devanagari script',
};

export function normalizeLanguage(value?: string | null): string {
  if (!value || typeof value !== 'string') return DEFAULT_LANGUAGE;
  const code = value.trim().toLowerCase().split(/[-_]/)[0];
  return SUPPORTED_LANGUAGES.includes(code) ? code : DEFAULT_LANGUAGE;
}

export function languageInstruction(lang?: string | null, options: { verbatimText?: boolean } = {}): string {
  const code = normalizeLanguage(lang);
  if (code === DEFAULT_LANGUAGE) return '';

  const name = LANGUAGE_NAMES[code];

  const base = `
LANGUAGE:
Write every value that will be spoken or shown to the user in ${name}.
Use everyday, conversational wording — the kind a person would actually
say out loud — not formal or literary vocabulary. Keep sentences short:
this text is read aloud to someone who cannot see the screen.
Keep proper nouns, place names, route numbers, platform numbers, prices
and brand names exactly as they appear — do not translate or transliterate
them. "VisionBridge" and the wake word "Vision" always stay in Latin script.
The user may speak to you in English, Hindi or Marathi; understand any of
them, and always answer in ${name}.`;

  if (options.verbatimText) {
    return `${base}
IMPORTANT: text you read out of the image is evidence, not prose. Reproduce
it exactly as written, in its original language and script. Only the
surrounding explanation is written in ${name}.`;
  }

  return base;
}

export const SPEECH: Record<string, Record<string, string>> = {
  en: {
    couldNotDescribe: 'This image could not be fully described. Please try pointing the camera at your surroundings again.',
    couldNotRead: 'Could not read text from this image. Please make sure the camera is focused and try again.',
    noReadableText: 'No readable text was found in this image.',
    noTransportInfo: 'No transportation or navigation information was found in view.',
    locationUnavailable: 'Your location is being determined. Please try again in a moment.',
    notUnderstood: "I didn't understand that. Please try again.",
    finderNotVisible: 'I could not find your {object} in view.',
  },
  hi: {
    couldNotDescribe: 'इस तस्वीर का पूरा विवरण नहीं मिल सका। कृपया कैमरा फिर से सामने रखकर प्रयास करें।',
    couldNotRead: 'लिखावट पढ़ी नहीं जा सकी। कृपया कैमरा स्थिर रखें और पुनः प्रयास करें।',
    noReadableText: 'तस्वीर में कोई पठनीय लिखावट नहीं मिली।',
    noTransportInfo: 'सामने कोई बस, ट्रेन या दिशा-निर्देश बोर्ड नहीं दिखा।',
    locationUnavailable: 'आपका स्थान खोजा जा रहा है। कृपया एक क्षण बाद पुनः प्रयास करें।',
    notUnderstood: 'मुझे समझ नहीं आया। कृपया दोबारा कहें।',
    finderNotVisible: 'सामने {object} दिखाई नहीं दे रहा है।',
  },
  mr: {
    couldNotDescribe: 'या दृश्याचे पूर्ण वर्णन करता आले नाही. कृपया कॅमेरा पुन्हा समोर धरून प्रयत्न करा.',
    couldNotRead: 'मजकूर वाचता आला नाही. कृपया कॅमेरा स्थिर ठेवा आणि पुन्हा प्रयत्न करा.',
    noReadableText: 'या प्रतिमेत वाचण्याजोगा कोणताही मजकूर आढळला नाही.',
    noTransportInfo: 'समोर कोणतीही बस, गाडी किंवा दिशा फलक आढळला नाही.',
    locationUnavailable: 'तुमचे स्थान शोधले जात आहे. कृपया थोड्या वेळाने पुन्हा प्रयत्न करा.',
    notUnderstood: 'मला समजले नाही. कृपया पुन्हा सांगा.',
    finderNotVisible: 'समोर {object} दिसत नाही.',
  },
};

export function speech(key: string, lang?: string | null, params: Record<string, string> = {}): string {
  const code = normalizeLanguage(lang);
  let text = SPEECH[code]?.[key] || SPEECH.en[key] || '';
  for (const [k, v] of Object.entries(params)) {
    text = text.replace(new RegExp(`\\{${k}\\}`, 'g'), v);
  }
  return text;
}
