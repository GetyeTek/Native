package com.example.myandroid

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
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
    private val COLOR_GLASS = 0xFF1E1E1E.toInt()
    private val COLOR_ACCENT_CYAN = 0xFF00F2FF.toInt()
    private val COLOR_ACCENT_PURPLE = 0xFFBD00FF.toInt()
    private val COLOR_ACCENT_GREEN = 0xFF00FF9D.toInt()
    private val COLOR_TEXT_MUTED = 0xFF8B9BB4.toInt()
    private val COLOR_TEXT_WHITE = 0xFFFFFFFF.toInt()

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
                setStroke(2, 0x33FFFFFF.toInt())
            }
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 160).apply {
                gravity = Gravity.BOTTOM
                setMargins(40, 0, 40, 40)
            }
            elevation = 20f
        }

        val tabs = listOf("📊" to "DASH", "⚙️" to "SPECS", "🌍" to "NET", "🧩" to "TOOLS")
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
                setTextColor(COLOR_TEXT_MUTED)
            }
            val tvLabel = TextView(this).apply {
                text = label
                textSize = 9f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(COLOR_TEXT_MUTED)
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
                icon.setTextColor(COLOR_ACCENT_CYAN)
                icon.setShadowLayer(15f, 0f, 0f, COLOR_ACCENT_CYAN)
                label.setTextColor(COLOR_TEXT_WHITE)
            } else {
                icon.setTextColor(COLOR_TEXT_MUTED)
                icon.setShadowLayer(0f, 0f, 0f, 0) // Clear shadow
                label.setTextColor(COLOR_TEXT_MUTED)
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
            setBackgroundColor(0x00000000) // Transparent
        }
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 200)
        }

        // Header
        content.addView(createHeader(ctx, "My", "Android", "DASHBOARD"))

        // RAM & BATTERY ROW
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
        }
        
        // Logic for RAM
        val actManager = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        val totalRam = memInfo.totalMem.toDouble() / (1024 * 1024 * 1024)
        val availRam = memInfo.availMem.toDouble() / (1024 * 1024 * 1024)
        val usedRam = totalRam - availRam
        val ramPct = ((usedRam / totalRam) * 100).toInt()

        // Logic for Battery
        val batt = ctx.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val level = batt?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, 0) ?: 0

        row.addView(createGaugeCard(ctx, "RAM", "${DecimalFormat("#.1").format(usedRam)} GB", ramPct, 0xFFBD00FF.toInt()).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 15 }
        })
        row.addView(createGaugeCard(ctx, "POWER", "$level%", level, 0xFF00FF9D.toInt()).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 15 }
        })
        content.addView(row)

        // STORAGE BAR
        val dataDir = android.os.Environment.getDataDirectory()
        val stat = android.os.StatFs(dataDir.path)
        val totalStorage = (stat.blockCountLong * stat.blockSizeLong).toDouble() / (1024 * 1024 * 1024)
        val freeStorage = (stat.availableBlocksLong * stat.blockSizeLong).toDouble() / (1024 * 1024 * 1024)
        val usedStorage = totalStorage - freeStorage
        val storePct = ((usedStorage / totalStorage) * 100).toInt()

        content.addView(createProgressCard(ctx, "INTERNAL STORAGE", "${usedStorage.toInt()} GB used of ${totalStorage.toInt()} GB", storePct, 0xFF00F2FF.toInt()))

        // OS CARD
        content.addView(createDetailCard(ctx, "SYSTEM OS", mapOf(
            "Android Ver" to Build.VERSION.RELEASE,
            "SDK Level" to Build.VERSION.SDK_INT.toString(),
            "Security Patch" to (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else "Unknown"),
            "Build ID" to Build.ID
        )))

        scroll.addView(content)
        return scroll
    }
}

// --- 2. SPECS FRAGMENT ---
class SpecsFragment : Fragment() {
    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val scroll = ScrollView(ctx).apply { isFillViewport = true }
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 200)
        }

        content.addView(createHeader(ctx, "Hard", "ware", "SPECIFICATIONS"))

        // CPU
        val cores = Runtime.getRuntime().availableProcessors()
        content.addView(createDetailCard(ctx, "PROCESSOR", mapOf(
            "SoC / Board" to Build.BOARD.uppercase(),
            "Hardware" to Build.HARDWARE.uppercase(),
            "CPU Cores" to "$cores Cores",
            "ABIs" to Build.SUPPORTED_ABIS.joinToString(", "),
            "Bootloader" to Build.BOOTLOADER
        )))

        // DISPLAY
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        val dm = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(dm)
        val refresh = wm.defaultDisplay.refreshRate
        val hdr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && wm.defaultDisplay.isHdr) "Supported" else "No"
        
        content.addView(createDetailCard(ctx, "DISPLAY", mapOf(
            "Resolution" to "${dm.widthPixels} x ${dm.heightPixels}",
            "Density" to "${dm.densityDpi} DPI (${dm.density}x)",
            "Refresh Rate" to "${refresh.toInt()} Hz",
            "HDR Capable" to hdr
        )))

        // DEVICE
        content.addView(createDetailCard(ctx, "MODEL INFO", mapOf(
            "Manufacturer" to Build.MANUFACTURER.uppercase(),
            "Model" to Build.MODEL,
            "Brand" to Build.BRAND,
            "Device Name" to Build.DEVICE
        )))

        scroll.addView(content)
        return scroll
    }
}

