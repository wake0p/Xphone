package com.safe.discipline.data.model

import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap

data class AppInfo(
    val appName: String,
    val packageName: String,
    val icon: Drawable,
    val iconBitmap: ImageBitmap? = null, // 预处理后的位图，用于极致滑动性能
    val isEnabled: Boolean = true,
    val blockedBy: String? = null,      // 如果被计划屏蔽，显示计划名称
    val blockSchedule: String? = null   // 显示屏蔽时间范围，例如 "22:00 - 07:00"
)
