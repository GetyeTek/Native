package com.example.myandroid

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// --- CINEMATIC THEME COLORS ---
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
        
        // 1. RAM: The Workbench size
        val actManager = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        val ramGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
        score += when {
            ramGb > 11.5 -> 30 // 12GB+
            ramGb > 7.5 -> 20  // 8GB+
            ramGb > 5.5 -> 10  // 6GB+
            else -> 5
        }

        // 2. REFRESH RATE: The fluidity
        val refreshStr = disp["Refresh Rate"] ?: "60"
        val hz = refreshStr.filter { it.isDigit() || it == '.' }.toFloatOrNull() ?: 60f
        score += when {
            hz >= 119f -> 20
            hz >= 89f -> 10
            else -> 0
        }

        // 3. SoC LOGIC: The Brains
        val board = Build.BOARD.lowercase()
        val hardware = Build.HARDWARE.lowercase()
        val chipset = if (board.contains("taro") || board.contains("kalama") || hardware.contains("sm8")) "High-End Snap"
                 else if (board.contains("exynos") && ramGb > 7) "High-End Exynos"
                 else if (board.contains("mt69") || hardware.contains("dimensity")) "High-End MTK"
                 else "Standard"
        
        score += when(chipset) {
            "High-End Snap" -> 30
            "High-End Exynos" -> 25
            "High-End MTK" -> 25
            else -> 10
        }

        // 4. MODERNITY (API)
        val sdk = android.os.Build.VERSION.SDK_INT
        score += when {
            sdk >= 34 -> 10 // A14
            sdk >= 31 -> 5  // A12
            else -> 0
        }

        // 5. DENSITY
        val densityStr = disp["Density (DPI)"] ?: "0"
        val dpi = densityStr.toIntOrNull() ?: 300
        score += if (dpi >= 400) 10 else 0

        val finalScore = score.coerceIn(0, 100)
        val (tier, color) = when {
            finalScore >= 90 -> "GOD TIER" to NeonPurple
            finalScore >= 75 -> "FLAGSHIP" to NeonBlue
            finalScore >= 50 -> "STANDARD" to NeonGreen
            else -> "LEGACY" to NeonRed
        }
        return Triple(finalScore, tier, color)
    }

    fun getDetailedManifesto(): String {
        return """
            CORTEX RATING ALGORITHM v2.1
            ----------------------------
            This engine evaluates raw potential vs. modern bloat.
            
            1. SILICON ARCHITECTURE (30%)
               We analyze the Board ID and Hardware Strings to identify high-performance clusters (Snapdragon 8-Series, Dimensity 9000+, High-Tier Exynos).
            
            2. VOLATILE MEMORY (30%)
               Physical RAM is weighed. >12GB is required for 'God Tier' to ensure apps never reload.
            
            3. VISUAL FLUIDITY (20%)
               Screen Refresh Rate (Hz) is sampled. 120Hz+ is mandatory for top scores. 60Hz is penalized.
            
            4. SOFTWARE FRESHNESS (10%)
               API Level is checked. Android 14+ is preferred for security and feature sets.
            
            5. PIXEL DENSITY (10%)
               DPI analysis determines screen sharpness (>400 DPI preferred).
        """.trimIndent()
    }
}

