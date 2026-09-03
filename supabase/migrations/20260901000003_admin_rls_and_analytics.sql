-- ==============================================================================
-- VisionBridge Migration: Admin RLS Access & Analytics RPC Stored Procedures
-- ==============================================================================

-- 1. Helper function to check if current user is an admin
CREATE OR REPLACE FUNCTION public.is_admin()
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.profiles
        WHERE id = auth.uid() AND role = 'admin'
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER STABLE;

-- 2. Admin RLS Policies for Profiles
DROP POLICY IF EXISTS "Admins can update any profile" ON public.profiles;
CREATE POLICY "Admins can update any profile"
    ON public.profiles FOR UPDATE
    TO authenticated
    USING (public.is_admin())
    WITH CHECK (public.is_admin());

-- 3. Admin RLS Policies for Help Requests
DROP POLICY IF EXISTS "Admins can view all help requests" ON public.help_requests;
CREATE POLICY "Admins can view all help requests"
    ON public.help_requests FOR SELECT
    TO authenticated
    USING (public.is_admin());

DROP POLICY IF EXISTS "Admins can manage all help requests" ON public.help_requests;
CREATE POLICY "Admins can manage all help requests"
    ON public.help_requests FOR ALL
    TO authenticated
    USING (public.is_admin())
    WITH CHECK (public.is_admin());

-- 4. Admin RLS Policies for Volunteer Call Logs
DROP POLICY IF EXISTS "Admins can view all volunteer call logs" ON public.volunteer_call_logs;
CREATE POLICY "Admins can view all volunteer call logs"
    ON public.volunteer_call_logs FOR SELECT
    TO authenticated
    USING (public.is_admin());

DROP POLICY IF EXISTS "Admins can manage volunteer call logs" ON public.volunteer_call_logs;
CREATE POLICY "Admins can manage volunteer call logs"
    ON public.volunteer_call_logs FOR ALL
    TO authenticated
    USING (public.is_admin())
    WITH CHECK (public.is_admin());

-- 5. Admin RLS Policies for Emergency Events (SOS)
DROP POLICY IF EXISTS "Admins can view all emergency events" ON public.emergency_events;
CREATE POLICY "Admins can view all emergency events"
    ON public.emergency_events FOR SELECT
    TO authenticated
    USING (public.is_admin());

DROP POLICY IF EXISTS "Admins can update emergency events" ON public.emergency_events;
CREATE POLICY "Admins can update emergency events"
    ON public.emergency_events FOR UPDATE
    TO authenticated
    USING (public.is_admin())
    WITH CHECK (public.is_admin());

-- 6. Atomic RPC Function: Get Admin Overview Metrics
CREATE OR REPLACE FUNCTION public.get_admin_overview()
RETURNS JSONB AS $$
DECLARE
    v_total_users INT;
    v_total_volunteers INT;
    v_active_volunteers INT;
    v_total_help_requests INT;
    v_active_calls INT;
    v_completed_calls INT;
    v_active_sos INT;
BEGIN
    IF NOT public.is_admin() THEN
        RAISE EXCEPTION 'Access denied: Administrator privileges required';
    END IF;

    SELECT COUNT(*) INTO v_total_users FROM public.profiles WHERE role = 'lowVisionUser';
    SELECT COUNT(*) INTO v_total_volunteers FROM public.profiles WHERE role = 'volunteer';
    SELECT COUNT(*) INTO v_active_volunteers FROM public.profiles WHERE role = 'volunteer' AND availability = TRUE;
    SELECT COUNT(*) INTO v_total_help_requests FROM public.help_requests;
    SELECT COUNT(*) INTO v_active_calls FROM public.volunteer_call_logs WHERE status = 'ACTIVE';
    SELECT COUNT(*) INTO v_completed_calls FROM public.volunteer_call_logs WHERE status = 'COMPLETED';
    SELECT COUNT(*) INTO v_active_sos FROM public.emergency_events WHERE status = 'ACTIVE';

    RETURN jsonb_build_object(
        'totalUsers', v_total_users,
        'totalVolunteers', v_total_volunteers,
        'activeVolunteers', v_active_volunteers,
        'totalHelpRequests', v_total_help_requests,
        'activeCalls', v_active_calls,
        'completedCalls', v_completed_calls,
        'activeSos', v_active_sos
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
