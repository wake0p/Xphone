package com.safe.discipline.data.model

import java.text.SimpleDateFormat
import java.util.*

/**
 * 调度类型：
 * - WEEKLY: 按星期循环（原有逻辑）
 * - SPECIFIC_DATES: 指定具体日期
 * - DAILY: 每天执行
 */
enum class ScheduleType {
    WEEKLY,
    SPECIFIC_DATES,
    DAILY
}

/**
 * 屏蔽模式：
 * - HIDE_DURING: 在指定时间段内隐藏应用（原有逻辑）
 * - SHOW_DURING: 在指定时间段内显示应用，其余时间隐藏
 */
enum class BlockMode {
    HIDE_DURING,
    SHOW_DURING
}

data class BlockPlan(
        val id: String = UUID.randomUUID().toString(),
        val label: String,
        val packages: List<String>,
        val groupIds: List<String> = emptyList(),
        val daysOfWeek: List<Int> = listOf(1,2,3,4,5,6,7), // 1=Sunday..7=Saturday
        val startTime: String, // "HH:mm"
        val endTime: String, // "HH:mm"
        val isEnabled: Boolean = true,
        val isForceMode: Boolean = false,
        val unlockLimit: Int = 3,
        val usedUnlocks: Int = 0,
        val scheduleType: ScheduleType = ScheduleType.WEEKLY,
        val specificDates: List<String> = emptyList(), // 格式 "yyyy-MM-dd"
        val blockMode: BlockMode = BlockMode.HIDE_DURING
) {
    /**
     * 检查日期+时间是否在计划范围内
     */
    fun isActiveNow(): Boolean {
        val now = Calendar.getInstance()

        // 1. 检查日期是否匹配
        val dateMatch = when (scheduleType) {
            ScheduleType.DAILY -> true
            ScheduleType.WEEKLY -> {
                val currentDay = now.get(Calendar.DAY_OF_WEEK)
                daysOfWeek.contains(currentDay)
            }
            ScheduleType.SPECIFIC_DATES -> {
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now.time)
                specificDates.contains(todayStr)
            }
        }

        if (!dateMatch) return false

        // 2. 检查时间是否匹配
        val currentTimeStr =
                String.format("%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
        return if (startTime <= endTime) {
            currentTimeStr >= startTime && currentTimeStr <= endTime
        } else {
            // 跨天计划 (比如 22:00 到 07:00)
            currentTimeStr >= startTime || currentTimeStr <= endTime
        }
    }

    /**
     * 核心判断：当前时刻是否应该屏蔽（隐藏）该计划中的应用
     *
     * HIDE_DURING: 计划激活时 → 隐藏
     * SHOW_DURING: 计划激活时 → 显示（即不隐藏），计划不激活时 → 隐藏
     */
    fun shouldBlockNow(): Boolean {
        return when (blockMode) {
            BlockMode.HIDE_DURING -> isActiveNow()
            BlockMode.SHOW_DURING -> !isActiveNow()
        }
    }

    /** 获取调度描述文本 */
    fun getScheduleDescription(): String {
        val modePrefix = if (blockMode == BlockMode.SHOW_DURING) "仅 " else ""
        val timePart = "$startTime~$endTime"
        val datePart = when (scheduleType) {
            ScheduleType.DAILY -> "每天"
            ScheduleType.WEEKLY -> {
                val dayNames = listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
                if (daysOfWeek.size == 7) "每天"
                else daysOfWeek.sorted().joinToString("、") { dayNames[it - 1] }
            }
            ScheduleType.SPECIFIC_DATES -> {
                if (specificDates.size <= 2) specificDates.joinToString("、")
                else "${specificDates.first()} 等${specificDates.size}天"
            }
        }
        val modeSuffix = when (blockMode) {
            BlockMode.HIDE_DURING -> " 隐藏"
            BlockMode.SHOW_DURING -> " 可用"
        }
        return "$modePrefix$datePart $timePart$modeSuffix"
    }
}

