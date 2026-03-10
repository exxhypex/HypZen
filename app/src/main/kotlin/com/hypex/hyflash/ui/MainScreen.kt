package com.hypex.hyflash.ui

import android.content.ClipData
import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypex.hyflash.model.ThemeMode
import com.hypex.hyflash.ui.components.DeviceChip
import com.hypex.hyflash.ui.components.LogConsole
import com.hypex.hyflash.ui.components.pressEffect
import com.hypex.hyflash.ui.screens.*
import com.hypex.hyflash.ui.theme.DarkBg
import com.hypex.hyflash.ui.theme.Mint
import com.hypex.hyflash.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf("Dashboard") }

    val deviceState by viewModel.deviceState.collectAsState()
    val logLines by viewModel.logLines.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val confirmSheetState by viewModel.confirmSheetState.collectAsState()
    val clipboard = LocalClipboard.current

    var showConsole by remember { mutableStateOf(false) }

    if (confirmSheetState != null) {
        val state = confirmSheetState!!
        com.hypex.hyflash.ui.components.ConfirmBottomSheet(
            title = state.title,
            subtitle = state.subtitle,
            command = state.command,
            isDangerous = state.isDangerous,
            onConfirm = state.onConfirm,
            onDismiss = { viewModel.dismissConfirm() }
        )
    }

    if (showConsole) {
        ModalBottomSheet(
            onDismissRequest = { showConsole = false },
            containerColor = MaterialTheme.colorScheme.background,
            dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outlineVariant) }
        ) {
            Box(modifier = Modifier.fillMaxHeight(0.7f).padding(8.dp)) {
                LogConsole(
                    lines = logLines,
                    onClear = { viewModel.clearLogs() },
                    onCopy = {
                        val text = logLines.joinToString("\n") { "[${it.timestamp}] ${it.text}" }
                        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("logs", text))) }
                    }
                )
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
                modifier = Modifier.width(300.dp)
            ) {
                Spacer(Modifier.height(48.dp))
                Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Column {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(colors = listOf(Mint, Mint.copy(alpha = 0.7f)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ElectricBolt, contentDescription = null, tint = DarkBg)
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Hyflash", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Premium Flash Tool", fontSize = 12.sp, color = Mint, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(32.dp))
                
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
                    label = { Text("Dashboard") },
                    selected = currentScreen == "Dashboard",
                    onClick = { currentScreen = "Dashboard"; scope.launch { drawerState.close() } },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = Mint.copy(alpha = 0.1f), selectedIconColor = Mint, selectedTextColor = Mint)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.FlashOn, contentDescription = null) },
                    label = { Text("Flash Engine") },
                    selected = currentScreen == "Flash",
                    onClick = { currentScreen = "Flash"; scope.launch { drawerState.close() } },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = Mint.copy(alpha = 0.1f), selectedIconColor = Mint, selectedTextColor = Mint)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
                    label = { Text("ADB Sideload") },
                    selected = currentScreen == "Sideload",
                    onClick = { currentScreen = "Sideload"; scope.launch { drawerState.close() } },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = Mint.copy(alpha = 0.1f), selectedIconColor = Mint, selectedTextColor = Mint)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    label = { Text("Wipe / Erase") },
                    selected = currentScreen == "Wipe",
                    onClick = { currentScreen = "Wipe"; scope.launch { drawerState.close() } },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = Mint.copy(alpha = 0.1f), selectedIconColor = Mint, selectedTextColor = Mint)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.RestartAlt, contentDescription = null) },
                    label = { Text("Reboot Control") },
                    selected = currentScreen == "Reboot",
                    onClick = { currentScreen = "Reboot"; scope.launch { drawerState.close() } },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = Mint.copy(alpha = 0.1f), selectedIconColor = Mint, selectedTextColor = Mint)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Terminal, contentDescription = null) },
                    label = { Text("Terminal (ADB)") },
                    selected = currentScreen == "Terminal",
                    onClick = { currentScreen = "Terminal"; scope.launch { drawerState.close() } },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = Mint.copy(alpha = 0.1f), selectedIconColor = Mint, selectedTextColor = Mint)
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                    selected = currentScreen == "Settings",
                    onClick = { currentScreen = "Settings"; scope.launch { drawerState.close() } },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = Mint.copy(alpha = 0.1f), selectedIconColor = Mint, selectedTextColor = Mint)
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            com.hypex.hyflash.ui.components.AnimatedBackground()

            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                currentScreen.uppercase(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        },
                        actions = {
                            DeviceChip(deviceState)
                            IconButton(onClick = { showConsole = true }) {
                                Icon(Icons.Default.Terminal, contentDescription = "Console")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                }
            ) { padding ->
                Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "ScreenTransition"
                    ) { screen ->
                        when (screen) {
                            "Dashboard" -> DashboardScreen(viewModel) { currentScreen = it }
                            "Flash" -> FlashScreen(viewModel)
                            "Sideload" -> SideloadScreen(viewModel)
                            "Wipe" -> WipeScreen(viewModel)
                            "Reboot" -> RebootScreen(viewModel)
                            "Terminal" -> CmdScreen(viewModel)
                            "Settings" -> SettingsScreen(viewModel)
                        }
                    }
                }
            }
        }
    }
}
