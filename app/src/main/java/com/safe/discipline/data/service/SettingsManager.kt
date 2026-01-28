package com.safe.discipline.data.service

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 全局设置管理器 管理强制模式等应用级别的设置 */
object SettingsManager {
    private const val PREFS_NAME = "discipline_settings"
    private const val KEY_FORCE_MODE_ENABLED = "force_mode_enabled"
    private const val KEY_UNLOCK_LIMIT = "unlock_limit"
    private const val KEY_UNLOCK_USED_TODAY = "unlock_used_today"
    private const val KEY_LAST_UNLOCK_DATE = "last_unlock_date"
    private const val KEY_FORCE_MODE_DELAY = "force_mode_delay"
    private const val KEY_FORCE_UNLOCK_DELAY = "force_unlock_delay"
    private const val KEY_LAST_FORCE_MODE_DISABLE_DATE = "last_force_mode_disable_date"

    private val _forceModeEnabled = MutableStateFlow(false)
    val forceModeEnabled: StateFlow<Boolean> = _forceModeEnabled.asStateFlow()

    private val _unlockLimit = MutableStateFlow(3)
    val unlockLimit: StateFlow<Int> = _unlockLimit.asStateFlow()

    private val _unlockUsedToday = MutableStateFlow(0)
    val unlockUsedToday: StateFlow<Int> = _unlockUsedToday.asStateFlow()

    private val _forceModeDelay = MutableStateFlow(0L) // in seconds
    val forceModeDelay: StateFlow<Long> = _forceModeDelay.asStateFlow()

    private val _forceUnlockDelay = MutableStateFlow(0L) // in seconds
    val forceUnlockDelay: StateFlow<Long> = _forceUnlockDelay.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _forceModeEnabled.value = prefs.getBoolean(KEY_FORCE_MODE_ENABLED, false)
        _unlockLimit.value = prefs.getInt(KEY_UNLOCK_LIMIT, 3)
        _forceModeDelay.value = prefs.getLong(KEY_FORCE_MODE_DELAY, 0L)
        _forceUnlockDelay.value = prefs.getLong(KEY_FORCE_UNLOCK_DELAY, 0L)

        // 检查是否是新的一天，重置解锁次数
        val today =
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                        .format(java.util.Date())
        val lastDate = prefs.getString(KEY_LAST_UNLOCK_DATE, "") ?: ""

        if (today != lastDate) {
            // 新的一天，重置解锁次数
            _unlockUsedToday.value = 0
            prefs.edit()
                    .putInt(KEY_UNLOCK_USED_TODAY, 0)
                    .putString(KEY_LAST_UNLOCK_DATE, today)
                    .apply()
        } else {
            _unlockUsedToday.value = prefs.getInt(KEY_UNLOCK_USED_TODAY, 0)
        }
    }

    fun setForceModeEnabled(context: Context, enabled: Boolean) {
        _forceModeEnabled.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_FORCE_MODE_ENABLED, enabled)
                .apply()
    }

    fun setUnlockLimit(context: Context, limit: Int) {
        _unlockLimit.value = limit
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_UNLOCK_LIMIT, limit)
                .apply()
    }

    fun setForceModeDelay(context: Context, delaySeconds: Long) {
        _forceModeDelay.value = delaySeconds
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_FORCE_MODE_DELAY, delaySeconds)
                .apply()
    }

    fun setForceUnlockDelay(context: Context, delaySeconds: Long) {
        _forceUnlockDelay.value = delaySeconds
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_FORCE_UNLOCK_DELAY, delaySeconds)
                .apply()
    }

    /**
     * 尝试使用一次解锁机会
     * @return true 如果还有剩余次数，false 如果已用完
     */
    fun tryUseUnlock(context: Context): Boolean {
        if (_unlockUsedToday.value >= _unlockLimit.value) {
            return false
        }

        val newCount = _unlockUsedToday.value + 1
        _unlockUsedToday.value = newCount

        val today =
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                        .format(java.util.Date())

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_UNLOCK_USED_TODAY, newCount)
                .putString(KEY_LAST_UNLOCK_DATE, today)
                .apply()

        return true
    }

    /** 获取剩余解锁次数 */
    fun getRemainingUnlocks(): Int {
        return maxOf(0, _unlockLimit.value - _unlockUsedToday.value)
    }

    /** 检查今天是否还能关闭强制模式 (每天限制一次) */
    fun canDisableForceMode(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastDisableDate = prefs.getString(KEY_LAST_FORCE_MODE_DISABLE_DATE, "") ?: ""
        val today =
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                        .format(java.util.Date())
        return lastDisableDate != today
    }

    /** 记录关闭强制模式的时间 */
    fun recordForceModeDisabled(context: Context) {
        val today =
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                        .format(java.util.Date())
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_FORCE_MODE_DISABLE_DATE, today)
                .apply()
    }

    /** 检查是否需要强制模式验证 */
    fun requiresForceModeValidation(): Boolean {
        return _forceModeEnabled.value
    }
}
