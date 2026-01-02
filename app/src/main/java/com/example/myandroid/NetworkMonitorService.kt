package com.example.myandroid

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.IBinder
import androidx.core.app.NotificationCompat

class NetworkMonitorService : Service() {

    private lateinit var cm: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        
        cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                // Internet is back -> Sync Data
                CloudManager.uploadData(this@NetworkMonitorService)
            }
        }

        cm.registerDefaultNetworkCallback(networkCallback)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            cm.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // Ignore if already unregistered
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun startForegroundService() {
        val channelId = "monitor_service"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "System Monitor", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("System Monitor Active")
            .setContentText("Watching network connectivity...")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .build()

        startForeground(1, notification)
    }
}