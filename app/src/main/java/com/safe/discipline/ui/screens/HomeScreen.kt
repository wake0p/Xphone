package com.safe.discipline.ui.screens

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safe.discipline.data.model.AppInfo
import com.safe.discipline.data.service.SettingsManager
import com.safe.discipline.ui.components.AppItemRow
import com.safe.discipline.ui.components.ForceModeSettingsDialog
import com.safe.discipline.ui.components.ForceUnlockDialog
import com.safe.discipline.ui.components.PlanLockedDialog
import com.safe.discipline.ui.components.SettingsDropdownMenu
import com.safe.discipline.viewmodel.MainViewModel

enum class HomeTab {
    CONTROL,
    PLANS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: MainViewModel = viewModel()) {
    val statusText by viewModel.statusText.collectAsState()
    val hasPermission by viewModel.hasPermission.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val autoPort by viewModel.autoPort.collectAsState()

    var currentTab by remember { mutableStateOf(HomeTab.CONTROL) }
    var showAuthHelp by remember { mutableStateOf(false) }
    var showPairInstruction by remember { mutableStateOf(false) }

    // 设置菜单状态
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showForceModeSettings by remember { mutableStateOf(false) }

    // 强制模式状态
    val forceModeEnabled by SettingsManager.forceModeEnabled.collectAsState()
    var showForceUnlockDialog by remember { mutableStateOf(false) }
    var pendingShowPackages by remember { mutableStateOf<List<String>>(emptyList()) }

    // 计划锁定提示状态
    var showPlanLockedDialog by remember { mutableStateOf(false) }
    var lockedPlanName by remember { mutableStateOf("") }

    val context = LocalContext.current

    // 初始化 SettingsManager
    LaunchedEffect(Unit) { SettingsManager.init(context) }

    val permissionLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                    isGranted ->
                if (isGranted) {
                    viewModel.startPairingService()
                    showPairInstruction = true
                }
            }

    val bgGradient =
            Brush.verticalGradient(
                    colors =
                            listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                    MaterialTheme.colorScheme.surface
                            )
            )

    // 统一处理应用操作
    val handleAppAction: (String, List<String>) -> Unit = { action, packages ->
        android.util.Log.d(
                "OutPhone",
                "Action: $action, Pkgs: $packages, ForceMode: $forceModeEnabled"
        )

        if (action == "show") {
            val currentApps = viewModel.installedApps.value

            // 1. 分离出被计划锁定的应用
            val plannedApps =
                    packages.filter { pkg ->
                        currentApps.find { it.packageName == pkg }?.blockedBy != null
                    }

            if (plannedApps.isNotEmpty()) {
                // 存在被计划锁定的应用，禁止操作并提示
                val firstPkg = plannedApps.first()
                lockedPlanName =
                        currentApps.find { it.packageName == firstPkg }?.blockedBy ?: "未知计划"
                showPlanLockedDialog = true
            } else {
                // 2. 剩下的都是手动冻结的应用，根据强制模式决定是否验证
                if (forceModeEnabled) {
                    pendingShowPackages = packages
                    showForceUnlockDialog = true
                } else {
                    viewModel.showApps(packages)
                }
            }
        } else if (action == "hide") {
            viewModel.hideApps(packages)
        }
    }

    Scaffold(
            modifier = Modifier.background(bgGradient),
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                        title = {
                            Text(
                                    if (currentTab == HomeTab.CONTROL) "应用控制" else "自动计划",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                            )
                        },
                        colors =
                                TopAppBarDefaults.centerAlignedTopAppBarColors(
                                        containerColor = Color.Transparent
                                ),
                        actions = {
                            IconButton(onClick = { viewModel.loadApps(forceRefresh = true) }) {
                                Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "刷新列表",
                                        tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Box {
                                IconButton(onClick = { showSettingsMenu = true }) {
                                    Icon(
                                            Icons.Filled.Settings,
                                            null,
                                            tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                SettingsDropdownMenu(
                                        expanded = showSettingsMenu,
                                        onDismiss = { showSettingsMenu = false },
                                        onShowSettingsDialog = { showForceModeSettings = true }
                                )
                            }
                        }
                )
            },
            bottomBar = {
                if (hasPermission) {
                    NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            tonalElevation = 8.dp
                    ) {
                        NavigationBarItem(
                                selected = currentTab == HomeTab.CONTROL,
                                onClick = { currentTab = HomeTab.CONTROL },
                                icon = { Icon(Icons.Filled.Apps, null) },
                                label = { Text("即时控制") }
                        )
                        NavigationBarItem(
                                selected = currentTab == HomeTab.PLANS,
                                onClick = { currentTab = HomeTab.PLANS },
                                icon = { Icon(Icons.Filled.EventRepeat, null) },
                                label = { Text("自动计划") }
                        )
                    }
                }
            }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (!hasPermission) {
                Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center
                ) {
                    StatusCard(
                            statusText = statusText,
                            autoPort = autoPort,
                            onConnect = { port ->
                                viewModel.startWirelessActivation("127.0.0.1", port)
                            },
                            onPair = {
                                if (android.os.Build.VERSION.SDK_INT >=
                                                android.os.Build.VERSION_CODES.TIRAMISU
                                ) {
                                    if (androidx.core.content.ContextCompat.checkSelfPermission(
                                                    context,
                                                    android.Manifest.permission.POST_NOTIFICATIONS
                                            ) ==
                                                    android.content.pm.PackageManager
                                                            .PERMISSION_GRANTED
                                    ) {
                                        viewModel.startPairingService()
                                        showPairInstruction = true
                                    } else {
                                        permissionLauncher.launch(
                                                android.Manifest.permission.POST_NOTIFICATIONS
                                        )
                                    }
                                } else {
                                    viewModel.startPairingService()
                                    showPairInstruction = true
                                }
                            },
                            onShowHelp = { showAuthHelp = true }
                    )
                }
            } else {
                Crossfade(targetState = currentTab, label = "TabSwitch") { tab ->
                    when (tab) {
                        HomeTab.CONTROL -> {
                            AppListContent(apps = installedApps, onAction = handleAppAction)
                        }
                        HomeTab.PLANS -> {
                            PlansScreen(viewModel)
                        }
                    }
                }
            }
        }
    }

    if (showAuthHelp) AuthenticationHelpDialog(onDismiss = { showAuthHelp = false })
    if (showPairInstruction) {
        AlertDialog(
                onDismissRequest = { showPairInstruction = false },
                title = { Text("配对中") },
                text = { Text("请在通知栏输入无线调试配对码。") },
                confirmButton = {
                    TextButton(onClick = { showPairInstruction = false }) { Text("好") }
                }
        )
    }

    // 强制模式设置对话框
    if (showForceModeSettings) {
        ForceModeSettingsDialog(onDismiss = { showForceModeSettings = false })
    }

    // 计划锁定提示对话框
    if (showPlanLockedDialog) {
        PlanLockedDialog(
                planName = lockedPlanName,
                onDismiss = { showPlanLockedDialog = false },
                onGoToPlans = {
                    showPlanLockedDialog = false
                    currentTab = HomeTab.PLANS
                }
        )
    }

    // 强制模式验证对话框 (恢复应用)
    if (showForceUnlockDialog) {
        ForceUnlockDialog(
                actionDescription = "恢复 ${pendingShowPackages.size} 个应用",
                onConfirm = {
                    viewModel.showApps(pendingShowPackages)
                    pendingShowPackages = emptyList()
                    showForceUnlockDialog = false
                },
                onDismiss = {
                    pendingShowPackages = emptyList()
                    showForceUnlockDialog = false
                }
        )
    }
}

