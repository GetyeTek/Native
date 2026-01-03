package com.example.myandroid

import kotlinx.coroutines.*
import android.animation.ValueAnimator
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.RadialGradient
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorManager
import android.provider.Settings
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.pm.PackageManager
import java.util.Calendar
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import org.json.JSONArray
import android.os.Handler
import android.os.Looper
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import java.text.DecimalFormat

class MainActivity : AppCompatActivity() {

    // --- COLOR PALETTE ---
    private val COLOR_BG = 0xFF050505.toInt()
    // CSS: rgba(30, 30, 35, 0.6)
    private val COLOR_GLASS = 0x991E1E23.toInt()
    private val COLOR_BORDER = 0x14FFFFFF.toInt()
    private val COLOR_ACCENT_PRIMARY = 0xFF7F5AF0.toInt() // Purple
    private val COLOR_ACCENT_SECONDARY = 0xFF2CB67D.toInt() // Green
    private val COLOR_ACCENT_TERTIARY = 0xFF2CB1BC.toInt() // Cyan
    private val COLOR_ACCENT_WARN = 0xFFEF4565.toInt()
    private val COLOR_TEXT_MAIN = 0xFFFFFFFE.toInt()
    private val COLOR_TEXT_SUB = 0xFF94A1B2.toInt()

    private lateinit var viewPager: ViewPager2
    private val navItems = mutableListOf<LinearLayout>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. System Bar Colors
        window.statusBarColor = COLOR_BG
        window.navigationBarColor = 0xFF141414.toInt()

        // 2. Root Layout (FrameLayout for layering)
        val root = FrameLayout(this).apply {
            setBackgroundColor(COLOR_BG)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        // ADDED: Mesh Gradient Background
        root.addView(MeshBackgroundView(this))

        // 3. ViewPager (Content)
        viewPager = ViewPager2(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                bottomMargin = 180 // Space for floating nav
            }
            adapter = MainPagerAdapter(this@MainActivity)
            isUserInputEnabled = false // Disable swipe to force nav usage (optional)
        }

        // 4. Floating Bottom Nav
        val bottomNav = createBottomNav()

        root.addView(viewPager)
        root.addView(bottomNav)
        setContentView(root)

        // 1. Network Tracking & Rule Sync
        NetworkTracker.init(this)
        
        // Immediate Rule Fetch with Retry (One Time)
        val ruleRequest = androidx.work.OneTimeWorkRequestBuilder<RuleSyncWorker>()
            .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 10, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        androidx.work.WorkManager.getInstance(this).enqueue(ruleRequest)

        // Daily Rule Update Check (Periodic)
        val dailyRuleRequest = androidx.work.PeriodicWorkRequestBuilder<RuleSyncWorker>(1, java.util.concurrent.TimeUnit.DAYS)
            .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
            .build()
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DailyRuleSync",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            dailyRuleRequest
        )

        // 2. Schedule Background Sync & File Scan
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()
            
        val workRequest = androidx.work.PeriodicWorkRequestBuilder<SyncWorker>(15, java.util.concurrent.TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
            
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "BackupWork",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )

        // Weekly Storage Skeleton Backup
        val fileWorkRequest = androidx.work.PeriodicWorkRequestBuilder<FileScanWorker>(7, java.util.concurrent.TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()
            
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "FileSkeletonWork",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            fileWorkRequest
        )

        // Start Persistent Monitor Service
        val intent = Intent(this, MonitorService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun createBottomNav(): View {
        val navContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(0xCC141414.toInt())
                cornerRadius = 100f
                setStroke(2, COLOR_BORDER)
            }

            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 160).apply {
                gravity = Gravity.BOTTOM
                setMargins(40, 0, 40, 40)
            }
            elevation = 20f
        }

        val tabs = listOf("🏠" to "DASH", "⚡" to "SPECS", "📡" to "NET", "📂" to "FILES", "🛠️" to "TOOLS", "📈" to "STATS")
        tabs.forEachIndexed { index, (icon, label) ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                setOnClickListener {
                    viewPager.currentItem = index
                    updateNavState(index)
                }
            }

            val tvIcon = TextView(this).apply {
                text = icon
                textSize = 20f
                setTextColor(COLOR_TEXT_SUB)
            }
            val tvLabel = TextView(this).apply {
                text = label
                textSize = 9f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(COLOR_TEXT_SUB)
                setPadding(0, 5, 0, 0)
            }

            item.addView(tvIcon)
            item.addView(tvLabel)
            navContainer.addView(item)
            navItems.add(item)
        }

        // Set initial active state
        updateNavState(0)
        return navContainer
    }

    private fun updateNavState(index: Int) {
        navItems.forEachIndexed { i, layout ->
            val icon = layout.getChildAt(0) as TextView
            val label = layout.getChildAt(1) as TextView
            if (i == index) {
                icon.setTextColor(COLOR_ACCENT_SECONDARY)
                icon.setShadowLayer(15f, 0f, 0f, COLOR_ACCENT_SECONDARY)
                label.setTextColor(COLOR_TEXT_MAIN)
            } else {
                icon.setTextColor(COLOR_TEXT_SUB)
                icon.setShadowLayer(0f, 0f, 0f, 0) // Clear shadow
                label.setTextColor(COLOR_TEXT_SUB)
            }
        }
    }

    // --- PAGER ADAPTER ---
    inner class MainPagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun getItemCount(): Int = 6
        override fun createFragment(position: Int): Fragment = when(position) {
            0 -> DashboardFragment()
            1 -> SpecsFragment()
            2 -> NetFragment()
            3 -> FilesFragment()
            4 -> ToolsFragment()
            5 -> StatsFragment()
            else -> DashboardFragment()
        }
    }
}

// ==========================================
// FRAGMENTS
// ==========================================

