package com.hypex.hypzen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypex.hypzen.ui.theme.*
import com.hypex.hypzen.ui.components.PremiumCard
import com.hypex.hypzen.ui.components.pressEffect
import com.hypex.hypzen.viewmodel.MainViewModel

@Composable
fun WipeScreen(viewModel: MainViewModel) {
    val partitions = listOf("cache", "userdata", "system", "vendor", "odm", "metadata", "super", "frp", "config", "misc")
    val selectedPartitions = remember { mutableStateListOf<String>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            "WIPE PARTITIONS",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        PremiumCard(modifier = Modifier.weight(1f)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(partitions) { partition ->
                    PremiumWipeChip(
                        label = partition,
                        isSelected = selectedPartitions.contains(partition),
                        onToggle = {
                            if (selectedPartitions.contains(partition)) {
                                selectedPartitions.remove(partition)
                            } else {
                                selectedPartitions.add(partition)
                            }
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    if (selectedPartitions.size == partitions.size) selectedPartitions.clear()
                    else {
                        selectedPartitions.clear()
                        selectedPartitions.addAll(partitions)
                    }
                },
                modifier = Modifier.weight(1f).height(50.dp).pressEffect(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text(if (selectedPartitions.size == partitions.size) "NONE" else "ALL", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { viewModel.wipePartitions(selectedPartitions) },
                modifier = Modifier.weight(1f).height(50.dp).pressEffect(enabled = selectedPartitions.isNotEmpty()),
                colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Color.Black),
                shape = RoundedCornerShape(14.dp),
                enabled = selectedPartitions.isNotEmpty()
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("WIPE", fontWeight = FontWeight.Bold)
            }
        }

        // Factory Reset
        PremiumCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Error.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Error, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("FACTORY RESET", color = Error, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Erase all user data (irreversible)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { viewModel.factoryReset() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Error, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("⚠️ RESET DEVICE", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PremiumWipeChip(label: String, isSelected: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Mint.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            .border(
                width = 1.dp,
                color = if (isSelected) Mint else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onToggle() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label.uppercase(),
                color = if (isSelected) Mint else MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            if (isSelected) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Mint, modifier = Modifier.size(14.dp))
            }
        }
    }
}