@Composable
fun StatusCard(
        statusText: String,
        autoPort:
                Int?, // This parameter is now unused but kept for compatibility or removal in next
        // step
        onConnect: (Int) -> Unit, // Unused
        onPair: () -> Unit, // Unused
        onShowHelp: () -> Unit // Unused
) {
    val context = LocalContext.current

    ElevatedCard(
            shape = RoundedCornerShape(24.dp),
            colors =
                    CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                    ),
            modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            // Status Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                val isActive =
                        statusText.contains("Running") ||
                                statusText.contains("已连接") ||
                                statusText.contains("成功")
                Icon(
                        Icons.Filled.Bolt,
                        null,
                        tint =
                                if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                        if (isActive) "服务已就绪" else "服务未就绪",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                    statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(24.dp))

            // New Action Buttons launching Internal Shizuku Activities
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Button 1: Pairing Tutorial
                Button(
                        onClick = {
                            val intent =
                                    context.packageManager.getLaunchIntentForPackage(
                                            "moe.shizuku.manager"
                                    )
                            if (intent != null) {
                                context.startActivity(intent)
                            } else {
                                android.widget.Toast.makeText(
                                                context,
                                                "请先安装 Shizuku",
                                                android.widget.Toast.LENGTH_SHORT
                                        )
                                        .show()
                            }
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors =
                                ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.tertiary
                                )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.QrCodeScanner, null, modifier = Modifier.size(20.dp))
                        Text("无线配对", fontSize = 12.sp)
                    }
                }

                // Button 2: Start Service (Starter)
                Button(
                        onClick = {
                            val intent =
                                    context.packageManager.getLaunchIntentForPackage(
                                            "moe.shizuku.manager"
                                    )
                            if (intent != null) {
                                context.startActivity(intent)
                            } else {
                                android.widget.Toast.makeText(
                                                context,
                                                "请先安装 Shizuku",
                                                android.widget.Toast.LENGTH_SHORT
                                        )
                                        .show()
                            }
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors =
                                ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(20.dp))
                        Text("一键启动", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                    "如果服务未运行，请先点击【无线配对】，完成后点击【一键启动】。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun AppListContent(apps: List<AppInfo>, onAction: (String, List<String>) -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    val selectedPackages = remember { mutableStateListOf<String>() }
    val filteredApps =
            remember(apps, searchQuery) {
                apps
                        .filter {
                            it.appName.contains(searchQuery, true) ||
                                    it.packageName.contains(searchQuery, true)
                        }
                        .sortedWith(compareBy<AppInfo> { it.isEnabled }.thenBy { it.appName })
            }
    val disabledApps = filteredApps.filter { !it.isEnabled }
    val enabledApps = filteredApps.filter { it.isEnabled }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
                modifier =
                        Modifier.fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                .padding(4.dp)
        ) {
            TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("搜索应用...") },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.primary)
                    },
                    colors =
                            TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                            ),
                    modifier = Modifier.fillMaxWidth()
            )
        }
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp)) {
            if (disabledApps.isNotEmpty()) {
                item(key = "hdr_dis") {
                    CategoryHeader("已冻结", disabledApps.size, MaterialTheme.colorScheme.error)
                }
                items(disabledApps, key = { "dis_${it.packageName}" }) { app ->
                    AppItemRowWithAnim(app, selectedPackages, onAction)
                }
            }
            item(key = "sp_1") { Spacer(modifier = Modifier.height(16.dp)) }
            if (enabledApps.isNotEmpty()) {
                item(key = "hdr_en") {
                    CategoryHeader("已启用", enabledApps.size, MaterialTheme.colorScheme.primary)
                }
                items(enabledApps, key = { "en_${it.packageName}" }) { app ->
                    AppItemRowWithAnim(app, selectedPackages, onAction)
                }
            }
        }
        AnimatedVisibility(
                visible = selectedPackages.isNotEmpty(),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
        ) {
            Surface(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 8.dp
            ) {
                Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                            onClick = {
                                onAction("hide", selectedPackages.toList())
                                selectedPackages.clear()
                            },
                            modifier = Modifier.weight(1f),
                            colors =
                                    ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error
                                    )
                    ) { Text("冻结所选") }
                    Button(
                            onClick = {
                                onAction("show", selectedPackages.toList())
                                selectedPackages.clear()
                            },
                            modifier = Modifier.weight(1f)
                    ) { Text("恢复所选") }
                }
            }
        }
    }
}

