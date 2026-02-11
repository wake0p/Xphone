package com.safe.discipline.data.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.safe.discipline.data.model.BlockMode
import com.safe.discipline.data.model.BlockPlan
import com.safe.discipline.data.model.ScheduleType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

object PlanManager {
    private const val PREFS_NAME = "discipline_plans"
    private const val KEY_PLANS = "all_plans"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun updateServiceState(context: Context) {
        // 在后台线程执行，避免阻塞 UI
        scope.launch {
            android.util.Log.d("OutPhone", "========== updateServiceState 开始 ==========")
            try {
                val appContext = context.applicationContext
                android.util.Log.d("OutPhone", "Step 1: 获取 applicationContext 成功")

                val plans = getAllPlans(appContext)
                android.util.Log.d("OutPhone", "Step 2: 加载计划列表，共 ${plans.size} 个计划")

                val anyActive = plans.any { it.isEnabled }
                android.util.Log.d("OutPhone", "Step 3: 检查激活状态，anyActive = $anyActive")

                if (anyActive) {
                    android.util.Log.d("OutPhone", "Step 4: 准备启动服务...")
                    DisciplineService.start(appContext)
                    android.util.Log.d("OutPhone", "Step 5: 服务启动完成")
                } else {
                    android.util.Log.d("OutPhone", "Step 4: 准备停止服务...")
                    DisciplineService.stop(appContext)
                    android.util.Log.d("OutPhone", "Step 5: 服务停止完成")
                }

                android.util.Log.d("OutPhone", "========== updateServiceState 完成 ==========")
            } catch (e: Throwable) {
                android.util.Log.e("OutPhone", "========== updateServiceState 错误 ==========")
                android.util.Log.e("OutPhone", "错误类型: ${e.javaClass.name}")
                android.util.Log.e("OutPhone", "错误信息: ${e.message}")
                android.util.Log.e("OutPhone", "堆栈跟踪:", e)
                android.util.Log.e("OutPhone", "==========================================")
                // 不再抛出异常，静默处理以避免崩溃
            }
        }
    }

    fun savePlan(context: Context, plan: BlockPlan) {
        val plans = getAllPlans(context).toMutableList()
        val index = plans.indexOfFirst { it.id == plan.id }
        if (index != -1) plans[index] = plan else plans.add(plan)
        saveAllPlans(context, plans)
    }

