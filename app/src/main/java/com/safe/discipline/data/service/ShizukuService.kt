package com.safe.discipline.data.service

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.safe.discipline.data.model.AppInfo
import java.io.BufferedReader
import java.io.InputStreamReader
import rikka.shizuku.Shizuku

object ShizukuService {

    private const val TAG = "OutPhone"

    // 检查 Shizuku 服务是否可用且有权限
    fun isReady(): Boolean {
        return try {
            // 1. 检查 Binder 是否存活
            if (Shizuku.pingBinder()) {
                // 2. 检查是否有权限
                val granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                Log.d(TAG, "Shizuku Status: Alive, Granted=$granted")
                granted
            } else {
                Log.d(TAG, "Shizuku Status: Dead")
                false
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Shizuku check failed", e)
            false
        }
    }

    // 执行 Shell 命令
    fun runShell(cmd: String): Pair<Int, String> {
        if (!isReady()) return -1 to "Shizuku service not ready or permission denied"

        return try {
            Log.d(TAG, "Shizuku Exec: $cmd")

            // 内部版本或受限版本中 newProcess 是私有的，必须用反射
            // 参数: String[] cmd, String[] env, String workingDir
            val newProcessMethod =
                    Shizuku::class.java.getDeclaredMethod(
                            "newProcess",
                            Array<String>::class.java,
                            Array<String>::class.java,
                            String::class.java
                    )
            newProcessMethod.isAccessible = true

            val p =
                    newProcessMethod.invoke(null, arrayOf("sh", "-c", cmd), null, null) as
                            java.lang.Process

            val output = BufferedReader(InputStreamReader(p.inputStream)).use { it.readText() }
            val exitCode = p.waitFor()
            Log.d(TAG, "Shizuku Result [$exitCode]: $output")
            exitCode to output.trim()
        } catch (e: Throwable) {
            Log.e(TAG, "Shizuku exec error: ${e.message}", e)
            -1 to "Execution failed: ${e.message}"
        }
    }

    // 获取设备上所有用户ID（包括分身 999 等）
    private fun getUserIds(): List<Int> {
        val (code, output) = runShell("pm list users")
        if (code != 0) return listOf(0) // 默认至少有主用户 0

        // Output example: "UserInfo{0:Owner:13} running"
        // 匹配 "{数字:" 格式
        val regex = Regex("\\{([0-9]+):")
        val ids = regex.findAll(output).mapNotNull { it.groupValues[1].toIntOrNull() }.toList()

        return if (ids.isNotEmpty()) ids else listOf(0)
    }

    // 启用/禁用应用 (带安全白名单拦截)，支持分身
    fun setAppEnabled(context: Context, packageName: String, enable: Boolean): String {
        // --- 核心安全策略：绝对白名单 ---
        val criticalPackages =
                setOf(
                        context.packageName, // 禁止禁用自身 (防止自锁)
                        "moe.shizuku.manager", // 禁止禁用 Shizuku 驱动
                        "com.android.settings", // 禁止禁用设置
                        "com.google.android.packageinstaller" // 禁止禁用安装器
                )

        // 如果是尝试禁用关键包，拦截并返回
        if (!enable && criticalPackages.contains(packageName)) {
            Log.w(TAG, "安全拦截: 尝试禁用核心组件 $packageName 被拒绝")
            return "安全提醒：不能隐藏核心应用！"
        }

        // 尝试检测桌面应用，防止手机变砖
        if (!enable) {
            val launcherIntent =
                    android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                        addCategory(android.content.Intent.CATEGORY_HOME)
                    }
            val resolveInfo = context.packageManager.resolveActivity(launcherIntent, 0)
            if (resolveInfo?.activityInfo?.packageName == packageName) {
                Log.w(TAG, "安全拦截: 尝试禁用当前桌面 $packageName 被拒绝")
                return "安全提醒：不能隐藏您的屏幕桌面！"
            }
        }
        // --- 安全拦截结束 ---

        val userIds = getUserIds()
        Log.d(TAG, "Detected User IDs: $userIds")

        var successCount = 0
        var failOutput = ""

        for (userId in userIds) {
            // disable-user是标准的隐藏命令
            // enable通常不需要--user，但在多用户环境下指定更稳妥
            val action = if (enable) "enable" else "disable-user"
            val cmd = "pm $action --user $userId $packageName"

            val (code, output) = runShell(cmd)
            if (code == 0 && !output.contains("Error", true)) {
                successCount++
            } else {
                failOutput = output
                Log.w(TAG, "Failed for user $userId: $output")
            }
        }

        if (successCount > 0) {
            clearCache() // 状态变了，必须清缓存
            // 如果所有用户都成功，或者至少有一个成功（通常是主用户）
            return if (enable) "已恢复 $packageName (分身同步处理)" else "已隐藏 $packageName (分身同步处理)"
        } else {
            return "操作失败: $failOutput"
        }
    }

    // 内存中的应用列表缓存，实现“秒开”
    private var cachedApps: List<AppInfo>? = null

    // 获取已安装应用列表 (带缓存优化)
    fun getInstalledApps(context: Context, hasPermission: Boolean): List<AppInfo> {
        // 如果有缓存且不是强制刷新，先返回缓存，让 UI 瞬间显示
        val currentCache = cachedApps
        if (currentCache != null && currentCache.isNotEmpty()) {
            // 启动异步静默更新（这里简单处理，实际更新由 ViewModel 触发下一次 loadApps）
            Log.d(TAG, "返回缓存应用列表，size=${currentCache.size}")
            return currentCache
        }

        if (hasPermission) {
            try {
                val apps = mutableListOf<AppInfo>()
                val pm = context.packageManager
                val (code, output) = runShell("pm list packages -3")

                if (code == 0) {
                    val allPackages =
                            output.split("\n")
                                    .map { it.trim().removePrefix("package:").trim() }
                                    .filter { it.isNotEmpty() }

                    allPackages.forEach { pkgName ->
                        try {
                            val appInfoObj =
                                    pm.getApplicationInfo(
                                            pkgName,
                                            PackageManager.MATCH_DISABLED_COMPONENTS
                                    )
                            apps.add(
                                    AppInfo(
                                            appName = appInfoObj.loadLabel(pm).toString(),
                                            packageName = pkgName,
                                            icon = appInfoObj.loadIcon(pm),
                                            isEnabled = appInfoObj.enabled
                                    )
                            )
                        } catch (e: Exception) {
                            // 跳过不存在的包
                        }
                    }
                    val sortedApps = apps.sortedBy { it.appName }
                    cachedApps = sortedApps // 更新全局缓存
                    return sortedApps
                }
            } catch (e: Throwable) {
                Log.e(TAG, "GetApps Error", e)
            }
        }

        return emptyList()
    }

    // 强制清除缓存（当执行了启用/禁用操作后调用）
    fun clearCache() {
        cachedApps = null
    }
}