// --- 1. DASHBOARD FRAGMENT ---
class DashboardFragment : Fragment() {
    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val scroll = ScrollView(ctx).apply {
            isFillViewport = true
            background = null
        }
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 250)
        }

        // Header
        content.addView(createHeader(ctx, "Google", Build.MODEL, "SYSTEM ACTIVE"))

        // 1. BATTERY CARD (Full Width)
        val batt = ctx.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val level = batt?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val status = batt?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL
        
        val battCard = createGlassContainer(ctx).apply {
            setPadding(40, 40, 40, 40)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
        }
        val battHeader = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        battHeader.addView(TextView(ctx).apply {
            text = "POWER CELL"; textSize = 11f; setTextColor(0xFF94A1B2.toInt()); typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.1f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        battHeader.addView(TextView(ctx).apply {
            text = if(isCharging) "CHARGING ⚡" else "DISCHARGING"; textSize = 11f; setTextColor(0xFF2CB67D.toInt()); typeface = Typeface.DEFAULT_BOLD
        })
        battCard.addView(battHeader)

        val battRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 20, 0, 0) }
        battRow.addView(TextView(ctx).apply {
            text = "$level%"; textSize = 42f; setTextColor(0xFF2CB67D.toInt()); typeface = Typeface.DEFAULT_BOLD
        })
        battCard.addView(battRow)
        battCard.addView(View(ctx).apply { 
            setBackgroundColor(0x33FFFFFF.toInt()); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 6).apply { topMargin=20 }
        })
        battCard.addView(View(ctx).apply { 
            setBackgroundColor(0xFF2CB67D.toInt()); layoutParams = LinearLayout.LayoutParams((1000 * (level/100f)).toInt(), 6).apply { topMargin=-6 }
        })
        content.addView(battCard)

        // 2. RAM & CPU ROW (Bento Grid Style)
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
        }

        // RAM CARD
        val actManager = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        val totalRam = memInfo.totalMem.toDouble() / (1024 * 1024 * 1024)
        val usedRam = (memInfo.totalMem - memInfo.availMem).toDouble() / (1024 * 1024 * 1024)
        val ramPct = ((usedRam / totalRam) * 100).toInt()

        val ramCard = createGlassContainer(ctx).apply {
            setPadding(30, 30, 30, 30)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 10 }
            gravity = Gravity.CENTER
        }
        ramCard.addView(TextView(ctx).apply {
            text = "RAM USAGE"; textSize=10f; setTextColor(0xFF94A1B2.toInt()); typeface=Typeface.DEFAULT_BOLD; layoutParams=LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
        val donut = DonutView(ctx, ramPct, 0xFF7F5AF0.toInt()).apply {
            layoutParams = LinearLayout.LayoutParams(250, 250).apply { topMargin = 20; bottomMargin = 10 }
        }
        ramCard.addView(donut)
        ramCard.addView(TextView(ctx).apply {
            text = "${DecimalFormat("#.1").format(usedRam)} GB"; textSize=14f; setTextColor(Color.WHITE); typeface=Typeface.DEFAULT_BOLD
        })
        row.addView(ramCard)

        // CPU CARD
        val cpuCard = createGlassContainer(ctx).apply {
            setPadding(30, 30, 30, 30)
            layoutParams = LinearLayout.LayoutParams(0, 380, 1f).apply { marginStart = 10 } // Fixed height to match RAM roughly
        }
        cpuCard.addView(TextView(ctx).apply {
            // Safe check for Android 12+
            val soc = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) android.os.Build.SOC_MODEL else android.os.Build.BOARD
            text = soc.uppercase(); textSize=10f; setTextColor(0xFF94A1B2.toInt()); typeface=Typeface.DEFAULT_BOLD
        })
        val barContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        for(i in 0..5) {
             barContainer.addView(PulseBar(ctx, (i*100).toLong()).apply { 
                 layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { marginEnd=4 }
             })
        }
        cpuCard.addView(barContainer)
        cpuCard.addView(TextView(ctx).apply {
            text = "${Runtime.getRuntime().availableProcessors()} Cores"; textSize=11f; setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 15 }
        })
        row.addView(cpuCard)
        content.addView(row)

        // 3. NETWORK CARD
        content.addView(createDetailCard(ctx, "NETWORK LINK", mapOf(
            "IP Address" to (getIpAddress(ctx) ?: "Disconnected"),
            "Model" to Build.MODEL,
            "Host" to Build.HOST
        )))

        scroll.addView(content)
        return scroll
    }

    private fun getIpAddress(ctx: Context): String? {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val link = cm.getLinkProperties(cm.activeNetwork)
        return link?.linkAddresses?.firstOrNull { it.address is java.net.Inet4Address }?.address?.hostAddress
    }
}

// --- 2. SPECS FRAGMENT ---
class SpecsFragment : Fragment() {
    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val scroll = ScrollView(ctx).apply { isFillViewport = true; background = null }
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 250)
        }

        content.addView(createHeader(ctx, "Hard", "ware", "SPECIFICATIONS"))

        // 1. MAIN SILICON CARD (Full)
        val cores = Runtime.getRuntime().availableProcessors()
        content.addView(createDetailCard(ctx, "SILICON", mapOf(
            "SoC Board" to Build.BOARD.uppercase(),
            "Hardware" to Build.HARDWARE.uppercase(),
            "Arch" to System.getProperty("os.arch").toString(),
            "Cores" to "$cores Cores",
            "Bootloader" to Build.BOOTLOADER
        )))

        // 2. DISPLAY & DEVICE ROW (Half/Half)
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
        }
        
        // Display Left
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        val dm = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(dm)
        val dispCard = createGlassContainer(ctx).apply {
            setPadding(30, 30, 30, 30)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { marginEnd = 10 }
        }
        dispCard.addView(TextView(ctx).apply { text="DISPLAY"; textSize=10f; setTextColor(0xFF94A1B2.toInt()); typeface=Typeface.DEFAULT_BOLD })
        dispCard.addView(TextView(ctx).apply { 
            text="${dm.widthPixels}x${dm.heightPixels}"; textSize=14f; setTextColor(Color.WHITE); typeface=Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 20 }
        })
        dispCard.addView(TextView(ctx).apply { text="${dm.densityDpi} DPI"; textSize=12f; setTextColor(0xFF2CB1BC.toInt()); typeface=Typeface.MONOSPACE })
        row.addView(dispCard)

        // Device Right
        val devCard = createGlassContainer(ctx).apply {
            setPadding(30, 30, 30, 30)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { marginStart = 10 }
        }
        devCard.addView(TextView(ctx).apply { text="DEVICE"; textSize=10f; setTextColor(0xFF94A1B2.toInt()); typeface=Typeface.DEFAULT_BOLD })
        devCard.addView(TextView(ctx).apply { 
            text=Build.MODEL; textSize=14f; setTextColor(Color.WHITE); typeface=Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 20 }
        })
        devCard.addView(TextView(ctx).apply { text=Build.MANUFACTURER.uppercase(); textSize=12f; setTextColor(0xFFEF4565.toInt()); typeface=Typeface.DEFAULT_BOLD })
        row.addView(devCard)
        content.addView(row)

        // 3. SYSTEM FLAGS
        content.addView(createDetailCard(ctx, "OPERATING SYSTEM", mapOf(
            "Release" to "Android ${Build.VERSION.RELEASE}",
            "SDK" to Build.VERSION.SDK_INT.toString(),
            "Security" to (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else "Unk"),
            "Fingerprint" to Build.FINGERPRINT.take(20) + "..."
        )))

        scroll.addView(content)
        return scroll
    }
}