    fun getAllPlans(context: Context): List<BlockPlan> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_PLANS, "[]") ?: "[]"
        return parsePlans(json)
    }

    fun deletePlan(context: Context, planId: String) {
        val filtered = getAllPlans(context).filter { it.id != planId }
        saveAllPlans(context, filtered)
    }

    private fun saveAllPlans(context: Context, plans: List<BlockPlan>) {
        val array = JSONArray()
        plans.forEach { plan ->
            val obj =
                    JSONObject().apply {
                        put("id", plan.id)
                        put("label", plan.label)
                        put("packages", JSONArray(plan.packages))
                        put("groupIds", JSONArray(plan.groupIds))
                        put("daysOfWeek", JSONArray(plan.daysOfWeek))
                        put("startTime", plan.startTime)
                        put("endTime", plan.endTime)
                        put("isEnabled", plan.isEnabled)
                        put("isForceMode", plan.isForceMode)
                        put("unlockLimit", plan.unlockLimit)
                        put("usedUnlocks", plan.usedUnlocks)
                        put("scheduleType", plan.scheduleType.name)
                        put("specificDates", JSONArray(plan.specificDates))
                        put("blockMode", plan.blockMode.name)
                    }
            array.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PLANS, array.toString())
                .apply()

        // 1. 任何计划更新后，重新调度下一次检查
        scheduleNextCheck(context)
        // 2. 直接在后台线程执行一次计划检查，确保立即生效
        executePlanCheckNow(context)
        // 3. 同时发送广播作为备份触发
        val intent = Intent(context, PlanReceiver::class.java)
        context.sendBroadcast(intent)
        // 4. 更新守护服务状态
        updateServiceState(context)
    }

    /**
     * 立即在后台执行一次计划检查与应用状态同步
     * 不依赖广播，直接执行核心逻辑
     */
    fun executePlanCheckNow(context: Context) {
        scope.launch {
            try {
                android.util.Log.d("OutPhone", ">>> executePlanCheckNow 开始直接执行计划检查 <<<")

                val plans = getAllPlans(context)
                val groups = getAllGroups(context).associateBy { it.id }

                if (!ShizukuService.isReady()) {
                    android.util.Log.e("OutPhone", "直接检查失败: Shizuku 未就绪")
                    return@launch
                }

                val coreSafePackages = setOf(context.packageName, "moe.shizuku.manager")

                fun com.safe.discipline.data.model.BlockPlan.resolveAllPackages(): Set<String> {
                    val result = packages.toMutableSet()
                    groupIds.forEach { gId -> groups[gId]?.packages?.let { result.addAll(it) } }
                    result.removeAll(coreSafePackages)
                    return result
                }

                // 计算应该隐藏的包
                val currentlyShouldBeHidden =
                        plans
                                .filter { plan ->
                                    plan.isEnabled && (plan.shouldBlockNow() || plan.isForceMode)
                                }
                                .flatMap { it.resolveAllPackages() }
                                .toSet()

                // 详细日志
                plans.forEach { plan ->
                    val pkgs = plan.resolveAllPackages()
                    android.util.Log.d(
                            "OutPhone",
                            "直接检查·计划[${plan.label}]: 启用=${plan.isEnabled}, " +
                            "模式=${plan.blockMode}, " +
                            "isActiveNow=${plan.isActiveNow()}, " +
                            "shouldBlock=${plan.shouldBlockNow()}, " +
                            "强制=${plan.isForceMode}, " +
                            "包=[${pkgs.joinToString(",")}]"
                    )
                }

                android.util.Log.d("OutPhone", "直接检查·目标隐藏 ${currentlyShouldBeHidden.size} 个包: ${currentlyShouldBeHidden.joinToString(",")}")

                val allManagedPackages = plans.flatMap { it.resolveAllPackages() }.toSet()
                val prefs = context.getSharedPreferences("discipline_state", Context.MODE_PRIVATE)
                val lastManagedSet = prefs.getStringSet("last_managed_packages", emptySet()) ?: emptySet()
                val scanScope = allManagedPackages + lastManagedSet

                val pm = context.packageManager
                var changeCount = 0
                for (pkg in scanScope) {
                    val targetEnable = !currentlyShouldBeHidden.contains(pkg)
                    val currentStatus =
                            try {
                                pm.getApplicationInfo(
                                        pkg,
                                        android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS or
                                                android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES
                                ).enabled
                            } catch (e: Exception) {
                                true
                            }

                    if (currentStatus != targetEnable) {
                        android.util.Log.d("OutPhone", "直接检查·校准: $pkg 当前=${if(currentStatus)"启用" else "禁用"} -> 目标=${if(targetEnable)"启用" else "禁用"}")
                        ShizukuService.setAppEnabled(context, pkg, targetEnable)
                        changeCount++
                    }
                }

                prefs.edit().putStringSet("last_managed_packages", allManagedPackages).apply()
                android.util.Log.d("OutPhone", ">>> executePlanCheckNow 完成, 校准了 $changeCount 个应用 <<<")

            } catch (e: Throwable) {
                android.util.Log.e("OutPhone", "executePlanCheckNow 异常", e)
            }
        }
    }

    private fun parsePlans(json: String): List<BlockPlan> {
        val list = mutableListOf<BlockPlan>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val pkgs = mutableListOf<String>()
                val pkgsArr = obj.optJSONArray("packages")
                if (pkgsArr != null) {
                    for (j in 0 until pkgsArr.length()) pkgs.add(pkgsArr.getString(j))
                }

                val gIds = mutableListOf<String>()
                val gIdsArr = obj.optJSONArray("groupIds")
                if (gIdsArr != null) {
                    for (j in 0 until gIdsArr.length()) gIds.add(gIdsArr.getString(j))
                }

                val days = mutableListOf<Int>()
                val daysArr = obj.optJSONArray("daysOfWeek")
                if (daysArr != null) {
                    for (j in 0 until daysArr.length()) days.add(daysArr.getInt(j))
                } else {
                    days.addAll(listOf(1,2,3,4,5,6,7))
                }

                // 解析调度类型
                val scheduleTypeStr = obj.optString("scheduleType", "WEEKLY")
                val scheduleType = try {
                    ScheduleType.valueOf(scheduleTypeStr)
                } catch (e: Exception) {
                    ScheduleType.WEEKLY
                }

                // 解析指定日期
                val specificDates = mutableListOf<String>()
                val datesArr = obj.optJSONArray("specificDates")
                if (datesArr != null) {
                    for (j in 0 until datesArr.length()) specificDates.add(datesArr.getString(j))
                }

                // 解析屏蔽模式
                val blockModeStr = obj.optString("blockMode", "HIDE_DURING")
                val blockMode = try {
                    BlockMode.valueOf(blockModeStr)
                } catch (e: Exception) {
                    BlockMode.HIDE_DURING
                }

                list.add(
                        BlockPlan(
                                id = obj.getString("id"),
                                label = obj.getString("label"),
                                packages = pkgs,
                                groupIds = gIds,
                                daysOfWeek = days,
                                startTime = obj.getString("startTime"),
                                endTime = obj.getString("endTime"),
                                isEnabled = obj.optBoolean("isEnabled", true),
                                isForceMode = obj.optBoolean("isForceMode", false),
                                unlockLimit = obj.optInt("unlockLimit", 3),
                                usedUnlocks = obj.optInt("usedUnlocks", 0),
                                scheduleType = scheduleType,
                                specificDates = specificDates,
                                blockMode = blockMode
                        )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    // --- 分组管理 ---
    private const val KEY_GROUPS = "all_groups"

    fun getAllGroups(context: Context): List<com.safe.discipline.data.model.AppGroup> {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_GROUPS, "[]") ?: "[]"
            val array = JSONArray(json)
            val list = mutableListOf<com.safe.discipline.data.model.AppGroup>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val pkgs = mutableSetOf<String>()
                val pkgsArr = obj.getJSONArray("packages")
                for (j in 0 until pkgsArr.length()) pkgs.add(pkgsArr.getString(j))

                list.add(
                        com.safe.discipline.data.model.AppGroup(
                                id = obj.getString("id"),
                                name = obj.getString("name"),
                                packages = pkgs,
                                color = obj.optInt("color", 0)
                        )
                )
            }
            return list
        } catch (e: Exception) {
            return emptyList()
        }
    }

    fun saveGroup(context: Context, group: com.safe.discipline.data.model.AppGroup) {
        val groups = getAllGroups(context).toMutableList()
        val index = groups.indexOfFirst { it.id == group.id }
        if (index != -1) groups[index] = group else groups.add(group)
        saveAllGroups(context, groups)
    }

    fun deleteGroup(context: Context, groupId: String) {
        val filtered = getAllGroups(context).filter { it.id != groupId }
        saveAllGroups(context, filtered)
    }

    private fun saveAllGroups(
            context: Context,
            groups: List<com.safe.discipline.data.model.AppGroup>
    ) {
        val array = JSONArray()
        groups.forEach { group ->
            val obj =
                    JSONObject().apply {
                        put("id", group.id)
                        put("name", group.name)
                        put("packages", JSONArray(group.packages.toList()))
                        put("color", group.color)
                    }
            array.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_GROUPS, array.toString())
                .apply()

        // 分组变动可能影响计划，触发一次扫描
        val intent = Intent(context, PlanReceiver::class.java)
        context.sendBroadcast(intent)
    }

    fun scheduleNextCheck(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, PlanReceiver::class.java)
        val pendingIntent =
                PendingIntent.getBroadcast(
                        context,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

        // 设置 1 分钟后的检查，提高计划生效的及时性
        val checkInterval = 1 * 60 * 1000L
        val triggerTime = System.currentTimeMillis() + checkInterval

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTime,
                            pendingIntent
                    )
                } else {
                    // 没有精确闹钟权限，降级为普通闹钟，避免崩溃
                    alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTime,
                            pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // 最后的兜底：普通闹钟
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }
}