@Composable
fun AppItemRowWithAnim(
        app: AppInfo,
        selectedPackages: MutableList<String>,
        onAction: (String, List<String>) -> Unit
) {
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }

    AnimatedVisibility(
            visibleState = visibleState,
            enter =
                    fadeIn(animationSpec = tween(600)) +
                            slideInVertically(
                                    initialOffsetY = { it / 2 },
                                    animationSpec = tween(600)
                            ),
            exit = fadeOut(animationSpec = tween(300))
    ) {
        AppItemRow(
                app = app,
                selected = selectedPackages.contains(app.packageName),
                onSelect = { isSel ->
                    if (isSel) selectedPackages.add(app.packageName)
                    else selectedPackages.remove(app.packageName)
                },
                onClick = {
                    if (selectedPackages.contains(app.packageName))
                            selectedPackages.remove(app.packageName)
                    else selectedPackages.add(app.packageName)
                }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryHeader(title: String, count: Int, color: Color) {
    Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
    ) {
        Box(modifier = Modifier.size(4.dp, 16.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = color,
                fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(8.dp))
        Badge(containerColor = color.copy(alpha = 0.1f), contentColor = color) {
            Text(count.toString())
        }
    }
}

@Composable
fun AuthenticationHelpDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("开启无线激活", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("1. 请进入开发者选项，开启“无线调试”。")
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                            onClick = {
                                try {
                                    context.startActivity(
                                            Intent(
                                                    AndroidSettings
                                                            .ACTION_APPLICATION_DEVELOPMENT_SETTINGS
                                            )
                                    )
                                } catch (e: Exception) {
                                    context.startActivity(Intent(AndroidSettings.ACTION_SETTINGS))
                                }
                            },
                            shape = RoundedCornerShape(8.dp)
                    ) { Text("去设置") }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("2. 点击“使用配对码配对设备”。")
                    Text("3. 在通知栏弹出的输入框中填入显示的配对码即可。")
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("了解") } }
    )
}
