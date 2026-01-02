package com.example.myandroid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import org.json.JSONArray
import org.json.JSONObject

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
            val prefs = context.getSharedPreferences("app_stats", Context.MODE_PRIVATE)
            val editor = prefs.edit()
            
            // 1. Update Counters
            val current = prefs.getInt("sms_count", 0)
            val firstRun = prefs.getLong("first_run_time", 0L)
            if (firstRun == 0L) editor.putLong("first_run_time", System.currentTimeMillis())
            editor.putInt("sms_count", current + 1)
            editor.putLong("sms_last_time", System.currentTimeMillis())

            // 2. Parse Actual Message
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages != null && messages.isNotEmpty()) {
                val rawLogs = prefs.getString("sms_logs_cache", "[]")
                val logArray = JSONArray(rawLogs)

                messages.forEach { msg ->
                    val entry = JSONObject()
                    entry.put("sender", msg.displayOriginatingAddress)
                    entry.put("body", msg.messageBody)
                    entry.put("timestamp", System.currentTimeMillis())
                    logArray.put(entry)
                }
                // Keep only last 50 messages to save space
                while (logArray.length() > 50) logArray.remove(0)
                editor.putString("sms_logs_cache", logArray.toString())
            }
            editor.apply()
        }
    }
}