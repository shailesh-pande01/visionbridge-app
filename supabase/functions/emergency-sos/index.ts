// supabase/functions/emergency-sos/index.ts
import { serve } from 'https://deno.land/std@0.168.0/http/server.ts';
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.39.7';
import { corsHeaders, handleCors } from '../_shared/cors.ts';

function normalizePhoneNumber(phone: string): string | null {
  if (!phone) return null;
  let digits = phone.toString().replace(/\D/g, '');
  if (digits.length === 10) {
    digits = '91' + digits;
  }
  return digits.length >= 10 ? digits : null;
}

serve(async (req: Request) => {
  const corsResponse = handleCors(req);
  if (corsResponse) return corsResponse;

  try {
    const { userId, latitude, longitude } = await req.json();

    if (latitude == null || longitude == null) {
      return new Response(
        JSON.stringify({ success: false, error: 'latitude and longitude are required' }),
        { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const supabaseUrl = Deno.env.get('SUPABASE_URL') ?? '';
    const supabaseServiceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? Deno.env.get('SUPABASE_ANON_KEY') ?? '';

    const supabase = createClient(supabaseUrl, supabaseServiceKey);

    const locationUrl = `https://www.google.com/maps/search/?api=1&query=${latitude},${longitude}`;

    // 1. Insert emergency event
    let eventId: string | null = null;
    let eventCreatedAt = new Date().toISOString();

    if (userId) {
      const { data: event, error: eventErr } = await supabase
        .from('emergency_events')
        .insert({
          user_id: userId,
          latitude: Number(latitude),
          longitude: Number(longitude),
          location_url: locationUrl,
          status: 'ACTIVE',
        })
        .select()
        .single();

      if (!eventErr && event) {
        eventId = event.id;
        eventCreatedAt = event.created_at;
      }
    }

    // 2. Fetch emergency contacts
    let contacts: any[] = [];
    if (userId) {
      const { data } = await supabase
        .from('emergency_contacts')
        .select('name, phone, relationship')
        .eq('user_id', userId);
      contacts = data || [];
    }

    // 3. Resolve user display name
    let userName = 'VisionBridge User';
    if (userId) {
      const { data: profile } = await supabase
        .from('profiles')
        .select('name, username')
        .eq('id', userId)
        .single();
      if (profile) {
        userName = profile.name || profile.username || 'VisionBridge User';
      }
    }

    // 4. Send WhatsApp Alert if configured
    const accessToken = Deno.env.get('WHATSAPP_ACCESS_TOKEN');
    const phoneNumberId = Deno.env.get('WHATSAPP_PHONE_NUMBER_ID');
    const apiVersion = Deno.env.get('WHATSAPP_API_VERSION') || 'v18.0';

    let whatsappSent = false;
    let whatsappError: string | null = null;

    const phoneNumbers: string[] = [];
    contacts.forEach((c) => {
      if (c.phone) phoneNumbers.push(c.phone);
    });

    const uniqueNormalized = [...new Set(phoneNumbers.map(normalizePhoneNumber).filter(Boolean))] as string[];

    if (accessToken && phoneNumberId && uniqueNormalized.length > 0) {
      const timeString = new Date(eventCreatedAt).toLocaleString();
      const messageBody = `🚨 VISIONBRIDGE EMERGENCY ALERT\n\nEmergency SOS activated by: ${userName}\n\nTime: ${timeString}\nCoordinates: ${latitude}, ${longitude}\n\nLive Map Location:\n${locationUrl}\n\nPlease contact or assist immediately.`;

      for (const phone of uniqueNormalized) {
        try {
          const waUrl = `https://graph.facebook.com/${apiVersion}/${phoneNumberId}/messages`;
          const waResp = await fetch(waUrl, {
            method: 'POST',
            headers: {
              Authorization: `Bearer ${accessToken}`,
              'Content-Type': 'application/json',
            },
            body: JSON.stringify({
              messaging_product: 'whatsapp',
              recipient_type: 'individual',
              to: phone,
              type: 'text',
              text: { preview_url: true, body: messageBody },
            }),
          });

          if (waResp.ok) {
            whatsappSent = true;
          } else {
            const errData = await waResp.json().catch(() => ({}));
            whatsappError = errData.error?.message || `HTTP ${waResp.status}`;
          }
        } catch (waErr: any) {
          whatsappError = waErr.message;
        }
      }
    } else if (!accessToken || !phoneNumberId) {
      whatsappError = 'WhatsApp credentials not configured on server';
    }

    // Update event with outcome
    if (eventId) {
      await supabase
        .from('emergency_events')
        .update({
          whatsapp_sent: whatsappSent,
          whatsapp_error: whatsappError,
        })
        .eq('id', eventId);
    }

    return new Response(
      JSON.stringify({
        success: true,
        data: {
          id: eventId || 'local_event',
          status: 'ACTIVE',
          whatsappSent,
          whatsappError,
          coordinates: { latitude: Number(latitude), longitude: Number(longitude) },
          timestamp: eventCreatedAt,
          locationUrl,
        },
      }),
      { status: 201, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  } catch (err: any) {
    return new Response(
      JSON.stringify({ success: false, error: err.message, code: 'SERVER_ERROR' }),
      { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }
});
