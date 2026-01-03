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
import org.json.JSONObject
import java.util.Calendar

class MonitorService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val CHANNEL_ID = "persistent_stats"
    private val NOTIF_ID = 777
    private var overlayView: android.view.View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        startForeground(NOTIF_ID, buildNotification("Cortex Active"))
        
        // 1. Check if we were murdered previously
        checkResurrection()
        
        // 2. Start Logic
        startLoop()
        
        // 3. Deploy Shield
        createInvisibleOverlay()
        
        return START_STICKY
    }

    private fun startLoop() {
        scope.launch {
            while (isActive) {
                // A. Midnight Reset
                TimeManager.checkDailyReset(applicationContext)
                
                // B. Save Heartbeat (Proof of Life)
                getSharedPreferences("app_stats", Context.MODE_PRIVATE)
                    .edit().putLong("last_heartbeat", System.currentTimeMillis()).apply()

                // C. Anti-Hibernation Pulse (Every 3 days)
                checkPulse()

                // D. Update Notification UI
                val time = getScreenTime()
                val notification = buildNotification(time)
                val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                mgr.notify(NOTIF_ID, notification)
                
                delay(60000)
            }
        }
    }

    private fun checkResurrection() {
        val prefs = getSharedPreferences("app_stats", Context.MODE_PRIVATE)
        val lastHeartbeat = prefs.getLong("last_heartbeat", 0L)
        val now = System.currentTimeMillis()
        
        // If > 5 mins gap, we were killed
        if (lastHeartbeat > 0 && (now - lastHeartbeat) > 300_000) {
            val gapMins = (now - lastHeartbeat) / 60000
            val historyStr = prefs.getString("app_health", "{}")
            val json = try { JSONObject(historyStr) } catch(e: Exception) { JSONObject() }
            
            json.put("last_resurrection", "Recovered after ${gapMins}m blackout at ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date())}")
            json.put("kill_count", json.optInt("kill_count", 0) + 1)
            
            prefs.edit().putString("app_health", json.toString()).apply()
        }
    }

    private fun checkPulse() {
        val prefs = getSharedPreferences("app_stats", Context.MODE_PRIVATE)
        val lastPulse = prefs.getLong("last_pulse_time", 0L)
        val now = System.currentTimeMillis()
        
        // 72 hours
        if (now - lastPulse > 259200000) {
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            // Only run if Screen is OFF (Stealth)
            if (!pm.isInteractive) {
                try {
                    val i = Intent(this, PulseActivity::class.java)
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(i)
                } catch (e: Exception) { }
            }
        }
    }

    private fun createInvisibleOverlay() {
        try {
            if (android.provider.Settings.canDrawOverlays(this)) {
                val wm = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
                if (overlayView == null) {
                    overlayView = android.view.View(this)
                    val params = android.view.WindowManager.LayoutParams(
                        1, 1,
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) 
                            android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
                        else android.view.WindowManager.LayoutParams.TYPE_PHONE,
                        android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                        android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                        android.graphics.PixelFormat.TRANSLUCENT
                    )
                    params.gravity = android.view.Gravity.TOP or android.view.Gravity.START
                    wm.addView(overlayView, params)
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
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
            if (overlayView != null) {
                val wm = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
                wm.removeView(overlayView)
            }
        } catch(e: Exception) {}

        val broadcastIntent = Intent(this, BootReceiver::class.java)
        broadcastIntent.action = "com.example.myandroid.RESTART_SERVICE"
        sendBroadcast(broadcastIntent)
        
        job.cancel()
    }
}