// --- 3. NET FRAGMENT ---
class NetFragment : Fragment() {
    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val scroll = ScrollView(ctx).apply { isFillViewport = true }
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 200)
        }

        content.addView(createHeader(ctx, "Net", "work", "CONNECTIVITY"))

        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(net)
        val link = cm.getLinkProperties(net)

        val isConnected = net != null
        val type = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WiFi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true -> "VPN"
            else -> "None"
        }

        // BIG STATUS CARD
        val statusCard = createGlassContainer(ctx).apply {
            gravity = Gravity.CENTER
            setPadding(0, 60, 0, 60)
            addView(TextView(ctx).apply {
                text = if(isConnected) "📡" else "🔌"
                textSize = 50f
                gravity = Gravity.CENTER
            })
            addView(TextView(ctx).apply {
                text = if(isConnected) "CONNECTED" else "DISCONNECTED"
                textSize = 22f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(0, 20, 0, 0)
            })
            addView(TextView(ctx).apply {
                text = "via $type"
                textSize = 14f
                setTextColor(0xFF00F2FF.toInt())
                gravity = Gravity.CENTER
            })
        }
        content.addView(statusCard)

        // DETAILS
        if (isConnected) {
            val downSpeed = (caps?.linkDownstreamBandwidthKbps ?: 0) / 1000
            val upSpeed = (caps?.linkUpstreamBandwidthKbps ?: 0) / 1000
            val isMetered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false
            val ip = link?.linkAddresses?.firstOrNull()?.address?.hostAddress ?: "Unknown"

            content.addView(createDetailCard(ctx, "LINK METRICS", mapOf(
                "Down Speed" to "$downSpeed Mbps",
                "Up Speed" to "$upSpeed Mbps",
                "Local IP" to ip,
                "Metered" to if(isMetered) "Yes" else "No",
                "VPN Active" to if(caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) "Yes" else "No"
            )))
        }

        scroll.addView(content)
        return scroll
    }
}

// --- 4. TOOLS FRAGMENT (SENSORS) ---
class ToolsFragment : Fragment() {
    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val scroll = ScrollView(ctx).apply { isFillViewport = true }
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 200)
        }

        content.addView(createHeader(ctx, "Sen", "sors", "HARDWARE TOOLS"))

        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val list = sm.getSensorList(Sensor.TYPE_ALL)

        content.addView(TextView(ctx).apply {
            text = "${list.size} SENSORS DETECTED"
            textSize = 12f
            setTextColor(0xFF00FF9D.toInt())
            setPadding(0, 0, 0, 20)
            letterSpacing = 0.1f
        })

        list.forEach { s ->
            val item = createGlassContainer(ctx).apply {
                setPadding(30, 30, 30, 30)
                orientation = LinearLayout.HORIZONTAL
                
                val icon = TextView(ctx).apply {
                    text = "💠"
                    textSize = 18f
                    setPadding(0, 0, 30, 0)
                }
                val info = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(ctx).apply {
                        text = s.name
                        textSize = 14f
                        setTextColor(Color.WHITE)
                        typeface = Typeface.DEFAULT_BOLD
                    })
                    addView(TextView(ctx).apply {
                        text = "Vendor: ${s.vendor}\nPower: ${s.power} mA"
                        textSize = 10f
                        setTextColor(0xFF8B9BB4.toInt())
                    })
                }
                addView(icon)
                addView(info)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = 20
                }
            }
            content.addView(item)
        }

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
    val title = TextView(ctx).apply {
        text = "$t1 $t2"
        textSize = 32f
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
    }
    val subtitle = TextView(ctx).apply {
        text = sub
        textSize = 12f
        setTextColor(0xFF00F2FF.toInt())
        letterSpacing = 0.2f
    }
    layout.addView(title)
    layout.addView(subtitle)
    return layout
}

fun createGlassContainer(ctx: Context): LinearLayout {
    return LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            setColor(0xFF1E1E1E.toInt()) // Base glass color
            alpha = 230 // Slight transparency
            cornerRadius = 30f
            setStroke(2, 0x1AFFFFFF.toInt())
        }
        elevation = 15f
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
            bottomMargin = 30
        }
    }

    card.addView(TextView(ctx).apply {
        text = title
        textSize = 11f
        setTextColor(0xFF00F2FF.toInt())
        letterSpacing = 0.15f
        setPadding(0, 0, 0, 20)
        typeface = Typeface.DEFAULT_BOLD
    })

    data.forEach { (k, v) ->
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 15)
        }
        row.addView(TextView(ctx).apply {
            text = k
            textSize = 13f
            setTextColor(0xFF8B9BB4.toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(ctx).apply {
            text = v
            textSize = 13f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.END
        })
        card.addView(row)
    }
    return card
}