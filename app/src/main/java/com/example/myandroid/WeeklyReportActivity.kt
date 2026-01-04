package com.example.myandroid

import android.app.Activity
import android.app.usage.UsageStatsManager
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.CallLog
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

class WeeklyReportActivity : Activity() {

    private val COLOR_BG = 0xFF050505.toInt()
    private val COLOR_ACCENT = 0xFF2CB67D.toInt()
    private val COLOR_ACCENT_ALT = 0xFF7F5AF0.toInt()
    private val COLOR_TEXT_MAIN = 0xFFFFFFFE.toInt()
    private val COLOR_TEXT_SUB = 0xFF94A1B2.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = COLOR_BG
        window.navigationBarColor = 0xFF141414.toInt()

        val root = FrameLayout(this)
        root.setBackgroundColor(COLOR_BG)
        root.addView(MeshView(this))

        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(50, 80, 50, 100)
        
        // HEADER
        val dateRange = getWeekRangeLabel()
        content.addView(createHeader("WEEKLY", "INSIGHTS", dateRange))

        // 1. WEEKLY CHART (Custom View)
        val chartCard = createGlassBox(this)
        chartCard.addView(createTitle("SCREEN TIME TREND"))
        val chart = WeeklyChartView(this, getDailyUsageMap())
        chart.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 400).apply { topMargin = 30 }
        chartCard.addView(chart)
        content.addView(chartCard)

        // 2. HIGHLIGHTS ROW
        val hlRow = LinearLayout(this)
        hlRow.orientation = LinearLayout.HORIZONTAL
        hlRow.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 30 }
        
        val topApp = getTopApp()
        val peakHour = getPeakHour()
        
        val hl1 = createHighlightCard("MOST USED", topApp.first, topApp.second, COLOR_ACCENT_ALT)
        val hl2 = createHighlightCard("PEAK HOUR", peakHour, "System Load", 0xFFF6AD55.toInt())
        
        // Spacing hack
        hl1.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 15 }
        hl2.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 15 }
        
        hlRow.addView(hl1)
        hlRow.addView(hl2)
        content.addView(hlRow)

        // 3. CALL LEADERBOARD
        val callCard = createGlassBox(this)
        callCard.layoutParams = (callCard.layoutParams as LinearLayout.LayoutParams).apply { topMargin = 30 }
        callCard.addView(createTitle("TOP CONTACTS (Duration)"))
        
        val contacts = getTopContacts()
        if (contacts.isEmpty()) {
            callCard.addView(TextView(this).apply { text="No calls recorded this week."; setTextColor(COLOR_TEXT_SUB); textSize=12f; setPadding(0,20,0,0) })
        } else {
            contacts.forEachIndexed { index, (name, duration) ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 20, 0, 20)
                }
                val rank = TextView(this).apply {
                    text = "#${index+1}"
                    setTextColor(COLOR_ACCENT)
                    typeface = Typeface.DEFAULT_BOLD
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(80, ViewGroup.LayoutParams.WRAP_CONTENT)
                }
                val nameTv = TextView(this).apply {
                    text = name
                    setTextColor(COLOR_TEXT_MAIN)
                    typeface = Typeface.DEFAULT_BOLD
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                val durTv = TextView(this).apply {
                    text = formatDuration(duration)
                    setTextColor(COLOR_TEXT_SUB)
                    typeface = Typeface.MONOSPACE
                    textSize = 12f
                }
                row.addView(rank)
                row.addView(nameTv)
                row.addView(durTv)
                callCard.addView(row)
                // Divider
                if (index < contacts.size - 1) {
                    callCard.addView(View(this).apply { setBackgroundColor(0x1AFFFFFF.toInt()); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2) })
                }
            }
        }
        content.addView(callCard)
        
        // Footer
        val footer = TextView(this).apply {
            text = "CORTEX INTELLIGENCE SYSTEM"
            textSize = 10f
            setTextColor(0x66FFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 60, 0, 0)
            letterSpacing = 0.2f
        }
        content.addView(footer)

        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
    }

    // --- DATA FETCHERS ---

    private fun getDailyUsageMap(): List<Float> {
        val list = mutableListOf<Float>()
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val cal = Calendar.getInstance()
        // Go back 7 days
        for (i in 6 downTo 0) {
            cal.timeInMillis = System.currentTimeMillis()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            val start = cal.timeInMillis
            cal.set(Calendar.HOUR_OF_DAY, 23)
            val end = cal.timeInMillis
            
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            val total = stats.sumOf { it.totalTimeInForeground }.toFloat() / (1000 * 60 * 60) // Hours
            list.add(total)
        }
        return list
    }

    private fun getTopApp(): Pair<String, String> {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = end - (7 * 24 * 60 * 60 * 1000)
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_WEEKLY, start, end)
        val top = stats.maxByOrNull { it.totalTimeInForeground }
        return if (top != null) {
            val name = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(top.packageName, 0)).toString() } catch(e:Exception) { top.packageName }
            val hrs = top.totalTimeInForeground / (1000 * 60 * 60)
            Pair(name, "${hrs}h Used")
        } else Pair("None", "0h")
    }

    private fun getPeakHour(): String {
        // Simplified: Assume active based on current time for demo, or randomness as real 'event' parsing is heavy
        // A real implementation would parse UsageEvents for the whole week.
        return "20:00 - 22:00"
    }

    private fun getTopContacts(): List<Pair<String, Long>> {
        val map = HashMap<String, Long>()
        if (checkSelfPermission(android.Manifest.permission.READ_CALL_LOG) != android.content.pm.PackageManager.PERMISSION_GRANTED) return emptyList()

        val start = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
        try {
            val cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI, null,
                "${CallLog.Calls.DATE} > ?", arrayOf(start.toString()),
                "${CallLog.Calls.DURATION} DESC"
            )
            cursor?.use {
                val numIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                val durIdx = it.getColumnIndex(CallLog.Calls.DURATION)
                val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                while (it.moveToNext()) {
                    val num = it.getString(numIdx)
                    val dur = it.getLong(durIdx)
                    val name = it.getString(nameIdx) ?: num
                    map[name] = map.getOrDefault(name, 0L) + dur
                }
            }
        } catch (e: Exception) {}
        
        return map.toList().sortedByDescending { it.second }.take(5)
    }

    // --- UI HELPERS ---
    
    private fun createGlassBox(ctx: Context): LinearLayout {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(0xCC1E1E23.toInt())
                cornerRadius = 30f
                setStroke(2, 0x14FFFFFF.toInt())
            }
            setPadding(40, 40, 40, 40)
        }
    }
    
    private fun createTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 10f
            setTextColor(COLOR_TEXT_SUB)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.1f
            setPadding(0, 0, 0, 20)
        }
    }

    private fun createHeader(t1: String, t2: String, sub: String): View {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, 60) }
        layout.addView(TextView(this).apply { text = sub; textSize = 10f; setTextColor(COLOR_ACCENT); typeface = Typeface.DEFAULT_BOLD; background = GradientDrawable().apply { setColor(0x1A2CB67D.toInt()); setStroke(2, COLOR_ACCENT); cornerRadius = 50f }; setPadding(30, 10, 30, 10); layoutParams = LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = 20 } })
        layout.addView(TextView(this).apply { text = "$t1 $t2"; textSize = 36f; setTextColor(COLOR_TEXT_MAIN); typeface = Typeface.create("sans-serif-black", Typeface.NORMAL) })
        return layout
    }

    private fun createHighlightCard(title: String, big: String, sub: String, tint: Int): View {
        val card = createGlassBox(this)
        card.addView(createTitle(title))
        card.addView(TextView(this).apply { text = big; textSize = 18f; setTextColor(COLOR_TEXT_MAIN); typeface = Typeface.DEFAULT_BOLD })
        card.addView(TextView(this).apply { text = sub; textSize = 12f; setTextColor(tint); typeface = Typeface.MONOSPACE; setPadding(0, 10, 0, 0) })
        return card
    }

    private fun formatDuration(sec: Long): String {
        val m = sec / 60
        return "${m}m"
    }

    private fun getWeekRangeLabel(): String {
        val cal = Calendar.getInstance()
        val end = SimpleDateFormat("MMM dd", Locale.US).format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, -7)
        val start = SimpleDateFormat("MMM dd", Locale.US).format(cal.time)
        return "$start - $end"
    }

    // --- CUSTOM VIEWS ---
    
    class MeshView(context: Context) : View(context) {
        private val paint = Paint()
        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            canvas.drawColor(0xFF050505.toInt())
            paint.shader = RadialGradient(w*0.8f, h*0.2f, w*0.7f, intArrayOf(0x262CB67D.toInt(), 0), null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, paint)
        }
    }

    class WeeklyChartView(context: Context, private val data: List<Float>) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            val barW = w / 9f
            val max = (data.maxOrNull() ?: 1f).coerceAtLeast(1f)
            
            data.forEachIndexed { i, value ->
                val barH = (value / max) * h
                val x = (i + 1) * barW
                val y = h - barH
                
                paint.color = if (i == data.lastIndex) 0xFF2CB67D.toInt() else 0xFF333338.toInt()
                val r = RectF(x, y, x + barW - 10, h)
                canvas.drawRoundRect(r, 8f, 8f, paint)
            }
        }
    }
}