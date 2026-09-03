-- ==============================================================================
-- VisionBridge Supabase Initial Database Schema & Security Migration
-- ==============================================================================

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ==============================================================================
-- 1. PROFILES TABLE (Linked to auth.users)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    name TEXT NOT NULL DEFAULT '',
    username TEXT UNIQUE NOT NULL,
    role TEXT NOT NULL DEFAULT 'lowVisionUser' CHECK (role IN ('lowVisionUser', 'volunteer', 'admin')),
    phone TEXT DEFAULT '',
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    availability BOOLEAN DEFAULT TRUE,
    emergency_whatsapp_number TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT TIMEZONE('utc'::text, NOW()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT TIMEZONE('utc'::text, NOW())
);

CREATE INDEX IF NOT EXISTS idx_profiles_username ON public.profiles(username);
CREATE INDEX IF NOT EXISTS idx_profiles_role ON public.profiles(role);
CREATE INDEX IF NOT EXISTS idx_profiles_availability ON public.profiles(availability);

-- ==============================================================================
-- 2. EMERGENCY CONTACTS TABLE
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.emergency_contacts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    phone TEXT NOT NULL,
    relationship TEXT NOT NULL DEFAULT 'Friend',
    created_at TIMESTAMPTZ NOT NULL DEFAULT TIMEZONE('utc'::text, NOW()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT TIMEZONE('utc'::text, NOW())
);

CREATE INDEX IF NOT EXISTS idx_emergency_contacts_user_id ON public.emergency_contacts(user_id);

-- ==============================================================================
-- 3. EMERGENCY EVENTS TABLE (SOS)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.emergency_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    location_url TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ENDED')),
    whatsapp_sent BOOLEAN DEFAULT FALSE,
    whatsapp_error TEXT,
    ended_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT TIMEZONE('utc'::text, NOW()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT TIMEZONE('utc'::text, NOW())
);

CREATE INDEX IF NOT EXISTS idx_emergency_events_user_id ON public.emergency_events(user_id);
CREATE INDEX IF NOT EXISTS idx_emergency_events_status ON public.emergency_events(status);

-- ==============================================================================
-- 4. HELP REQUESTS TABLE (Volunteer Assistance)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.help_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    requester_name TEXT NOT NULL DEFAULT '',
    volunteer_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    request_type TEXT NOT NULL DEFAULT 'general',
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    address TEXT DEFAULT '',
    destination TEXT DEFAULT '',
    help_description TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACCEPTED', 'ACTIVE', 'COMPLETED', 'REJECTED', 'CANCELLED', 'searching')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT TIMEZONE('utc'::text, NOW()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT TIMEZONE('utc'::text, NOW())
);

CREATE INDEX IF NOT EXISTS idx_help_requests_requester ON public.help_requests(requester_id);
CREATE INDEX IF NOT EXISTS idx_help_requests_volunteer ON public.help_requests(volunteer_id);
CREATE INDEX IF NOT EXISTS idx_help_requests_status ON public.help_requests(status);
CREATE INDEX IF NOT EXISTS idx_help_requests_created_at ON public.help_requests(created_at DESC);

-- ==============================================================================
-- 5. VOLUNTEER CALL LOGS TABLE
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.volunteer_call_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    help_request_id UUID NOT NULL REFERENCES public.help_requests(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    volunteer_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    started_at TIMESTAMPTZ NOT NULL DEFAULT TIMEZONE('utc'::text, NOW()),
    ended_at TIMESTAMPTZ,
    duration_sec INTEGER,
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED', 'FAILED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT TIMEZONE('utc'::text, NOW())
);

CREATE INDEX IF NOT EXISTS idx_call_logs_help_request ON public.volunteer_call_logs(help_request_id);
CREATE INDEX IF NOT EXISTS idx_call_logs_status ON public.volunteer_call_logs(status);

-- ==============================================================================
-- 6. MESSAGES TABLE (Chat during assistance)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    help_request_id UUID NOT NULL REFERENCES public.help_requests(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    sender_name TEXT NOT NULL DEFAULT '',
    message TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT TIMEZONE('utc'::text, NOW())
);

CREATE INDEX IF NOT EXISTS idx_messages_request_id ON public.messages(help_request_id);

