package com.example.myandroid

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class BeaconService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private var intervalSeconds = 60L // Default safety

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val freqStr = intent?.getStringExtra("frequency") ?: "0"
        val freq = freqStr.toLongOrNull() ?: 0L
        
        // Safety: If < 5 seconds, force 5 to prevent flooding
        intervalSeconds = if (freq < 5) 5 else freq

        startForeground(9999, createNotification())
        
        scope.launch {
            DebugLogger.log("BEACON", "Starting loop every ${intervalSeconds}s")
            while (isActive) {
                CloudManager.sendPing(applicationContext, "Live Beacon (${intervalSeconds}s)")
                delay(intervalSeconds * 1000)
            }
        }

        return START_STICKY
    }

    private fun createNotification(): Notification {
        val channelId = "beacon_channel"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId, "Live Connection", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(chan)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Uplink Active")
            .setContentText("Transmitting live status...")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
        DebugLogger.log("BEACON", "Service Stopped")
    }
}