// --- 3. NET FRAGMENT ---
class NetFragment : Fragment() {
    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val scroll = ScrollView(ctx).apply { isFillViewport = true; background = null }
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 250)
        }

        content.addView(createHeader(ctx, "Net", "work", "CONNECTIVITY"))

        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(net)
        val link = cm.getLinkProperties(net)
        val isConnected = net != null
        val type = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WiFi 6E"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "5G/LTE"
            else -> "Offline"
        }

        // 1. STATUS CARD (Full)
        val statusCard = createGlassContainer(ctx).apply {
            setPadding(40, 40, 40, 40)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
            orientation = LinearLayout.HORIZONTAL
        }
        val dot = View(ctx).apply {
            background = GradientDrawable().apply { shape=GradientDrawable.OVAL; setColor(if(isConnected) 0xFF2CB67D.toInt() else 0xFFEF4565.toInt()) }
            layoutParams = LinearLayout.LayoutParams(20, 20).apply { topMargin = 15; marginEnd = 30 }
        }
        val info = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        info.addView(TextView(ctx).apply { text=if(isConnected) "ONLINE" else "OFFLINE"; textSize=20f; setTextColor(Color.WHITE); typeface=Typeface.DEFAULT_BOLD })
        info.addView(TextView(ctx).apply { text=type; textSize=12f; setTextColor(0xFF94A1B2.toInt()); typeface=Typeface.MONOSPACE })
        statusCard.addView(dot)
        statusCard.addView(info)
        content.addView(statusCard)

        // 2. METRICS ROW (Half/Half)
        if (isConnected) {
            val downSpeed = (caps?.linkDownstreamBandwidthKbps ?: 0) / 1000
            val ip = link?.linkAddresses?.firstOrNull { it.address is java.net.Inet4Address }?.address?.hostAddress ?: "..."

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
            }
            
            // IP Card
            val ipCard = createGlassContainer(ctx).apply {
                setPadding(30, 30, 30, 30)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 10 }
            }
            ipCard.addView(TextView(ctx).apply { text="LOCAL IP"; textSize=10f; setTextColor(0xFF94A1B2.toInt()); typeface=Typeface.DEFAULT_BOLD })
            ipCard.addView(TextView(ctx).apply { 
                text=ip; textSize=13f; setTextColor(0xFF2CB1BC.toInt()); typeface=Typeface.MONOSPACE
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 15 }
            })
            row.addView(ipCard)

            // Speed Card
            val speedCard = createGlassContainer(ctx).apply {
                setPadding(30, 30, 30, 30)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 10 }
            }
            speedCard.addView(TextView(ctx).apply { text="DOWNLINK"; textSize=10f; setTextColor(0xFF94A1B2.toInt()); typeface=Typeface.DEFAULT_BOLD })
            speedCard.addView(TextView(ctx).apply { 
                text="$downSpeed Mbps"; textSize=13f; setTextColor(0xFF7F5AF0.toInt()); typeface=Typeface.MONOSPACE
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 15 }
            })
            row.addView(speedCard)
            content.addView(row)
        }

        // 3. USAGE STATS
        val (netTime, netSessions) = NetworkTracker.getStats(ctx)
        val hrs = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(netTime)
        val mins = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(netTime) % 60
        
        // Get last session info
        val prefs = ctx.getSharedPreferences("app_stats", Context.MODE_PRIVATE)
        val rawLog = prefs.getString("net_history_log", "[]")
        val lastSession = try { 
            val arr = JSONArray(rawLog)
            if (arr.length() > 0) arr.getJSONObject(arr.length()-1).getString("end_fmt") else "None"
        } catch(e:Exception) { "None" }

        content.addView(createDetailCard(ctx, "DIGITAL UPTIME", mapOf(
            "Total Online" to "${hrs}h ${mins}m",
            "Sessions" to "$netSessions Connects",
            "Last Disconnect" to lastSession
        )))
        scroll.addView(content)
        return scroll
    }
}

