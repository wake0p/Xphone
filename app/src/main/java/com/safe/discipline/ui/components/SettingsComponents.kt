package com.safe.discipline.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.safe.discipline.data.MotivationalQuotes
import com.safe.discipline.data.service.SettingsManager

/** 设置菜单下拉组件 */
@Composable
fun SettingsDropdownMenu(
        expanded: Boolean,
        onDismiss: () -> Unit,
        onShowSettingsDialog: () -> Unit
) {
    val context = LocalContext.current

    val forceModeEnabled by SettingsManager.forceModeEnabled.collectAsState()
    val forceModeDelay by SettingsManager.forceModeDelay.collectAsState()
    var showDisableDialog by remember { mutableStateOf(false) }
    var showModifyDelayDialog by remember { mutableStateOf(false) }

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        // 强制模式开关
        DropdownMenuItem(
                text = {
                    Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                                if (forceModeEnabled) Icons.Default.Lock
                                else Icons.Default.LockOpen,
                                null,
                                tint =
                                        if (forceModeEnabled) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.outline
                        )
                        Column {
                            Text("强制模式", fontWeight = FontWeight.Medium)
                            Text(
                                    if (forceModeEnabled) "已开启 - 解锁需要验证" else "已关闭",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                },
                trailingIcon = {
                    Switch(
                            checked = forceModeEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    SettingsManager.setForceModeEnabled(context, true)
                                } else {
                                    if (forceModeDelay > 0) showDisableDialog = true
                                    else {
                                        SettingsManager.setForceModeEnabled(context, false)
                                    }
                                }
                            }
                    )
                },
                onClick = {
                    if (forceModeEnabled) {
                        if (forceModeDelay > 0) showDisableDialog = true
                        else {
                            SettingsManager.setForceModeEnabled(context, false)
                        }
                    } else {
                        SettingsManager.setForceModeEnabled(context, true)
                    }
                }
        )

        // 强制模式设置
        DropdownMenuItem(
                text = {
                    Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Settings, null)
                        Text("强制模式设置")
                    }
                },
                onClick = {
                    onDismiss()
                    if (forceModeEnabled && forceModeDelay > 0) {
                        // 如果开启了强制模式且有延时，修改设置也需要冷静期
                        showModifyDelayDialog = true
                    } else {
                        onShowSettingsDialog()
                    }
                }
        )

        Divider()

        // 关于
        DropdownMenuItem(
                text = {
                    Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Info, null)
                        Text("关于")
                    }
                },
                onClick = {
                    onDismiss()
                    // TODO: 显示关于对话框
                }
        )
    }

    if (showDisableDialog) {
        DisableForceModeDialog(
                delaySeconds = forceModeDelay,
                onConfirm = {
                    SettingsManager.setForceModeEnabled(context, false)
                    showDisableDialog = false
                },
                onDismiss = { showDisableDialog = false }
        )
    }

    if (showModifyDelayDialog) {
        DisableForceModeDialog(
                delaySeconds = forceModeDelay,
                onConfirm = {
                    showModifyDelayDialog = false
                    onShowSettingsDialog()
                },
                onDismiss = { showModifyDelayDialog = false }
        )
    }
}

