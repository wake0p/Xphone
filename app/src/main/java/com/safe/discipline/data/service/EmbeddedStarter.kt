package com.safe.discipline.data.service

import android.content.Context
import java.io.File

object EmbeddedStarter {

    private const val SERVER_ASSET = "server.dex"
    private const val SERVER_MAIN_CLASS = "rikka.shizuku.server.ShizukuService"

    private fun resolveServerAssetName(context: Context): String? {
        val rootAssets = context.assets.list("")?.toList().orEmpty()
        if (rootAssets.contains(SERVER_ASSET)) return SERVER_ASSET
        val caseInsensitiveMatch =
                rootAssets.firstOrNull { it.equals(SERVER_ASSET, ignoreCase = true) }
        if (caseInsensitiveMatch != null) return caseInsensitiveMatch
        return rootAssets.firstOrNull { it.endsWith(".dex", ignoreCase = true) }
    }

    private fun ensureServerDex(context: Context): File? {
        val targetDir = File(context.filesDir, "embedded")
        if (!targetDir.exists()) targetDir.mkdirs()
        val target = File(targetDir, SERVER_ASSET)
        if (target.exists() && target.length() > 0L) return target

        val assetName = resolveServerAssetName(context) ?: return null

        return try {
            context.assets.open(assetName).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            if (target.length() > 0L) target else null
        } catch (_: Exception) {
            null
        }
    }

    fun debugAssetState(context: Context): String {
        val rootAssets = context.assets.list("")?.toList().orEmpty()
        val dexAssets = rootAssets.filter { it.endsWith(".dex", ignoreCase = true) }
        return if (dexAssets.isEmpty()) {
            "assets 下未找到 dex 文件，当前文件: ${rootAssets.joinToString(", ").ifBlank { "(空)" }}"
        } else {
            "检测到 dex 文件: ${dexAssets.joinToString(", ")}"
        }
    }

    fun buildActivationCommand(context: Context): String? {
        val dexFile = ensureServerDex(context) ?: return null
        val packageName = context.packageName
        val shellReadableDexPath = "/data/local/tmp/${packageName.replace('.', '_')}_server.dex"
        val shellCommand =
                "run-as $packageName cat ${dexFile.absolutePath} > $shellReadableDexPath && " +
                        "chmod 644 $shellReadableDexPath && " +
                        "CLASSPATH=$shellReadableDexPath /system/bin/app_process /system/bin " +
                        "$SERVER_MAIN_CLASS"
        return "adb shell \"$shellCommand\""
    }
}
