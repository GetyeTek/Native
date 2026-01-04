package com.example.myandroid

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
val SurfaceColor = Color(0xFF101012)
val GridBorder = Color(0xFF333333)

@Composable
fun CyberpunkDashboard(ctx: Context) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("DASH", "SPECS", "NET", "FILES")

    Column(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        // HEADER
        Column(modifier = Modifier.padding(20.dp)) {
            Text("CORTEX", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
            Text("SYSTEM MONITORING ACTIVE", color = NeonGreen, fontSize = 10.sp, letterSpacing = 1.sp)
        }

        // CONTENT AREA
        Box(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            when(selectedTab) {
                0 -> DashboardScreen(ctx)
                1 -> SpecsScreen(ctx)
                2 -> NetScreen(ctx)
                3 -> FilesScreen(ctx)
            }
        }

        // BOTTOM NAV
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(SurfaceColor, RoundedCornerShape(50))
                .border(1.dp, GridBorder, RoundedCornerShape(50))
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            tabs.forEachIndexed { index, title ->
                Button(
                    onClick = { selectedTab = index },
                    colors = ButtonDefaults.buttonColors(containerColor = if(selectedTab == index) NeonGreen.copy(alpha=0.2f) else Color.Transparent),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(title, color = if(selectedTab == index) NeonGreen else Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(ctx: Context) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        CyberCard(NeonPurple) {
             Column {
                 Text("RESILIENCE STATUS", color = Color.Gray, fontSize = 10.sp)
                 Spacer(modifier = Modifier.height(8.dp))
                 val acc = PermissionManager.hasAccessibility(ctx)
                 StatusRow("Accessibility", acc)
                 StatusRow("Notification Link", PermissionManager.hasNotificationListener(ctx))
                 StatusRow("Usage Stats", PermissionManager.hasUsageStats(ctx))
             }
        }
        Spacer(modifier = Modifier.height(16.dp))
        CyberCard(NeonGreen) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                 Text("⚡", fontSize = 24.sp)
                 Spacer(modifier = Modifier.width(12.dp))
                 Column {
                     Text("BATTERY", color = Color.Gray, fontSize = 10.sp)
                     Text("OPTIMIZED MODE", color = NeonGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                 }
            }
        }
    }
}

@Composable
fun StatusRow(label: String, active: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White, fontSize = 12.sp)
        Text(if(active) "ACTIVE" else "INACTIVE", color = if(active) NeonGreen else NeonRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SpecsScreen(ctx: Context) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        DenseDataGrid("SILICON ARCHITECTURE", SystemDeepScan.getHardwareMap(ctx))
        DenseDataGrid("RUNTIME ENVIRONMENT", mapOf(
            "Java VM" to System.getProperty("java.vm.name").toString(),
            "User" to System.getProperty("user.name").toString(),
            "OS Arch" to System.getProperty("os.arch").toString()
        ))
    }
}

@Composable
fun NetScreen(ctx: Context) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        DenseDataGrid("ACTIVE UPLINK", SystemDeepScan.getNetworkMap(ctx))
    }
}

@Composable
fun FilesScreen(ctx: Context) {
    // Placeholder for now
    Text("FILE SYSTEM SCAN READY", color = Color.Gray, fontSize = 12.sp)
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