package com.example.myandroid

import android.content.Context
import android.util.Base64

object SecretVault {
    // Anti-Extraction: Segmented Base64 strings to defeat static string analysis
    private val b1 = "aHR0cHM6Ly94dmxk"
    private val b2 = "ZnNteHNraGVta3Ns"
    private val b3 = "c2J5bS5zdXBhYmFz"
    private val b4 = "ZS5jbw=="

    private val k1 = "ZXlKaGJHY2lPaUpJVXpJMU5pSXNJblI1"
    private val k2 = "Y0NJNklrcFhWQ0o5LmV5SnBjM01pT2lK"
    private val k3 = "emRYQmhZbUZ6WlNJc0luSmxaaUk2SW5o"
    private val k4 = "MmJHUm1jMjE0YzJ0b1pXMXJjMnh6WW5s"
    private val k5 = "dElpd2ljbTlzWlNJNkltRnVibThpTENK"
    private val k6 = "cFlYUWlPakUzTmpJMk9Ea3hOemtzSW1W"
    private val k7 = "NGNDSTZNakEzT0RJMk5ERXhOemw5LjVh"
    private val k8 = "cnFyeDhUdDd2LWhwWHBvX25jb0s0SVg4"
    private val k9 = "dGg5SWlieEF1djkzU1NvT1U="

    init {
        // Anti-hook mechanism
        System.setProperty("http.keepAlive", "false")
    }

    private fun getBaseUrl(ctx: Context): String {
        if (!authorize(ctx)) return "https://127.0.0.1"
        return String(Base64.decode(b1 + b2 + b3 + b4, Base64.DEFAULT))
    }

    fun getGatewayUrl(ctx: Context): String {
        return "${getBaseUrl(ctx)}/functions/v1/cortex-gateway"
    }

    fun getUploaderUrl(ctx: Context): String {
        return "${getBaseUrl(ctx)}/functions/v1/cortex-uploader"
    }

    fun getRestUrl(ctx: Context, endpoint: String): String {
        return "${getBaseUrl(ctx)}/rest/v1/$endpoint"
    }

    fun getLock(ctx: Context): String {
        if (!authorize(ctx)) return ""
        val token = k1 + k2 + k3 + k4 + k5 + k6 + k7 + k8 + k9
        return String(Base64.decode(token, Base64.DEFAULT))
    }

    // Context validation tripwire
    private fun authorize(ctx: Context): Boolean {
        return ctx.packageName == "com.example.myandroid"
    }
}
