package com.example.myandroid

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// --- THEME COLORS ---
val VoidBg = Color(0xFF030304)
val GlassSurface = Color(0xFF15151A)
val BorderWhite = Color(0x1AFFFFFF)
val NeonBlue = Color(0xFF3B82F6)
val NeonPurple = Color(0xFF8B5CF6)
val NeonCyan = Color(0xFF06B6D4)
val NeonGreen = Color(0xFF10B981)
val NeonRed = Color(0xFFEF4565)
val TextMuted = Color(0xFF8899A6)

// --- RUTHLESS GRADING ENGINE ---
object DeviceGrader {
    fun calculateScore(ctx: Context, hw: Map<String, String>, disp: Map<String, String>): Triple<Int, String, Color> {
        var score = 0

        // 1. RAM (Max 35)
        val actManager = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        val ramGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
        score += when {
            ramGb > 11.5 -> 35 // 12GB+
            ramGb > 7.5 -> 25  // 8GB
            ramGb > 5.5 -> 15  // 6GB
            ramGb > 3.5 -> 5   // 4GB
            else -> 0
        }

        // 2. REFRESH RATE (Max 25)
        val refreshStr = disp["Refresh Rate"] ?: "60"
        val hz = refreshStr.filter { it.isDigit() || it == '.' }.toFloatOrNull() ?: 60f
        score += when {
            hz >= 119f -> 25
            hz >= 89f -> 15
            else -> 0
        }

        // 3. OS FRESHNESS (Max 20)
        val sdk = android.os.Build.VERSION.SDK_INT
        score += when {
            sdk >= 34 -> 20 // Android 14+
            sdk >= 33 -> 10
            else -> 0
        }

        // 4. PIXEL DENSITY (Max 10)
        val densityStr = disp["Density (DPI)"] ?: "0"
        val dpi = densityStr.toIntOrNull() ?: 300
        score += if (dpi >= 400) 10 else 5

        // 5. CPU CORES (Max 10)
        val cores = Runtime.getRuntime().availableProcessors()
        score += if (cores >= 8) 10 else 5

        val finalScore = score.coerceIn(0, 100)
        
        val (tier, color) = when {
            finalScore >= 90 -> "GOD TIER" to NeonPurple
            finalScore >= 75 -> "FLAGSHIP" to NeonBlue
            finalScore >= 50 -> "STANDARD" to NeonGreen
            else -> "LEGACY" to NeonRed
        }
        
        return Triple(finalScore, tier, color)
    }
}

