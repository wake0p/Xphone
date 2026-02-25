package com.safe.discipline.data.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay

object WirelessActivationService {
    private const val TAG = "OutPhone"

    sealed class Result {
        data object Success : Result()

        data class Failed(val message: String) : Result()
    }

    /**
     * Local ADB 引擎接口。
     * 当前项目先提供接口与流程，后续可替换为真正的无线 ADB 实现。
     */
    interface LocalAdbEngine {
        suspend fun runShell(host: String, port: Int, command: String): Pair<Int, String>
    }

    private object UnavailableEngine : LocalAdbEngine {
        override suspend fun runShell(host: String, port: Int, command: String): Pair<Int, String> {
            return -1 to "无线 ADB 引擎未实现，请先使用复制命令方式激活"
        }
    }

    @Volatile private var localAdbEngine: LocalAdbEngine = UnavailableEngine

    fun installEngine(engine: LocalAdbEngine) {
        localAdbEngine = engine
    }

    suspend fun start(context: Context, host: String, port: Int): Result {
        Log.d(TAG, "Wireless start requested: host=$host port=$port")
        val shellCommand = EmbeddedStarter.buildActivationShellCommand(context)
        if (shellCommand.isNullOrBlank()) {
            Log.e(TAG, "Wireless start aborted: empty activation shell command")
            return Result.Failed("未找到激活命令，请先确认 server.dex 已打包")
        }

        val packageName = context.packageName
        val runAsProbe = "run-as $packageName id"
        val (probeCode, probeOutput) = localAdbEngine.runShell(host, port, runAsProbe)
        if (probeCode != 0) {
            Log.e(TAG, "run-as probe failed: code=$probeCode output=$probeOutput")
            return Result.Failed("run-as 不可用（通常是非 debug 包）: $probeOutput")
        }

        val logFile = "/data/local/tmp/${packageName.replace('.', '_')}_wireless_start.log"
        val detachedCommand = wrapDetached(shellCommand, logFile)
        Log.d(TAG, "Wireless detached command length=${detachedCommand.length}")

        val (code, output) = localAdbEngine.runShell(host, port, detachedCommand)
        if (code != 0) {
            Log.e(TAG, "Wireless shell dispatch failed: code=$code output=$output")
            return Result.Failed(output.ifBlank { "无线启动失败(code=$code)" })
        }
        Log.d(TAG, "Wireless shell dispatched successfully, checking binder readiness...")

        repeat(10) {
            if (ShizukuService.isReady()) {
                Log.d(TAG, "Wireless activation success: Shizuku is ready")
                return Result.Success
            }
            delay(500)
        }

        val (_, startupLog) =
                localAdbEngine.runShell(
                        host,
                        port,
                        "if [ -f $logFile ]; then cat $logFile; else echo '__NO_START_LOG__'; ls -l /data/local/tmp/${packageName.replace('.', '_')}* 2>/dev/null; fi"
                )
        Log.e(TAG, "Wireless activation timed out, startup log: $startupLog")
        Log.e(TAG, "Wireless activation timed out: binder still not ready")
        return Result.Failed("命令已发送，但服务未就绪。启动日志: ${startupLog.take(500)}")
    }

    private fun wrapDetached(command: String, logFile: String): String {
        return "echo \"[wireless-start] begin\" > $logFile; $command >> $logFile 2>&1 &"
    }
}
