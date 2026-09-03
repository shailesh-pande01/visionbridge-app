-- ==============================================================================
-- VisionBridge Migration: Auth Security Hardening & Role Protection
-- ==============================================================================

-- 1. Restrict trigger on auth.users so users cannot self-register as admin
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
    
    -- Strict security: Only lowVisionUser and volunteer can be chosen during self-registration.
    -- Any attempt to register as admin or invalid role is forced to lowVisionUser.
    IF v_role NOT IN ('lowVisionUser', 'volunteer') THEN
        v_role := 'lowVisionUser';
    END IF;

    INSERT INTO public.profiles (id, name, username, role, availability)
    VALUES (NEW.id, v_name, v_username, v_role, TRUE)
    ON CONFLICT (id) DO UPDATE
    SET name = EXCLUDED.name,
        username = EXCLUDED.username;

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

-- 2. Trigger to prevent clients from tampering with their own role column
CREATE OR REPLACE FUNCTION public.protect_profile_role()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.role IS DISTINCT FROM OLD.role THEN
        -- Only allow role update if executed by database admin/service role
        IF current_user != 'postgres' AND COALESCE((auth.jwt() ->> 'role'), '') != 'service_role' THEN
            -- Check if current authenticated user is an existing admin
            IF NOT EXISTS (SELECT 1 FROM public.profiles WHERE id = auth.uid() AND role = 'admin') THEN
                NEW.role := OLD.role;
            END IF;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS trg_protect_profile_role ON public.profiles;
CREATE TRIGGER trg_protect_profile_role
    BEFORE UPDATE ON public.profiles
    FOR EACH ROW
    EXECUTE FUNCTION public.protect_profile_role();
