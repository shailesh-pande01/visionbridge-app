// supabase/functions/assistant-command/index.ts
import { serve } from 'https://deno.land/std@0.168.0/http/server.ts';
import { corsHeaders, handleCors } from '../_shared/cors.ts';
import { generateJson } from '../_shared/gemini.ts';
import { languageInstruction, normalizeLanguage, speech as getSpeech } from '../_shared/i18n.ts';

const ALLOWED_ACTIONS = [
  'OPEN_FEATURE',
  'CAPTURE_IMAGE',
  'ASK_CONTEXTUAL_QUESTION',
  'FIND_OBJECT',
  'GO_HOME',
  'START_VOLUNTEER_HELP',
  'EMERGENCY_SOS',
  'CONFIRM',
  'REPEAT_LAST',
  'STOP_SPEAKING',
  'CANCEL',
  'RADIO_NEXT',
  'RADIO_PAUSE',
  'RADIO_RESUME',
  'RADIO_STOP',
  'LIVE_RADIO_PLAY',
  'LIVE_RADIO_NEXT',
  'LIVE_RADIO_PREV',
  'LIVE_RADIO_SEARCH',
  'LIVE_RADIO_INFO',
  'STORY_CONTINUE',
  'STORY_PLAY',
  'STORY_PLAY_GENRE',
  'STORY_NEXT_CHAPTER',
  'STORY_PREV_CHAPTER',
  'STORY_RESTART',
  'STORY_INFO',
  'STORY_FILTER_GENRE',
  'STORY_PLAY_LANGUAGE',
  'STORY_FILTER_LANGUAGE',
  'START_GAME',
  'GET_PROGRESS',
  'STOP_ENTERTAINMENT',
  'UNKNOWN',
];

const FEATURE_TARGETS = [
  'surroundings',
  'hazard',
  'reading',
  'currency',
  'transport',
  'objectFinder',
  'location',
  'volunteer',
  'emergency',
  'home',
  'liveVision',
  'entertainment',
  'radio',
  'liveRadio',
  'stories',
  'games',
  'dailyChallenge',
  'progress',
];

function buildContextBlock(context: any = {}): string {
  const { activeFeature = 'home', contextLabel = '', contextSummary = '', readingText = '', recentTurns = [] } = context;
  const lines = [`Active screen: ${activeFeature}`];

  const fullText = (typeof readingText === 'string' && readingText) ? readingText : contextSummary;
  if (fullText) {
    lines.push(`Stored ${contextLabel || 'reading text'} from user's last capture:`);
    lines.push(`"""${fullText.slice(0, 10000)}"""`);
  } else {
    lines.push('Stored context: none.');
  }

  if (Array.isArray(recentTurns) && recentTurns.length > 0) {
    const turns = recentTurns
      .slice(-3)
      .map((t: any) => `User: ${t.user}\nVisionBridge: ${t.assistant}`)
      .join('\n');
    lines.push(`Recent conversation:\n${turns}`);
  }

  return lines.join('\n');
}

serve(async (req: Request) => {
  const corsResponse = handleCors(req);
  if (corsResponse) return corsResponse;

  try {
    const { command, context = {}, language = 'en' } = await req.json();

    if (!command) {
      return new Response(
        JSON.stringify({ success: false, error: 'command is required' }),
        { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const normalizedLang = normalizeLanguage(language);

    const intentPrompt = `You are VisionBridge, a voice-first AI accessibility assistant for low-vision users.
TASK: Classify one spoken command into exactly one application action.

${languageInstruction(normalizedLang)}

${buildContextBlock(context)}

Allowed actions (use one):
${ALLOWED_ACTIONS.join('\n')}

Allowed values for "target" (OPEN_FEATURE only):
${FEATURE_TARGETS.join('\n')}

Return JSON ONLY:
{
  "action": "ACTION_NAME",
  "target": "target_feature_or_null",
  "question": "question_if_contextual_qa",
  "objectName": "object_if_find_object",
  "speech": "concise spoken feedback to say aloud",
  "confidence": 0.9
}`;

    const parsed = await generateJson(intentPrompt, {
      temperature: 0.1,
      topP: 0.9,
      maxOutputTokens: 350,
    });

    let action = String(parsed?.action || '').trim().toUpperCase();
    if (!ALLOWED_ACTIONS.includes(action)) action = 'UNKNOWN';

    let target = typeof parsed?.target === 'string' ? parsed.target.trim() : null;
    if (target && !FEATURE_TARGETS.includes(target)) target = null;
    if (action === 'OPEN_FEATURE' && !target) action = 'UNKNOWN';

    let speechText = String(parsed?.speech || (action === 'UNKNOWN' ? getSpeech('notUnderstood', normalizedLang) : ''));
    let answerText = parsed?.answer || null;

    // Follow-up question: answer it directly from stored reading text
    const activeText = context.readingText || context.contextSummary;
    if (action === 'ASK_CONTEXTUAL_QUESTION' && typeof activeText === 'string' && activeText.trim().length > 0) {
      const qText = parsed?.question || command;
      const qaPrompt = `You are VisionBridge AI accessibility assistant answering a follow-up question about text previously captured by VisionBridge Smart Reading.

Use the provided reading text as the primary and authoritative source for your answer.

READING TEXT:
"""${activeText.slice(0, 10000)}"""

USER QUESTION:
"${qText}"

${languageInstruction(normalizedLang)}

Instructions:
- Answer the user's question directly in the very first sentence.
- Use information from the reading text whenever available.
- Do not merely acknowledge the question. NEVER say "I'll check", "Let me check", "I can help", "Checking", or similar phrases.
- Do not pretend to perform another scan or ask the user to scan again.
- Extract specific values, names, prices, dates, quantities, and phone numbers from the text when asked.
- If asked for comparisons (e.g. cheapest), filtering (e.g. under 200), or counting, calculate the answer from the text.
- If not present in the text, say clearly that it is not present in the captured text. Do not invent.
- Preserve currency symbols and amounts (e.g. ₹250).
- Keep the answer concise (1-2 sentences).

Return JSON ONLY:
{
  "answer": "Direct concise spoken answer to the question",
  "confidence": 0.95
}`;

      try {
        const qaParsed = await generateJson(qaPrompt, {
          temperature: 0.1,
          topP: 0.9,
          maxOutputTokens: 300,
        });
        const ans = String(qaParsed?.answer || '').trim();
        if (ans) {
          speechText = ans;
          answerText = ans;
        }
      } catch (_err) {
        // Fall back to original parsed speech
      }
    }

    const isNavigation = ['OPEN_FEATURE', 'GO_HOME', 'FIND_OBJECT', 'START_VOLUNTEER_HELP', 'EMERGENCY_SOS'].includes(action);

    const data = {
      action,
      target,
      question: parsed?.question || (action === 'ASK_CONTEXTUAL_QUESTION' ? command : null),
      objectName: parsed?.objectName || null,
      speech: speechText,
      answer: answerText,
      confidence: Number(parsed?.confidence ?? 0.85),
      type: isNavigation ? 'navigation' : (action === 'ASK_CONTEXTUAL_QUESTION' ? 'answer' : 'action'),
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
