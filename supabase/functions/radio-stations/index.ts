// supabase/functions/radio-stations/index.ts
import { serve } from 'https://deno.land/std@0.168.0/http/server.ts';
import { corsHeaders, handleCors } from '../_shared/cors.ts';

const RADIO_BROWSER_MIRRORS = [
  'https://de1.api.radio-browser.info',
  'https://nl1.api.radio-browser.info',
  'https://at1.api.radio-browser.info',
  'https://all.api.radio-browser.info',
];

const UNPLAYABLE_EXTENSIONS = ['.m3u8', '.m3u', '.pls', '.asx', '.wax', '.wma', '.ram', '.smil'];

function isPlayableUrl(streamUrl?: string | null): boolean {
  if (!streamUrl || typeof streamUrl !== 'string') return false;
  const clean = streamUrl.toLowerCase().trim();
  if (!clean.startsWith('https://')) return false;

  try {
    const parsed = new URL(clean);
    const path = parsed.pathname.toLowerCase();
    if (UNPLAYABLE_EXTENSIONS.some((ext) => path.endsWith(ext) || path.includes(`${ext}?`))) {
      return false;
    }
  } catch {
    return false;
  }
  return true;
}

async function queryRadioBrowser(path: string, params: Record<string, string>): Promise<any[]> {
  const qs = new URLSearchParams(params).toString();
  const endpoint = `${path}${qs ? `?${qs}` : ''}`;

  for (const mirror of RADIO_BROWSER_MIRRORS) {
    try {
      const resp = await fetch(`${mirror}${endpoint}`, {
        headers: { 'User-Agent': 'VisionBridgeApp/1.0' },
      });
      if (resp.ok) {
        const data = await resp.json();
        if (Array.isArray(data)) return data;
      }
    } catch {
      continue;
    }
  }
  return [];
}

serve(async (req: Request) => {
  const corsResponse = handleCors(req);
  if (corsResponse) return corsResponse;

  try {
    const url = new URL(req.url);
    const search = url.searchParams.get('q') || url.searchParams.get('search') || '';
    const city = url.searchParams.get('city') || '';
    const state = url.searchParams.get('state') || '';
    const countryCode = url.searchParams.get('countryCode') || 'IN';
    const language = url.searchParams.get('language') || '';
    const limit = Math.min(25, Math.max(1, Number(url.searchParams.get('limit')) || 10));

    let rawStations: any[] = [];

    if (search) {
      rawStations = await queryRadioBrowser('/json/stations/byname/' + encodeURIComponent(search), {
        limit: String(limit * 2),
        order: 'clickcount',
        reverse: 'true',
      });
    } else {
      const queryParams: Record<string, string> = {
        limit: String(limit * 2),
        countrycode: countryCode,
        order: 'clickcount',
        reverse: 'true',
        hidebroken: 'true',
      };
      if (state) queryParams.state = state;
      if (language && language !== 'all') queryParams.language = language;

      rawStations = await queryRadioBrowser('/json/stations/search', queryParams);
    }

    const stations = rawStations
      .filter((s: any) => isPlayableUrl(s.url_resolved || s.url))
      .slice(0, limit)
      .map((s: any) => ({
        id: s.stationuuid || s.id || '',
        name: s.name || 'Local Radio',
        streamUrl: s.url_resolved || s.url || '',
        city: s.state || city || '',
        state: s.state || state || '',
        country: s.country || '',
        countryCode: s.countrycode || countryCode,
        language: s.language || language,
        tags: s.tags ? s.tags.split(',').map((t: string) => t.trim()).filter(Boolean) : [],
        codec: s.codec || 'MP3',
        bitrate: Number(s.bitrate) || 128,
        isHttps: true,
        votes: Number(s.votes) || 0,
        clickcount: Number(s.clickcount) || 0,
        favicon: s.favicon || '',
        homepage: s.homepage || '',
      }));

    const data = {
      location: { city, state, countryCode },
      stations,
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