/** 强制模式设置对话框 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForceModeSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val forceModeEnabled by SettingsManager.forceModeEnabled.collectAsState()
    val unlockLimit by SettingsManager.unlockLimit.collectAsState()
    val unlockUsedToday by SettingsManager.unlockUsedToday.collectAsState()
    val currentDelay by SettingsManager.forceModeDelay.collectAsState()
    val currentUnlockDelay by SettingsManager.forceUnlockDelay.collectAsState()

    var tempUnlockLimit by remember { mutableStateOf(unlockLimit) }
    var tempDelay by remember { mutableStateOf(currentDelay) }
    var tempUnlockDelay by remember { mutableStateOf(currentUnlockDelay) }
    AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary)
                    Text("强制模式设置", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // 说明文字
                    Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                                "开启强制模式后，解锁/恢复应用时需要填写原因，" + "并且每天的解锁次数有限制，帮助您更好地控制自己。",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }

                    // 强制模式开关
                    Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("启用强制模式", fontWeight = FontWeight.Medium)
                            Text(
                                    if (forceModeEnabled) "当前已开启" else "当前已关闭",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Switch(
                                checked = forceModeEnabled,
                                onCheckedChange = { isChecked ->
                                    if (!isChecked) {
                                        SettingsManager.setForceModeEnabled(context, false)
                                    } else {
                                        SettingsManager.setForceModeEnabled(context, true)
                                    }
                                }
                        )
                    }

                    Divider()

                    // 取消强制模式延时设置
                    Column {
                        Text("取消强制模式延时", fontWeight = FontWeight.Medium)
                        val delayText =
                                when (tempDelay) {
                                    0L -> "无延时"
                                    else -> "${tempDelay} 秒"
                                }
                        Text(
                                "当前设置：$delayText",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            val delays = listOf(0L to "无", 10L to "10秒", 30L to "30秒", 60L to "60秒")
                            delays.forEach { (seconds, label) ->
                                FilterChip(
                                        selected = tempDelay == seconds,
                                        onClick = { tempDelay = seconds },
                                        label = { Text(label) },
                                        modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Divider()

                    // 每日解锁次数限制
                    Column {
                        Text("每日解锁次数限制", fontWeight = FontWeight.Medium)
                        Text(
                                "今日已使用 $unlockUsedToday / $unlockLimit 次",
                                style = MaterialTheme.typography.labelSmall,
                                color =
                                        if (unlockUsedToday >= unlockLimit)
                                                MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.outline
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(1, 3, 5, 10).forEach { limit ->
                                FilterChip(
                                        selected = tempUnlockLimit == limit,
                                        onClick = { tempUnlockLimit = limit },
                                        label = { Text("$limit 次") },
                                        modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // 警告提示
                    if (forceModeEnabled) {
                        Surface(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                        Icons.Default.Warning,
                                        null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                )
                                Text(
                                        "强制模式开启后，关闭自动化任务或恢复应用将变得困难！",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                        onClick = {
                            SettingsManager.setUnlockLimit(context, tempUnlockLimit)
                            SettingsManager.setUnlockLimit(context, tempUnlockLimit)
                            SettingsManager.setForceModeDelay(context, tempDelay)
                            SettingsManager.setForceUnlockDelay(context, tempUnlockDelay)
                            onDismiss()
                        }
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** 强制模式解锁验证对话框 用户需要填写解锁原因才能继续操作 */
@Composable
fun ForceUnlockDialog(
        actionDescription: String, // 例如 "关闭自动计划" 或 "恢复应用"
        onConfirm: () -> Unit,
        onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var reason by remember { mutableStateOf("") }
    val remainingUnlocks = SettingsManager.getRemainingUnlocks()
    val hasRemainingUnlocks = remainingUnlocks > 0
    val forceUnlockDelay by SettingsManager.forceUnlockDelay.collectAsState()

    var isCountingDown by remember { mutableStateOf(false) }
    var timeLeft by remember { mutableStateOf(forceUnlockDelay) }
    val quote by remember { mutableStateOf(MotivationalQuotes.getRandomQuote()) }

    LaunchedEffect(isCountingDown) {
        if (isCountingDown) {
            while (timeLeft > 0) {
                kotlinx.coroutines.delay(1000L)
                timeLeft--
            }
        }
    }

    AlertDialog(
            onDismissRequest = onDismiss,
            icon = {
                Icon(
                        Icons.Default.Lock,
                        null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                        "强制模式验证",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("您正在尝试「$actionDescription」", style = MaterialTheme.typography.bodyMedium)

                    // 剩余次数提示
                    Surface(
                            color =
                                    if (hasRemainingUnlocks)
                                            MaterialTheme.colorScheme.primaryContainer.copy(
                                                    alpha = 0.3f
                                            )
                                    else
                                            MaterialTheme.colorScheme.errorContainer.copy(
                                                    alpha = 0.5f
                                            ),
                            shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                    if (hasRemainingUnlocks) Icons.Default.Key
                                    else Icons.Default.Block,
                                    null,
                                    modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                    if (hasRemainingUnlocks) "今日剩余解锁次数：$remainingUnlocks"
                                    else "今日解锁次数已用完！",
                                    fontWeight = FontWeight.Bold,
                                    color =
                                            if (hasRemainingUnlocks)
                                                    MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    if (hasRemainingUnlocks) {
                        if (!isCountingDown) {
                            // 输入原因
                            Text(
                                    "请填写您要执行此操作的原因：",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.outline
                            )

                            OutlinedTextField(
                                    value = reason,
                                    onValueChange = { reason = it },
                                    placeholder = { Text("例如：需要查看紧急工作消息...") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    minLines = 3,
                                    maxLines = 5
                            )

                            Text(
                                    "请认真思考是否真的需要解锁，每一次解锁都会消耗您的意志力！",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            // 倒计时界面
                            Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                        "冷静期",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                // 显示随机励志语录
                                Surface(
                                        color =
                                                MaterialTheme.colorScheme.surfaceVariant.copy(
                                                        alpha = 0.5f
                                                ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                            text = quote,
                                            style =
                                                    MaterialTheme.typography.bodyMedium.copy(
                                                            fontStyle =
                                                                    androidx.compose.ui.text.font
                                                                            .FontStyle.Italic
                                                    ),
                                            textAlign =
                                                    androidx.compose.ui.text.style.TextAlign.Center,
                                            modifier = Modifier.padding(12.dp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    } else {
                        Text(
                                "您今天的解锁次数已经用完了。\n\n" + "请坚持到明天，或者在设置中调整解锁次数限制。\n\n" + "加油，您可以的！💪",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            },
            confirmButton = {
                if (hasRemainingUnlocks && !isCountingDown) {
                    Button(
                            onClick = {
                                if (reason.length >= 5) {
                                    if (forceUnlockDelay > 0) {
                                        isCountingDown = true
                                    } else {
                                        if (SettingsManager.tryUseUnlock(context)) {
                                            onConfirm()
                                        }
                                    }
                                }
                            },
                            enabled = reason.length >= 5, // 至少5个字符
                            colors =
                                    ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error
                                    )
                    ) { Text("我确定要解锁 (${reason.length}/5)") }
                } else if (hasRemainingUnlocks && isCountingDown) {
                    Button(
                            onClick = {
                                if (SettingsManager.tryUseUnlock(context)) {
                                    onConfirm()
                                }
                            },
                            enabled = timeLeft <= 0,
                            colors =
                                    ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error
                                    ),
                            modifier = Modifier.fillMaxWidth()
                    ) { Text(if (timeLeft > 0) "确认 ($timeLeft)" else "确认解锁") }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(if (hasRemainingUnlocks) "我再想想" else "我知道了")
                }
            }
    )
}

/** 计划锁定提示对话框 */
@Composable
fun PlanLockedDialog(planName: String, onDismiss: () -> Unit, onGoToPlans: () -> Unit) {
    AlertDialog(
            onDismissRequest = onDismiss,
            icon = {
                Icon(
                        Icons.Default.DateRange,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                        "应用已锁定",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("此应用当前正被自动计划「$planName」管理中。", style = MaterialTheme.typography.bodyMedium)

                    Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                    Icons.Default.Info,
                                    null,
                                    modifier = Modifier.size(20.dp).padding(top = 2.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                    "为了帮助您保持专注，该应用暂时无法直接恢复。\n\n如需取消限制，请前往【自动计划】页面修改或删除相关计划。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = onGoToPlans) { Text("前往管理计划") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("知道了") } }
    )
}

/** 取消强制模式延时确认对话框 */
@Composable
fun DisableForceModeDialog(delaySeconds: Long, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var timeLeft by remember { mutableStateOf(delaySeconds) }
    val quote by remember { mutableStateOf(MotivationalQuotes.getRandomQuote()) }

    LaunchedEffect(Unit) {
        while (timeLeft > 0) {
            kotlinx.coroutines.delay(1000L)
            timeLeft--
        }
    }

    AlertDialog(
            onDismissRequest = {}, // 禁止点击外部取消
            icon = {
                Icon(
                        Icons.Default.Timer,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                        "稍等一下",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                            "您设置了取消强制模式的冷静期。",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 显示随机励志语录
                    Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                                text = quote,
                                style =
                                        MaterialTheme.typography.bodyMedium.copy(
                                                fontStyle =
                                                        androidx.compose.ui.text.font.FontStyle
                                                                .Italic
                                        ),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                            "请确认您现在是否真的需要关闭强制模式？",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline
                    )
                }
            },
            confirmButton = {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("我再想想")
                    }

                    Button(
                            onClick = onConfirm,
                            enabled = timeLeft <= 0,
                            modifier = Modifier.weight(1f),
                            colors =
                                    ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error
                                    )
                    ) { Text(if (timeLeft > 0) "确认 ($timeLeft)" else "确认取消", maxLines = 1) }
                }
            }
    )
}