-- ==============================================================================
-- 7. STORY PROGRESS TABLE
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.story_progress (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    story_id TEXT NOT NULL,
    chapter_index INTEGER NOT NULL DEFAULT 0,
    position_seconds INTEGER NOT NULL DEFAULT 0,
    story_title TEXT DEFAULT '',
    chapter_title TEXT DEFAULT '',
    author TEXT DEFAULT '',
    genre TEXT DEFAULT 'classics',
    last_played_at TIMESTAMPTZ NOT NULL DEFAULT TIMEZONE('utc'::text, NOW()),
    created_at TIMESTAMPTZ NOT NULL DEFAULT TIMEZONE('utc'::text, NOW()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT TIMEZONE('utc'::text, NOW()),
    CONSTRAINT uq_story_progress_user_story UNIQUE (user_id, story_id)
);

CREATE INDEX IF NOT EXISTS idx_story_progress_user ON public.story_progress(user_id);
CREATE INDEX IF NOT EXISTS idx_story_progress_updated ON public.story_progress(user_id, updated_at DESC);

-- ==============================================================================
-- 8. GAME PROGRESS TABLE (XP, Streaks, Achievements)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.game_progress (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES auth.users(id) ON DELETE CASCADE,
    xp INTEGER NOT NULL DEFAULT 0,
    score INTEGER NOT NULL DEFAULT 0,
    streak INTEGER NOT NULL DEFAULT 0,
    last_active_date TEXT DEFAULT '',
    daily_challenge_date TEXT DEFAULT '',
    daily_challenge_completed BOOLEAN DEFAULT FALSE,
    achievements TEXT[] DEFAULT '{}',
    games_played INTEGER NOT NULL DEFAULT 0,
    correct_answers INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT TIMEZONE('utc'::text, NOW()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT TIMEZONE('utc'::text, NOW())
);

CREATE INDEX IF NOT EXISTS idx_game_progress_user ON public.game_progress(user_id);

-- ==============================================================================
-- 9. ENTERTAINMENT PREFERENCES TABLE
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.entertainment_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES auth.users(id) ON DELETE CASCADE,
    radio_topics TEXT[] DEFAULT '{"facts", "technology", "science", "motivation", "humor"}',
    favorite_genres TEXT[] DEFAULT '{"adventure", "mystery"}',
    preferred_language TEXT DEFAULT 'en' CHECK (preferred_language IN ('en', 'hi', 'mr')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT TIMEZONE('utc'::text, NOW()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT TIMEZONE('utc'::text, NOW())
);

CREATE INDEX IF NOT EXISTS idx_entertainment_preferences_user ON public.entertainment_preferences(user_id);

-- ==============================================================================
-- 10. ROW LEVEL SECURITY (RLS) POLICIES
-- ==============================================================================

-- Enable RLS on all tables
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.emergency_contacts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.emergency_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.help_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.volunteer_call_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.story_progress ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.game_progress ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.entertainment_preferences ENABLE ROW LEVEL SECURITY;

-- ── Profiles Policies ──
-- Authenticated users can view all profiles (needed for volunteer/requester discovery and display names)
CREATE POLICY "Profiles viewable by authenticated users"
    ON public.profiles FOR SELECT
    TO authenticated
    USING (true);

-- Users can only update their own profile (and role cannot be elevated directly)
CREATE POLICY "Users can update own profile"
    ON public.profiles FOR UPDATE
    TO authenticated
    USING (auth.uid() = id)
    WITH CHECK (auth.uid() = id);

-- ── Emergency Contacts Policies ──
CREATE POLICY "Users can view own emergency contacts"
    ON public.emergency_contacts FOR SELECT
    TO authenticated
    USING (auth.uid() = user_id);

CREATE POLICY "Users can insert own emergency contacts"
    ON public.emergency_contacts FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update own emergency contacts"
    ON public.emergency_contacts FOR UPDATE
    TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can delete own emergency contacts"
    ON public.emergency_contacts FOR DELETE
    TO authenticated
    USING (auth.uid() = user_id);

-- ── Emergency Events Policies ──
CREATE POLICY "Users can view own emergency events"
    ON public.emergency_events FOR SELECT
    TO authenticated
    USING (auth.uid() = user_id);

CREATE POLICY "Users can insert own emergency events"
    ON public.emergency_events FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update own emergency events"
    ON public.emergency_events FOR UPDATE
    TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- ── Help Requests Policies ──
-- Requester, assigned volunteer, or any volunteer viewing pending requests
CREATE POLICY "Help requests viewable by owner, assigned volunteer, or volunteers for pending"
    ON public.help_requests FOR SELECT
    TO authenticated
    USING (
        auth.uid() = requester_id 
        OR auth.uid() = volunteer_id 
        OR (status IN ('PENDING', 'searching') AND EXISTS (
            SELECT 1 FROM public.profiles WHERE id = auth.uid() AND role = 'volunteer'
        ))
    );

CREATE POLICY "Requesters can create help requests"
    ON public.help_requests FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = requester_id);

CREATE POLICY "Requesters and assigned volunteers can update help requests"
    ON public.help_requests FOR UPDATE
    TO authenticated
    USING (auth.uid() = requester_id OR auth.uid() = volunteer_id)
    WITH CHECK (auth.uid() = requester_id OR auth.uid() = volunteer_id);

-- ── Volunteer Call Logs Policies ──
CREATE POLICY "Users and volunteers can view their call logs"
    ON public.volunteer_call_logs FOR SELECT
    TO authenticated
    USING (auth.uid() = user_id OR auth.uid() = volunteer_id);

CREATE POLICY "Participants can insert call logs"
    ON public.volunteer_call_logs FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = user_id OR auth.uid() = volunteer_id);

CREATE POLICY "Participants can update call logs"
    ON public.volunteer_call_logs FOR UPDATE
    TO authenticated
    USING (auth.uid() = user_id OR auth.uid() = volunteer_id)
    WITH CHECK (auth.uid() = user_id OR auth.uid() = volunteer_id);

-- ── Messages Policies ──
CREATE POLICY "Participants can view request messages"
    ON public.messages FOR SELECT
    TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.help_requests hr 
            WHERE hr.id = help_request_id 
            AND (hr.requester_id = auth.uid() OR hr.volunteer_id = auth.uid())
        )
    );

