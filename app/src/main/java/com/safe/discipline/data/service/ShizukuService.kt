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

    /**
     * 检查 Shizuku 服务是否就绪。
     * 内置模式下，如果返回 false，说明需要通过 ADB 激活。
     */
    fun isReady(): Boolean {
        return try {
            if (Shizuku.pingBinder()) {
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

    /**
     * 执行 Shell 命令
     */
    fun runShell(cmd: String): Pair<Int, String> {
        if (!isReady()) return -1 to "Shizuku service not ready"

        return try {
            // 反射调用 newProcess
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcessMethod.isAccessible = true

            val p = newProcessMethod.invoke(null, arrayOf("sh", "-c", cmd), null, null) as java.lang.Process

            val output = BufferedReader(InputStreamReader(p.inputStream)).use { it.readText() }
            val exitCode = p.waitFor()
            exitCode to output.trim()
        } catch (e: Throwable) {
            Log.e(TAG, "Exec error: ${e.message}")
            -1 to "Error: ${e.message}"
        }
    }

    private fun getUserIds(): List<Int> {
        val (code, output) = runShell("pm list users")
        if (code != 0) return listOf(0)
        val regex = Regex("\\{([0-9]+):")
        return regex.findAll(output).mapNotNull { it.groupValues[1].toIntOrNull() }.toList().ifEmpty { listOf(0) }
    }

    fun setAppEnabled(context: Context, packageName: String, enable: Boolean): String {
        val criticalPackages = setOf(context.packageName, "com.android.settings")
        if (!enable && criticalPackages.contains(packageName)) return "不能隐藏核心应用"

        val userIds = getUserIds()
        var successCount = 0
        for (userId in userIds) {
            val action = if (enable) "enable" else "disable-user"
            val (code, _) = runShell("pm $action --user $userId $packageName")
            if (code == 0) successCount++
        }
        clearCache()
        return if (successCount > 0) "操作成功" else "操作失败"
    }

    private var cachedApps: List<AppInfo>? = null

    fun getInstalledApps(context: Context, hasPermission: Boolean): List<AppInfo> {
        if (cachedApps != null) return cachedApps!!
        if (!hasPermission) return emptyList()

        try {
            val apps = mutableListOf<AppInfo>()
            val pm = context.packageManager
            val (code, output) = runShell("pm list packages -3")
            if (code == 0) {
                output.split("\n")
                    .map { it.trim().removePrefix("package:").trim() }
                    .filter { it.isNotEmpty() }
                    .forEach { pkg ->
                        try {
                            val info = pm.getApplicationInfo(pkg, PackageManager.MATCH_DISABLED_COMPONENTS)
                            apps.add(AppInfo(
                                appName = info.loadLabel(pm).toString(),
                                packageName = pkg,
                                icon = info.loadIcon(pm),
                                isEnabled = info.enabled // 使用默认的 iconBitmap=null
                            ))
                        } catch (e: Exception) {}
                    }
                cachedApps = apps.sortedBy { it.appName }
                return cachedApps!!
            }
        } catch (e: Exception) {}
        return emptyList()
    }

    fun clearCache() { cachedApps = null }
}
