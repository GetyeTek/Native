package com.example.myandroid

import android.os.Bundle
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.DisplayMetrics

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Force Dark Theme colors programmatically for this 'Hacker' vibe
        window.statusBarColor = 0xFF121212.toInt()
        window.navigationBarColor = 0xFF121212.toInt()
        super.onCreate(savedInstanceState)

        // 1. Root Layout
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(0xFF121212.toInt()) // Deep Dark Background
        }

        // 2. Tab Layout & ViewPager
        val tabLayout = TabLayout(this).apply {
            setBackgroundColor(0xFF1E1E1E.toInt())
            setTabTextColors(0xFF888888.toInt(), 0xFFFFFFFF.toInt())
            setSelectedTabIndicatorColor(0xFF00FF00.toInt()) // Cyber Green Indicator
        }
        val viewPager = ViewPager2(this).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        root.addView(tabLayout)
        root.addView(viewPager)
        setContentView(root)

        // 3. Set Adapter
        viewPager.adapter = InfoPagerAdapter(this)

        // 4. Link Tabs
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "🤖 OS"
                1 -> "⚙️ Hard"
                2 -> "🖥️ Disp"
                3 -> "📡 Net"
                4 -> "🧭 Sens"
                5 -> "✨ Feat"
                else -> "Info"
            }
        }.attach()
    }

    // --- ADAPTER ---
    class InfoPagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun getItemCount(): Int = 6
        override fun createFragment(position: Int): Fragment = InfoFragment.newInstance(position)
    }

    // --- FRAGMENT ---
    class InfoFragment : Fragment() {
        companion object {
            fun newInstance(pos: Int) = InfoFragment().apply {
                arguments = Bundle().apply { putInt("pos", pos) }
            }
        }

        override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            val context = requireContext()
            val pos = arguments?.getInt("pos") ?: 0
            
            val scroll = ScrollView(context).apply {
                isFillViewport = true
                setBackgroundColor(0xFF121212.toInt()) // Dark bg
            }
            
            val content = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(40, 40, 40, 40)
            }

            val data = when (pos) {
                0 -> getOsInfo()
                1 -> getHardwareInfo(context)
                2 -> getDisplayInfo(context)
                3 -> getNetworkInfo(context)
                4 -> getSensorInfo(context)
                5 -> getFeatureInfo(context)
                else -> emptyMap()
            }

            data.forEach { (k, v) ->
                content.addView(createCard(context, k, v))
            }

            scroll.addView(content)
            return scroll
        }

        private fun createCard(ctx: android.content.Context, label: String, value: String): View {
            return LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(50, 50, 50, 50)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, 0, 30)
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    color = android.content.res.ColorStateList.valueOf(0xFF1E1E1E.toInt())
                    cornerRadius = 24f
                    setStroke(2, 0xFF333333.toInt())
                }

                addView(TextView(ctx).apply {
                    text = label
                    textSize = 12f
                    setTextColor(0xFFAAAAAA.toInt())
                    letterSpacing = 0.1f
                })
                addView(TextView(ctx).apply {
                    text = value
                    textSize = 18f
                    setTextColor(0xFFFFFFFF.toInt())
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding(0, 15, 0, 0)
                })
            }
        }

        private fun getOsInfo(): Map<String, String> = mapOf(
            "ANDROID VER" to Build.VERSION.RELEASE,
            "SDK API" to Build.VERSION.SDK_INT.toString(),
            "SECURITY PATCH" to (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else "Unknown"),
            "KERNEL" to System.getProperty("os.version").toString(),
            "BUILD ID" to Build.ID,
            "BOOTLOADER" to Build.BOOTLOADER,
            "FINGERPRINT" to Build.FINGERPRINT.take(20) + "...",
            "BUILD HOST" to Build.HOST,
            "BUILD USER" to Build.USER,
            "BUILD TIME" to java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date(Build.TIME))
        )

        private fun getHardwareInfo(ctx: Context): Map<String, String> {
            // Battery
            val batt = ctx.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val level = batt?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, 0) ?: 0
            val volt = batt?.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
            val temp = batt?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            
            // RAM
            val actManager = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            
            // CPU
            val cores = Runtime.getRuntime().availableProcessors()
            
            return mapOf(
                "MANUFACTURER" to Build.MANUFACTURER.uppercase(),
                "MODEL" to Build.MODEL,
                "PRODUCT" to Build.PRODUCT,
                "BOARD" to Build.BOARD,
                "CPU CORES" to "$cores Cores",
                "SUPPORTED ABIS" to Build.SUPPORTED_ABIS.joinToString(", "),
                "RAM TOTAL" to "${memInfo.totalMem / (1024*1024)} MB",
                "RAM AVAIL" to "${memInfo.availMem / (1024*1024)} MB",
                "BATTERY LEVEL" to "$level%",
                "VOLTAGE" to "$volt mV",
                "TEMP" to "${temp/10.0} °C"
            )
        }

        private fun getDisplayInfo(ctx: Context): Map<String, String> {
            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            val dm = DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(dm)
            val refresh = wm.defaultDisplay.refreshRate
            val hdr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && wm.defaultDisplay.isHdr) "Yes" else "No"
            
            return mapOf(
                "RESOLUTION" to "${dm.widthPixels} x ${dm.heightPixels}",
                "DPI (DENSITY)" to "${dm.densityDpi} dpi",
                "REFRESH RATE" to "${refresh.toInt()} Hz",
                "SCALE FACTOR" to "${dm.density}x",
                "HDR SUPPORT" to hdr,
                "ORIENTATION" to if (ctx.resources.configuration.orientation == 1) "Portrait" else "Landscape"
            )
        }

        private fun getNetworkInfo(ctx: Context): Map<String, String> {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(net)
            val link = cm.getLinkProperties(net)
            
            val transport = when {
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WiFi"
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular Data"
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true -> "VPN"
                else -> "No Connection"
            }
            
            return mapOf(
                "STATUS" to if(net != null) "Connected" else "Disconnected",
                "TYPE" to transport,
                "DOWN SPEED" to "${(caps?.linkDownstreamBandwidthKbps ?: 0) / 1000} Mbps",
                "UP SPEED" to "${(caps?.linkUpstreamBandwidthKbps ?: 0) / 1000} Mbps",
                "METERED" to if(caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false) "Yes" else "No",
                "LOCAL IP" to (link?.linkAddresses?.firstOrNull()?.address?.hostAddress ?: "Unknown")
            )
        }

        private fun getSensorInfo(ctx: Context): Map<String, String> {
            val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val list = sm.getSensorList(Sensor.TYPE_ALL)
            val map = mutableMapOf<String, String>()
            
            map["TOTAL SENSORS"] = "${list.size} Found"
            list.take(15).forEachIndexed { i, s ->
                map["#${i+1} ${s.name}"] = "Vendor: ${s.vendor}\nPower: ${s.power} mA"
            }
            return map
        }

        private fun getFeatureInfo(ctx: Context): Map<String, String> {
            val pm = ctx.packageManager
            return mapOf(
                "NFC" to pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_NFC).toString(),
                "BLUETOOTH LE" to pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_BLUETOOTH_LE).toString(),
                "WIFI DIRECT" to pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_WIFI_DIRECT).toString(),
                "USB HOST" to pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_USB_HOST).toString(),
                "FINGERPRINT" to pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_FINGERPRINT).toString(),
                "FACE RECOG" to pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_FACE).toString(),
                "VR MODE" to pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_VR_MODE_HIGH_PERFORMANCE).toString(),
                "PIP" to pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE).toString()
            )
        }
    }
}