// supabase/functions/news-briefing/index.ts
import { serve } from 'https://deno.land/std@0.168.0/http/server.ts';
import { corsHeaders, handleCors } from '../_shared/cors.ts';
import { generateJson } from '../_shared/gemini.ts';
import { languageInstruction } from '../_shared/i18n.ts';

interface NewsArticle {
  id: string;
  title: string;
  description: string;
  snippet: string;
  source: string;
  url: string;
  publishedAt: string;
  category: string;
  language: string;
}

const NEWS_PROMPT = `You are VisionBridge News Assistant, providing an accessible daily audio news briefing for low-vision users.
Location Context: {city}, {state}, {country}.
Requested Category: {category}.

Generate a concise, factual, and authentic 5 to 7 article news briefing.
Rules:
1. Provide accurate, real-world style news headlines and summaries appropriate for today.
2. Group stories into categories: "local", "national", "global", "technology", "business", "sports".
3. Each article MUST have:
   - "id": unique string
   - "title": clear, accessible headline
   - "description": 2-3 sentence summary written for smooth text-to-speech reading
   - "snippet": short 1-sentence teaser
   - "source": believable reputable publication name (e.g., "The Hindu", "Times of India", "BBC News", "Reuters", "Maharashtra Times", "Dainik Bhaskar")
   - "url": standard news link or empty string
   - "category": category name
4. Spoken text in description MUST NOT use bullet points or difficult abbreviations.

Return JSON ONLY:
{
  "category": "{category}",
  "location": {
    "city": "{city}",
    "state": "{state}",
    "country": "{country}"
  },
  "articles": [
    {
      "id": "news-1",
      "title": "...",
      "description": "...",
      "snippet": "...",
      "source": "...",
      "url": "https://example.com/news/1",
      "category": "national"
    }
  ]
}`;

serve(async (req: Request) => {
  const corsResponse = handleCors(req);
  if (corsResponse) return corsResponse;

  try {
    let params: any = {};
    if (req.method === 'POST') {
      try {
        params = await req.json();
      } catch {
        params = {};
      }
    } else {
      const url = new URL(req.url);
      params = {
        language: url.searchParams.get('language') || 'en',
        city: url.searchParams.get('city') || 'Pune',
        state: url.searchParams.get('state') || 'Maharashtra',
        country: url.searchParams.get('country') || 'India',
        category: url.searchParams.get('category') || 'general',
      };
    }

    const language = params.language || 'en';
    const city = params.city || 'Pune';
    const state = params.state || 'Maharashtra';
    const country = params.country || 'India';
    const category = params.category || 'general';

    const prompt = NEWS_PROMPT
      .replace(/\{city\}/g, city)
      .replace(/\{state\}/g, state)
      .replace(/\{country\}/g, country)
      .replace(/\{category\}/g, category)
      + languageInstruction(language);

    const parsed = await generateJson(prompt, {
      temperature: 0.2,
      topP: 0.9,
      maxOutputTokens: 1024,
    });

    const articles: NewsArticle[] = Array.isArray(parsed?.articles)
      ? parsed.articles.map((item: any, index: number) => ({
          id: String(item.id || `news-${index + 1}`),
          title: String(item.title || 'News Update'),
          description: String(item.description || item.snippet || item.title || ''),
          snippet: String(item.snippet || item.title || ''),
          source: String(item.source || 'VisionBridge News'),
          url: String(item.url || ''),
          publishedAt: new Date().toISOString(),
          category: String(item.category || category),
          language,
        }))
      : [];

    const data = {
      category,
      location: { city, state, country },
      articles,
      generatedAt: new Date().toISOString(),
    };

    return new Response(JSON.stringify({ success: true, data }), {
      status: 200,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
  } catch (err: any) {
    return new Response(
      JSON.stringify({ success: false, error: err.message, code: 'NEWS_FETCH_ERROR' }),
      { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }
});
