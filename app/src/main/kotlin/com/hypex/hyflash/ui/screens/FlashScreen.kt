package com.hypex.hyflash.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypex.hyflash.ui.components.*
import com.hypex.hyflash.ui.components.pressEffect
import com.hypex.hyflash.ui.theme.*
import com.hypex.hyflash.viewmodel.MainViewModel
import com.hypex.hyflash.model.FlashOperation
import com.hypex.hyflash.model.OperationStatus

@Composable
fun FlashScreen(viewModel: MainViewModel) {
    var mode by remember { mutableStateOf(FlashMode.SINGLE) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Mode Selector
        PremiumCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (mode == FlashMode.SINGLE) Mint else Color.Transparent)
                        .clickable { mode = FlashMode.SINGLE },
                    contentAlignment = Alignment.Center
                ) {
                    Text("SINGLE", color = if (mode == FlashMode.SINGLE) Color.Black else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (mode == FlashMode.BATCH) Mint else Color.Transparent)
                        .clickable { mode = FlashMode.BATCH },
                    contentAlignment = Alignment.Center
                ) {
                    Text("BATCH", color = if (mode == FlashMode.BATCH) Color.Black else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        AnimatedContent(
            targetState = mode,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "FlashModeTransition"
        ) { currentMode ->
            if (currentMode == FlashMode.SINGLE) {
                SingleFlashContent(viewModel)
            } else {
                BatchFlashContent(viewModel)
            }
        }
    }
}

enum class FlashMode { SINGLE, BATCH }

@Composable
fun SingleFlashContent(viewModel: MainViewModel) {
    val selectedFileName by viewModel.selectedFileName.collectAsState()
    val selectedPartition by viewModel.selectedPartition.collectAsState()
    val flashProgress by viewModel.flashProgress.collectAsState()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.selectFile(it) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PremiumCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .pressEffect()
                .clickable { filePicker.launch("*/*") }
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
                    Icon(Icons.Default.FileUpload, contentDescription = null, tint = Mint, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    if (selectedFileName == null) "SELECT IMAGE" else "CHANGE IMAGE",
                    color = Mint,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        selectedFileName?.let { fileName ->
            PremiumFileCard(
                fileName = fileName,
                partition = selectedPartition,
                progress = flashProgress,
                onPartitionSelect = { viewModel.setPartition(it) }
            )
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = { viewModel.flashPartition() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .pressEffect(enabled = selectedFileName != null && selectedPartition != null),
            colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Color.Black),
            shape = RoundedCornerShape(16.dp),
            enabled = selectedFileName != null && selectedPartition != null
        ) {
            Icon(Icons.Default.ElectricBolt, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("START FLASHING", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun BatchFlashContent(viewModel: MainViewModel) {
    val batchOps by viewModel.batchOperations.collectAsState()
    val stopOnError by viewModel.stopOnError.collectAsState()
    val globalProgress by viewModel.globalProgress.collectAsState()

    val dirPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { viewModel.selectDirectory(it) }
    }

    val scriptPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.selectScript(it) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickActionItem(
                icon = Icons.Default.Folder,
                label = "ROM DIR",
                modifier = Modifier.weight(1f),
                onClick = { dirPicker.launch(null) }
            )
            QuickActionItem(
                icon = Icons.Default.Description,
                label = "SCRIPT",
                modifier = Modifier.weight(1f),
                onClick = { scriptPicker.launch("*/*") }
            )
        }

        if (batchOps.isNotEmpty()) {
            PremiumCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("BATCH STATUS", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("${batchOps.size} Operations", color = Mint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("STOP ERROR", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                        Switch(
                            checked = stopOnError,
                            onCheckedChange = { viewModel.setStopOnError(it) },
                            modifier = Modifier.scale(0.7f),
                            colors = SwitchDefaults.colors(checkedThumbColor = Mint, checkedTrackColor = Mint.copy(alpha = 0.5f))
                        )
                    }
                }

                if (globalProgress > 0f) {
                    Spacer(Modifier.height(16.dp))
                    PremiumProgressBar(progress = globalProgress)
                }
            }

            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(batchOps.size) { index ->
                    PremiumOperationItem(batchOps[index])
                }
            }
            
            Button(
                onClick = { viewModel.runBatchFlash() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .pressEffect(enabled = batchOps.any { it.status == OperationStatus.READY }),
                colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Color.Black),
                shape = RoundedCornerShape(16.dp),
                enabled = batchOps.any { it.status == OperationStatus.READY }
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("EXECUTE BATCH", fontWeight = FontWeight.Bold)
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Select folder and script to begin batch process", color = MaterialTheme.colorScheme.outline, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun PremiumFileCard(
    fileName: String,
    partition: String?,
    progress: Float,
    onPartitionSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val partitions = listOf("boot", "recovery", "system", "vendor", "vendor_boot", "dtbo", "vbmeta", "super", "userdata")

    PremiumCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Target Partition", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                Box {
                    Row(
                        modifier = Modifier
                            .clickable { expanded = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = partition?.uppercase() ?: "SELECT...",
                            color = Mint,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Mint)
                    }
                    
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        partitions.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.uppercase(), fontWeight = FontWeight.Bold) },
                                onClick = {
                                    onPartitionSelect(p)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
            
            if (progress > 0f) {
                PremiumLoadingSpinner(modifier = Modifier.size(32.dp))
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(fileName, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1)

        if (progress > 0f) {
            Spacer(Modifier.height(16.dp))
            PremiumProgressBar(progress = progress)
            Spacer(Modifier.height(8.dp))
            Text("Flashing: ${(progress * 100).toInt()}%", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PremiumOperationItem(op: FlashOperation) {
    val statusColor = when (op.status) {
        OperationStatus.READY -> Mint
        OperationStatus.DONE -> Color.Green
        OperationStatus.FLASHING -> Yellow
        OperationStatus.MISSING -> Error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    PremiumCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(op.partition.uppercase(), color = statusColor, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                Text(op.fileName ?: "No file", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1)
            }
            
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.1f))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(op.status.name, color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
