package com.example.myandroid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
                                 Text(k, color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold, textDecoration = null)
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