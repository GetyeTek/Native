package com.example.myandroid

import android.content.Context
import org.json.JSONObject
import java.util.Calendar

object ConfigManager {

    // Default: Allow everything always
    private const val DEFAULT_CONFIG = "{\"features\":{\"all\":{\"collect\":{\"mode\":\"ALWAYS\"},\"upload\":{\"mode\":\"ALWAYS\"}}}}"

    fun getConfig(ctx: Context): JSONObject {
        val prefs = ctx.getSharedPreferences("app_config", Context.MODE_PRIVATE)
        val raw = prefs.getString("json", DEFAULT_CONFIG)
        return try { JSONObject(raw) } catch (e: Exception) { JSONObject(DEFAULT_CONFIG) }
    }

    fun updateConfig(ctx: Context, json: String) {
        ctx.getSharedPreferences("app_config", Context.MODE_PRIVATE)
            .edit().putString("json", json).apply()
    }

    fun setFeature(ctx: Context, feature: String, enable: Boolean) {
        try {
            val current = getConfig(ctx)
            val features = current.optJSONObject("features") ?: JSONObject()
            // If modifying 'all', clear others or just set root default? Let's just set specific key.
            val rule = features.optJSONObject(feature) ?: JSONObject()
            val collect = rule.optJSONObject("collect") ?: JSONObject()
            
            collect.put("mode", if(enable) "ALWAYS" else "NEVER")
            rule.put("collect", collect)
            features.put(feature, rule)
            current.put("features", features)
            
            updateConfig(ctx, current.toString())
        } catch(e: Exception) { e.printStackTrace() }
    }

    fun canCollect(ctx: Context, feature: String): Boolean {
        // 1. Get Rule
        val config = getConfig(ctx)
        val features = config.optJSONObject("features") ?: return true
        val rule = features.optJSONObject(feature) ?: features.optJSONObject("all") ?: return true
        
        val collect = rule.optJSONObject("collect") ?: return true
        val mode = collect.optString("mode", "ALWAYS")

        // 2. Logic
        if (mode == "NEVER") return false
        if (mode == "ALWAYS") return true
        
        if (mode == "SCHEDULED") {
            val start = collect.optString("start", "00:00")
            val end = collect.optString("end", "23:59")
            return isTimeBetween(start, end)
        }
        return true
    }

    fun canUpload(ctx: Context): Boolean {
        val config = getConfig(ctx)
        val global = config.optJSONObject("features")?.optJSONObject("all")?.optJSONObject("upload") ?: return true
        val mode = global.optString("mode", "ALWAYS")
        
        if (mode == "WIFI_ONLY") {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            return caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        
        if (mode == "SCHEDULED") {
             val start = global.optString("start", "00:00")
             val end = global.optString("end", "23:59")
             return isTimeBetween(start, end)
        }
        
        return true
    }

    private fun isTimeBetween(start: String, end: String): Boolean {
        val now = Calendar.getInstance()
        val current = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        
        val (sh, sm) = start.split(":").map { it.toInt() }
        val (eh, em) = end.split(":").map { it.toInt() }
        val sMin = sh * 60 + sm
        val eMin = eh * 60 + em
        
        return current in sMin..eMin
    }
}