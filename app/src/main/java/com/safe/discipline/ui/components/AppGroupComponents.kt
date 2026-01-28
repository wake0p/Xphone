package com.safe.discipline.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.safe.discipline.data.model.AppGroup
import com.safe.discipline.data.model.AppInfo
import com.safe.discipline.viewmodel.MainViewModel
import java.util.*

@Composable
fun AppGroupsDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
        val groups by viewModel.groups.collectAsState()
        var groupToEdit by remember { mutableStateOf<AppGroup?>(null) }
        var showCreateGroup by remember { mutableStateOf(false) }

        Dialog(
                onDismissRequest = onDismiss,
                properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
                Surface(
                        modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.8f),
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp
                ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Text(
                                                "应用分类管理",
                                                style = MaterialTheme.typography.headlineSmall,
                                                fontWeight = FontWeight.Bold
                                        )
                                        IconButton(onClick = onDismiss) {
                                                Icon(Icons.Default.Close, null)
                                        }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                LazyColumn(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                        if (groups.isEmpty()) {
                                                item {
                                                        Box(
                                                                Modifier.fillMaxWidth()
                                                                        .padding(40.dp),
                                                                contentAlignment = Alignment.Center
                                                        ) {
                                                                Text(
                                                                        "暂无分类，点击下方按钮创建",
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .outline
                                                                )
                                                        }
                                                }
                                        }
                                        items(groups, key = { it.id }) { group ->
                                                Surface(
                                                        onClick = { groupToEdit = group },
                                                        shape = RoundedCornerShape(16.dp),
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .surfaceVariant.copy(
                                                                        alpha = 0.4f
                                                                ),
                                                        border =
                                                                BorderStroke(
                                                                        1.dp,
                                                                        MaterialTheme.colorScheme
                                                                                .outlineVariant
                                                                                .copy(alpha = 0.5f)
                                                                )
                                                ) {
                                                        Row(
                                                                modifier = Modifier.padding(16.dp),
                                                                verticalAlignment =
                                                                        Alignment.CenterVertically
                                                        ) {
                                                                Box(
                                                                        Modifier.size(12.dp)
                                                                                .background(
                                                                                        Color(
                                                                                                if (group.color !=
                                                                                                                0
                                                                                                )
                                                                                                        group.color
                                                                                                else
                                                                                                        MaterialTheme
                                                                                                                .colorScheme
                                                                                                                .primary
                                                                                                                .toArgb()
                                                                                        ),
                                                                                        CircleShape
                                                                                )
                                                                )
                                                                Spacer(
                                                                        modifier =
                                                                                Modifier.width(
                                                                                        16.dp
                                                                                )
                                                                )
                                                                Column(Modifier.weight(1f)) {
                                                                        Text(
                                                                                group.name,
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Bold
                                                                        )
                                                                        Text(
                                                                                "${group.packages.size} 个应用",
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .bodySmall,
                                                                                color =
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .outline
                                                                        )
                                                                }
                                                                Icon(
                                                                        Icons.Default.Edit,
                                                                        null,
                                                                        tint =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .primary,
                                                                        modifier =
                                                                                Modifier.size(20.dp)
                                                                )
                                                        }
                                                }
                                        }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                        onClick = { showCreateGroup = true },
                                        modifier = Modifier.fillMaxWidth().height(56.dp),
                                        shape = RoundedCornerShape(16.dp)
                                ) {
                                        Icon(Icons.Default.Add, null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("新建分类")
                                }
                        }
                }
        }

        if (showCreateGroup) {
                GroupEditDialog(
                        viewModel = viewModel,
                        group = null,
                        onDismiss = { showCreateGroup = false }
                )
        }

        if (groupToEdit != null) {
                GroupEditDialog(
                        viewModel = viewModel,
                        group = groupToEdit,
                        onDismiss = { groupToEdit = null }
                )
        }
}

