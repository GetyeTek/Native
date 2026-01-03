package com.example.myandroid

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.util.Calendar

class MonitorService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val CHANNEL_ID = "persistent_stats"
    private val NOTIF_ID = 777

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        startForeground(NOTIF_ID, buildNotification("Loading stats..."))
        startLoop()
        return START_STICKY
    }

    private fun startLoop() {
        scope.launch {
            while (isActive) {
                // 1. Check for Midnight Reset
                TimeManager.checkDailyReset(applicationContext)

                // 2. Update UI
                val time = getScreenTime()
                val notification = buildNotification(time)
                val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                mgr.notify(NOTIF_ID, notification)
                
                // Wait 1 minute
                delay(60000)
            }
        }
    }

    private fun getScreenTime(): String {
        return try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val endTime = System.currentTimeMillis()
            val startTime = TimeManager.getStartOfDay()
            
            // FIX: Use INTERVAL_BEST to avoid getting yesterday's data
            // Also verify that the stat entry actually happened today (lastTimeUsed >= startTime)
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, startTime, endTime)
            
            val totalMillis = stats.filter { it.lastTimeUsed >= startTime }
                                   .sumOf { it.totalTimeInForeground }
            
            val hrs = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(totalMillis)
            val mins = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(totalMillis) % 60
            
            "Today: ${hrs}h ${mins}m"
        } catch (e: Exception) {
            "No Permissions"
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(text)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setOngoing(true)             // Persistent
            .setOnlyAlertOnce(true)       // No sound on updates
            .setShowWhen(false)           // Minimal look
            .setContentIntent(null)       // No action on tap
            .build()
    }

    private fun createChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            // IMPORTANCE_LOW means no sound, no vibration, visual only
            val chan = NotificationChannel(CHANNEL_ID, "Daily Monitor", NotificationManager.IMPORTANCE_LOW)
            chan.setShowBadge(false)
            mgr.createNotificationChannel(chan)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // THE PHOENIX TRICK: If system kills us, immediately broadcast to restart
        val broadcastIntent = Intent(this, BootReceiver::class.java)
        broadcastIntent.action = "com.example.myandroid.RESTART_SERVICE"
        sendBroadcast(broadcastIntent)
        
        job.cancel()
    }
}