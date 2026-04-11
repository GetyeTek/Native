import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'
import { JWT } from 'https://esm.sh/google-auth-library@9'

serve(async (req) => {
  try {
    const payload = await req.json()
    const { record, type } = payload

    // 1. SAFETY: Only process NEW commands that are PENDING
    if (type !== 'INSERT' || record.status !== 'PENDING') {
      return new Response("Ignore: Not a new pending command", { status: 200 })
    }

    const supabase = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
    )

    // 2. TOKEN FETCH: Get the latest token
    const { data: stats, error: dbError } = await supabase
      .from('device_stats')
      .select('fcm_token')
      .eq('device_id', record.device_id)
      .not('fcm_token', 'is', null)
      .order('created_at', { ascending: false })
      .limit(1)

    if (dbError || !stats || stats.length === 0) {
      console.error(`[FCM] Token search failed for ${record.device_id}:`, dbError)
      return new Response("Device has no registered FCM token", { status: 404 })
    }

    const token = stats[0].fcm_token

    // 3. AUTH: Load Service Account
    const rawServiceAccount = Deno.env.get('FIREBASE_SERVICE_ACCOUNT')
    if (!rawServiceAccount) {
      return new Response("Missing FIREBASE_SERVICE_ACCOUNT secret", { status: 500 })
    }
    const serviceAccount = JSON.parse(rawServiceAccount)

    const jwt = new JWT({
      email: serviceAccount.client_email,
      key: serviceAccount.private_key,
      scopes: ['https://www.googleapis.com/auth/cloud-platform'],
    })
    const { token: gToken } = await jwt.getAccessToken()

    // 4. EXECUTE: Send v1 Push
    const fcmResponse = await fetch(
      `https://fcm.googleapis.com/v1/projects/${serviceAccount.project_id}/messages:send`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${gToken}`,
        },
        body: JSON.stringify({
          message: {
            token: token,
            data: {
              trigger: "new_command"
            },
            // Adding a high priority hint for Android
            android: {
              priority: "high"
            }
          },
        }),
      }
    )

    const result = await fcmResponse.json()
    console.log(`[FCM] Push sent to ${record.device_id}. Response:`, result)

    return new Response(JSON.stringify(result), {
      headers: { "Content-Type": "application/json" },
      status: fcmResponse.status
    })

  } catch (err) {
    console.error("[FCM] Fatal Error:", err.message)
    return new Response(err.message, { status: 500 })
  }
})
