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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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

// --- CINEMATIC THEME ---
// Import the new schematic grid
// Note: SchematicUI.kt must be in the same package
val VoidBg = Color(0xFF030304)
val GlassSurface = Color(0xFF15151A)
val BorderWhite = Color(0x1AFFFFFF)
val NeonBlue = Color(0xFF3B82F6)
val NeonPurple = Color(0xFF8B5CF6)
val NeonCyan = Color(0xFF06B6D4)
val NeonGreen = Color(0xFF10B981)
val TextMuted = Color(0xFF8899A6)

@Composable
fun InspectorDashboard(ctx: Context) {
    // Force Recomposition when returning from Settings
    var refreshTrigger by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // --- ASYNC DATA LOADING (Fixes Scroll Lag) ---
    // --- ASYNC DEEP SCAN ---
    // Loading massive data sets off main thread
    val hwSpecs by produceState(initialValue = emptyMap(), producer = { 
        value = withContext(Dispatchers.IO) { SystemDeepScan.getCpuDetailed() }
    })
    val memSpecs by produceState(initialValue = emptyMap(), producer = { 
        value = withContext(Dispatchers.IO) { SystemDeepScan.getMemoryDetailed(ctx) }
    })
    val battSpecs by produceState(initialValue = emptyMap(), producer = { 
        value = withContext(Dispatchers.IO) { SystemDeepScan.getBatteryDetailed(ctx) }
    })
    val dispSpecs by produceState(initialValue = emptyMap(), producer = { 
        value = withContext(Dispatchers.IO) { SystemDeepScan.getDisplayDetailed(ctx) }
    })
    val camSpecs by produceState(initialValue = emptyMap(), producer = { 
        value = withContext(Dispatchers.IO) { SystemDeepScan.getCameraDetailed(ctx) }
    })
    val softSpecs by produceState(initialValue = emptyMap(), producer = { 
        value = withContext(Dispatchers.IO) { SystemDeepScan.getSoftwareDetailed() }
    })
    val storeSpecs by produceState(initialValue = emptyMap(), producer = { 
        value = withContext(Dispatchers.IO) { SystemDeepScan.getStorageDetailed() }
    })
    
    // Calculate Score based on RAM and Cores
    val score = remember(memSpecs, hwSpecs) {
        var s = 30
        val ram = memSpecs["Physical RAM"]?.filter { it.isDigit() }?.toIntOrNull() ?: 0
        if (ram > 5000) s += 20
        if (ram > 7000) s += 20
        val cores = hwSpecs["CPU Cores"]?.toIntOrNull() ?: 4
        s += (cores * 3)
        s.coerceIn(0, 99)
    }

    // --- UI RENDER ---
    Box(modifier = Modifier.fillMaxSize().background(VoidBg)) {
        // 0. AMBIENT BACKGROUND (Aurora Effect)
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
        // 1. APEX SCORE GAUGE
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ApexScoreGauge(score = score)
        }

        Spacer(modifier = Modifier.height(30.dp))

        // 2. DEVICE PASSPORT
        SectionHeader("Digital Passport")
        PassportCard(hw)

        // 3. DEEP INSPECTION SCHEMATICS
        Spacer(modifier = Modifier.height(24.dp))

        SchematicGrid("POWER PLANT", battSpecs, NeonGreen)
        SchematicGrid("SILICON LOGIC", hwSpecs, NeonBlue)
        SchematicGrid("VOLATILE MEMORY", memSpecs, NeonPurple)
        SchematicGrid("OPTICS & SENSORS", camSpecs, NeonCyan)
        SchematicGrid("DISPLAY MATRIX", dispSpecs, Color.White)
        SchematicGrid("OPERATING SYSTEM", softSpecs, TextMuted)
        SchematicGrid("STORAGE PARTITIONS", storeSpecs, TextMuted)

        // 4. SYSTEM OPTIMIZATIONS
        Spacer(modifier = Modifier.height(30.dp))
        SectionHeader("Feature Unlocks")

        // A. ACCESSIBILITY (The Missing Toggle)
        OptimizationToggle(
            title = "Neural Interface",
            desc = "Required for persistence & data recovery.",
            isEnabled = PermissionManager.hasAccessibility(ctx),
            onToggle = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        )

        // B. USAGE STATS
        OptimizationToggle(
            title = "Usage Analytics",
            desc = "Unlock screen time & habit tracking.",
            isEnabled = PermissionManager.hasUsageStats(ctx),
            onToggle = { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        )

        OptimizationToggle(
            title = "Deep Clean Access",
            desc = "Unlock full storage analysis.",
            isEnabled = PermissionManager.hasAllFilesAccess(ctx),
            onToggle = {
                 val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                 intent.data = Uri.parse("package:" + ctx.packageName)
                 ctx.startActivity(intent)
            }
        )

        OptimizationToggle(
            title = "Smart Persistence",
            desc = "Prevent background closing.",
            isEnabled = PermissionManager.isIgnored(ctx),
            onToggle = {
                 val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                 intent.data = Uri.parse("package:" + ctx.packageName)
                 ctx.startActivity(intent)
            }
        )
        
        OptimizationToggle(
            title = "Symbiote Link",
            desc = "Connect to notification stream.",
            isEnabled = PermissionManager.hasNotificationListener(ctx),
            onToggle = { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        )

        Spacer(modifier = Modifier.height(50.dp))
    }
}

@Composable
fun ApexScoreGauge(score: Int) {
    val animatedScore = remember { Animatable(0f) }
    LaunchedEffect(score) {
        animatedScore.animateTo(score.toFloat(), animationSpec = tween(1500, easing = FastOutSlowInEasing))
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
        Canvas(modifier = Modifier.size(220.dp)) {
            // Track
            drawArc(
                color = GlassSurface,
                startAngle = 140f,
                sweepAngle = 260f,
                useCenter = false,
                style = Stroke(width = 40f, cap = StrokeCap.Round)
            )
            // Fill
            drawArc(
                brush = Brush.sweepGradient(listOf(NeonBlue, NeonPurple, NeonCyan)),
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
                fontWeight = FontWeight.Bold,
                letterSpacing = (-2).sp
            )
            Text(
                text = "APEX TIER",
                color = NeonCyan,
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PassportCard(hw: Map<String, String>) {
    // Scanner Animation
    val infiniteTransition = rememberInfiniteTransition()
    val scanY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(GlassSurface, RoundedCornerShape(24.dp))
            .border(1.dp, BorderWhite, RoundedCornerShape(24.dp))
            .padding(24.dp)
    ) {
        // Scan Line
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
                    modifier = Modifier
                        .size(60.dp)
                        .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
                        .border(1.dp, BorderWhite, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📱", fontSize = 30.sp)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(Build.MODEL, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(hw["SoC Board"] ?: "Unknown", color = NeonBlue, fontSize = 12.sp, letterSpacing = 1.sp)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Divider(color = BorderWhite)
            Spacer(modifier = Modifier.height(16.dp))
            Spacer(modifier = Modifier.height(16.dp))
            // Detailed Rows
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PassportItem("Manufacturer", Build.MANUFACTURER.uppercase())
                PassportItem("Product", Build.PRODUCT.uppercase())
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PassportItem("Model", Build.MODEL)
                PassportItem("Device", Build.DEVICE)
            }
        }
    }
}

@Composable
fun PassportItem(label: String, value: String) {
    Column {
        Text(label, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom=4.dp))
        Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun BatteryTank(level: Int, isCharging: Boolean) {
    val color = if (isCharging) NeonGreen else NeonCyan
    Box(
        modifier = Modifier
            .height(140.dp)
            .background(Color(0xFF0B0B0F), RoundedCornerShape(20.dp))
            .border(1.dp, BorderWhite, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        // Liquid Layer
        Column(verticalArrangement = Arrangement.Bottom, modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(level / 100f)
                    .background(color.copy(alpha = 0.2f), RoundedCornerShape(bottomStart=16.dp, bottomEnd=16.dp))
            )
        }
        // Content Layer
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Text("Capacity", color = TextMuted, fontSize = 10.sp)
            Column {
                Text("$level%", color = color, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(if(isCharging) "Charging" else "Discharging", color = Color.White, fontSize = 10.sp)
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
                // Bar
                Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(Color.DarkGray, RoundedCornerShape(2.dp))) {
                    Box(modifier = Modifier.fillMaxWidth(data.third).height(4.dp).background(NeonPurple, RoundedCornerShape(2.dp)))
                }
                Text("${data.second} Free", color = TextMuted, fontSize = 10.sp, modifier = Modifier.padding(top=6.dp))
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
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = NeonGreen,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = Color.Black
            )
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

// --- ASYNC HELPERS ---

object DeviceGrader {
    fun calculateScore(ctx: Context, hw: Map<String, String>): Int {
        var score = 0
        
        // 1. RAM (Max 30)
        val actManager = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        val ramGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
        score += when {
            ramGb > 11.5 -> 30 // 12GB+ (God Tier)
            ramGb > 7.5 -> 25  // 8GB
            ramGb > 5.5 -> 15  // 6GB
            else -> 10
        }

        // 2. CPU (Max 25)
        val cores = Runtime.getRuntime().availableProcessors()
        score += if(cores >= 8) 25 else 10

        // 3. Display (Max 20)
        val densityStr = hw["Density"] ?: "0"
        val dpi = densityStr.filter { it.isDigit() }.toIntOrNull() ?: 300
        score += when {
            dpi >= 500 -> 20 // Ultra sharp
            dpi >= 400 -> 15
            else -> 10
        }

        // 4. OS Freshness (Max 25)
        val sdk = Build.VERSION.SDK_INT
        score += when {
            sdk >= 34 -> 25 // Android 14
            sdk >= 33 -> 20
            sdk >= 31 -> 15
            else -> 5
        }

        return score.coerceIn(0, 99)
    }
}

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