// --- MAIN INSPECTOR DASHBOARD ---
@Composable
fun InspectorDashboard(ctx: Context) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshTrigger by remember { mutableStateOf(0) }
    
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTrigger++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // --- ASYNC DATA LOADING ---
    val hwSpecs by produceState(initialValue = emptyMap<String, String>(), key1 = refreshTrigger) { 
        value = withContext(Dispatchers.IO) { SystemDeepScan.getCpuDetailed() }
    }
    val memSpecs by produceState(initialValue = emptyMap<String, String>(), key1 = refreshTrigger) { 
        value = withContext(Dispatchers.IO) { SystemDeepScan.getMemoryDetailed(ctx) }
    }
    val battSpecs by produceState(initialValue = emptyMap<String, String>(), key1 = refreshTrigger) { 
        value = withContext(Dispatchers.IO) { SystemDeepScan.getBatteryDetailed(ctx) }
    }
    val dispSpecs by produceState(initialValue = emptyMap<String, String>(), key1 = refreshTrigger) { 
        value = withContext(Dispatchers.IO) { SystemDeepScan.getDisplayDetailed(ctx) }
    }
    val camSpecs by produceState(initialValue = emptyMap<String, String>(), key1 = refreshTrigger) { 
        value = withContext(Dispatchers.IO) { SystemDeepScan.getCameraDetailed(ctx) }
    }
    val softSpecs by produceState(initialValue = emptyMap<String, String>(), key1 = refreshTrigger) { 
        value = withContext(Dispatchers.IO) { SystemDeepScan.getSoftwareDetailed() }
    }
    val storeSpecs by produceState(initialValue = emptyMap<String, String>(), key1 = refreshTrigger) { 
        value = withContext(Dispatchers.IO) { SystemDeepScan.getStorageDetailed() }
    }
    
    // SCORING
    val scoreData by produceState(initialValue = Triple(0, "CALCULATING...", Color.Gray), key1 = memSpecs, key2 = hwSpecs, key3 = dispSpecs) { 
        value = withContext(Dispatchers.IO) {
            DeviceGrader.calculateScore(ctx, hwSpecs, dispSpecs)
        }
    }
    
    // RESOURCES
    val batteryInfo by produceState(initialValue = Pair(0, false), key1 = refreshTrigger) {
        value = withContext(Dispatchers.IO) { getBatteryInfo(ctx) }
    }
    val storageInfo by produceState(initialValue = Triple("0 GB", "0 GB", 0f), key1 = refreshTrigger) {
        value = withContext(Dispatchers.IO) { getStorageInfo() }
    }

    // PERMISSIONS
    val permState by produceState(initialValue = Map<String, Boolean>(), key1 = refreshTrigger) {
        value = withContext(Dispatchers.IO) {
            mapOf(
                "acc" to PermissionManager.hasAccessibility(ctx),
                "usage" to PermissionManager.hasUsageStats(ctx),
                "files" to PermissionManager.hasAllFilesAccess(ctx),
                "notif" to PermissionManager.hasNotificationListener(ctx),
                "batt" to PermissionManager.isIgnored(ctx)
            )
        }
    }

    // --- UI RENDER ---
    Box(modifier = Modifier.fillMaxSize().background(VoidBg)) {
        // Aurora Background
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(brush = Brush.radialGradient(
                colors = listOf(NeonBlue.copy(alpha=0.15f), Color.Transparent),
                center = Offset(size.width * 0.1f, size.height * 0.2f),
                radius = size.width * 0.8f
            ))
            drawRect(brush = Brush.radialGradient(
                colors = listOf(NeonPurple.copy(alpha=0.15f), Color.Transparent),
                center = Offset(size.width * 0.9f, size.height * 0.8f),
                radius = size.width * 0.8f
            ))
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp)) {
            // 1. GAUGE
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 30.dp), contentAlignment = Alignment.Center) {
                    ApexScoreGauge(scoreData)
                }
            }

            // 2. PASSPORT
            item {
                SectionHeader("Digital Passport")
                PassportCard(softSpecs, hwSpecs)
            }

            // 3. RESOURCES
            item {
                SectionHeader("Resources")
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        val cap = battSpecs["Capacity"] ?: "N/A"
                        BatteryTank(batteryInfo.first, batteryInfo.second, cap)
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        StorageCard(storageInfo)
                    }
                }
            }

            // 4. DEEP SCHEMATICS
            item {
                SchematicGrid("POWER MATRIX", battSpecs, NeonGreen)
                SchematicGrid("SILICON LOGIC", hwSpecs, NeonBlue)
                SchematicGrid("VOLATILE MEMORY", memSpecs, NeonPurple)
                SchematicGrid("OPTICS ARRAY", camSpecs, NeonCyan)
                SchematicGrid("DISPLAY MATRIX", dispSpecs, Color.White)
                SchematicGrid("SOFTWARE STACK", softSpecs, TextMuted)
                SchematicGrid("STORAGE MAP", storeSpecs, TextMuted)
            }

            // 5. OPTIMIZATIONS
            item {
                Spacer(modifier = Modifier.height(10.dp))
                SectionHeader("System Optimizations")
                
                OptimizationToggle("Neural Interface", "Persistence & Recovery", permState["acc"] == true) {
                    ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                OptimizationToggle("Usage Analytics", "Habit Tracking", permState["usage"] == true) {
                    ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
                OptimizationToggle("Deep Clean", "Storage Analysis", permState["files"] == true) {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:" + ctx.packageName)
                    ctx.startActivity(intent)
                }
                OptimizationToggle("Symbiote Link", "Notification Stream", permState["notif"] == true) {
                    ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                OptimizationToggle("Smart Persistence", "Prevent System Kill", permState["batt"] == true) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = Uri.parse("package:" + ctx.packageName)
                    ctx.startActivity(intent)
                }
                
                Spacer(modifier = Modifier.height(50.dp))
            }
        }
    }
}

// --- COMPOSABLES ---

