package com.example.myandroid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        DebugLogger.log("SYSTEM_EVENT", "Triggered by: $action")

        // THE DEFIBRILLATOR LOGIC
        try {
            if (!MonitorService.isRunning) {
                DebugLogger.log("DEFIBRILLATOR", "System event resurrection for dead service.")
                val i = Intent(context, MonitorService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(i)
                } else {
                    context.startService(i)
                }
            }
            
            // Ensure the next alarm heartbeat is also scheduled
            KeepAliveReceiver.scheduleNext(context)
            
        } catch (e: Exception) {
            DebugLogger.log("DEFIB_ERR", "Failed resurrection: ${e.message}")
        }
    }
}