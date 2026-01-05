package com.example.myandroid

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.Calendar

class MonitorService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val CHANNEL_ID = "persistent_stats"
    private val NOTIF_ID = 777
    // OPTIMIZATION: Overlay removed to prevent CPU wake-locks and heat.

    // --- SMART UPDATE RECEIVER ---
    // Updates UI only when user is actually looking at the screen.
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> {
                    // Wake up: Update stats immediately
                    val time = getScreenTime()
                    val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    mgr.notify(NOTIF_ID, buildNotification(time))
                    checkResurrection()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    // Sleep: Do absolutely nothing to save battery
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        startForeground(NOTIF_ID, buildNotification("Cortex Active"))
        
        // 1. Diagnostics
        checkResurrection()
        
        // 2. Start Logic Loop
        startLoop()
        
        // 3. Register Smart Shield Triggers
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenStateReceiver, filter)

        // 4. Initial State Check
        // Removed overlay toggle to allow Deep Doze
        
        return START_STICKY
    }



    private fun startLoop() {
        // OPTIMIZED LOOP: No 60s wake-lock.
        // Logic is now event-driven by ScreenReceiver and WorkManager.
        scope.launch {
            // We still check Pulse/Reset once on startup
            TimeManager.checkDailyReset(applicationContext)
            checkPulse()
            
            // Daily Dump Check
            DumpManager.createDailyDump(applicationContext)
        }
    }

    private fun checkResurrection() {
        val prefs = getSharedPreferences("app_stats", Context.MODE_PRIVATE)
        val lastHeartbeat = prefs.getLong("last_heartbeat", 0L)
        val now = System.currentTimeMillis()
        
        if (lastHeartbeat > 0 && (now - lastHeartbeat) > 300_000) {
            val gapMins = (now - lastHeartbeat) / 60000
            val historyStr = prefs.getString("app_health", "{}")
            val json = try { JSONObject(historyStr) } catch(e: Exception) { JSONObject() }
            
            val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date())
            json.put("last_resurrection", "Recovered after ${gapMins}m blackout at $time")
            json.put("kill_count", json.optInt("kill_count", 0) + 1)
            
            DebugLogger.log("CRITICAL", "SYSTEM KILLED ME! Recovered after ${gapMins}m blackout.")
            prefs.edit().putString("app_health", json.toString()).apply()
        }
    }

    private fun checkPulse() {
        val prefs = getSharedPreferences("app_stats", Context.MODE_PRIVATE)
        val lastPulse = prefs.getLong("last_pulse_time", 0L)
        val now = System.currentTimeMillis()
        
        if (now - lastPulse > 259200000) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isInteractive) {
                try {
                    val i = Intent(this, PulseActivity::class.java)
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(i)
                } catch (e: Exception) { }
            }
        }
    }

    private fun getScreenTime(): String {
        return try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val endTime = System.currentTimeMillis()
            val startTime = TimeManager.getStartOfDay()
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, startTime, endTime)
            val totalMillis = stats.filter { it.lastTimeUsed >= startTime }.sumOf { it.totalTimeInForeground }
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
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(null)
            .build()
    }

    private fun createChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            val chan = NotificationChannel(CHANNEL_ID, "Daily Monitor", NotificationManager.IMPORTANCE_LOW)
            chan.setShowBadge(false)
            mgr.createNotificationChannel(chan)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (e: Exception) {}



        val broadcastIntent = Intent(this, BootReceiver::class.java)
        broadcastIntent.action = "com.example.myandroid.RESTART_SERVICE"
        sendBroadcast(broadcastIntent)
        
        job.cancel()
    }
}