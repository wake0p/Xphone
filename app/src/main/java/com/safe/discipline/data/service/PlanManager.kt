package com.safe.discipline.data.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.safe.discipline.data.model.BlockPlan
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
                    }
            array.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PLANS, array.toString())
                .apply()

        // 1. 任何计划更新后，重新调度下一次检查
        scheduleNextCheck(context)
        // 2. 立即触发一次检查，确保所见即所得
        val intent = Intent(context, PlanReceiver::class.java)
        context.sendBroadcast(intent)
        // 3. 更新守护服务状态
        updateServiceState(context)
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
                val daysArr = obj.getJSONArray("daysOfWeek")
                for (j in 0 until daysArr.length()) days.add(daysArr.getInt(j))

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
                                usedUnlocks = obj.optInt("usedUnlocks", 0)
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
