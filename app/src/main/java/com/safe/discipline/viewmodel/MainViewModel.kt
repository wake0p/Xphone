package com.safe.discipline.viewmodel

import android.app.Application
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safe.discipline.data.model.AppInfo
import com.safe.discipline.data.service.ShizukuService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class MainViewModel(application: Application) : AndroidViewModel(application) {

    // UI 状态
    private val _statusText = MutableStateFlow("Shizuku 检查中...")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _targetPackage = MutableStateFlow("com.tencent.tmgp.sgame")
    val targetPackage: StateFlow<String> = _targetPackage.asStateFlow()

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    private val _autoPort = MutableStateFlow<Int?>(null)
    val autoPort = _autoPort.asStateFlow()

    // 计划任务列表
    private val _plans =
            MutableStateFlow<List<com.safe.discipline.data.model.BlockPlan>>(emptyList())
    val plans = _plans.asStateFlow()

    // 应用分组列表
    private val _groups =
            MutableStateFlow<List<com.safe.discipline.data.model.AppGroup>>(emptyList())
    val groups = _groups.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener { checkPermission() }

    private val binderDeadListener =
            Shizuku.OnBinderDeadListener {
                _hasPermission.value = false
                _statusText.value = "Shizuku 服务已停止"
            }

    private val requestPermissionResultListener =
            object : Shizuku.OnRequestPermissionResultListener {
                override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                    try {
                        val granted =
                                grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED
                        _hasPermission.value = granted
                        if (granted) {
                            _statusText.value = "Shizuku 授权成功"
                            // 延迟一点点执行，防止 Binder 还在回调栈中导致某些重入问题
                            loadApps()
                        } else {
                            _statusText.value = "Shizuku 授权被拒绝"
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("OutPhone", "Shizuku Callback Error", e)
                        _statusText.value = "授权回调异常: ${e.message}"
                    }
                }
            }

    init {
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)

            checkPermission()
            loadPlans()
            loadGroups()
        } catch (e: Exception) {
            android.util.Log.e("OutPhone", "ViewModel Init Error", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
        } catch (e: Exception) {
            // ignore
        }
    }

    fun checkPermission() {
        if (Shizuku.pingBinder()) {
            if (Shizuku.checkSelfPermission() ==
                            android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                _hasPermission.value = true
                _statusText.value = "Shizuku 已连接"
                loadApps()
            } else {
                _hasPermission.value = false
                _statusText.value = "等待 Shizuku 授权..."
                requestPermission()
            }
        } else {
            _hasPermission.value = false
            _statusText.value = "Shizuku 未运行，请先在 Shizuku App 中启动"
        }
    }

    fun requestPermission() {
        if (Shizuku.pingBinder()) {
            try {
                Shizuku.requestPermission(0)
            } catch (e: Exception) {
                _statusText.value = "授权请求失败: ${e.message}"
            }
        }
    }

    private fun drawableToBitmap(
            drawable: android.graphics.drawable.Drawable?
    ): androidx.compose.ui.graphics.ImageBitmap? {
        if (drawable == null) return null
        return try {
            val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 120
            val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 120

            // 限制最大尺寸以节省显存并防止异常尺寸导致的 Crash
            val finalWidth = width.coerceAtMost(160)
            val finalHeight = height.coerceAtMost(160)

            val bitmap =
                    android.graphics.Bitmap.createBitmap(
                            finalWidth,
                            finalHeight,
                            android.graphics.Bitmap.Config.ARGB_8888
                    )
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, finalWidth, finalHeight)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        } catch (e: Exception) {
            android.util.Log.e("OutPhone", "Icon convert error", e)
            null
        }
    }

    fun loadApps(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            try {
                // 如果不是强制刷新，且已有名单，就不再重复转换图标（最耗时的步骤）
                if (!forceRefresh && _installedApps.value.isNotEmpty()) {
                    android.util.Log.d(
                            "OutPhone",
                            "使用现有 ViewModel 缓存中的 ${installedApps.value.size} 个应用"
                    )
                    return@launch
                }

                if (forceRefresh) {
                    ShizukuService.clearCache()
                }

                val apps =
                        withContext(Dispatchers.IO) {
                            val context = getApplication<android.app.Application>()
                            val rawApps =
                                    ShizukuService.getInstalledApps(context, _hasPermission.value)

                            // 动态识别桌面 (Launcher)，禁止自杀
                            val launcherIntent =
                                    android.content.Intent(android.content.Intent.ACTION_MAIN)
                                            .apply {
                                                addCategory(android.content.Intent.CATEGORY_HOME)
                                            }
                            val launcherPkg =
                                    context.packageManager.resolveActivity(launcherIntent, 0)
                                            ?.activityInfo
                                            ?.packageName

                            val criticalPackages =
                                    setOf(
                                            context.packageName,
                                            "moe.shizuku.manager",
                                            "com.android.settings",
                                            "com.google.android.packageinstaller",
                                            launcherPkg
                                    )

                            // 获取最新的计划和分组信息
                            val activePlans =
                                    com.safe.discipline.data.service.PlanManager.getAllPlans(
                                                    context
                                            )
                                            .filter { it.isEnabled && it.isActiveNow() }
                            val allGroups =
                                    com.safe.discipline.data.service.PlanManager.getAllGroups(
                                            context
                                    )

                            rawApps
                                    .filter { app -> !criticalPackages.contains(app.packageName) }
                                    .map { app ->
                                        // 优化：如果已经有转换过的 bitmap 就复用，避免重复绘图
                                        val existing =
                                                _installedApps.value.find {
                                                    it.packageName == app.packageName
                                                }

                                        // 计算被哪个计划封锁
                                        val blockingPlan =
                                                activePlans.find { plan ->
                                                    plan.packages.contains(app.packageName) ||
                                                            (plan.groupIds.isNotEmpty() &&
                                                                    allGroups
                                                                            .filter {
                                                                                plan.groupIds
                                                                                        .contains(
                                                                                                it.id
                                                                                        )
                                                                            }
                                                                            .any {
                                                                                it.packages
                                                                                        .contains(
                                                                                                app.packageName
                                                                                        )
                                                                            })
                                                }

                                        val blockedBy = blockingPlan?.label
                                        val blockSchedule =
                                                if (blockingPlan != null)
                                                        "${blockingPlan.startTime} - ${blockingPlan.endTime}"
                                                else null

                                        if (existing?.iconBitmap != null && !forceRefresh) {
                                            app.copy(
                                                    iconBitmap = existing.iconBitmap,
                                                    blockedBy = blockedBy,
                                                    blockSchedule = blockSchedule
                                            )
                                        } else {
                                            app.copy(
                                                    iconBitmap = drawableToBitmap(app.icon),
                                                    blockedBy = blockedBy,
                                                    blockSchedule = blockSchedule
                                            )
                                        }
                                    }
                        }
                _installedApps.value = apps
                android.util.Log.d("OutPhone", "加载了 ${apps.size} 个应用 (已安全过滤核心应用)")
            } catch (e: Exception) {
                android.util.Log.e("OutPhone", "Load Apps Error", e)
            }
        }
    }

    fun loadPlans() {
        _plans.value = com.safe.discipline.data.service.PlanManager.getAllPlans(getApplication())
    }

    fun loadGroups() {
        _groups.value = com.safe.discipline.data.service.PlanManager.getAllGroups(getApplication())
    }

    fun savePlan(plan: com.safe.discipline.data.model.BlockPlan) {
        com.safe.discipline.data.service.PlanManager.savePlan(getApplication(), plan)
        loadPlans()
    }

    fun saveGroup(group: com.safe.discipline.data.model.AppGroup) {
        com.safe.discipline.data.service.PlanManager.saveGroup(getApplication(), group)
        loadGroups()
        loadPlans() // 分组改变可能影响计划显示
    }

    fun deleteGroup(groupId: String) {
        com.safe.discipline.data.service.PlanManager.deleteGroup(getApplication(), groupId)
        loadGroups()
        loadPlans()
    }

    fun deletePlan(planId: String) {
        com.safe.discipline.data.service.PlanManager.deletePlan(getApplication(), planId)
        loadPlans()
    }

    fun hideApp() {
        if (!_hasPermission.value) {
            requestPermission()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result =
                        ShizukuService.setAppEnabled(getApplication(), _targetPackage.value, false)
                _statusText.value = result
                loadApps()
            } catch (e: Exception) {
                android.util.Log.e("OutPhone", "Hide Error", e)
                _statusText.value = "执行失败: ${e.message}"
            }
        }
    }

    fun showApp() {
        if (!_hasPermission.value) {
            requestPermission()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result =
                        ShizukuService.setAppEnabled(getApplication(), _targetPackage.value, true)
                _statusText.value = result
                loadApps()
            } catch (e: Exception) {
                android.util.Log.e("OutPhone", "Show Error", e)
                _statusText.value = "执行失败: ${e.message}"
            }
        }
    }

    fun runSelfTest() {
        if (!_hasPermission.value) {
            _statusText.value = "Shizuku 未授权"
            requestPermission()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (code, output) = ShizukuService.runShell("id")
                withContext(Dispatchers.Main) {
                    if (code == 0) {
                        _statusText.value = "测试成功! 身份:\n$output"
                    } else {
                        _statusText.value = "测试失败 (Code $code):\n$output"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { _statusText.value = "执行异常: ${e.message}" }
            }
        }
    }

    fun handleNotificationClick(intent: android.content.Intent?) {
        // No-op for now
    }

    fun startPairingService() {
        // Handled by Shizuku app
    }

    fun startWirelessActivation(host: String, port: Int) {
        // Handled by Shizuku app
    }

    fun hideApps(packages: List<String>) {
        if (!hasPermission.value) {
            requestPermission()
            return
        }
        _statusText.value = "正在批量隐藏 ${packages.size} 个应用..."
        viewModelScope.launch(Dispatchers.IO) {
            packages.forEach { pkg -> ShizukuService.setAppEnabled(getApplication(), pkg, false) }
            withContext(Dispatchers.Main) {
                _statusText.value = "批量隐藏完成。"
                loadApps(forceRefresh = true)
            }
        }
    }

    fun showApps(packages: List<String>) {
        if (!hasPermission.value) {
            requestPermission()
            return
        }
        _statusText.value = "正在批量恢复 ${packages.size} 个应用..."
        viewModelScope.launch(Dispatchers.IO) {
            packages.forEach { pkg -> ShizukuService.setAppEnabled(getApplication(), pkg, true) }
            withContext(Dispatchers.Main) {
                _statusText.value = "批量恢复完成。"
                loadApps(forceRefresh = true)
            }
        }
    }
}