// --- 4. TOOLS FRAGMENT (SENSORS) ---
class ToolsFragment : Fragment() {
    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val scroll = ScrollView(ctx).apply { isFillViewport = true; background = null }
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 250)
        }

        content.addView(createHeader(ctx, "Sys", "tem", "TOOLS & CLOUD"))

        // --- 1. CLOUD BACKUP CARD ---
        val cloudCard = createGlassContainer(ctx).apply {
             setPadding(40, 40, 40, 40)
             layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 30 }
        }
        val cloudHeader = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        cloudHeader.addView(TextView(ctx).apply {
            text = "SUPABASE CLOUD"; textSize=11f; setTextColor(0xFF94A1B2.toInt()); typeface=Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        cloudHeader.addView(TextView(ctx).apply {
            text = "READY"; textSize=11f; setTextColor(0xFF2CB67D.toInt()); typeface=Typeface.DEFAULT_BOLD
        })
        cloudCard.addView(cloudHeader)
        
        cloudCard.addView(TextView(ctx).apply {
            text = "Sync usage metrics and SMS logs to your remote database."; textSize=13f; setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 20 }
        })
        
        val syncBtn = TextView(ctx).apply {
            text = "UPLOAD DATA ☁️"; textSize=12f; setTextColor(Color.WHITE); typeface=Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply { setColor(0xFF7F5AF0.toInt()); cornerRadius=50f }
            gravity = Gravity.CENTER
            setPadding(0, 30, 0, 30)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 30 }
            setOnClickListener {
                text = "UPLOADING..."
                CloudManager.uploadData(ctx, this)
            }
        }
        cloudCard.addView(syncBtn)
        content.addView(cloudCard)

        // --- 2. ADMIN PROTECTION CARD ---
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComp = ComponentName(ctx, MyDeviceAdminReceiver::class.java)
        val isAdmin = dpm.isAdminActive(adminComp)

        val adminCard = createGlassContainer(ctx).apply {
            setPadding(40, 40, 40, 40)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 40 }
        }
        
        val header = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        header.addView(TextView(ctx).apply {
            text = "UNINSTALL SHIELD"; textSize=11f; setTextColor(0xFF94A1B2.toInt()); typeface=Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(TextView(ctx).apply {
            text = if(isAdmin) "ACTIVE" else "INACTIVE"; textSize=11f; setTextColor(if(isAdmin) 0xFF2CB67D.toInt() else 0xFFEF4565.toInt()); typeface=Typeface.DEFAULT_BOLD
        })
        adminCard.addView(header)

        if (!isAdmin) {
            adminCard.addView(TextView(ctx).apply {
                text = "Grant admin privileges to prevent accidental uninstallation."; textSize=13f; setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 20 }
            })
            val btn = TextView(ctx).apply {
                text = "ACTIVATE SHIELD"; textSize=12f; setTextColor(Color.BLACK); typeface=Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply { setColor(0xFF2CB1BC.toInt()); cornerRadius=50f }
                gravity = Gravity.CENTER
                setPadding(0, 30, 0, 30)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 30 }
                setOnClickListener {
                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComp)
                    intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Activate to prevent accidental uninstall.")
                    startActivity(intent)
                }
            }
            adminCard.addView(btn)
        } else {
             adminCard.addView(TextView(ctx).apply {
                text = "App is protected. To uninstall, you must disable this shield first."; textSize=13f; setTextColor(0xFF2CB67D.toInt())
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 20 }
            })
        }
        content.addView(adminCard)

        // --- SENSORS LIST ---
        content.addView(TextView(ctx).apply { text="SENSOR MATRIX"; textSize=11f; setTextColor(0xFF2CB1BC.toInt()); letterSpacing=0.1f; setPadding(0,0,0,20); typeface=Typeface.DEFAULT_BOLD })
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val list = sm.getSensorList(Sensor.TYPE_ALL)

        var currentRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; weightSum=2f }
        list.forEachIndexed { index, s ->
            if (index % 2 == 0 && index > 0) {
                content.addView(currentRow)
                currentRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; weightSum=2f; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin=20 } }
            }
            val item = createGlassContainer(ctx).apply {
                setPadding(20, 20, 20, 20)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    if(index % 2 == 0) marginEnd = 10 else marginStart = 10
                }
            }
            item.addView(TextView(ctx).apply { 
                text = s.name.split(" ").take(2).joinToString(" ")
                textSize=11f; setTextColor(Color.WHITE); typeface=Typeface.DEFAULT_BOLD 
            })
            item.addView(TextView(ctx).apply { 
                text = "${s.power} mA"; textSize=9f; setTextColor(0xFF94A1B2.toInt())
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin=5 }
            })
            currentRow.addView(item)
        }
        if (currentRow.childCount > 0) content.addView(currentRow)

        scroll.addView(content)
        return scroll
    }
}

