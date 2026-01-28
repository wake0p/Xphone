package com.safe.discipline.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.safe.discipline.data.model.AppInfo
import kotlinx.coroutines.launch

@Composable
fun AppItemRow(
        app: AppInfo,
        selected: Boolean = false,
        groupLabel: String? = null, // 新增：显示所属分类名称
        onSelect: ((Boolean) -> Unit)? = null,
        onClick: () -> Unit
) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    // 状态切换时的柔和颜色过渡
    val targetColor =
            if (app.isEnabled) {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
            }
    val animatedBgColor by
            animateColorAsState(
                    targetValue = targetColor,
                    animationSpec = tween(500),
                    label = "GlassColor"
            )

    Surface(
            onClick = {
                scope.launch {
                    scale.animateTo(0.96f, animationSpec = spring())
                    scale.animateTo(1f, animationSpec = spring())
                }
                if (onSelect != null) onSelect(!selected) else onClick()
            },
            color = animatedBgColor,
            border =
                    BorderStroke(
                            width = 0.5.dp,
                            color =
                                    if (app.isEnabled) {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                    } else {
                                        MaterialTheme.colorScheme.error.copy(alpha = 0.25f)
                                    }
                    ),
            shape = RoundedCornerShape(16.dp),
            modifier =
                    Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
                            .animateContentSize()
                            .graphicsLayer {
                                scaleX = scale.value
                                scaleY = scale.value
                                clip = true
                            }
    ) {
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .background(
                                        Brush.radialGradient(
                                                colors =
                                                        listOf(
                                                                Color.White.copy(alpha = 0.08f),
                                                                Color.Transparent
                                                        ),
                                                center =
                                                        androidx.compose.ui.geometry.Offset(0f, 0f),
                                                radius = 2000f
                                        )
                                )
                                .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            // 极致性能点：优先使用预处理好的 iconBitmap
            if (app.iconBitmap != null) {
                Image(
                        bitmap = app.iconBitmap,
                        contentDescription = null,
                        modifier =
                                Modifier.size(42.dp).graphicsLayer {
                                    alpha = if (!app.isEnabled) 0.6f else 1.0f
                                }
                )
            } else {
                // 回退方案（基本不会走到这里）
                Box(modifier = Modifier.size(42.dp).background(Color.Gray.copy(alpha = 0.1f)))
            }

            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurface
                )

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // 显示分类标签
                    if (groupLabel != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                    Icons.Filled.Category,
                                    null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                    text = "属于「$groupLabel」",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // 显示计划锁定状态
                    if (app.blockedBy != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                    Icons.Filled.LockClock,
                                    null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                    text = "🔒 由「${app.blockedBy}」锁定",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else if (!app.isEnabled) {
                        Text(
                                text = "手动冻结",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            if (onSelect != null) {
                Checkbox(checked = selected, onCheckedChange = onSelect)
            }
        }
    }
}