@Composable
fun GroupEditDialog(viewModel: MainViewModel, group: AppGroup?, onDismiss: () -> Unit) {
        var name by remember { mutableStateOf(group?.name ?: "") }
        val selectedPackages = remember {
                mutableStateListOf<String>().apply { if (group != null) addAll(group.packages) }
        }
        val installedApps by viewModel.installedApps.collectAsState()
        val allGroups by viewModel.groups.collectAsState()
        var searchQuery by remember { mutableStateOf("") }

        Dialog(
                onDismissRequest = onDismiss,
                properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
                Surface(
                        modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.9f),
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp
                ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                        if (group == null) "新建分类" else "编辑分类",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                OutlinedTextField(
                                        value = name,
                                        onValueChange = { name = it },
                                        label = { Text("分类名称") },
                                        placeholder = { Text("例如：社交游戏、办公协作") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        singleLine = true
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                AppSelectorComponent(
                                        installedApps = installedApps,
                                        allGroups =
                                                allGroups.filter { it.id != group?.id }, // 避免循环引用
                                        selectedPackages = selectedPackages,
                                        searchQuery = searchQuery,
                                        onSearchQueryChange = { searchQuery = it },
                                        modifier = Modifier.weight(1f)
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                        if (group != null) {
                                                OutlinedButton(
                                                        onClick = {
                                                                viewModel.deleteGroup(group.id)
                                                                onDismiss()
                                                        },
                                                        modifier =
                                                                Modifier.weight(1f).height(50.dp),
                                                        shape = RoundedCornerShape(12.dp),
                                                        colors =
                                                                ButtonDefaults.outlinedButtonColors(
                                                                        contentColor =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .error
                                                                )
                                                ) { Text("删除") }
                                        }

                                        Button(
                                                onClick = {
                                                        if (name.isNotBlank()) {
                                                                viewModel.saveGroup(
                                                                        AppGroup(
                                                                                id = group?.id
                                                                                                ?: UUID.randomUUID()
                                                                                                        .toString(),
                                                                                name = name,
                                                                                packages =
                                                                                        selectedPackages
                                                                                                .toSet()
                                                                        )
                                                                )
                                                                onDismiss()
                                                        }
                                                },
                                                modifier = Modifier.weight(2f).height(50.dp),
                                                shape = RoundedCornerShape(12.dp)
                                        ) { Text("保存分类") }
                                }
                        }
                }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectorComponent(
        installedApps: List<AppInfo>,
        allGroups: List<AppGroup>,
        selectedPackages: MutableList<String>,
        selectedGroupIds: MutableList<String>? = null,
        searchQuery: String,
        onSearchQueryChange: (String) -> Unit,
        modifier: Modifier = Modifier
) {
        val filteredApps by
                remember(installedApps, searchQuery) {
                        derivedStateOf {
                                installedApps.filter {
                                        it.appName.contains(searchQuery, true) ||
                                                it.packageName.contains(searchQuery, true)
                                }
                        }
                }

        // 构建 packageName -> groupName 的映射，用于显示分类标签
        val packageToGroupName by
                remember(allGroups, selectedGroupIds) {
                        derivedStateOf {
                                val result = mutableMapOf<String, String>()
                                if (selectedGroupIds != null) {
                                        allGroups
                                                .filter { selectedGroupIds.contains(it.id) }
                                                .forEach { group ->
                                                        group.packages.forEach { pkg ->
                                                                // 如果一个包属于多个选中的分类，显示第一个
                                                                if (!result.containsKey(pkg)) {
                                                                        result[pkg] = group.name
                                                                }
                                                        }
                                                }
                                }
                                result
                        }
                }

        // 对话框状态：当用户尝试取消选中一个属于分类的应用时显示
        var showRemoveConfirmDialog by remember { mutableStateOf<Pair<AppInfo, String>?>(null) }

        Column(modifier = modifier) {
                OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("搜索应用并勾选...") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors =
                                TextFieldDefaults.colors(
                                        unfocusedContainerColor =
                                                MaterialTheme.colorScheme.surfaceVariant.copy(
                                                        alpha = 0.3f
                                                ),
                                        focusedContainerColor =
                                                MaterialTheme.colorScheme.surfaceVariant.copy(
                                                        alpha = 0.3f
                                                )
                                )
                )

                if (allGroups.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                                "快捷选择分类",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                        )
                        LazyRow(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                                items(allGroups) { group ->
                                        val isGroupSelected =
                                                selectedGroupIds?.contains(group.id) == true

                                        FilterChip(
                                                selected = isGroupSelected,
                                                onClick = {
                                                        if (selectedGroupIds != null) {
                                                                if (isGroupSelected) {
                                                                        selectedGroupIds.remove(
                                                                                group.id
                                                                        )
                                                                } else {
                                                                        selectedGroupIds.add(
                                                                                group.id
                                                                        )
                                                                }
                                                        }
                                                },
                                                label = { Text(group.name) },
                                                leadingIcon =
                                                        if (isGroupSelected) {
                                                                {
                                                                        Icon(
                                                                                Icons.Default.Check,
                                                                                null,
                                                                                modifier =
                                                                                        Modifier.size(
                                                                                                16.dp
                                                                                        )
                                                                        )
                                                                }
                                                        } else null,
                                                shape = RoundedCornerShape(12.dp)
                                        )
                                }
                        }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 显示选中统计和取消全部按钮
                val groupCount = selectedGroupIds?.size ?: 0
                val appCount = selectedPackages.size
                val hasSelection = groupCount > 0 || appCount > 0
                val statsText =
                        when {
                                groupCount > 0 && appCount > 0 ->
                                        "已选 $groupCount 个分类 + $appCount 个单独应用"
                                groupCount > 0 -> "已选 $groupCount 个分类"
                                appCount > 0 -> "已选 $appCount 个应用"
                                else -> "未选择任何应用"
                        }

                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Text(
                                statsText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                        )

                        if (hasSelection) {
                                TextButton(
                                        onClick = {
                                                selectedGroupIds?.clear()
                                                selectedPackages.clear()
                                        },
                                        contentPadding =
                                                PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                        Icon(
                                                Icons.Default.ClearAll,
                                                null,
                                                modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("取消全部", style = MaterialTheme.typography.labelSmall)
                                }
                        }
                }

                LazyColumn(modifier = Modifier.weight(1f)) {
                        items(filteredApps, key = { it.packageName }) { app ->
                                val groupName = packageToGroupName[app.packageName]
                                val isFromGroup = groupName != null
                                val isDirectlySelected = selectedPackages.contains(app.packageName)
                                val isSelected = isFromGroup || isDirectlySelected

                                AppItemRow(
                                        app = app,
                                        selected = isSelected,
                                        groupLabel = groupName,
                                        onSelect = { isSel ->
                                                if (isSel) {
                                                        // 勾选应用
                                                        if (!selectedPackages.contains(
                                                                        app.packageName
                                                                )
                                                        ) {
                                                                selectedPackages.add(
                                                                        app.packageName
                                                                )
                                                        }
                                                } else {
                                                        // 取消勾选
                                                        if (isFromGroup) {
                                                                // 这个应用属于某个分类，弹出确认框
                                                                showRemoveConfirmDialog =
                                                                        Pair(app, groupName!!)
                                                        } else {
                                                                // 直接移除
                                                                selectedPackages.remove(
                                                                        app.packageName
                                                                )
                                                        }
                                                }
                                        },
                                        onClick = {}
                                )
                        }
                }
        }

        // 确认对话框
        if (showRemoveConfirmDialog != null) {
                val (app, gName) = showRemoveConfirmDialog!!
                AlertDialog(
                        onDismissRequest = { showRemoveConfirmDialog = null },
                        title = { Text("移除「${app.appName}」") },
                        text = { Text("该应用属于「$gName」分类，您想要：") },
                        confirmButton = {
                                TextButton(
                                        onClick = {
                                                // 取消整个分类
                                                val groupToRemove =
                                                        allGroups.find { it.name == gName }
                                                if (groupToRemove != null &&
                                                                selectedGroupIds != null
                                                ) {
                                                        selectedGroupIds.remove(groupToRemove.id)
                                                }
                                                showRemoveConfirmDialog = null
                                        }
                                ) { Text("取消整个「$gName」分类") }
                        },
                        dismissButton = {
                                Row {
                                        TextButton(onClick = { showRemoveConfirmDialog = null }) {
                                                Text("不做修改")
                                        }
                                }
                        }
                )
        }
}
