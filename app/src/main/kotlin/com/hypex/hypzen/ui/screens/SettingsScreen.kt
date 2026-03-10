package com.hypex.hypzen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypex.hypzen.model.FastbootEngine
import com.hypex.hypzen.model.ThemeMode
import com.hypex.hypzen.ui.theme.Mint
import com.hypex.hypzen.ui.components.PremiumCard
import com.hypex.hypzen.ui.components.pressEffect
import com.hypex.hypzen.viewmodel.MainViewModel

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val engine by viewModel.fastbootEngine.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Text(
                "PREFERENCES",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        // Appearance
        item {
            PremiumCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = Mint)
                    Spacer(Modifier.width(12.dp))
                    Text("Appearance", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(Modifier.height(16.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PremiumOptionButton(
                        label = "System",
                        selected = themeMode == ThemeMode.SYSTEM,
                        onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
                        modifier = Modifier.weight(1f)
                    )
                    PremiumOptionButton(
                        label = "Dark",
                        selected = themeMode == ThemeMode.DARK,
                        onClick = { viewModel.setThemeMode(ThemeMode.DARK) },
                        modifier = Modifier.weight(1f)
                    )
                    PremiumOptionButton(
                        label = "Light",
                        selected = themeMode == ThemeMode.LIGHT,
                        onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Engine
        item {
            PremiumCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SettingsSuggest, contentDescription = null, tint = Mint)
                    Spacer(Modifier.width(12.dp))
                    Text("Command Engine", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(Modifier.height(16.dp))
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EngineSelectionRow(
                        title = "Embedded Lib (Recommended)",
                        subtitle = "Uses in-app USB engine for ADB/Fastboot.",
                        selected = engine == FastbootEngine.LIB_ANDROID,
                        onClick = { viewModel.setFastbootEngine(FastbootEngine.LIB_ANDROID) }
                    )
                    EngineSelectionRow(
                        title = "Platform Tools",
                        subtitle = "Uses platform adb/fastboot only (no fallback). ADB runs via local TCP proxy; fastboot may not see OTG devices on Android.",
                        selected = engine == FastbootEngine.PLATFORM_TOOLS,
                        onClick = { viewModel.setFastbootEngine(FastbootEngine.PLATFORM_TOOLS) }
                    )
                }
            }
        }

        // Info
        item {
            PremiumCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Mint)
                    Spacer(Modifier.width(12.dp))
                    Text("Advanced Info", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Platform-tools executables are shipped via jniLibs (nativeLibraryDir) to avoid Android noexec/SELinux blocks. Shared libs are prepared from assets and provided via LD_LIBRARY_PATH.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
        
        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "HypZen v1.2.0-Premium",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun PremiumOptionButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Mint else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) Color.Black else MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun EngineSelectionRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Mint.copy(alpha = 0.1f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) Mint else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = Mint)
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (selected) Mint else MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
