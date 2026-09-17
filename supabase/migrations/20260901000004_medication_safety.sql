-- ==============================================================================
-- VisionBridge Supabase Migration: Medication Safety & Accessible Reminders
-- ==============================================================================

-- 1. MEDICATIONS TABLE
CREATE TABLE IF NOT EXISTS public.medications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    strength TEXT DEFAULT '',
    form TEXT DEFAULT '',
    active_ingredients JSONB DEFAULT '[]'::jsonb,
    printed_directions TEXT DEFAULT '',
    expiry_date TEXT DEFAULT '',
    storage_information TEXT DEFAULT '',
    warnings_visible_on_package JSONB DEFAULT '[]'::jsonb,
    raw_visible_text TEXT DEFAULT '',
    manufacturer TEXT DEFAULT '',
    batch_number TEXT DEFAULT '',
    confidence DOUBLE PRECISION DEFAULT 0.0,
    verification_status TEXT NOT NULL DEFAULT 'AI_EXTRACTED' CHECK (verification_status IN ('AI_EXTRACTED', 'USER_VERIFIED', 'VOLUNTEER_VERIFIED', 'UNCERTAIN')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT TIMEZONE('utc'::text, NOW()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT TIMEZONE('utc'::text, NOW())
);

CREATE INDEX IF NOT EXISTS idx_medications_user_id ON public.medications(user_id);
CREATE INDEX IF NOT EXISTS idx_medications_name ON public.medications(name);
CREATE INDEX IF NOT EXISTS idx_medications_created_at ON public.medications(created_at DESC);

-- 2. MEDICATION REMINDERS TABLE
CREATE TABLE IF NOT EXISTS public.medication_reminders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    medication_id UUID REFERENCES public.medications(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    medication_name TEXT NOT NULL,
    reminder_time TEXT NOT NULL, -- e.g. "09:00", "21:00" in 24-hr format
    frequency TEXT NOT NULL DEFAULT 'DAILY' CHECK (frequency IN ('DAILY', 'MORNING', 'AFTERNOON', 'EVENING', 'NIGHT', 'CUSTOM')),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    dosage_label TEXT DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT TIMEZONE('utc'::text, NOW()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT TIMEZONE('utc'::text, NOW())
);

CREATE INDEX IF NOT EXISTS idx_medication_reminders_user_id ON public.medication_reminders(user_id);
CREATE INDEX IF NOT EXISTS idx_medication_reminders_medication_id ON public.medication_reminders(medication_id);
CREATE INDEX IF NOT EXISTS idx_medication_reminders_enabled ON public.medication_reminders(enabled);

-- 3. ROW LEVEL SECURITY (RLS) POLICIES
ALTER TABLE public.medications ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.medication_reminders ENABLE ROW LEVEL SECURITY;

-- Medications RLS
CREATE POLICY "Users can view own medications"
    ON public.medications FOR SELECT
    TO authenticated
    USING (auth.uid() = user_id);

CREATE POLICY "Users can insert own medications"
    ON public.medications FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update own medications"
    ON public.medications FOR UPDATE
    TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can delete own medications"
    ON public.medications FOR DELETE
    TO authenticated
    USING (auth.uid() = user_id);

-- Medication Reminders RLS
CREATE POLICY "Users can view own medication reminders"
    ON public.medication_reminders FOR SELECT
    TO authenticated
    USING (auth.uid() = user_id);

CREATE POLICY "Users can insert own medication reminders"
    ON public.medication_reminders FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update own medication reminders"
    ON public.medication_reminders FOR UPDATE
    TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can delete own medication reminders"
    ON public.medication_reminders FOR DELETE
    TO authenticated
    USING (auth.uid() = user_id);
