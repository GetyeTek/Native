package com.example.myandroid

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- THEME COLORS ---
val DarkBg = Color(0xFF050505)
val NeonGreen = Color(0xFF2CB67D)
val NeonPurple = Color(0xFF7F5AF0)
val NeonRed = Color(0xFFEF4565)
val NeonOrange = Color(0xFFF6AD55)
val SurfaceColor = Color(0xFF101012)
val GridBorder = Color(0xFF333333)

@Composable
fun CyberpunkDashboard(ctx: Context) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // 1. HEADER
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text("CORTEX // CORE", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
            Text("INTELLIGENCE STREAM ACTIVE", color = NeonGreen, fontSize = 10.sp, letterSpacing = 1.sp)
            Divider(color = NeonGreen, thickness = 2.dp, modifier = Modifier.padding(top = 8.dp).width(60.dp))
        }

        // 2. PERMISSION ALERTS (Active Fix Buttons)
        // These appear ONLY if permissions are missing
        PermissionAlertSection(ctx)

        // 3. VITALS (Battery & Status)
        Text("SYSTEM VITALS", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom=8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
             Box(modifier = Modifier.weight(1f)) {
                 CyberCard(NeonGreen) {
                     Column {
                         Text("BATTERY", color = Color.Gray, fontSize = 10.sp)
                         Text("OPTIMIZED", color = NeonGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                     }
                 }
             }
             Box(modifier = Modifier.weight(1f)) {
                 CyberCard(NeonPurple) {
                     Column {
                         Text("PERSISTENCE", color = Color.Gray, fontSize = 10.sp)
                         val acc = PermissionManager.hasAccessibility(ctx)
                         Text(if(acc) "SECURE" else "VULNERABLE", color = if(acc) NeonPurple else NeonRed, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                     }
                 }
             }
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        // 4. DEEP SCANS (Stacked)
        DenseDataGrid("SILICON ARCHITECTURE", SystemDeepScan.getHardwareMap(ctx))
        DenseDataGrid("ACTIVE UPLINK", SystemDeepScan.getNetworkMap(ctx))
        
        // 5. RUNTIME ENVIRONMENT
        DenseDataGrid("JAVA RUNTIME", mapOf(
            "Java VM" to System.getProperty("java.vm.name").toString(),
            "VM Version" to System.getProperty("java.vm.version").toString(),
            "User" to System.getProperty("user.name").toString(),
            "OS Arch" to System.getProperty("os.arch").toString()
        ))

        // 6. STORAGE OVERVIEW
        val root = android.os.Environment.getExternalStorageDirectory()
        val total = root.totalSpace / (1024*1024*1024)
        val free = root.freeSpace / (1024*1024*1024)
        DenseDataGrid("STORAGE PARTITION", mapOf(
            "Path" to root.absolutePath,
            "Total Space" to "$total GB",
            "Free Space" to "$free GB",
            "State" to android.os.Environment.getExternalStorageState().uppercase()
        ))
        
        Spacer(modifier = Modifier.height(40.dp))
        Text("END OF STREAM", color = Color.DarkGray, fontSize = 10.sp, modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally))
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun PermissionAlertSection(ctx: Context) {
    // ACCESSIBILITY (The most critical)
    if (!PermissionManager.hasAccessibility(ctx)) {
        AlertCard(
            title = "CRITICAL: ACCESSIBILITY OFFLINE",
            desc = "System cannot monitor usage or recover data without this hook.",
            btnText = "INITIALIZE HOOK",
            color = NeonRed
        ) {
            ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    // USAGE STATS
    if (!PermissionManager.hasUsageStats(ctx)) {
        AlertCard(
            title = "WARNING: DATA STREAM BLOCKED",
            desc = "Screen time and digital habits cannot be calculated.",
            btnText = "GRANT ACCESS",
            color = NeonOrange
        ) {
            ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    // NOTIFICATION LISTENER
    if (!PermissionManager.hasNotificationListener(ctx)) {
        AlertCard(
            title = "ALERT: SYMBIOTE LINK BROKEN",
            desc = "Cannot read incoming alerts or use them for heartbeat.",
            btnText = "LINK SYSTEM",
            color = NeonOrange
        ) {
             ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }
}

@Composable
fun AlertCard(title: String, desc: String, btnText: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            .border(1.dp, color, RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Column {
            Text(title, color = color, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(desc, color = Color.LightGray, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = color),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth().height(40.dp)
            ) {
                Text(btnText, color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DenseDataGrid(title: String, data: Map<String, String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .background(SurfaceColor, RoundedCornerShape(8.dp))
            .border(1.dp, GridBorder, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(
            text = title,
            color = NeonGreen,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        val items = data.toList()
        val rows = items.chunked(2)
        rows.forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                rowItems.forEach { (key, value) ->
                    Column(modifier = Modifier.weight(1f)) {
                        Text(key.uppercase(), color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        Text(value, color = Color(0xFFE0E0E0), fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                    }
                }
                if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
            Divider(color = Color(0xFF1A1A1A), thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

@Composable
fun CyberCard(borderColor: Color, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(listOf(Color(0xFF151515), Color(0xFF0A0A0A))),
                shape = RoundedCornerShape(12.dp)
            )
            .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(20.dp)
    ) {
        content()
    }
}