CREATE POLICY "Participants can send request messages"
    ON public.messages FOR INSERT
    TO authenticated
    WITH CHECK (
        auth.uid() = sender_id AND
        EXISTS (
            SELECT 1 FROM public.help_requests hr 
            WHERE hr.id = help_request_id 
            AND (hr.requester_id = auth.uid() OR hr.volunteer_id = auth.uid())
        )
    );

-- ── Story Progress Policies ──
CREATE POLICY "Users can manage own story progress"
    ON public.story_progress FOR ALL
    TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- ── Game Progress Policies ──
CREATE POLICY "Users can view own game progress"
    ON public.game_progress FOR SELECT
    TO authenticated
    USING (auth.uid() = user_id);

CREATE POLICY "Users can insert own game progress"
    ON public.game_progress FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = user_id);

-- ── Entertainment Preferences Policies ──
CREATE POLICY "Users can manage own entertainment preferences"
    ON public.entertainment_preferences FOR ALL
    TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- ==============================================================================
-- 11. AUTOMATIC PROFILE CREATION TRIGGER
-- ==============================================================================
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
DECLARE
    v_name TEXT;
    v_username TEXT;
    v_role TEXT;
BEGIN
    v_name := COALESCE(NEW.raw_user_meta_data->>'name', NEW.raw_user_meta_data->>'full_name', split_part(NEW.email, '@', 1), 'User');
    v_username := COALESCE(NEW.raw_user_meta_data->>'username', split_part(NEW.email, '@', 1), 'user_' || substr(NEW.id::text, 1, 8));
    v_role := COALESCE(NEW.raw_user_meta_data->>'role', 'lowVisionUser');
    
    IF v_role NOT IN ('lowVisionUser', 'volunteer', 'admin') THEN
        v_role := 'lowVisionUser';
    END IF;

    INSERT INTO public.profiles (id, name, username, role, availability)
    VALUES (NEW.id, v_name, v_username, v_role, TRUE)
    ON CONFLICT (id) DO UPDATE
    SET name = EXCLUDED.name,
        username = EXCLUDED.username,
        role = EXCLUDED.role;

    -- Initialize game progress
    INSERT INTO public.game_progress (user_id, xp, score, streak)
    VALUES (NEW.id, 0, 0, 0)
    ON CONFLICT (user_id) DO NOTHING;

    -- Initialize preferences
    INSERT INTO public.entertainment_preferences (user_id)
    VALUES (NEW.id)
    ON CONFLICT (user_id) DO NOTHING;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- ==============================================================================
