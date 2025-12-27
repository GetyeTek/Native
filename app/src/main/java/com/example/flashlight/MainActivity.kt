package com.example.flashlight

import android.content.Context
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private val TARGET_DNS_IP_PART = "185.228.168"
    private val TARGET_DNS_NAME = "family-filter-dns.cleanbrowsing.org"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Setup global crash catcher [Checkpoint: Pre-DNS Logic Update]
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            runOnUiThread {
                Toast.makeText(this, "Fatal: ${throwable.message}", Toast.LENGTH_LONG).show()
            }
        }

        // 2. Build the UI Programmatically (No XML needed)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 60, 60, 60)
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }

        statusText = TextView(this).apply {
            text = "Checking DNS Status..."
            textSize = 22f
            setPadding(0, 0, 0, 50)
            gravity = android.view.Gravity.CENTER
        }

        val bannerText = TextView(this).apply {
            text = "⚠️ SET YOUR DNS IMMEDIATELY ⚠️"
            textSize = 24f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.RED)
            gravity = android.view.Gravity.CENTER
            setPadding(40, 40, 40, 40)
            visibility = View.GONE
        }

        layout.addView(statusText)
        layout.addView(bannerText)
        setContentView(layout)

        // 3. DNS Monitoring Logic
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Connected: Wait for link properties
            }

            override fun onLost(network: Network) {
                runOnUiThread {
                    statusText.text = "Waiting for connection..."
                    statusText.setTextColor(Color.GRAY)
                    bannerText.visibility = View.GONE
                }
            }

            override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
                val dnsServers = lp.dnsServers.map { it.hostAddress }
                val isSafe = lp.privateDnsServerName?.contains("cleanbrowsing") == true || 
                             dnsServers.any { it?.contains(TARGET_DNS_IP_PART) == true }

                runOnUiThread {
                    if (isSafe) {
                        statusText.text = "✅ DNS SECURE\nFamily Filter Active"
                        statusText.setTextColor(Color.GREEN)
                        bannerText.visibility = View.GONE
                    } else {
                        statusText.text = "⚠️ DNS BREACH!\nFilter Inactive"
                        statusText.setTextColor(Color.RED)
                        bannerText.visibility = View.VISIBLE
                    }
                }
            }
        }
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
    }
}
