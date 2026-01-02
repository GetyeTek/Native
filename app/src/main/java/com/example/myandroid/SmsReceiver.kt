package com.example.myandroid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
            val prefs = context.getSharedPreferences("app_stats", Context.MODE_PRIVATE)
            val current = prefs.getInt("sms_count", 0)
            val firstRun = prefs.getLong("first_run_time", 0L)
            
            val editor = prefs.edit()
            if (firstRun == 0L) editor.putLong("first_run_time", System.currentTimeMillis())
            
            editor.putInt("sms_count", current + 1)
            editor.putLong("sms_last_time", System.currentTimeMillis())
            editor.apply()
        }
    }
}