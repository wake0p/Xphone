package com.safe.discipline.data.service

import android.content.Context
import java.io.File

object EmbeddedStarter {

    private const val SERVER_ASSET = "server.dex"
    private const val SERVER_APK_ASSET = "server.apk"
    private const val SERVER_MAIN_CLASS = "rikka.shizuku.server.ShizukuService"

    private fun ensureServerApk(context: Context): File? {
        val rootAssets = context.assets.list("")?.toList().orEmpty()
        val apkAsset = rootAssets.firstOrNull { it.equals(SERVER_APK_ASSET, ignoreCase = true) } ?: return null
        val targetDir = File(context.filesDir, "embedded")
        if (!targetDir.exists()) targetDir.mkdirs()
        val target = File(targetDir, SERVER_APK_ASSET)
        return try {
            context.assets.open(apkAsset).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            if (target.length() > 0L) target else null
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveServerAssetNames(context: Context): List<String> {
        val rootAssets = context.assets.list("")?.toList().orEmpty()
        if (rootAssets.isEmpty()) return emptyList()

        val serverDexes =
                rootAssets.filter {
                    it.startsWith("server", ignoreCase = true) &&
                            it.endsWith(".dex", ignoreCase = true)
                }
        if (serverDexes.isNotEmpty()) {
            return serverDexes.sortedWith(
                    compareBy(
                            { asset ->
                                Regex("""\d+""").find(asset)?.value?.toIntOrNull() ?: 1
                            },
                            { it.lowercase() }
                    )
            )
        }

        val fallback = rootAssets.filter { it.endsWith(".dex", ignoreCase = true) }
        return fallback.sortedBy { it.lowercase() }
    }

    private fun ensureServerDexes(context: Context): List<File> {
        val targetDir = File(context.filesDir, "embedded")
        if (!targetDir.exists()) targetDir.mkdirs()
        val assetNames = resolveServerAssetNames(context)
        if (assetNames.isEmpty()) return emptyList()

        return try {
            val copied = mutableListOf<File>()
            assetNames.forEach { assetName ->
                val target = File(targetDir, assetName)
                // Always resync from assets to avoid stale dex after upgrades.
                context.assets.open(assetName).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                if (target.length() > 0L) copied += target
            }
            copied
        } catch (_: Exception) {
            emptyList()
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

    fun buildActivationShellCommand(context: Context): String? {
        val packageName = context.packageName
        ensureServerApk(context)?.let { apkFile ->
            val shellReadableApkPath = "/data/local/tmp/${packageName.replace('.', '_')}_server.apk"
            return "run-as $packageName cat ${apkFile.absolutePath} > $shellReadableApkPath && " +
                    "chmod 644 $shellReadableApkPath && " +
                    "CLASSPATH=$shellReadableApkPath /system/bin/app_process /system/bin $SERVER_MAIN_CLASS"
        }

        val dexFiles = ensureServerDexes(context)
        if (dexFiles.isEmpty()) return null
        val shellReadablePrefix = "/data/local/tmp/${packageName.replace('.', '_')}_server"
        val copyScript = buildString {
            append("for n in '' 2 3 4 5 6 7 8 9 10 11 12; do ")
            append("src=/data/user/0/$packageName/files/embedded/server${'$'}n.dex; ")
            append("dst=${shellReadablePrefix}${'$'}n.dex; ")
            append("run-as $packageName sh -c \"[ -f \\\"${'$'}src\\\" ]\" || continue; ")
            append("run-as $packageName cat \\\"${'$'}src\\\" > \\\"${'$'}dst\\\"; ")
            append("done")
        }
        val classpathCmd =
                "CLASSPATH=\$(ls ${shellReadablePrefix}*.dex | tr '\\n' ':' | sed 's/:$//') " +
                        "/system/bin/app_process /system/bin $SERVER_MAIN_CLASS"
        return "rm -f ${shellReadablePrefix}*.dex && " +
                "$copyScript && " +
                "chmod 644 ${shellReadablePrefix}*.dex && " +
                classpathCmd
    }

    fun buildActivationCommand(context: Context): String? {
        val shellCommand = buildActivationShellCommand(context) ?: return null
        return "adb shell \"$shellCommand\""
    }
}
