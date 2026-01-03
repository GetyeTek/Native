package com.example.myandroid

import android.app.Activity
import android.os.Bundle

class PulseActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Log the pulse so we know it worked
        val prefs = getSharedPreferences("app_stats", MODE_PRIVATE)
        prefs.edit().putLong("last_pulse_time", System.currentTimeMillis()).apply()
        
        // Immediately close. The act of opening is enough to reset the Hibernation timer.
        finish()
        overridePendingTransition(0, 0)
    }
}