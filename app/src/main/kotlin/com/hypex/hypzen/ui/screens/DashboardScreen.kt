package com.hypex.hypzen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypex.hypzen.model.DeviceState
import com.hypex.hypzen.model.LogLine
import com.hypex.hypzen.model.LogType
import com.hypex.hypzen.ui.components.PremiumCard
import com.hypex.hypzen.ui.components.pressEffect
import com.hypex.hypzen.ui.theme.Mint
import com.hypex.hypzen.viewmodel.MainViewModel

@Composable
fun DashboardScreen(viewModel: MainViewModel, onNavigate: (String) -> Unit) {
    val deviceState by viewModel.deviceState.collectAsState()
    val logLines by viewModel.logLines.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Text(
                "DASHBOARD",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        // Device Status Card
        item {
            PremiumCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Mint.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (deviceState) {
                                is DeviceState.ADB -> Icons.Default.Usb
                                is DeviceState.Fastboot -> Icons.Default.ElectricBolt
                                else -> Icons.Default.UsbOff
                            },
                            contentDescription = null,
                            tint = Mint,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = when (deviceState) {
                                is DeviceState.ADB -> "ADB CONNECTED"
                                is DeviceState.Fastboot -> "FASTBOOT MODE"
                                is DeviceState.UsbPermissionRequired -> "PERMISSION NEEDED"
                                is DeviceState.Disconnected -> "NO DEVICE"
                            },
                            color = Mint,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = when (val s = deviceState) {
                                is DeviceState.ADB -> "Serial: ${s.serial}"
                                is DeviceState.Fastboot -> "Serial: ${s.serial}"
                                else -> "Connect device via OTG"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Quick Actions
        item {
            Text(
                "QUICK ACTIONS",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickActionItem(
                    icon = Icons.Default.FlashOn,
                    label = "Flash",
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate("Flash") }
                )
                QuickActionItem(
                    icon = Icons.Default.Terminal,
                    label = "Shell",
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate("Terminal") }
                )
                QuickActionItem(
                    icon = Icons.Default.RestartAlt,
                    label = "Reboot",
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate("Reboot") }
                )
            }
        }

        // Recent Activity
        item {
            Text(
                "RECENT ACTIVITY",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        val recentLogs = logLines.takeLast(5).reversed()
        if (recentLogs.isEmpty()) {
            item {
                PremiumCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "No recent activity recorded.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        } else {
            items(recentLogs) { log ->
                LogItem(log)
            }
        }
    }
}

@Composable
fun QuickActionItem(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    PremiumCard(
        modifier = modifier
            .pressEffect()
            .clickable { onClick() }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(icon, contentDescription = null, tint = Mint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(8.dp))
            Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun LogItem(log: LogLine) {
    PremiumCard(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        when (log.type) {
                            LogType.OK -> Color.Green
                            LogType.ERROR -> Color.Red
                            LogType.WARN -> Color.Yellow
                            else -> Mint
                        }
                    )
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(log.text, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, maxLines = 1)
                Text(log.timestamp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            }
        }
    }
}
