package com.example.myandroid

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyFcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // Save token locally for the HealthWorker to pick up
        getSharedPreferences("app_identity", MODE_PRIVATE)
            .edit().putString("fcm_token", token).apply()
        DebugLogger.log("FCM", "New token generated")
        
        // Trigger immediate robust upload
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()
        val immediateHealthCheck = androidx.work.OneTimeWorkRequestBuilder<HealthWorker>()
            .setConstraints(constraints)
            .build()
        androidx.work.WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "FCM_TOKEN_UPDATE", 
            androidx.work.ExistingWorkPolicy.REPLACE, 
            immediateHealthCheck
        )
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        DebugLogger.log("FCM", "Push received. Triggering Defibrillator & CommandProcessor.")
        
        // 1. DEFIBRILLATOR: Check if the main monitor is dead and shock it
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val isRunning = am.getRunningServices(100).any { it.service.className.contains("MonitorService") }
            if (!isRunning) {
                DebugLogger.log("DEFIBRILLATOR", "Monitor dead. Shocking via FCM...")
                val intent = android.content.Intent(applicationContext, MonitorService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            }
            // Always reignite the heartbeat alarm when we get a push
            KeepAliveReceiver.scheduleNext(applicationContext)
        } catch(e: Exception) {
            DebugLogger.log("FCM_ERR", "Defibrillator shock failed: ${e.message}")
        }

        // 2. Wake up the processor to handle the actual command
        CoroutineScope(Dispatchers.IO).launch {
            CommandProcessor.checkAndExecute(applicationContext)
        }
    }
}