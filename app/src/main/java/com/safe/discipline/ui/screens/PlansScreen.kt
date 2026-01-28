package com.safe.discipline.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.safe.discipline.data.model.AppInfo
import com.safe.discipline.data.model.BlockPlan
import com.safe.discipline.data.service.SettingsManager
import com.safe.discipline.ui.components.*
import com.safe.discipline.viewmodel.MainViewModel
import java.util.*
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlansScreen(viewModel: MainViewModel) {
    val plans by viewModel.plans.collectAsState()
    var planToEdit by remember { mutableStateOf<BlockPlan?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showGroupsDialog by remember { mutableStateOf(false) }

    // 强制模式相关状态
    val context = LocalContext.current
    val forceModeEnabled by SettingsManager.forceModeEnabled.collectAsState()
    var pendingDisablePlan by remember { mutableStateOf<BlockPlan?>(null) }
    var pendingSavePlan by remember { mutableStateOf<BlockPlan?>(null) }
    var showForceUnlockDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadPlans()
        SettingsManager.init(context)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (plans.isEmpty()) {
            Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                        Icons.Filled.Schedule,
                        null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("暂无自动计划", color = MaterialTheme.colorScheme.outline)
                TextButton(onClick = { showCreateDialog = true }) { Text("点击创建一个吧") }

                Spacer(modifier = Modifier.height(24.dp))
                FilledTonalButton(
                        onClick = { showGroupsDialog = true },
                        shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Category, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("管理应用分类")
                }
            }
        } else {
            LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                                "自动化规则",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                        )
                        FilledTonalButton(
                                onClick = { showGroupsDialog = true },
                                shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Category, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("应用分类")
                        }
                    }
                }

                items(plans, key = { it.id }) { plan ->
                    PlanItemCard(
                            plan = plan,
                            onToggle = { enabled ->
                                if (!enabled && plan.isEnabled && forceModeEnabled) {
                                    // 尝试禁用一个已启用的计划，且强制模式开启
                                    pendingDisablePlan = plan
                                    showForceUnlockDialog = true
                                } else {
                                    viewModel.savePlan(plan.copy(isEnabled = enabled))
                                    viewModel.loadApps()
                                }
                            },
                            onDelete = {
                                if (forceModeEnabled && plan.isEnabled) {
                                    // 尝试删除一个已启用的计划
                                    pendingDisablePlan = plan.copy(isEnabled = false) // 标记为删除操作
                                    showForceUnlockDialog = true
                                } else {
                                    viewModel.deletePlan(plan.id)
                                    viewModel.loadApps()
                                }
                            },
                            onClick = { planToEdit = plan }
                    )
                }
            }
        }

        LargeFloatingActionButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) { Icon(Icons.Filled.Add, "创建计划") }
    }

    if (showCreateDialog || planToEdit != null) {
        CreatePlanFullScreenDialog(
                initialPlan = planToEdit,
                installedApps = viewModel.installedApps.collectAsState().value,
                allGroups = viewModel.groups.collectAsState().value,
                onConfirm = { name, pkgs, gIds, days, start, end ->
                    val isEditing = planToEdit != null
                    val newPlan =
                            planToEdit?.copy(
                                    label = name,
                                    packages = pkgs,
                                    groupIds = gIds,
                                    daysOfWeek = days,
                                    startTime = start,
                                    endTime = end
                            )
                                    ?: BlockPlan(
                                            label = name,
                                            packages = pkgs,
                                            groupIds = gIds,
                                            daysOfWeek = days,
                                            startTime = start,
                                            endTime = end,
                                            isEnabled = false // Default closed as requested
                                    )

                    if (isEditing && forceModeEnabled && planToEdit!!.isEnabled) {
                        // 尝试修改一个已启用的计划，且强制模式开启
                        pendingSavePlan = newPlan
                        showForceUnlockDialog = true
                    } else {
                        viewModel.savePlan(newPlan)
                        viewModel.loadApps()
                        showCreateDialog = false
                        planToEdit = null
                    }
                },
                onDismiss = {
                    showCreateDialog = false
                    planToEdit = null
                }
        )
    }

    if (showGroupsDialog) {
        AppGroupsDialog(viewModel) { showGroupsDialog = false }
    }

    // 强制模式解锁对话框
    if (showForceUnlockDialog && (pendingDisablePlan != null || pendingSavePlan != null)) {
        val isDisable = pendingDisablePlan != null
        val planLabel = if (isDisable) pendingDisablePlan?.label else pendingSavePlan?.label ?: ""
        val actionDesc = if (isDisable) "关闭自动计划「$planLabel」" else "修改自动计划「$planLabel」"

        ForceUnlockDialog(
                actionDescription = actionDesc,
                onConfirm = {
                    if (isDisable) {
                        pendingDisablePlan?.let { plan ->
                            if (!plan.isEnabled
                            ) { // isEnabled is false in pending object means delete
                                viewModel.deletePlan(plan.id)
                            } else {
                                viewModel.savePlan(plan.copy(isEnabled = false))
                            }
                        }
                    } else {
                        // Config Save
                        pendingSavePlan?.let { plan -> viewModel.savePlan(plan) }
                        showCreateDialog = false
                        planToEdit = null
                    }
                    viewModel.loadApps()
                    showForceUnlockDialog = false
                    pendingDisablePlan = null
                    pendingSavePlan = null
                },
                onDismiss = {
                    showForceUnlockDialog = false
                    pendingDisablePlan = null
                    pendingSavePlan = null
                }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanItemCard(
        plan: BlockPlan,
        onToggle: (Boolean) -> Unit,
        onDelete: () -> Unit,
        onClick: () -> Unit
) {
    ElevatedCard(
            shape = RoundedCornerShape(20.dp),
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                            plan.label,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                    )
                    Text(
                            "${plan.startTime} - ${plan.endTime}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                    )
                }
                Switch(checked = plan.isEnabled, onCheckedChange = onToggle)
            }

            Spacer(modifier = Modifier.height(8.dp))

            val dayNames = listOf("日", "一", "二", "三", "四", "五", "六")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 1..7) {
                    val isActive = plan.daysOfWeek.contains(i)
                    Box(
                            modifier =
                                    Modifier.size(24.dp)
                                            .clip(CircleShape)
                                            .background(
                                                    if (isActive) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.surfaceVariant
                                            ),
                            contentAlignment = Alignment.Center
                    ) {
                        Text(
                                dayNames[i - 1],
                                fontSize = 10.sp,
                                color =
                                        if (isActive) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val groupText =
                        if (plan.groupIds.isNotEmpty()) "及 ${plan.groupIds.size} 个分类" else ""
                Text(
                        "管理 ${plan.packages.size} 个应用$groupText",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePlanFullScreenDialog(
        initialPlan: BlockPlan? = null,
        installedApps: List<AppInfo>,
        allGroups: List<com.safe.discipline.data.model.AppGroup>,
        onConfirm: (String, List<String>, List<String>, List<Int>, String, String) -> Unit,
        onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialPlan?.label ?: "我的计划") }
    var startTime by remember { mutableStateOf(initialPlan?.startTime ?: "22:00") }
    var endTime by remember { mutableStateOf(initialPlan?.endTime ?: "07:00") }

    val selectedDays = remember {
        mutableStateListOf<Int>().apply {
            addAll(initialPlan?.daysOfWeek ?: listOf(1, 2, 3, 4, 5, 6, 7))
        }
    }
    val selectedApps = remember {
        mutableStateListOf<String>().apply { addAll(initialPlan?.packages ?: emptyList()) }
    }
    val selectedGroupIds = remember {
        mutableStateListOf<String>().apply { addAll(initialPlan?.groupIds ?: emptyList()) }
    }
    var searchQuery by remember { mutableStateOf("") }

    var pickingTimeForStart by remember { mutableStateOf<Boolean?>(null) }

    Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Scaffold(
                    topBar = {
                        TopAppBar(
                                title = {
                                    Text(
                                            if (initialPlan == null) "创建自动计划" else "编辑自动计划",
                                            fontWeight = FontWeight.Bold
                                    )
                                },
                                navigationIcon = {
                                    IconButton(onClick = onDismiss) {
                                        Icon(Icons.Filled.Close, "取消")
                                    }
                                },
                                actions = {
                                    Button(
                                            onClick = {
                                                if (name.isNotBlank() &&
                                                                (selectedApps.isNotEmpty() ||
                                                                        selectedGroupIds
                                                                                .isNotEmpty())
                                                ) {
                                                    onConfirm(
                                                            name,
                                                            selectedApps.toList(),
                                                            selectedGroupIds.toList(),
                                                            selectedDays.toList(),
                                                            startTime,
                                                            endTime
                                                    )
                                                }
                                            },
                                            enabled =
                                                    name.isNotBlank() &&
                                                            (selectedApps.isNotEmpty() ||
                                                                    selectedGroupIds.isNotEmpty()),
                                            modifier = Modifier.padding(end = 8.dp)
                                    ) { Text("保存") }
                                }
                        )
                    }
            ) { padding ->
                Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                    Column(
                            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("计划名称") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                        )

                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ReadOnlyTextField(
                                    value = startTime,
                                    label = "开始时刻",
                                    onClick = { pickingTimeForStart = true },
                                    modifier = Modifier.weight(1f)
                            )
                            ReadOnlyTextField(
                                    value = endTime,
                                    label = "结束时刻",
                                    onClick = { pickingTimeForStart = false },
                                    modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val days = listOf("日", "一", "二", "三", "四", "五", "六")
                            for (i in 1..7) {
                                Box(
                                        modifier =
                                                Modifier.size(40.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                                if (selectedDays.contains(i))
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                                else
                                                                        MaterialTheme.colorScheme
                                                                                .surfaceVariant
                                                        )
                                                        .clickable {
                                                            if (selectedDays.contains(i))
                                                                    selectedDays.remove(i)
                                                            else selectedDays.add(i)
                                                        },
                                        contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                            days[i - 1],
                                            color =
                                                    if (selectedDays.contains(i))
                                                            MaterialTheme.colorScheme.onPrimary
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                                "选择管控范围",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                        )

                        AppSelectorComponent(
                                installedApps = installedApps,
                                allGroups = allGroups,
                                selectedPackages = selectedApps,
                                selectedGroupIds = selectedGroupIds,
                                searchQuery = searchQuery,
                                onSearchQueryChange = { searchQuery = it },
                                modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    if (pickingTimeForStart != null) {
        PremiumTimePickerDialog(
                initialTime = if (pickingTimeForStart == true) startTime else endTime,
                title = if (pickingTimeForStart == true) "设定禁用时刻" else "设定恢复时刻",
                onConfirm = { formattedTime ->
                    if (pickingTimeForStart == true) startTime = formattedTime
                    else endTime = formattedTime
                    pickingTimeForStart = null
                },
                onDismiss = { pickingTimeForStart = null }
        )
    }
}

@Composable
fun TimeHeaderChip(value: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            color =
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.size(72.dp, 80.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                    value,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color =
                            if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun AmPmButton(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
            onClick = onClick,
            shape = RoundedCornerShape(8.dp),
            color = if (isSelected) MaterialTheme.colorScheme.secondary else Color.Transparent,
            border = null, // 简化处理：移除边框
            modifier = Modifier.size(48.dp, 32.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color =
                            if (isSelected) MaterialTheme.colorScheme.onSecondary
                            else MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun ReadOnlyTextField(
        value: String,
        label: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        OutlinedTextField(
                value = value,
                onValueChange = {},
                label = { Text(label) },
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                trailingIcon = {
                    Icon(Icons.Filled.AccessTime, null, tint = MaterialTheme.colorScheme.primary)
                }
        )
        Box(
                modifier =
                        Modifier.matchParentSize()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(onClick = onClick)
        )
    }
}
