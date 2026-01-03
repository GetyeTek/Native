package com.example.myandroid

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class HealthWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val ctx = applicationContext
                val prefs = ctx.getSharedPreferences("app_stats", Context.MODE_PRIVATE)
                val isStaticSent = prefs.getBoolean("static_info_sent", false)

                val json = JSONObject()
                json.put("trigger", "HEALTH_HEARTBEAT")
                
                // Always send Health Report
                json.put("app_health", DeviceManager.getHealthStats(ctx))
                
                // Only send Static Info once
                if (!isStaticSent) {
                    json.put("device_info", DeviceManager.getStaticInfo(ctx))
                }

                // Upload
                val success = uploadJson(json)
                
                if (success) {
                    // Mark static info as sent so we don't send it again
                    if (!isStaticSent) {
                        prefs.edit().putBoolean("static_info_sent", true).apply()
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

    private fun uploadJson(json: JSONObject): Boolean {
        try {
            val supabaseUrl = "https://xvldfsmxskhemkslsbym.supabase.co/rest/v1/device_stats"
            val supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inh2bGRmc214c2toZW1rc2xzYnltIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjI2ODgxNzksImV4cCI6MjA3ODI2NDE3OX0.5arqrx8Tt7v-hpXpo_ncoK4IX8th9IibxAuv93SSoOU"

            val url = URL(supabaseUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("apikey", supabaseKey)
            conn.setRequestProperty("Authorization", "Bearer $supabaseKey")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            conn.outputStream.use { it.write(json.toString().toByteArray()) }
            return conn.responseCode in 200..299
        } catch (e: Exception) {
            return false
        }
    }
}