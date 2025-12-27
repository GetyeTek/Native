package com.example.flashlight

import android.content.Context
import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
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

        val flashButton = ToggleButton(this).apply {
            textOn = "Flashlight ON"
            textOff = "Flashlight OFF"
            textSize = 24f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        layout.addView(statusText)
        layout.addView(flashButton)
        setContentView(layout)

        // 3. DNS Monitoring Logic
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
                val dnsServers = lp.dnsServers.map { it.hostAddress }
                
                // Check for CleanBrowsing via Private DNS name or IP range
                val isSafe = lp.privateDnsServerName?.contains("cleanbrowsing") == true || 
                             dnsServers.any { it?.contains(TARGET_DNS_IP_PART) == true }

                runOnUiThread {
                    if (isSafe) {
                        statusText.text = "✅ DNS SECURE\nFamily Filter Active"
                        statusText.setTextColor(Color.GREEN)
                    } else {
                        statusText.text = "⚠️ DNS BREACH!\nFilter Inactive"
                        statusText.setTextColor(Color.RED)
                    }
                }
            }
        }
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

        // 4. Working Flashlight Logic
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        flashButton.setOnCheckedChangeListener { _, isChecked ->
            try {
                val list = cameraManager.cameraIdList
                var flashId: String? = null

                for (id in list) {
                    val chars = cameraManager.getCameraCharacteristics(id)
                    if (chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true) {
                        flashId = id
                        break
                    }
                }

                if (flashId != null) {
                    cameraManager.setTorchMode(flashId, isChecked)
                } else {
                    Toast.makeText(this, "No flash unit found!", Toast.LENGTH_SHORT).show()
                    flashButton.isChecked = false
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                flashButton.isChecked = false
            }
        }
    }
}
