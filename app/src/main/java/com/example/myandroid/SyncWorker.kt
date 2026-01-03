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
import java.util.Calendar

class SyncWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val notifManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifId = 999

        try {
            // 1. Show Notification
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

            // 2. Prepare Data
            val ctx = applicationContext
            val prefs = ctx.getSharedPreferences("app_stats", Context.MODE_PRIVATE)
            
            // --- LOCATION LOGIC ---
            var lat = 0.0
            var lon = 0.0
            val hasPerm = androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            
            if (hasPerm) {
                try {
                    val fused = LocationServices.getFusedLocationProviderClient(ctx)
                    val loc = fused.lastLocation.await()
                    
                    // GPS NOISE FILTER
                    // 1. If loc is null, skip
                    // 2. If accuracy is worse than 50 meters, skip (Indoors drift)
                    val isCleanSignal = loc != null && (!loc.hasAccuracy() || loc.accuracy < 50)

                    if (isCleanSignal && loc != null) {
                        lat = loc.latitude
                        lon = loc.longitude
                        
                        val lastLat = prefs.getFloat("last_lat", 0f).toDouble()
                        val lastLon = prefs.getFloat("last_lon", 0f).toDouble()
                        
                        if (lastLat != 0.0) {
                             val results = FloatArray(1)
                             Location.distanceBetween(lastLat, lastLon, lat, lon, results)
                             val distMeters = results[0]
                             
                             // 3. Distance Threshold: Only record if moved > 200m (Parking lot size)
                             if (distMeters > 200) {
                                 val distKm = distMeters / 1000f
                                 val newTotal = prefs.getFloat("total_distance_km", 0f) + distKm
                                 prefs.edit().putFloat("total_distance_km", newTotal).apply()
                                 
                                 // Save History Point
                                 val locHistoryStr = prefs.getString("location_history", "[]")
                                 val locHistory = try { JSONArray(locHistoryStr) } catch(e: Exception) { JSONArray() }
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
                        } else {
                            // First Fix ever
                            prefs.edit().putFloat("last_lat", lat.toFloat()).putFloat("last_lon", lon.toFloat()).apply()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 3. Upload via CloudManager (Using "ALL" modules)
            CloudManager.uploadData(ctx, listOf("ALL"), null)

            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        } finally {
            notifManager.cancel(notifId)
        }
    }
}