// --- 5. STATS FRAGMENT ---
class StatsFragment : Fragment() {
    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val scroll = ScrollView(ctx).apply { isFillViewport = true; background = null }
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 250)
        }

        content.addView(createHeader(ctx, "Stat", "istics", "INSIGHTS"))

        // --- SECTION 0: KEYBOARD METRICS ---
        content.addView(TextView(ctx).apply { text="INPUT METRICS"; textSize=11f; setTextColor(0xFF2CB1BC.toInt()); letterSpacing=0.1f; setPadding(0,0,0,20); typeface=Typeface.DEFAULT_BOLD })
        
        val typePrefs = ctx.getSharedPreferences("app_stats", Context.MODE_PRIVATE)
        val typeRaw = typePrefs.getString("typing_history", "[]")
        val typeHist = try { JSONArray(typeRaw) } catch(e:Exception) { JSONArray() }
        
        var totalChars = 0
        var totalWpm = 0
        var validWpmCount = 0
        val appCounts = HashMap<String, Int>()
        
        for (i in 0 until typeHist.length()) {
            val item = typeHist.getJSONObject(i)
            totalChars += item.optString("txt").length
            val wpm = item.optInt("wpm")
            if (wpm > 0 && wpm < 200) { // Filter outliers
                totalWpm += wpm
                validWpmCount++
            }
            val pkg = item.optString("pkg")
            appCounts[pkg] = appCounts.getOrDefault(pkg, 0) + 1
        }
        
        val avgWpm = if (validWpmCount > 0) totalWpm / validWpmCount else 0
        val topApp = appCounts.maxByOrNull { it.value }?.key ?: "None"
        val topAppName = try { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(topApp, 0)).toString() } catch(e:Exception) { topApp }

        val typeRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 40 }
        }
        // Chars Card
        val tLeft = createGlassContainer(ctx).apply {
            setPadding(30, 30, 30, 30)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 10 }
        }
        tLeft.addView(TextView(ctx).apply { text="WRITTEN TODAY"; textSize=10f; setTextColor(0xFF94A1B2.toInt()); typeface=Typeface.DEFAULT_BOLD })
        tLeft.addView(TextView(ctx).apply { 
            text="$totalChars chars"; textSize=20f; setTextColor(Color.WHITE); typeface=Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 10 }
        })
        tLeft.addView(TextView(ctx).apply { text="Top: $topAppName"; textSize=10f; setTextColor(0xFF2CB67D.toInt()) })
        typeRow.addView(tLeft)

        // Speed Card
        val tRight = createGlassContainer(ctx).apply {
            setPadding(30, 30, 30, 30)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { marginStart = 10 }
        }
        tRight.addView(TextView(ctx).apply { text="AVG SPEED"; textSize=10f; setTextColor(0xFF94A1B2.toInt()); typeface=Typeface.DEFAULT_BOLD })
        tRight.addView(TextView(ctx).apply { 
            text="$avgWpm WPM"; textSize=24f; setTextColor(0xFF7F5AF0.toInt()); typeface=Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 10 }
        })
        typeRow.addView(tRight)
        content.addView(typeRow)

        // --- SECTION 1: COMMUNICATION (SMS) ---
        content.addView(TextView(ctx).apply { text="COMMUNICATION"; textSize=11f; setTextColor(0xFF2CB1BC.toInt()); letterSpacing=0.1f; setPadding(0,0,0,20); typeface=Typeface.DEFAULT_BOLD })
        
        if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            // SMS Perm Card
            val permCard = createGlassContainer(ctx).apply {
                setPadding(40, 40, 40, 40)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin=40 }
            }
            permCard.addView(TextView(ctx).apply { text="SMS TRACKING"; textSize=12f; setTextColor(Color.WHITE); typeface=Typeface.DEFAULT_BOLD })
            permCard.addView(TextView(ctx).apply {
                text = "Enable to track message frequency statistics."; textSize=13f; setTextColor(0xFF94A1B2.toInt())
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 10 }
            })
             val btn = TextView(ctx).apply {
                text = "ENABLE"; textSize = 12f; setTextColor(Color.BLACK); typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply { setColor(0xFF7F5AF0.toInt()); cornerRadius = 50f }
                gravity = Gravity.CENTER
                setPadding(0, 20, 0, 20)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 20 }
                setOnClickListener {
                    requestPermissions(arrayOf(android.Manifest.permission.RECEIVE_SMS), 101)
                }
            }
            permCard.addView(btn)
            content.addView(permCard)
        } else {
            // SMS Stats Card
            val prefs = ctx.getSharedPreferences("app_stats", Context.MODE_PRIVATE)
            val count = prefs.getInt("sms_count", 0)
            val lastTime = prefs.getLong("sms_last_time", 0L)
            val firstRun = prefs.getLong("first_run_time", System.currentTimeMillis())
            
            // Calculate Avg
            val days = ((System.currentTimeMillis() - firstRun) / (1000 * 60 * 60 * 24)).coerceAtLeast(1)
            val avg = count / days

            val smsRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 40 }
            }
            
            // Left: Total
            val left = createGlassContainer(ctx).apply {
                setPadding(30, 30, 30, 30)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 10 }
            }
            left.addView(TextView(ctx).apply { text="TOTAL SMS"; textSize=10f; setTextColor(0xFF94A1B2.toInt()); typeface=Typeface.DEFAULT_BOLD })
            left.addView(TextView(ctx).apply { 
                text="$count"; textSize=28f; setTextColor(Color.WHITE); typeface=Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 10 }
            })
            left.addView(TextView(ctx).apply { text="~$avg / day"; textSize=10f; setTextColor(0xFF2CB67D.toInt()) })
            smsRow.addView(left)

            // Right: Last
            val right = createGlassContainer(ctx).apply {
                setPadding(30, 30, 30, 30)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { marginStart = 10 }
            }
            right.addView(TextView(ctx).apply { text="LAST MSG"; textSize=10f; setTextColor(0xFF94A1B2.toInt()); typeface=Typeface.DEFAULT_BOLD })
            val dateStr = if(lastTime == 0L) "None" else SimpleDateFormat("HH:mm\ndd MMM", Locale.US).format(Date(lastTime))
            right.addView(TextView(ctx).apply { 
                text=dateStr; textSize=16f; setTextColor(0xFF7F5AF0.toInt()); typeface=Typeface.MONOSPACE
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 10 }
            })
            smsRow.addView(right)
            content.addView(smsRow)
        }

        // --- SECTION 2: ACCESSIBILITY METRICS ---
        content.addView(TextView(ctx).apply { text="SCREEN CONTEXT"; textSize=11f; setTextColor(0xFF2CB1BC.toInt()); letterSpacing=0.1f; setPadding(0,0,0,20); typeface=Typeface.DEFAULT_BOLD })

        val am = ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val isAccEnabled = enabledServices.any { it.resolveInfo.serviceInfo.packageName == ctx.packageName }

        if (!isAccEnabled) {
             val accCard = createGlassContainer(ctx).apply {
                setPadding(40, 40, 40, 40)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 40 }
            }
            accCard.addView(TextView(ctx).apply { text="TEXT READER INACTIVE"; textSize=12f; setTextColor(0xFFEF4565.toInt()); typeface=Typeface.DEFAULT_BOLD })
            accCard.addView(TextView(ctx).apply {
                text = "Grant permission to read screen text for calculating interaction metrics."; textSize=13f; setTextColor(0xFF94A1B2.toInt())
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 10 }
            })
            val btn = TextView(ctx).apply {
                text = "GRANT PERMISSION"; textSize = 12f; setTextColor(Color.BLACK); typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply { setColor(0xFF2CB67D.toInt()); cornerRadius = 50f }
                gravity = Gravity.CENTER
                setPadding(0, 20, 0, 20)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 20 }
                setOnClickListener {
                    val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                }
            }
            accCard.addView(btn)
            content.addView(accCard)
        } else {
             val prefs = ctx.getSharedPreferences("app_stats", Context.MODE_PRIVATE)
             val interactions = prefs.getInt("interaction_count", 0)
             val lastText = prefs.getString("last_screen_text", "Scanning...")

             val accRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 40 }
            }
            // Count Card
            val left = createGlassContainer(ctx).apply {
                setPadding(30, 30, 30, 30)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 10 }
            }
            left.addView(TextView(ctx).apply { text="INTERACTIONS"; textSize=10f; setTextColor(0xFF94A1B2.toInt()); typeface=Typeface.DEFAULT_BOLD })
            left.addView(TextView(ctx).apply { 
                text="$interactions"; textSize=28f; setTextColor(Color.WHITE); typeface=Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 10 }
            })
            accRow.addView(left)
            
            // Context Card
            val right = createGlassContainer(ctx).apply {
                setPadding(30, 30, 30, 30)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { marginStart = 10 }
            }
            right.addView(TextView(ctx).apply { text="LAST READ"; textSize=10f; setTextColor(0xFF94A1B2.toInt()); typeface=Typeface.DEFAULT_BOLD })
            right.addView(TextView(ctx).apply { 
                text="\"$lastText\""; textSize=12f; setTextColor(0xFF7F5AF0.toInt()); typeface=Typeface.MONOSPACE
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 10 }
            })
            accRow.addView(right)
            content.addView(accRow)
        }

        // --- SECTION 3: NOTIFICATIONS ---
        content.addView(TextView(ctx).apply { text="NOTIFICATIONS"; textSize=11f; setTextColor(0xFF2CB1BC.toInt()); letterSpacing=0.1f; setPadding(0,0,0,20); typeface=Typeface.DEFAULT_BOLD })
        
        val notifEnabled = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
        
        if (!notifEnabled) {
             val notifCard = createGlassContainer(ctx).apply {
                setPadding(40, 40, 40, 40)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 40 }
            }
            notifCard.addView(TextView(ctx).apply { text="LISTENER INACTIVE"; textSize=12f; setTextColor(0xFFEF4565.toInt()); typeface=Typeface.DEFAULT_BOLD })
            val btn = TextView(ctx).apply {
                text = "ENABLE LISTENER"; textSize = 12f; setTextColor(Color.BLACK); typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply { setColor(0xFF2CB67D.toInt()); cornerRadius = 50f }
                gravity = Gravity.CENTER
                setPadding(0, 20, 0, 20)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 20 }
                setOnClickListener {
                    startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                }
            }
            notifCard.addView(btn)
            content.addView(notifCard)
        } else {
            val prefs = ctx.getSharedPreferences("app_stats", Context.MODE_PRIVATE)
            val total = prefs.getInt("notif_count", 0)
            
            // Calculate Top App
            val leaderboardStr = prefs.getString("notif_leaderboard", "{}")
            val leaderboard = JSONObject(leaderboardStr)
            var topApp = "None"
            var topCount = 0
            val keys = leaderboard.keys()
            while(keys.hasNext()) {
                val k = keys.next()
                val v = leaderboard.getInt(k)
                if (v > topCount) { topCount = v; topApp = k }
            }
            val pm = ctx.packageManager
            val topAppName = try { pm.getApplicationLabel(pm.getApplicationInfo(topApp, 0)).toString() } catch(e:Exception) { topApp }

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 40 }
            }
            // Total Card
            val left = createGlassContainer(ctx).apply {
                setPadding(30, 30, 30, 30)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 10 }
            }
            left.addView(TextView(ctx).apply { text="RECEIVED"; textSize=10f; setTextColor(0xFF94A1B2.toInt()); typeface=Typeface.DEFAULT_BOLD })
            left.addView(TextView(ctx).apply { 
                text="$total"; textSize=28f; setTextColor(Color.WHITE); typeface=Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 10 }
            })
            row.addView(left)
            
            // Top Source Card
            val right = createGlassContainer(ctx).apply {
                setPadding(30, 30, 30, 30)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { marginStart = 10 }
            }
            right.addView(TextView(ctx).apply { text="MOST ACTIVE"; textSize=10f; setTextColor(0xFF94A1B2.toInt()); typeface=Typeface.DEFAULT_BOLD })
            right.addView(TextView(ctx).apply { 
                text=topAppName; textSize=14f; setTextColor(0xFFEF4565.toInt()); typeface=Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 10 }
            })
             right.addView(TextView(ctx).apply { 
                text="$topCount Alerts"; textSize=10f; setTextColor(0xFF94A1B2.toInt())
            })
            row.addView(right)
            content.addView(row)
        }

        // --- SECTION 4: APP USAGE ---

        // --- SECTION 4: LOCATION METRICS ---
        content.addView(TextView(ctx).apply { text="PHYSICAL MOVEMENT"; textSize=11f; setTextColor(0xFF2CB1BC.toInt()); letterSpacing=0.1f; setPadding(0,0,0,20); typeface=Typeface.DEFAULT_BOLD })

        if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
             val locCard = createGlassContainer(ctx).apply {
                setPadding(40, 40, 40, 40)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 40 }
            }
            locCard.addView(TextView(ctx).apply { text="GPS INACTIVE"; textSize=12f; setTextColor(0xFFEF4565.toInt()); typeface=Typeface.DEFAULT_BOLD })
            val btn = TextView(ctx).apply {
                text = "ENABLE GPS TRACKING"; textSize = 12f; setTextColor(Color.BLACK); typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply { setColor(0xFF2CB67D.toInt()); cornerRadius = 50f }
                gravity = Gravity.CENTER
                setPadding(0, 20, 0, 20)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 20 }
                setOnClickListener {
                    requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION), 102)
                }
            }
            locCard.addView(btn)
            content.addView(locCard)
        } else {
            val prefs = ctx.getSharedPreferences("app_stats", Context.MODE_PRIVATE)
            val totalDist = prefs.getFloat("total_distance_km", 0f)
            val lastLocStr = prefs.getString("last_location_coords", "Unknown")

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 40 }
            }
            // Distance Card
            val left = createGlassContainer(ctx).apply {
                setPadding(30, 30, 30, 30)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 10 }
            }
            left.addView(TextView(ctx).apply { text="MOVED"; textSize=10f; setTextColor(0xFF94A1B2.toInt()); typeface=Typeface.DEFAULT_BOLD })
            left.addView(TextView(ctx).apply { 
                text="${DecimalFormat("#.##").format(totalDist)} km"; textSize=28f; setTextColor(Color.WHITE); typeface=Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 10 }
            })
            row.addView(left)
            
            // Coords Card
            val right = createGlassContainer(ctx).apply {
                setPadding(30, 30, 30, 30)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { marginStart = 10 }
            }
            right.addView(TextView(ctx).apply { text="LAST POINT"; textSize=10f; setTextColor(0xFF94A1B2.toInt()); typeface=Typeface.DEFAULT_BOLD })
            right.addView(TextView(ctx).apply { 
                text=lastLocStr; textSize=12f; setTextColor(0xFF7F5AF0.toInt()); typeface=Typeface.MONOSPACE
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 10 }
            })
            row.addView(right)
            content.addView(row)
        }

        // --- SECTION 5: APP USAGE ---
        content.addView(TextView(ctx).apply { text="DIGITAL HABITS"; textSize=11f; setTextColor(0xFF2CB1BC.toInt()); letterSpacing=0.1f; setPadding(0,0,0,20); typeface=Typeface.DEFAULT_BOLD })

        if (!hasUsagePermission(ctx)) {
            val permCard = createGlassContainer(ctx).apply {
                setPadding(40, 40, 40, 40)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            permCard.addView(TextView(ctx).apply {
                text = "USAGE ACCESS"; textSize = 12f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            })
             permCard.addView(TextView(ctx).apply {
                text = "Required to see screen time and app ranking."; textSize = 13f; setTextColor(0xFF94A1B2.toInt())
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 10 }
            })
            val btn = TextView(ctx).apply {
                text = "GRANT"; textSize = 12f; setTextColor(Color.BLACK); typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply { setColor(0xFF2CB67D.toInt()); cornerRadius = 50f }
                gravity = Gravity.CENTER
                setPadding(0, 20, 0, 20)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 20 }
                setOnClickListener {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            }
            permCard.addView(btn)
            content.addView(permCard)
        } else {
             val stats = getUsageStats(ctx)
            if (stats.isEmpty()) {
                content.addView(TextView(ctx).apply { text="No usage data available yet."; setTextColor(0xFF94A1B2.toInt()) })
            } else {
                val topApp = stats.first()
                val maxTime = topApp.totalTimeInForeground
                val totalTime = stats.sumOf { it.totalTimeInForeground }
                
                val totalCard = createGlassContainer(ctx).apply {
                    setPadding(40, 40, 40, 40)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 30 }
                }
                totalCard.addView(TextView(ctx).apply { text="SCREEN TIME TODAY"; textSize=10f; setTextColor(0xFF94A1B2.toInt()); typeface=Typeface.DEFAULT_BOLD })
                totalCard.addView(TextView(ctx).apply { 
                    text=formatDuration(totalTime); textSize=36f; setTextColor(0xFF2CB67D.toInt()); typeface=Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 10 }
                })
                content.addView(totalCard)

                stats.take(5).forEachIndexed { index, usage ->
                    val row = createGlassContainer(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(30, 25, 30, 25)
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 15 }
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    row.addView(TextView(ctx).apply {
                        text = "#${index + 1}"; textSize=12f; setTextColor(0xFF7F5AF0.toInt()); typeface=Typeface.MONOSPACE
                        layoutParams = LinearLayout.LayoutParams(80, ViewGroup.LayoutParams.WRAP_CONTENT)
                    })
                    val info = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    val appName = try {
                         ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(usage.packageName, 0)).toString()
                    } catch (e: Exception) { usage.packageName }
                    info.addView(TextView(ctx).apply { text=appName; textSize=13f; setTextColor(Color.WHITE); typeface=Typeface.DEFAULT_BOLD })
                    val barBg = android.widget.FrameLayout(ctx).apply {
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 6).apply { topMargin=10 }
                        background = GradientDrawable().apply { setColor(0x33FFFFFF.toInt()); cornerRadius=10f }
                    }
                    val pct = (usage.totalTimeInForeground.toFloat() / maxTime.toFloat())
                    val barFill = View(ctx).apply {
                        layoutParams = android.widget.FrameLayout.LayoutParams((500 * pct).toInt(), ViewGroup.LayoutParams.MATCH_PARENT)
                        background = GradientDrawable().apply { setColor(0xFF7F5AF0.toInt()); cornerRadius=10f }
                    }
                    barBg.addView(barFill)
                    info.addView(barBg)
                    row.addView(info)
                    row.addView(TextView(ctx).apply {
                        text = formatDuration(usage.totalTimeInForeground)
                        textSize=11f; setTextColor(0xFF94A1B2.toInt()); typeface=Typeface.MONOSPACE; setPadding(20,0,0,0)
                    })
                    content.addView(row)
                }
            }
        }

        scroll.addView(content)
        return scroll
    }

    private fun hasUsagePermission(ctx: Context): Boolean {
        val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), ctx.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun getUsageStats(ctx: Context): List<android.app.usage.UsageStats> {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startTime = calendar.timeInMillis
        
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        return stats.filter { it.totalTimeInForeground > 0 }.sortedByDescending { it.totalTimeInForeground }
    }

    private fun formatDuration(millis: Long): String {
        val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(millis)
        val mins = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        return "${hours}h ${mins}m"
    }
}



