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
                0 -> "📱 Device"
                1 -> "⚡ Power"
                2 -> "💾 Storage"
                else -> "Info"
            }
        }.attach()
    }

    // --- ADAPTER ---
    class InfoPagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun getItemCount(): Int = 3
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
                0 -> getDeviceInfo()
                1 -> getPowerInfo(context)
                2 -> getStorageInfo(context)
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

        private fun getDeviceInfo(): Map<String, String> = mapOf(
            "MODEL" to Build.MODEL,
            "MANUFACTURER" to Build.MANUFACTURER.uppercase(),
            "ANDROID VERSION" to Build.VERSION.RELEASE,
            "SDK" to Build.VERSION.SDK_INT.toString(),
            "BOARD" to Build.BOARD,
            "BOOTLOADER" to Build.BOOTLOADER
        )

        private fun getPowerInfo(ctx: android.content.Context): Map<String, String> {
            val batteryStatus = ctx.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val level = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            val pct = if(level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else 0
            val status = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL
            
            return mapOf(
                "BATTERY LEVEL" to "$pct%",
                "STATUS" to if(isCharging) "⚡ Charging" else "🔋 Discharging",
                "HEALTH" to "Good (Sensors Hidden)",
                "VOLTAGE" to "${(batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, 0) ?: 0)} mV"
            )
        }

        private fun getStorageInfo(ctx: android.content.Context): Map<String, String> {
            val actManager = ctx.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            
            val totalRam = memInfo.totalMem / (1024 * 1024)
            val availRam = memInfo.availMem / (1024 * 1024)
            
            val dataDir = android.os.Environment.getDataDirectory()
            val stat = android.os.StatFs(dataDir.path)
            val totalStorage = (stat.blockCountLong * stat.blockSizeLong) / (1024 * 1024 * 1024)
            val freeStorage = (stat.availableBlocksLong * stat.blockSizeLong) / (1024 * 1024 * 1024)

            return mapOf(
                "RAM USAGE" to "${totalRam - availRam} MB / $totalRam MB",
                "RAM FREE" to "$availRam MB",
                "INTERNAL STORAGE" to "$totalStorage GB Total",
                "FREE SPACE" to "$freeStorage GB Available"
            )
        }
    }
}