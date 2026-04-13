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

class SyncWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private val isSyncing = java.util.concurrent.atomic.AtomicBoolean(false)
    }

    override suspend fun doWork(): Result {
        if (!isSyncing.compareAndSet(false, true)) {
            DebugLogger.log("SYNC_WORKER", "Sync skipped (already in progress)")
            return Result.success()
        }

        try {
            val ctx = applicationContext
            val prefs = ctx.getSharedPreferences("app_stats", Context.MODE_PRIVATE)
            
            // --- LOCATION LOGIC (RAW DATA) ---
            var lat = 0.0
            var lon = 0.0
            val hasPerm = androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val canCollectLoc = ConfigManager.canCollect(ctx, "location")
            
            if (hasPerm && canCollectLoc) {
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
                        
                        // 2. RAW HISTORY (STREAM)
                        val point = JSONObject().apply {
                            put("lat", lat)
                            put("lon", lon)
                            put("acc", acc)
                            put("ts", System.currentTimeMillis())
                        }
                        
                        DumpManager.appendLog("LOC", point)
                        
                        // Update stats only
                        prefs.edit()
                           .putFloat("last_lat", lat.toFloat())
                           .putFloat("last_lon", lon.toFloat())
                           .putString("last_location_coords", "${String.format(java.util.Locale.US, "%.4f", lat)}, ${String.format(java.util.Locale.US, "%.4f", lon)}")
                           .apply()
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }

            // --- SURVIVOR PROTOCOL: Stream Offline Chunks ---
            val offlineLogs = DumpManager.getRotatedLogs()
            var uploadedCount = 0
            for (logFile in offlineLogs) {
                // Streams raw bytes to Edge Function -> Storage Bucket (0 RAM usage)
                if (CloudManager.uploadFile(ctx, logFile, "OFFLINE_STREAM")) {
                    logFile.delete()
                    uploadedCount++
                }
            }
            if (uploadedCount > 0) DebugLogger.log("SYNC_WORKER", "Uploaded $uploadedCount offline chunks.")

            CloudManager.uploadData(ctx, listOf("ALL"))
            DebugLogger.log("SYNC_WORKER", "Periodic Sync completed successfully")
            return Result.success()
        } catch (e: Exception) {
            DebugLogger.log("SYNC_WORKER_ERR", "Failed: ${e.message}")
            return Result.retry()
        } finally {
            isSyncing.set(false)
        }
    }
}