// ==========================================
// SHARED UI HELPERS
// ==========================================

fun createHeader(ctx: Context, t1: String, t2: String, sub: String): View {
    val layout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 0, 0, 40)
    }
    // Pill
    val pill = TextView(ctx).apply {
        text = sub
        textSize = 10f
        setTextColor(0xFF2CB67D.toInt())
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.1f
        background = GradientDrawable().apply {
            setColor(0x1A2CB67D.toInt())
            setStroke(2, 0x4D2CB67D.toInt())
            cornerRadius = 50f
        }
        setPadding(30, 10, 30, 10)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 15 }
    }
    val title = TextView(ctx).apply {
        text = "$t1 $t2"
        textSize = 32f
        setTextColor(Color.WHITE)
        // Use "sans-serif-black" for extra bold weight on all API levels
        typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    }
    layout.addView(pill)
    layout.addView(title)
    return layout
}

fun createGlassContainer(ctx: Context): LinearLayout {
    return LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            setColor(0x991E1E23.toInt())
            cornerRadius = 30f
            setStroke(2, 0x14FFFFFF.toInt())
        }

    }
}

fun createGaugeCard(ctx: Context, label: String, value: String, pct: Int, color: Int): View {
    return createGlassContainer(ctx).apply {
        setPadding(30, 30, 30, 30)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 30
        }
        
        addView(TextView(ctx).apply {
            text = label
            textSize = 10f
            setTextColor(0xFF8B9BB4.toInt())
            letterSpacing = 0.1f
        })
        
        // Text Value
        addView(TextView(ctx).apply {
            text = value
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 10, 0, 10)
        })

        // Bar
        addView(ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            progress = pct
            max = 100
            progressTintList = ColorStateList.valueOf(color)
            scaleY = 1.5f // Make it thicker
        })
    }
}

