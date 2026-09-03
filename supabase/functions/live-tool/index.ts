// supabase/functions/live-tool/index.ts
import { serve } from 'https://deno.land/std@0.168.0/http/server.ts';
import { corsHeaders, handleCors } from '../_shared/cors.ts';

serve(async (req: Request) => {
  const corsResponse = handleCors(req);
  if (corsResponse) return corsResponse;

  try {
    const { toolName, args = {} } = await req.json().catch(() => ({}));

    switch (toolName) {
      case 'getEmergencyStatus':
        return new Response(
          JSON.stringify({ success: true, data: { emergencyReady: true } }),
          { status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
        );

      case 'getCurrentFeature':
        return new Response(
          JSON.stringify({ success: true, data: { activeFeature: 'liveVision' } }),
          { status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
        );

      default:
        return new Response(
          JSON.stringify({ success: false, error: `Unrecognized tool: ${toolName}` }),
          { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
        );
    }
  } catch (err: any) {
    return new Response(
      JSON.stringify({ success: false, error: err.message, code: 'SERVER_ERROR' }),
      { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }
});
