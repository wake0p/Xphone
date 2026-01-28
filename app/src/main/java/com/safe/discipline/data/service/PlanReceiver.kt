package com.safe.discipline.data.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.safe.discipline.data.model.BlockPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlanReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync() // 强制系统允许后台进程异步运行，不再被中途掐断
        Log.d("OutPhone", "定时任务触发: 处理计划恢复与同步...")

        scope.launch {
            try {
                // 1. 获取所有计划和分组
                val plans = PlanManager.getAllPlans(context)
                val groups = PlanManager.getAllGroups(context).associateBy { it.id }

                // 2. 检查 ADB 是否就绪
                if (!ShizukuService.isReady()) {
                    Log.e("OutPhone", "后台检查失败: Shizuku 未就绪。")
                    PlanManager.scheduleNextCheck(context)
                    return@launch
                }

                // 定义解析逻辑：合并单个包名和分组包名 (强制过滤核心安全包)
                val coreSafePackages = setOf(context.packageName, "moe.shizuku.manager")

                fun BlockPlan.resolveAllPackages(): Set<String> {
                    val result = packages.toMutableSet()
                    groupIds.forEach { gId -> groups[gId]?.packages?.let { result.addAll(it) } }
                    // 物理层面强制移除安全包
                    result.removeAll(coreSafePackages)
                    return result
                }

                // 3. 计算目标：当前应该隐藏的所有包
                val currentlyShouldBeHidden =
                        plans
                                .filter { it.isEnabled && it.isActiveNow() }
                                .flatMap { it.resolveAllPackages() }
                                .toSet()

                // 4. 获取所有曾经被管理过的包
                val allManagedPackages = plans.flatMap { it.resolveAllPackages() }.toSet()

                // --- 状态记忆机制 Start ---
                val prefs = context.getSharedPreferences("discipline_state", Context.MODE_PRIVATE)
                val lastManagedSet =
                        prefs.getStringSet("last_managed_packages", emptySet()) ?: emptySet()

                // 本次扫描的范围 = 当前计划中的包 + 上次记忆的包
                // 这样即使计划被删除了，上次记录的包还在扫描范围内，会被判定为 "不应隐藏" 从而被恢复
                val scanScope = allManagedPackages + lastManagedSet

                Log.d(
                        "OutPhone",
                        "后台同步: 扫描范围 ${scanScope.size} (当前 ${allManagedPackages.size} + 历史 ${lastManagedSet.size}), 目标隐藏 ${currentlyShouldBeHidden.size}"
                )

                var hasChanged = false
                val pm = context.packageManager
                for (pkg in scanScope) {
                    val targetEnable = !currentlyShouldBeHidden.contains(pkg)

                    // 物理状态对比：如果系统里的状态和计划不一样，强制校准
                    val currentStatus =
                            try {
                                pm.getApplicationInfo(
                                                pkg,
                                                android.content.pm.PackageManager
                                                        .MATCH_DISABLED_COMPONENTS or
                                                        android.content.pm.PackageManager
                                                                .MATCH_UNINSTALLED_PACKAGES
                                        )
                                        .enabled
                            } catch (e: Exception) {
                                true // 默认认为启用
                            }

                    if (currentStatus != targetEnable) {
                        ShizukuService.setAppEnabled(context, pkg, targetEnable)
                        Log.d("OutPhone", "后台校准: $pkg -> ${if (targetEnable) "启用" else "禁用"}")
                        hasChanged = true
                    }
                }

                // 更新状态记忆
                prefs.edit().putStringSet("last_managed_packages", allManagedPackages).apply()
                // --- 状态记忆机制 End ---

                // 5. 立即预约下一次心跳
                PlanManager.scheduleNextCheck(context)

                // 6. 如果有变动，发送 UI 刷新信号
                if (hasChanged) {
                    delay(500) // 等系统 PM 反应一下
                }
                context.sendBroadcast(Intent("com.safe.discipline.REFRESH_UI"))
            } catch (e: Throwable) {
                Log.e("OutPhone", "PlanReceiver 执行异常", e)
            } finally {
                pendingResult.finish() // 必须手动结束，否则进程会异常结束
            }
        }
    }
}