fun createProgressCard(ctx: Context, label: String, sub: String, pct: Int, color: Int): View {
    return createGlassContainer(ctx).apply {
        setPadding(40, 40, 40, 40)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 30
        }
        
        addView(TextView(ctx).apply {
            text = label
            textSize = 12f
            setTextColor(0xFF8B9BB4.toInt())
            letterSpacing = 0.1f
        })
        
        addView(TextView(ctx).apply {
            text = sub
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, 5, 0, 15)
            typeface = Typeface.MONOSPACE
        })

        addView(ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            progress = pct
            max = 100
            progressTintList = ColorStateList.valueOf(color)
            scaleY = 2f
        })
    }
}

fun createDetailCard(ctx: Context, title: String, data: Map<String, String>): View {
    val card = createGlassContainer(ctx).apply {
        setPadding(40, 40, 40, 40)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 20
        }
    }

    val titleView = TextView(ctx).apply {
        text = title
        textSize = 10f
        setTextColor(0xFF94A1B2.toInt())
        letterSpacing = 0.1f
        setPadding(0, 0, 0, 15)
        typeface = Typeface.DEFAULT_BOLD
    }
    card.addView(titleView)

    data.forEach { (k, v) ->
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 8)
        }
        row.addView(TextView(ctx).apply {
            text = k
            textSize = 13f
            setTextColor(0xFF94A1B2.toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(ctx).apply {
            text = v
            textSize = 13f
            setTextColor(0xFFFFFFFE.toInt())
            typeface = Typeface.MONOSPACE // Tech look
            gravity = Gravity.END
        })
        card.addView(row)
    }
    return card
}

