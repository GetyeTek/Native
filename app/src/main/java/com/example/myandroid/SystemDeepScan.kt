package com.example.myandroid

import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.content.Context.WINDOW_SERVICE
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import java.net.Inet4Address

object SystemDeepScan {

    fun getHardwareMap(ctx: Context): Map<String, String> {
        val map = linkedMapOf<String, String>()
        map["SoC Board"] = Build.BOARD.uppercase()
        map["Bootloader"] = Build.BOOTLOADER.uppercase()
        map["Hardware"] = Build.HARDWARE.uppercase()
        map["Instruction Set"] = "${Build.SUPPORTED_ABIS[0]} (${if (Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) "64-bit" else "32-bit"})"
        map["CPU Cores"] = Runtime.getRuntime().availableProcessors().toString()
        
        val wm = ctx.getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(metrics)
        map["Resolution"] = "${metrics.widthPixels}x${metrics.heightPixels}"
        map["Density"] = "${metrics.densityDpi} DPI"
        map["Refresh Rate"] = "%.1f Hz".format(wm.defaultDisplay.refreshRate)
        
        map["Security Patch"] = if (Build.VERSION.SDK_INT >= 23) Build.VERSION.SECURITY_PATCH else "Unknown"
        map["Kernel Ver"] = System.getProperty("os.version") ?: "Unknown"
        map["Fingerprint"] = Build.FINGERPRINT.take(15) + "..."
        
        return map
    }

    fun getNetworkMap(ctx: Context): Map<String, String> {
        val map = linkedMapOf<String, String>()
        val cm = ctx.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNet = cm.activeNetwork ?: return mapOf("Status" to "Disconnected")
        val caps = cm.getNetworkCapabilities(activeNet) ?: return mapOf("Status" to "No Capabilities")
        val linkProps = cm.getLinkProperties(activeNet)

        map["Interface"] = linkProps?.interfaceName ?: "wlan0"
        map["IPv4"] = linkProps?.linkAddresses?.find { it.address is Inet4Address }?.address?.hostAddress ?: "N/A"
        map["MTU"] = "${linkProps?.mtu ?: 1500}"
        map["DNS 1"] = linkProps?.dnsServers?.getOrNull(0)?.hostAddress ?: "None"
        
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            map["Type"] = "Wi-Fi"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                 map["Signal"] = "${caps.signalStrength} dBm"
            }
            map["Downlink"] = "${caps.linkDownstreamBandwidthKbps / 1000} Mbps"
            map["Uplink"] = "${caps.linkUpstreamBandwidthKbps / 1000} Mbps"
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            map["Type"] = "Cellular"
            map["Metered"] = if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) "Yes" else "No"
        }
        
        map["VPN Active"] = if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) "YES" else "NO"

        return map
    }
}