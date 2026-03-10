package com.hypex.hyflash.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import com.hypex.hyflash.ui.theme.*
import com.hypex.hyflash.ui.components.*
import com.hypex.hyflash.viewmodel.MainViewModel

@Composable
fun RebootScreen(viewModel: MainViewModel) {
    val rebootOptions = listOf(
        RebootOption("System", "system", Icons.Default.Refresh),
        RebootOption("Recovery", "recovery", Icons.Default.Settings),
        RebootOption("Bootloader", "bootloader", Icons.Default.LockOpen),
        RebootOption("Fastboot", "fastboot", Icons.Default.ElectricBolt),
        RebootOption("Sideload", "sideload", Icons.Default.Inbox),
        RebootOption("EDL", "edl", Icons.Default.Build)
    )

    val isRebooting by viewModel.isRebooting.collectAsState()
    val rebootStatus by viewModel.rebootStatus.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.rebootToastEvent.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            "REBOOT OPTIONS",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            letterSpacing = 1.sp
        )

        AnimatedVisibility(
            visible = isRebooting,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            PremiumCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PremiumLoadingSpinner(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Rebooting", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        Text(rebootStatus ?: "Initializing...", color = Mint, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(rebootOptions) { option ->
                PremiumRebootCard(option = option, enabled = !isRebooting) { viewModel.reboot(option.mode) }
            }
        }

        Text(
            "ADVANCED CONTROL",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { viewModel.oemUnlock() },
                modifier = Modifier.weight(1f).height(50.dp).pressEffect(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Error, contentColor = Color.White)
            ) {
                Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("OEM UNLOCK", fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
        }
    }
}

data class RebootOption(val label: String, val mode: String, val icon: ImageVector)

@Composable
fun PremiumRebootCard(option: RebootOption, enabled: Boolean, onClick: () -> Unit) {
    PremiumCard(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .pressEffect(enabled = enabled)
            .clickable(enabled = enabled) { onClick() }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Mint.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(option.icon, contentDescription = null, tint = Mint, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                option.label.uppercase(),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        }
    }
}
