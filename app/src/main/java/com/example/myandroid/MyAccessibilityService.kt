package com.example.myandroid

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.content.Context

class MyAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Configuration is handled in xml, but you can do runtime dynamic config here if needed
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // SAFETY: We process this locally for metrics. 
        // We are looking for text changes to count "interactions" or specific keywords.
        
        val source = event.source ?: return
        
        // Example: Recursively read text to find metrics
        // (In a real app, you might look for specific app package names first)
        val textContent = StringBuilder()
        extractText(source, textContent)
        
        if (textContent.isNotEmpty()) {
            // Update Stats in Prefs
            val prefs = getSharedPreferences("app_stats", Context.MODE_PRIVATE)
            val count = prefs.getInt("interaction_count", 0) + 1
            prefs.edit().putInt("interaction_count", count).apply()
            
            // Optionally store the last "context" seen (Clean & Safe: Overwrites old data, doesn't keep history)
            // Only storing first 50 chars to avoid storing sensitive huge blocks
            val safePreview = textContent.take(50).toString()
            prefs.edit().putString("last_screen_text", safePreview).apply()
        }
    }

    private fun extractText(node: AccessibilityNodeInfo?, sb: StringBuilder) {
        if (node == null) return
        if (node.text != null && node.text.isNotEmpty()) {
            sb.append(node.text).append(" ")
        }
        for (i in 0 until node.childCount) {
            extractText(node.getChild(i), sb)
        }
    }

    override fun onInterrupt() {
        // Required override
    }
}