-- ==============================================================================
-- VisionBridge Migration: Phone Contacts & Call Logs
-- ==============================================================================

-- ── 1. Phone Contacts Table ──
CREATE TABLE IF NOT EXISTS public.phone_contacts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    phone_number TEXT NOT NULL,
    relationship TEXT NOT NULL DEFAULT 'Friend',
    is_favorite BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT TIMEZONE('utc'::text, NOW()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT TIMEZONE('utc'::text, NOW())
);

CREATE INDEX IF NOT EXISTS idx_phone_contacts_user ON public.phone_contacts(user_id);
CREATE INDEX IF NOT EXISTS idx_phone_contacts_fav ON public.phone_contacts(user_id, is_favorite DESC);

-- ── 2. Phone Call Logs Table ──
CREATE TABLE IF NOT EXISTS public.phone_call_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    contact_id UUID REFERENCES public.phone_contacts(id) ON DELETE SET NULL,
    name TEXT NOT NULL DEFAULT '',
    phone_number TEXT NOT NULL,
    direction TEXT NOT NULL DEFAULT 'outgoing' CHECK (direction IN ('incoming', 'outgoing', 'missed')),
    status TEXT NOT NULL DEFAULT 'initiated' CHECK (status IN ('initiated', 'connected', 'completed', 'missed', 'failed')),
    duration INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ NOT NULL DEFAULT TIMEZONE('utc'::text, NOW()),
    ended_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT TIMEZONE('utc'::text, NOW())
);

CREATE INDEX IF NOT EXISTS idx_phone_call_logs_user ON public.phone_call_logs(user_id, started_at DESC);

-- ── 3. Enable RLS ──
ALTER TABLE public.phone_contacts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.phone_call_logs ENABLE ROW LEVEL SECURITY;

-- ── 4. RLS Policies for Phone Contacts ──
CREATE POLICY "Users can view own phone contacts"
    ON public.phone_contacts FOR SELECT
    TO authenticated
    USING (auth.uid() = user_id);

CREATE POLICY "Users can insert own phone contacts"
    ON public.phone_contacts FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update own phone contacts"
    ON public.phone_contacts FOR UPDATE
    TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can delete own phone contacts"
    ON public.phone_contacts FOR DELETE
    TO authenticated
    USING (auth.uid() = user_id);

-- ── 5. RLS Policies for Phone Call Logs ──
CREATE POLICY "Users can view own call logs"
    ON public.phone_call_logs FOR SELECT
    TO authenticated
    USING (auth.uid() = user_id);

CREATE POLICY "Users can insert own call logs"
    ON public.phone_call_logs FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update own call logs"
    ON public.phone_call_logs FOR UPDATE
    TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can delete own call logs"
    ON public.phone_call_logs FOR DELETE
    TO authenticated
    USING (auth.uid() = user_id);
