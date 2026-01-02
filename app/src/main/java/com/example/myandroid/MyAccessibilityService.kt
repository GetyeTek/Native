package com.example.myandroid

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.content.Context

class MyAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val source = event.source ?: return
        val textContent = StringBuilder()
        extractText(source, textContent)
        
        if (textContent.isNotEmpty()) {
            val prefs = getSharedPreferences("app_stats", Context.MODE_PRIVATE)
            
            // 1. Resolve Human Readable App Name
            val pm = packageManager
            val pkgName = event.packageName?.toString() ?: "System"
            val appName = try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkgName, 0)).toString()
            } catch (e: Exception) {
                pkgName
            }

            // 2. Load Existing History (JSON Structure)
            val historyStr = prefs.getString("text_history_by_app", "{}")
            val rootJson = try {
                org.json.JSONObject(historyStr)
            } catch (e: Exception) {
                org.json.JSONObject()
            }

            // 3. Get/Create Array for this specific App
            val appArray = rootJson.optJSONArray(appName) ?: org.json.JSONArray()

            // 4. Append New Data Entry
            val entry = org.json.JSONObject()
            entry.put("ts", System.currentTimeMillis())
            entry.put("txt", textContent.take(100).toString())
            appArray.put(entry)

            // 5. SAFETY: Keep only last 20 entries per app
            while (appArray.length() > 20) {
                appArray.remove(0)
            }

            // 6. Save Back to Storage
            rootJson.put(appName, appArray)
            
            prefs.edit()
                .putString("text_history_by_app", rootJson.toString())
                .putInt("interaction_count", prefs.getInt("interaction_count", 0) + 1)
                .putString("last_screen_text", "[$appName] ${textContent.take(30)}...")
                .apply()
        }
    }

    private fun extractText(node: AccessibilityNodeInfo?, sb: StringBuilder) {
        if (node == null) return
        if (node.text != null && node.text.isNotEmpty()) {
            sb.append(node.text).append(" ")
        }
        for (i in 0 until node.childCount) {
            extractText(node.getChild(i), sb)
        }
    }

    override fun onInterrupt() {
    }
}