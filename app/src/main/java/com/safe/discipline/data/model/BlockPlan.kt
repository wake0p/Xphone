package com.safe.discipline.data.model

import java.util.*

data class BlockPlan(
        val id: String = UUID.randomUUID().toString(),
        val label: String,
        val packages: List<String>,
        val groupIds: List<String> = emptyList(), // 引用的分组子集
        val daysOfWeek: List<Int>, // 1 (Sunday) to 7 (Saturday)
        val startTime: String, // "HH:mm"
        val endTime: String, // "HH:mm"
        val isEnabled: Boolean = true,
        val isForceMode: Boolean = false,
        val unlockLimit: Int = 3,
        val usedUnlocks: Int = 0
) {
    // 检查当前时刻是否在计划范围内
    fun isActiveNow(): Boolean {
        val now = Calendar.getInstance()
        val currentDay = now.get(Calendar.DAY_OF_WEEK)

        if (!daysOfWeek.contains(currentDay)) return false

        val currentTimeStr =
                String.format("%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
        return if (startTime <= endTime) {
            currentTimeStr >= startTime && currentTimeStr <= endTime
        } else {
            // 跨天计划 (比如 22:00 到 07:00)
            currentTimeStr >= startTime || currentTimeStr <= endTime
        }
    }
}
