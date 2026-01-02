package com.example.myandroid

import android.animation.ValueAnimator
import android.content.Context
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
            // Glass Blur for Dock
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                setRenderEffect(android.graphics.RenderEffect.createBlurEffect(20f, 20f, android.graphics.Shader.TileMode.MIRROR))
            }
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 160).apply {
                gravity = Gravity.BOTTOM
                setMargins(40, 0, 40, 40)
            }
            elevation = 20f
        }

        val tabs = listOf("🏠" to "DASH", "⚡" to "SPECS", "📡" to "NET", "🛠️" to "TOOLS")
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
        override fun getItemCount(): Int = 4
        override fun createFragment(position: Int): Fragment = when(position) {
            0 -> DashboardFragment()
            1 -> SpecsFragment()
            2 -> NetFragment()
            3 -> ToolsFragment()
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
            text = Build.SOC_MODEL.uppercase(); textSize=10f; setTextColor(0xFF94A1B2.toInt()); typeface=Typeface.DEFAULT_BOLD
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

        content.addView(createHeader(ctx, "Sen", "sors", "MATRIX"))

        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val list = sm.getSensorList(Sensor.TYPE_ALL)

        // Create Grid manually with rows of 2
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
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 5 }
            })
            currentRow.addView(item)
        }
        if (currentRow.childCount > 0) content.addView(currentRow)

        scroll.addView(content)
        return scroll
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
        typeface = Typeface.create(Typeface.DEFAULT, 900) // Black weight
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
        // Android 12 Blur
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            setRenderEffect(android.graphics.RenderEffect.createBlurEffect(30f, 30f, android.graphics.Shader.TileMode.MIRROR))
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