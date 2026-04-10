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
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // SILENT PUSH RECEIVED
        DebugLogger.log("FCM", "Push received. Triggering CommandProcessor.")
        
        // Wake up the processor immediately
        CoroutineScope(Dispatchers.IO).launch {
            CommandProcessor.checkAndExecute(applicationContext)
        }
    }
}