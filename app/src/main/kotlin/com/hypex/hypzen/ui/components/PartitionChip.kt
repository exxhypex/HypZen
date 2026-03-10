package com.hypex.hypzen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypex.hypzen.ui.theme.*

@Composable
fun PartitionChip(partition: String?, isDetected: Boolean) {
    val color = if (isDetected) Mint else Warning
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = partition?.uppercase() ?: "UNKNOWN",
            color = color,
            fontSize = 10.sp,
            fontFamily = Typography.labelSmall.fontFamily
        )
    }
}