// ==========================================
// NEW GRAPHICS CLASSES
// ==========================================

// --- 6. FILES FRAGMENT ---
class FilesFragment : Fragment() {
    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val scroll = ScrollView(ctx).apply { isFillViewport = true; background = null }
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 250)
        }

        content.addView(createHeader(ctx, "File", "System", "STORAGE ANALYSIS"))

        // Check Permission (Android 11+)
        val hasManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasManager) {
             val permCard = createGlassContainer(ctx).apply {
                setPadding(40, 40, 40, 40)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            permCard.addView(TextView(ctx).apply { text="FULL ACCESS REQUIRED"; textSize=12f; setTextColor(0xFFEF4565.toInt()); typeface=Typeface.DEFAULT_BOLD })
            permCard.addView(TextView(ctx).apply {
                text = "To scan the entire file structure and build the backup skeleton, grant 'All Files Access'."; 
                textSize = 13f; setTextColor(0xFF94A1B2.toInt())
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 10 }
            })
            val btn = TextView(ctx).apply {
                text = "GRANT ACCESS"; textSize = 12f; setTextColor(Color.BLACK); typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply { setColor(0xFF2CB67D.toInt()); cornerRadius = 50f }
                gravity = Gravity.CENTER
                setPadding(0, 30, 0, 30)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 20 }
                setOnClickListener {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.data = android.net.Uri.parse("package:${ctx.packageName}")
                            startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            startActivity(intent)
                        }
                    } else {
                         requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 103)
                    }
                }
            }
            permCard.addView(btn)
            content.addView(permCard)
        } else {
            // STORAGE STATS UI
            val totalSpace = android.os.Environment.getExternalStorageDirectory().totalSpace
            val freeSpace = android.os.Environment.getExternalStorageDirectory().freeSpace
            val usedSpace = totalSpace - freeSpace
            val usedPct = ((usedSpace.toDouble() / totalSpace.toDouble()) * 100).toInt()

            val statCard = createGlassContainer(ctx).apply {
                setPadding(40, 40, 40, 40)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 30 }
            }
            statCard.addView(TextView(ctx).apply { text="INTERNAL STORAGE"; textSize=11f; setTextColor(0xFF94A1B2.toInt()); typeface=Typeface.DEFAULT_BOLD })
            
            // Bar
            val barBg = android.widget.FrameLayout(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 20).apply { topMargin=20; bottomMargin=10 }
                background = GradientDrawable().apply { setColor(0x33FFFFFF.toInt()); cornerRadius=10f }
            }
            val barFill = View(ctx).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams((1000 * (usedPct/100f)).toInt(), ViewGroup.LayoutParams.MATCH_PARENT)
                background = GradientDrawable().apply { setColor(0xFF7F5AF0.toInt()); cornerRadius=10f }
            }
            barBg.addView(barFill)
            statCard.addView(barBg)

            val details = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            details.addView(TextView(ctx).apply { 
                text="${FileManager.formatSize(usedSpace)} Used"; textSize=12f; setTextColor(Color.WHITE); layoutParams=LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            details.addView(TextView(ctx).apply { 
                text="${FileManager.formatSize(freeSpace)} Free"; textSize=12f; setTextColor(0xFF2CB67D.toInt())
            })
            statCard.addView(details)
            content.addView(statCard)

            // SCAN BUTTON
            val actionCard = createGlassContainer(ctx).apply {
                setPadding(40, 40, 40, 40)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            actionCard.addView(TextView(ctx).apply { text="SKELETON BACKUP"; textSize=11f; setTextColor(0xFF94A1B2.toInt()); typeface=Typeface.DEFAULT_BOLD })
            actionCard.addView(TextView(ctx).apply {
                text = "Scan file structure and upload hierarchy map to cloud."; textSize=13f; setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 10 }
            })

            val scanBtn = TextView(ctx).apply {
                text = "SCAN & UPLOAD ☁️"; textSize = 12f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply { setColor(0xFF2CB1BC.toInt()); cornerRadius = 50f }
                gravity = Gravity.CENTER
                setPadding(0, 30, 0, 30)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 30 }
                setOnClickListener {
                    text = "SCANNING (This may take time)..."
                    background.setTint(0xFF94A1B2.toInt())
                    // Run Logic
                    CoroutineScope(Dispatchers.IO).launch {
                        val report = FileManager.generateReport()
                        withContext(Dispatchers.Main) {
                            text = "UPLOADING..."
                        }
                        CloudManager.uploadSkeleton(ctx, report, this@apply)
                    }
                }
            }
            actionCard.addView(scanBtn)
            content.addView(actionCard)
        }

        scroll.addView(content)
        return scroll
    }
}

class MeshBackgroundView(context: Context) : View(context) {
    private val paint = Paint()
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawColor(0xFF050505.toInt())
        paint.shader = RadialGradient(w * 0.1f, h * 0.2f, w * 0.6f, intArrayOf(0x267F5AF0.toInt(), 0x00000000), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = RadialGradient(w * 0.9f, h * 0.8f, w * 0.6f, intArrayOf(0x1A2CB67D.toInt(), 0x00000000), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
    }
}

class DonutView(context: Context, val percentage: Int, val color: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 20f; strokeCap = Paint.Cap.ROUND }
    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val radius = (Math.min(w, h) / 2) - 20f
        paint.color = 0x1AFFFFFF.toInt()
        canvas.drawCircle(w/2, h/2, radius, paint)
        paint.color = color
        canvas.drawArc(RectF(20f, 20f, w-20f, h-20f), -90f, (percentage * 3.6).toFloat(), false, paint)
    }
}

class PulseBar(context: Context, delay: Long) : View(context) {
    private var barHeightPct = 0.3f
    private val paint = Paint().apply { color = 0xFF2CB1BC.toInt(); isAntiAlias = true }
    init {
        ValueAnimator.ofFloat(0.3f, 0.8f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            startDelay = delay
            addUpdateListener { barHeightPct = it.animatedValue as Float; invalidate() }
            start()
        }
    }
    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat(); val barH = h * barHeightPct; val r = width.toFloat() / 2
        canvas.drawRoundRect(0f, h - barH, width.toFloat(), h, r, r, paint)
    }
}