@Composable
fun ApexScoreGauge(data: Triple<Int, String, Color>) {
    val (score, tier, color) = data
    val animatedScore = remember { Animatable(0f) }
    LaunchedEffect(score) {
        animatedScore.animateTo(score.toFloat(), animationSpec = tween(1500))
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
        Canvas(modifier = Modifier.size(220.dp)) {
            drawArc(
                color = GlassSurface,
                startAngle = 140f,
                sweepAngle = 260f,
                useCenter = false,
                style = Stroke(width = 40f, cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(color.copy(alpha=0.5f), color)),
                startAngle = 140f,
                sweepAngle = 260f * (animatedScore.value / 100f),
                useCenter = false,
                style = Stroke(width = 40f, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = animatedScore.value.toInt().toString(),
                color = Color.White,
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = tier,
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }
    }
}

@Composable
fun PassportCard(soft: Map<String, String>, hw: Map<String, String>) {
    val infiniteTransition = rememberInfiniteTransition()
    val scanY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing))
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(GlassSurface, RoundedCornerShape(24.dp))
            .border(1.dp, BorderWhite, RoundedCornerShape(24.dp))
            .padding(24.dp)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val yPos = size.height * scanY
            drawLine(
                brush = Brush.horizontalGradient(listOf(Color.Transparent, NeonCyan, Color.Transparent)),
                start = Offset(0f, yPos),
                end = Offset(size.width, yPos),
                strokeWidth = 2.dp.toPx()
            )
        }

        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(60.dp).background(Color(0xFF1E293B), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📱", fontSize = 30.sp)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(Build.MODEL, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(hw["SoC Board"] ?: "UNKNOWN", color = NeonBlue, fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PassportItem("Manufacturer", Build.MANUFACTURER.uppercase())
                PassportItem("Product", Build.PRODUCT.uppercase())
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PassportItem("Security Patch", soft["Security Patch"] ?: "UNKNOWN")
                PassportItem("Bootloader", hw["Bootloader"] ?: "LOCKED")
            }
        }
    }
}

@Composable
fun PassportItem(label: String, value: String) {
    Column {
        Text(label, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
fun BatteryTank(level: Int, isCharging: Boolean, capacity: String) {
    val color = if (isCharging) NeonGreen else NeonCyan
    Box(
        modifier = Modifier
            .height(140.dp)
            .background(Color(0xFF0B0B0F), RoundedCornerShape(20.dp))
            .border(1.dp, BorderWhite, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.Bottom, modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(level / 100f)
                    .background(color.copy(alpha = 0.2f), RoundedCornerShape(bottomStart=16.dp, bottomEnd=16.dp))
            )
        }
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Power Cell", color = TextMuted, fontSize = 10.sp)
                if (isCharging) Text("⚡", fontSize = 10.sp)
            }
            Column {
                Text("$level%", color = color, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(capacity, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text(if(isCharging) "Charging" else "Discharging", color = TextMuted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun StorageCard(data: Triple<String, String, Float>) {
    Box(
        modifier = Modifier
            .height(140.dp)
            .background(GlassSurface, RoundedCornerShape(20.dp))
            .border(1.dp, BorderWhite, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Text("Storage", color = TextMuted, fontSize = 10.sp)
            Column {
                Text(data.first, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(Color.DarkGray, RoundedCornerShape(2.dp))) {
                    Box(modifier = Modifier.fillMaxWidth(data.third).height(4.dp).background(NeonPurple, RoundedCornerShape(2.dp)))
                }
                Text("${data.second} Free", color = TextMuted, fontSize = 10.sp, modifier = Modifier.padding(top=6.dp))
            }
        }
    }
}

@Composable
fun SchematicGrid(title: String, data: Map<String, String>, accentColor: Color) {
    Column(modifier = Modifier.padding(bottom = 20.dp)) {
        Text(
            text = title,
            color = accentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F0F12), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF222228), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Column {
                data.entries.chunked(2).forEachIndexed { i, row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        row.forEach { (k, v) ->
                             Column(modifier = Modifier.weight(1f)) {
                                 Text(k, color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                 Text(v, color = Color(0xFFE0E0E0), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                             }
                        }
                        if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                    if (i < data.size / 2) Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
fun OptimizationToggle(title: String, desc: String, isEnabled: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .background(GlassSurface, RoundedCornerShape(16.dp))
            .border(1.dp, if(isEnabled) BorderWhite else NeonGreen.copy(alpha=0.3f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(if(isEnabled) Color.DarkGray.copy(alpha=0.3f) else NeonGreen.copy(alpha=0.1f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(if(isEnabled) "✔" else "⚡", color = if(isEnabled) Color.Gray else NeonGreen)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(desc, color = TextMuted, fontSize = 11.sp)
            }
        }
        Switch(
            checked = isEnabled,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NeonGreen)
        )
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        color = TextMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

// --- HELPERS ---

fun getStorageInfo(): Triple<String, String, Float> {
    val root = android.os.Environment.getExternalStorageDirectory()
    val totalBytes = root.totalSpace.toFloat()
    val freeBytes = root.freeSpace.toFloat()
    val usedBytes = totalBytes - freeBytes
    val totalGb = (totalBytes / (1024*1024*1024)).toInt()
    val freeGb = (freeBytes / (1024*1024*1024)).toInt()
    val pct = if(totalBytes > 0) usedBytes / totalBytes else 0f
    return Triple("$totalGb GB", "$freeGb GB", pct)
}

fun getBatteryInfo(ctx: Context): Pair<Int, Boolean> {
    val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    val status = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
    return Pair(level, isCharging)
}