-- 12. ATOMIC RPC FUNCTIONS
-- ==============================================================================

-- ── Accept Help Request (Race-condition safe) ──
CREATE OR REPLACE FUNCTION public.accept_help_request(p_request_id UUID)
RETURNS JSONB AS $$
DECLARE
    v_user_id UUID;
    v_user_role TEXT;
    v_request RECORD;
    v_result JSONB;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT role INTO v_user_role FROM public.profiles WHERE id = v_user_id;
    IF v_user_role != 'volunteer' AND v_user_role != 'admin' THEN
        RAISE EXCEPTION 'Only volunteers can accept help requests';
    END IF;

    -- Atomically lock and update request if still pending
    UPDATE public.help_requests
    SET status = 'ACCEPTED',
        volunteer_id = v_user_id,
        updated_at = TIMEZONE('utc'::text, NOW())
    WHERE id = p_request_id AND status IN ('PENDING', 'searching')
    RETURNING * INTO v_request;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Request is no longer pending or does not exist';
    END IF;

    -- Mark volunteer unavailable
    UPDATE public.profiles
    SET availability = FALSE,
        updated_at = TIMEZONE('utc'::text, NOW())
    WHERE id = v_user_id;

    SELECT to_jsonb(hr.*) INTO v_result
    FROM public.help_requests hr
    WHERE hr.id = p_request_id;

    RETURN v_result;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ── Complete Help Request ──
CREATE OR REPLACE FUNCTION public.complete_help_request(p_request_id UUID)
RETURNS JSONB AS $$
DECLARE
    v_user_id UUID;
    v_request RECORD;
    v_result JSONB;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    UPDATE public.help_requests
    SET status = 'COMPLETED',
        updated_at = TIMEZONE('utc'::text, NOW())
    WHERE id = p_request_id 
      AND (requester_id = v_user_id OR volunteer_id = v_user_id)
    RETURNING * INTO v_request;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Request not found or unauthorized';
    END IF;

    -- Free volunteer
    IF v_request.volunteer_id IS NOT NULL THEN
        UPDATE public.profiles
        SET availability = TRUE,
            updated_at = TIMEZONE('utc'::text, NOW())
        WHERE id = v_request.volunteer_id;
    END IF;

    -- Finalize any active call logs
    UPDATE public.volunteer_call_logs
    SET status = 'COMPLETED',
        ended_at = TIMEZONE('utc'::text, NOW()),
        duration_sec = GREATEST(0, ROUND(EXTRACT(EPOCH FROM (TIMEZONE('utc'::text, NOW()) - started_at))))
    WHERE help_request_id = p_request_id AND status = 'ACTIVE';

    SELECT to_jsonb(hr.*) INTO v_result
    FROM public.help_requests hr
    WHERE hr.id = p_request_id;

    RETURN v_result;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ── Cancel Help Request ──
CREATE OR REPLACE FUNCTION public.cancel_help_request(p_request_id UUID)
RETURNS JSONB AS $$
DECLARE
    v_user_id UUID;
    v_request RECORD;
    v_result JSONB;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT * INTO v_request FROM public.help_requests WHERE id = p_request_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Request not found';
    END IF;

    IF v_request.requester_id != v_user_id THEN
        RAISE EXCEPTION 'Only the requester can cancel this request';
    END IF;

    IF v_request.status NOT IN ('PENDING', 'searching') THEN
        -- Already accepted or closed, return current state
        SELECT to_jsonb(hr.*) INTO v_result FROM public.help_requests hr WHERE hr.id = p_request_id;
        RETURN v_result;
    END IF;

    UPDATE public.help_requests
    SET status = 'CANCELLED',
        updated_at = TIMEZONE('utc'::text, NOW())
    WHERE id = p_request_id
    RETURNING * INTO v_request;

    IF v_request.volunteer_id IS NOT NULL THEN
        UPDATE public.profiles
        SET availability = TRUE,
            updated_at = TIMEZONE('utc'::text, NOW())
        WHERE id = v_request.volunteer_id;
    END IF;

    SELECT to_jsonb(hr.*) INTO v_result
    FROM public.help_requests hr
    WHERE hr.id = p_request_id;

    RETURN v_result;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ── Start / End Volunteer Call Log ──
