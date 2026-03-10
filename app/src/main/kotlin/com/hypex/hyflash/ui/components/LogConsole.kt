package com.hypex.hyflash.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypex.hyflash.model.LogLine
import com.hypex.hyflash.model.LogType
import com.hypex.hyflash.ui.theme.*
import com.hypex.hyflash.ui.components.pressEffect
import kotlinx.coroutines.launch

@Composable
fun LogConsole(
    title: String = "SYSTEM LOGS",
    lines: List<LogLine>,
    onClear: () -> Unit,
    onCopy: () -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            if (layoutInfo.totalItemsCount == 0) true
            else visibleItemsInfo.lastOrNull()?.index == layoutInfo.totalItemsCount - 1
        }
    }

    val errorCount = lines.count { it.type == LogType.ERROR }
    val warnCount = lines.count { it.type == LogType.WARN }

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty() && isAtBottom) {
            listState.animateScrollToItem(lines.lastIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Mint))
                    Spacer(Modifier.width(10.dp))
                    Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    Text("ERR:$errorCount", color = Error, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.width(6.dp))
                    Text("WARN:$warnCount", color = Yellow, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCopy, modifier = Modifier.size(32.dp).pressEffect()) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Mint, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onClear, modifier = Modifier.size(32.dp).pressEffect()) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear", tint = Error, modifier = Modifier.size(18.dp))
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                itemsIndexed(lines) { index, line ->
                    LogItem(index = index + 1, line = line)
                    if (index < lines.lastIndex) Spacer(Modifier.height(5.dp))
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth().height(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("STATUS: ${if (errorCount > 0) "ISSUES" else "READY"}", color = if (errorCount > 0) Error else Mint, fontSize = 9.sp)
                    Text("LINES: ${lines.size}", color = MaterialTheme.colorScheme.outline, fontSize = 9.sp)
                }
            }
        }

        AnimatedVisibility(
            visible = !isAtBottom && lines.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp)
        ) {
            FloatingActionButton(
                onClick = { scope.launch { listState.animateScrollToItem(lines.lastIndex) } },
                containerColor = Mint,
                contentColor = DarkBg,
                modifier = Modifier.size(36.dp).pressEffect(),
                shape = CircleShape
            ) {
                Icon(Icons.Default.ArrowDownward, contentDescription = "Scroll to Bottom", modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun LogItem(index: Int, line: LogLine) {
    val (color, label) = when (line.type) {
        LogType.CMD -> Mint to "CMD"
        LogType.OK -> Mint to "OK"
        LogType.WARN -> Yellow to "WARN"
        LogType.ERROR -> Error to "ERR"
        LogType.INFO -> Info to "INFO"
        LogType.DIM -> MaterialTheme.colorScheme.outline to "DIM"
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "#${index.toString().padStart(4, '0')}  ${line.timestamp}",
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(color.copy(alpha = 0.15f))
                    .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(label, color = color, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = line.text,
            color = color,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 16.sp
        )
    }
}
