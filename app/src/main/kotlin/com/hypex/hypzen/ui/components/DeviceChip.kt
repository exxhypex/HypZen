package com.hypex.hypzen.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.UsbOff
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypex.hypzen.model.DeviceState
import com.hypex.hypzen.ui.theme.*

@Composable
fun DeviceChip(state: DeviceState) {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val (color, label, mode, icon) = when (state) {
        is DeviceState.ADB -> Quadruple(Mint, if (state.serial.length > 8) state.serial.take(8) + ".." else state.serial, "ADB", Icons.Default.Usb)
        is DeviceState.Fastboot -> Quadruple(Yellow, if (state.serial.length > 8) state.serial.take(8) + ".." else state.serial, "FB", Icons.Default.Usb)
        is DeviceState.UsbPermissionRequired -> Quadruple(Warning, "AUTH", "USB", Icons.Default.PriorityHigh)
        is DeviceState.Disconnected -> Quadruple(MaterialTheme.colorScheme.outline, "", "", Icons.Default.UsbOff)
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (state == DeviceState.Disconnected) {
            Icon(icon!!, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = alpha))
            )
            if (label.isNotEmpty()) {
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                    fontFamily = Typography.bodyMedium.fontFamily
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = mode,
                    color = color,
                    fontSize = 9.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }
        }
    }
}

data class Quadruple<out A, out B, out C, out D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
) {
    override fun toString(): String = "($first, $second, $third, $fourth)"
}
