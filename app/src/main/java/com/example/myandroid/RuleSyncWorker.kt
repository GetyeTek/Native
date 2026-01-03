package com.example.myandroid

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class RuleSyncWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val supabaseUrl = "https://xvldfsmxskhemkslsbym.supabase.co/rest/v1/monitoring_rules?select=*"
                val supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inh2bGRmc214c2toZW1rc2xzYnltIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjI2ODgxNzksImV4cCI6MjA3ODI2NDE3OX0.5arqrx8Tt7v-hpXpo_ncoK4IX8th9IibxAuv93SSoOU"

                val url = URL(supabaseUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("apikey", supabaseKey)
                conn.setRequestProperty("Authorization", "Bearer $supabaseKey")
                conn.connectTimeout = 15000
                conn.readTimeout = 15000

                if (conn.responseCode == 200) {
                    val rulesJson = conn.inputStream.bufferedReader().use { it.readText() }
                    
                    // Convert Array to Map for fast lookup
                    val rulesArray = JSONArray(rulesJson)
                    val rulesMap = JSONObject()
                    for (i in 0 until rulesArray.length()) {
                        val item = rulesArray.getJSONObject(i)
                        rulesMap.put(item.getString("package_name"), item)
                    }
                    
                    applicationContext.getSharedPreferences("app_stats", Context.MODE_PRIVATE)
                        .edit()
                        .putString("cached_rules", rulesMap.toString())
                        .apply()
                        
                    Result.success()
                } else {
                    // Server error, try again later
                    Result.retry()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Network error, try again when internet is back
                Result.retry()
            }
        }
    }
}