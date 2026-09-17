package com.example.visionbridge.supabase

import com.example.visionbridge.BuildConfig

object SupabaseConfig {

    val SUPABASE_URL: String
        get() {
            var url = BuildConfig.VISIONBRIDGE_SUPABASE_URL.trim().trimEnd('/')
            if (url.endsWith("/rest/v1")) {
                url = url.removeSuffix("/rest/v1").trimEnd('/')
            }
            return url
        }

    val SUPABASE_ANON_KEY: String
        get() = BuildConfig.VISIONBRIDGE_SUPABASE_ANON_KEY

    // PostgREST REST API endpoint base
    val REST_URL: String
        get() = "$SUPABASE_URL/rest/v1"

    // Supabase Auth API endpoint base
    val AUTH_URL: String
        get() = "$SUPABASE_URL/auth/v1"

    // Supabase Edge Functions endpoint base
    val FUNCTIONS_URL: String
        get() = "$SUPABASE_URL/functions/v1"

    // Supabase Realtime WebSocket URL
    val REALTIME_WS_URL: String
        get() {
            val wsBase = if (SUPABASE_URL.startsWith("https://")) {
                SUPABASE_URL.replaceFirst("https://", "wss://")
            } else if (SUPABASE_URL.startsWith("http://")) {
                SUPABASE_URL.replaceFirst("http://", "ws://")
            } else {
                "wss://$SUPABASE_URL"
            }
            return "$wsBase/realtime/v1/websocket?apikey=$SUPABASE_ANON_KEY&vsn=1.0.0"
        }

    // Edge Function Names
    const val FUNCTION_VISION_ANALYZE = "vision-analyze"
    const val FUNCTION_VISION_CURRENCY = "vision-currency"
    const val FUNCTION_READING_EXTRACT = "reading-extract"
    const val FUNCTION_OBJECT_FINDER = "object-finder"
    const val FUNCTION_ASSISTANT_COMMAND = "assistant-command"
    const val FUNCTION_ASSISTANT_ASK = "assistant-ask"
    const val FUNCTION_LOCATION_CURRENT = "location-current"
    const val FUNCTION_TRANSPORT_ANALYZE = "transport-analyze"
    const val FUNCTION_LIVE_SESSION = "live-session"
    const val FUNCTION_LIVE_TOOL = "live-tool"
    const val FUNCTION_EMERGENCY_SOS = "emergency-sos"
    const val FUNCTION_RADIO_SEGMENT = "radio-segment"
    const val FUNCTION_RADIO_STATIONS = "radio-stations"
    const val FUNCTION_GAMES_TRIVIA = "games-trivia"
    const val FUNCTION_GAMES_RIDDLE = "games-riddle"
    const val FUNCTION_GAMES_MEMORY = "games-memory"
    const val FUNCTION_GAMES_TWENTY_QUESTIONS = "games-twenty-questions"
    const val FUNCTION_GAMES_EVALUATE = "games-evaluate"
    const val FUNCTION_NEWS_BRIEFING = "news-briefing"
    const val FUNCTION_MEDICATION_EXTRACT = "medication-extract"
}