@Composable
fun InspectorDashboard(ctx: Context) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshTrigger by remember { mutableStateOf(0) }
    
    // Sidebar & Theme State
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var isDarkTheme by remember { mutableStateOf(true) }
    val currentBg = if(isDarkTheme) VoidBg else Color(0xFFE0E0E0)
    val currentTxt = if(isDarkTheme) Color.White else Color.Black
    val drawerBg = if(isDarkTheme) GlassSurface else Color.White

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTrigger++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // --- ASYNC DATA ---
    val hwSpecs by produceState(emptyMap<String,String>(), refreshTrigger) { value = withContext(Dispatchers.IO) { SystemDeepScan.getCpuDetailed() } }
    val memSpecs by produceState(emptyMap<String,String>(), refreshTrigger) { value = withContext(Dispatchers.IO) { SystemDeepScan.getMemoryDetailed(ctx) } }
    val battSpecs by produceState(emptyMap<String,String>(), refreshTrigger) { value = withContext(Dispatchers.IO) { SystemDeepScan.getBatteryDetailed(ctx) } }
    val dispSpecs by produceState(emptyMap<String,String>(), refreshTrigger) { value = withContext(Dispatchers.IO) { SystemDeepScan.getDisplayDetailed(ctx) } }
    val camSpecs by produceState(emptyMap<String,String>(), refreshTrigger) { value = withContext(Dispatchers.IO) { SystemDeepScan.getCameraDetailed(ctx) } }
    val softSpecs by produceState(emptyMap<String,String>(), refreshTrigger) { value = withContext(Dispatchers.IO) { SystemDeepScan.getSoftwareDetailed() } }
    val storeSpecs by produceState(emptyMap<String,String>(), refreshTrigger) { value = withContext(Dispatchers.IO) { SystemDeepScan.getStorageDetailed() } }
    
    val scoreData by produceState(Triple(0, "ANALYZING...", Color.Gray), memSpecs, hwSpecs) { 
        value = withContext(Dispatchers.IO) { DeviceGrader.calculateScore(ctx, hwSpecs, dispSpecs) }
    }
    val battInfo by produceState(Pair(0, false), refreshTrigger) { value = withContext(Dispatchers.IO) { getBatteryInfo(ctx) } }
    val storeInfo by produceState(Triple("0","0",0f), refreshTrigger) { value = withContext(Dispatchers.IO) { getStorageInfo() } }

    // Live Permission Check
    val permState by produceState(mapOf<String, Boolean>(), refreshTrigger) {
        value = withContext(Dispatchers.IO) {
            mapOf(
                "acc" to PermissionManager.hasAccessibility(ctx),
                "usage" to PermissionManager.hasUsageStats(ctx),
                "files" to PermissionManager.hasAllFilesAccess(ctx),
                "notif" to PermissionManager.hasNotificationListener(ctx),
                "batt" to PermissionManager.isIgnored(ctx),
                "admin" to PermissionManager.isAdmin(ctx)
            )
        }
    }
    val missingPerms = permState.filter { !it.value }.keys
    val allGranted = missingPerms.isEmpty()
    
    // Ghost Console
    var showConsole by remember { mutableStateOf(false) }
    var debugTaps by remember { mutableStateOf(0) }
    if (showConsole) DebugConsole(ctx) { showConsole = false }

    // Side Drawer Structure
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            // FIXED: Set width to 300dp (Half-ish) and specific colors
            ModalDrawerSheet(
                drawerContainerColor = drawerBg,
                modifier = Modifier.width(300.dp)
            ) {
                Text(
                    "CORTEX TOOLS", 
                    color = NeonBlue, 
                    modifier = Modifier.padding(20.dp), 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 18.sp
                )
                Divider(color = BorderWhite)
                
                // Theme Toggle
                NavigationDrawerItem(
                    label = { Text("THEME: ${if(isDarkTheme) "DARK" else "LIGHT"}", color = if(isDarkTheme) Color.White else Color.Black) },
                    selected = false,
                    onClick = { isDarkTheme = !isDarkTheme },
                    icon = { Text(if(isDarkTheme) "🌙" else "☀️") },
                    modifier = Modifier.padding(10.dp)
                )

                // Dump Log Button
                NavigationDrawerItem(
                    label = { Text("GENERATE DUMP (SECURE)", color = NeonRed, fontWeight = FontWeight.Bold) },
                    selected = false,
                    onClick = { 
                        scope.launch(Dispatchers.IO) { DumpManager.createDailyDump(ctx) }
                        scope.launch { drawerState.close() }
                    },
                    icon = { Text("🔒") },
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize().background(currentBg)) {
            // Menu Button
            IconButton(onClick = { scope.launch { drawerState.open() } }, modifier = Modifier.padding(top=30.dp, start=10.dp).zIndex(2f)) {
                Text("☰", color = currentTxt, fontSize = 24.sp)
            }

            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top=60.dp, start=24.dp, end=24.dp, bottom=24.dp)) {
                
                // 1. GAUGE & MANIFESTO (TOP)
                item {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(bottom=10.dp).clickable {
                        debugTaps++
                        if(debugTaps >= 5) { debugTaps=0; showConsole=true }
                    }) {
                         if (allGranted) {
                             ApexScoreGauge(scoreData)
                         } else {
                             Box(modifier = Modifier.size(220.dp).border(2.dp, NeonRed, CircleShape), contentAlignment = Alignment.Center) {
                                 Text("GRANT PERMS\nTO VIEW", color = NeonRed, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                             }
                         }
                    }
                    
                    // Manifesto (Explains logic, NO status)
                    var expanded by remember { mutableStateOf(false) }
                    Card(colors = CardDefaults.cardColors(containerColor = GlassSurface), modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
                         Column(modifier = Modifier.padding(16.dp)) {
                             Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                 Text("RATING MANIFESTO", color = NeonBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                 Text(if(expanded) "▲" else "▼", color = Color.Gray)
                             }
                             AnimatedVisibility(expanded) {
                                 Text(DeviceGrader.getDetailedManifesto(), color = Color.LightGray, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top=12.dp))
                             }
                         }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // 2. PERMISSIONS (HIDDEN IF GRANTED)
                item {
                    AnimatedVisibility(!allGranted) {
                        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
                            SectionHeader("SYSTEM OVERRIDES REQUIRED")
                            if (!permState["acc"]!!) OptimizationToggle("Neural Interface", "Accessibility", false) { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                            if (!permState["usage"]!!) OptimizationToggle("Usage Analytics", "Usage Stats", false) { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                            if (!permState["files"]!!) OptimizationToggle("Deep Clean", "All Files", false) { val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION); i.data = Uri.parse("package:"+ctx.packageName); ctx.startActivity(i) }
                            if (!permState["notif"]!!) OptimizationToggle("Symbiote Link", "Notifications", false) { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                            if (!permState["batt"]!!) OptimizationToggle("Persistence", "Ignore Battery Opt", false) { val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS); i.data = Uri.parse("package:"+ctx.packageName); ctx.startActivity(i) }
                            if (!permState["admin"]!!) OptimizationToggle("System Anchor", "Device Admin", false) { 
                                val comp = android.content.ComponentName(ctx, MyDeviceAdminReceiver::class.java)
                                val i = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                                i.putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, comp)
                                ctx.startActivity(i) 
                            }
                        }
                    }
                }

                // 3. PASSPORT
                item {
                    SectionHeader("Digital Passport")
                    PassportCard(softSpecs, hwSpecs)
                }
                // 4. RESOURCES
                item {
                    SectionHeader("Resources")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                         Box(modifier = Modifier.weight(1f)) { BatteryTank(battInfo.first, battInfo.second, battSpecs["Capacity"] ?: "N/A") }
                         Box(modifier = Modifier.weight(1f)) { StorageCard(storeInfo) }
                    }
                }
                // 5. SCHEMATICS
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    SchematicGrid("SILICON LOGIC", hwSpecs, NeonBlue)
                    SchematicGrid("MEMORY", memSpecs, NeonPurple)
                    SchematicGrid("POWER MATRIX", battSpecs, NeonGreen)
                    SchematicGrid("OPTICS ARRAY", camSpecs, NeonCyan)
                }
            }
        }
    }
}

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
            Text(text = animatedScore.value.toInt().toString(), color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Bold)
            Text(text = tier, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }
    }
}

@Composable
fun PassportCard(soft: Map<String, String>, hw: Map<String, String>) {
    val infiniteTransition = rememberInfiniteTransition()
    val scanY by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
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
                start = Offset(0f, yPos), end = Offset(size.width, yPos),
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
fun OptimizationToggle(title: String, desc: String, isEnabled: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .background(GlassSurface, RoundedCornerShape(16.dp))
            .border(1.dp, if(isEnabled) BorderWhite else NeonGreen.copy(alpha=0.3f), RoundedCornerShape(16.dp))
            .padding(16.dp)
            .clickable { onToggle() },
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

@Composable
fun DebugConsole(ctx: Context, onDismiss: () -> Unit) {
    val report = remember { 
        DeviceManager.getDiagnosticReport(ctx) + "\n\n--- LIVE LOGS ---\n" + DebugLogger.getLogs() 
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F0F10),
        title = { Text("SYSTEM DIAGNOSTICS", color = NeonRed, fontWeight = FontWeight.Bold, fontSize = 14.sp) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = report,
                    color = NeonGreen,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 14.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("CLOSE LINK", color = Color.White) }
        }
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