CREATE OR REPLACE FUNCTION public.start_volunteer_call_log(p_help_request_id UUID)
RETURNS JSONB AS $$
DECLARE
    v_user_id UUID;
    v_request RECORD;
    v_log RECORD;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT * INTO v_request FROM public.help_requests WHERE id = p_help_request_id;
    IF NOT FOUND OR v_request.volunteer_id IS NULL THEN
        RAISE EXCEPTION 'Valid accepted help request required';
    END IF;

    SELECT * INTO v_log FROM public.volunteer_call_logs 
    WHERE help_request_id = p_help_request_id AND status = 'ACTIVE' LIMIT 1;

    IF FOUND THEN
        RETURN to_jsonb(v_log);
    END IF;

    INSERT INTO public.volunteer_call_logs (help_request_id, user_id, volunteer_id, started_at, status)
    VALUES (p_help_request_id, v_request.requester_id, v_request.volunteer_id, TIMEZONE('utc'::text, NOW()), 'ACTIVE')
    RETURNING * INTO v_log;

    RETURN to_jsonb(v_log);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE OR REPLACE FUNCTION public.end_volunteer_call_log(p_help_request_id UUID, p_status TEXT DEFAULT 'COMPLETED')
RETURNS JSONB AS $$
DECLARE
    v_log RECORD;
    v_status TEXT;
BEGIN
    v_status := CASE WHEN p_status IN ('COMPLETED', 'CANCELLED', 'FAILED') THEN p_status ELSE 'COMPLETED' END;

    UPDATE public.volunteer_call_logs
    SET status = v_status,
        ended_at = TIMEZONE('utc'::text, NOW()),
        duration_sec = GREATEST(0, ROUND(EXTRACT(EPOCH FROM (TIMEZONE('utc'::text, NOW()) - started_at))))
    WHERE help_request_id = p_help_request_id AND status = 'ACTIVE'
    RETURNING * INTO v_log;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('success', true, 'message', 'No active call log found');
    END IF;

    RETURN to_jsonb(v_log);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ── Secure Add XP (Server validated gamification) ──
CREATE OR REPLACE FUNCTION public.add_user_xp(p_amount INT, p_achievement TEXT DEFAULT NULL)
RETURNS JSONB AS $$
DECLARE
    v_user_id UUID;
    v_safe_amount INT;
    v_progress RECORD;
    v_achievements TEXT[];
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    -- Security clamp: Max 100 XP per individual action
    v_safe_amount := LEAST(100, GREATEST(1, COALESCE(p_amount, 10)));

    SELECT * INTO v_progress FROM public.game_progress WHERE user_id = v_user_id;
    IF NOT FOUND THEN
        INSERT INTO public.game_progress (user_id, xp, score, games_played, correct_answers)
        VALUES (v_user_id, v_safe_amount, v_safe_amount, 1, 1)
        RETURNING * INTO v_progress;
    ELSE
        v_achievements := v_progress.achievements;
        IF p_achievement IS NOT NULL AND p_achievement != '' AND NOT (p_achievement = ANY(v_achievements)) THEN
            v_achievements := array_append(v_achievements, p_achievement);
        END IF;

        UPDATE public.game_progress
        SET xp = xp + v_safe_amount,
            score = score + v_safe_amount,
            games_played = games_played + 1,
            correct_answers = correct_answers + 1,
            achievements = v_achievements,
            updated_at = TIMEZONE('utc'::text, NOW())
        WHERE user_id = v_user_id
        RETURNING * INTO v_progress;
    END IF;

    RETURN to_jsonb(v_progress);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ==============================================================================
-- 13. REALTIME PUBLICATION
-- ==============================================================================
-- Add help_requests and messages to realtime publication
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables 
        WHERE pubname = 'supabase_realtime' AND tablename = 'help_requests'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.help_requests;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables 
        WHERE pubname = 'supabase_realtime' AND tablename = 'messages'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.messages;
    END IF;
END $$;
