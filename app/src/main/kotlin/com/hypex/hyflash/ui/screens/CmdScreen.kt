package com.hypex.hyflash.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypex.hyflash.model.LogType
import com.hypex.hyflash.ui.theme.*
import com.hypex.hyflash.ui.components.pressEffect
import com.hypex.hyflash.viewmodel.MainViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CmdScreen(viewModel: MainViewModel) {
    var command by remember { mutableStateOf("") }
    val deviceState by viewModel.deviceState.collectAsState()
    val history by viewModel.commandHistory.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val cmdLogs by viewModel.cmdLogs.collectAsState()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(cmdLogs.size) {
        if (cmdLogs.isNotEmpty()) {
            listState.animateScrollToItem(cmdLogs.size - 1)
        }
    }

    val adbCommands = listOf(
        "getprop ro.product.model" to "Model",
        "getprop ro.build.version.release" to "Android",
        "df -h" to "Disk",
        "dumpsys battery" to "Battery",
        "pm list packages -3" to "User Apps",
        "wm density" to "Density",
        "top -n 1" to "CPU Top",
        "ip addr" to "Network",
        "uptime" to "Uptime"
    )

    val fastbootCommands = listOf(
        "getvar all" to "All Vars",
        "getvar product" to "Product",
        "getvar current-slot" to "Active Slot",
        "getvar slot-count" to "Slots",
        "getvar unlocked" to "Unlocked?",
        "getvar secure" to "Secure?",
        "getvar max-download-size" to "Max DL",
        "oem device-info" to "OEM Info"
    )

    val currentQuickCommands = when (deviceState) {
        is com.hypex.hyflash.model.DeviceState.Fastboot -> fastbootCommands
        else -> adbCommands // Default to ADB presets for better visibility
    }
    val isDeviceConnected = deviceState is com.hypex.hyflash.model.DeviceState.ADB || deviceState is com.hypex.hyflash.model.DeviceState.Fastboot

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (currentQuickCommands.isNotEmpty()) {
            val label = if (deviceState is com.hypex.hyflash.model.DeviceState.Fastboot) "FASTBOOT PRESETS" else "ADB PRESETS"
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontFamily = Typography.labelSmall.fontFamily)

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                currentQuickCommands.forEach { (cmd, label) ->
                    OutlinedButton(
                        onClick = { viewModel.runCustomCommand(cmd) },
                        modifier = Modifier.pressEffect(enabled = isDeviceConnected),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isDeviceConnected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                        ),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = isDeviceConnected),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        enabled = isDeviceConnected
                    ) {
                        Text(label.uppercase(), fontSize = 10.sp)
                    }
                }
            }
        }

        if (favorites.isNotEmpty()) {
            Text("FAVORITES", color = Mint, fontSize = 10.sp, fontFamily = Typography.labelSmall.fontFamily)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                favorites.forEach { fav ->
                    HistoryChip(fav, color = Mint.copy(alpha = 0.15f)) { 
                        command = fav
                        viewModel.runCustomCommand(fav)
                    }
                }
            }
        }

        if (history.isNotEmpty()) {
            Text("RECENT", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontFamily = Typography.labelSmall.fontFamily)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                history.take(6).forEach { histCmd ->
                    HistoryChip(histCmd) { 
                        command = histCmd
                        viewModel.runCustomCommand(histCmd)
                    }
                }
            }
        }

        // Terminal Output
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.2f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
        ) {
            androidx.compose.foundation.lazy.LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(cmdLogs.size) { index ->
                    val log = cmdLogs[index]
                    val color = when (log.type) {
                        LogType.CMD -> Mint
                        LogType.ERROR -> Error
                        LogType.WARN -> Yellow
                        LogType.OK -> Mint
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    }
                    Text(
                        text = log.text,
                        color = color,
                        fontSize = 11.sp,
                        fontFamily = Typography.bodySmall.fontFamily,
                        lineHeight = 16.sp
                    )
                }
            }
            
            if (cmdLogs.isNotEmpty()) {
                IconButton(
                    onClick = { viewModel.clearCmdLogs() },
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).pressEffect()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
                }
            }
        }

        // Custom Command Input Area
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                value = command,
                onValueChange = { command = it },
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Mint,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                ),
                placeholder = { Text("Enter command...", color = MaterialTheme.colorScheme.outline, fontSize = 14.sp) },
                textStyle = Typography.bodyMedium,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    if (command.isNotBlank()) {
                        viewModel.runCustomCommand(command.trim())
                        command = ""
                    }
                }),
                trailingIcon = {
                    if (command.isNotBlank()) {
                        val isFav = favorites.contains(command.trim())
                        IconButton(onClick = { viewModel.toggleFavorite(command) }, modifier = Modifier.pressEffect()) {
                            Icon(
                                if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Favorite",
                                tint = if (isFav) Mint else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            )
            
            IconButton(
                onClick = {
                    if (command.isNotBlank()) {
                        viewModel.runCustomCommand(command.trim())
                        command = ""
                    }
                },
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Mint)
                    .pressEffect()
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Run", tint = DarkBg)
            }
        }
    }
}

@Composable
fun HistoryChip(command: String, color: Color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f), onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = color,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(
            text = if (command.length > 15) command.take(12) + ".." else command,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            fontSize = 10.sp,
            fontFamily = Typography.bodySmall.fontFamily
        )
    }
}
