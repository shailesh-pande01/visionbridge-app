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
  const { activeFeature = 'home', contextLabel = '', contextSummary = '', recentTurns = [] } = context;
  const lines = [`Active screen: ${activeFeature}`];

  if (contextSummary) {
    lines.push(`Stored ${contextLabel || 'context'} from user's last capture:`);
    lines.push(`"""${contextSummary.slice(0, 1500)}"""`);
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

    const speechText = String(parsed?.speech || (action === 'UNKNOWN' ? getSpeech('notUnderstood', normalizedLang) : ''));

    const isNavigation = ['OPEN_FEATURE', 'GO_HOME', 'FIND_OBJECT', 'START_VOLUNTEER_HELP', 'EMERGENCY_SOS'].includes(action);

    const data = {
      action,
      target,
      question: parsed?.question || (action === 'ASK_CONTEXTUAL_QUESTION' ? command : null),
      objectName: parsed?.objectName || null,
      speech: speechText,
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
