package com.hypex.hyflash.ui.screens

import android.content.ClipData
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypex.hyflash.ui.components.LogConsole
import com.hypex.hyflash.ui.theme.Mint
import com.hypex.hyflash.ui.components.pressEffect
import com.hypex.hyflash.ui.components.PremiumCard
import com.hypex.hyflash.ui.components.PremiumProgressBar
import com.hypex.hyflash.ui.components.PremiumLoadingSpinner
import com.hypex.hyflash.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun SideloadScreen(viewModel: MainViewModel) {
    val selectedName by viewModel.selectedSideloadFileName.collectAsState()
    val progress by viewModel.sideloadProgress.collectAsState()
    val prepareInProgress by viewModel.sideloadPrepareInProgress.collectAsState()
    val prepareProgress by viewModel.sideloadPrepareProgress.collectAsState()
    val logLines by viewModel.logLines.collectAsState()

    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val sideloadLogs = remember(logLines) {
        logLines
            .filter { it.text.startsWith("[SIDELOAD]") || it.text.startsWith("[PT]") }
            .takeLast(400)
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.selectSideloadFile(it) }
    }

    if (prepareInProgress) {
        AlertDialog(
            onDismissRequest = { },
            confirmButton = { },
            title = { Text("Preparing Package") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Copying ZIP into app cache for stable sideload.")
                    if (prepareProgress in 0f..0.99f) {
                        LinearProgressIndicator(progress = prepareProgress)
                        Text("${(prepareProgress * 100).toInt()}%", color = Mint, fontSize = 12.sp)
                    } else {
                        LinearProgressIndicator()
                    }
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            "ADB SIDELOAD",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            letterSpacing = 1.sp
        )

        PremiumCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .pressEffect()
                .clickable { if (progress == 0f && !prepareInProgress) picker.launch("*/*") }
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Mint.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null, tint = Mint, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    if (selectedName == null) "SELECT OTA ZIP" else "CHANGE FILE",
                    color = Mint,
                    fontSize = 14.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }
        }

        AnimatedVisibility(
            visible = selectedName != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            PremiumCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Selected Package", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        Text(selectedName ?: "", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                    }
                    if (progress > 0f) {
                        PremiumLoadingSpinner(modifier = Modifier.size(24.dp))
                    } else if (prepareInProgress) {
                        PremiumLoadingSpinner(modifier = Modifier.size(24.dp))
                    }
                }

                if (progress > 0f) {
                    Spacer(Modifier.height(16.dp))
                    PremiumProgressBar(progress = progress)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Transfer Progress", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        Text("${(progress * 100).toInt()}%", color = Mint, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LogConsole(
                title = "SIDELOAD LOGS",
                lines = sideloadLogs,
                onClear = { viewModel.clearSideloadLogs() },
                onCopy = {
                    val text = sideloadLogs.joinToString("\n") { "[${it.timestamp}] ${it.text}" }
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("sideload_logs", text))) }
                }
            )
        }

        Button(
            onClick = { viewModel.startSideload() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .pressEffect(enabled = selectedName != null && progress == 0f && !prepareInProgress),
            enabled = selectedName != null && progress == 0f && !prepareInProgress,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Mint,
                contentColor = Color.Black,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            if (progress > 0f) {
                Text("SIDELOADING...", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            } else if (prepareInProgress) {
                Text("PREPARING...", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            } else {
                Icon(Icons.Default.CloudUpload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("START SIDELOAD", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
        }
    }
}
