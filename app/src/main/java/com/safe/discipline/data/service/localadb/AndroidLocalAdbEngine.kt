package com.safe.discipline.data.service.localadb

import android.content.Context
import android.os.Build
import android.util.Log
import com.safe.discipline.data.service.WirelessActivationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidLocalAdbEngine(private val context: Context) : WirelessActivationService.LocalAdbEngine {
    private companion object {
        private const val TAG = "OutPhone"
    }

    private val adbKey by lazy(LazyThreadSafetyMode.NONE) {
        val prefs = context.getSharedPreferences("local_adb", Context.MODE_PRIVATE)
        AdbKey(PreferenceAdbKeyStore(prefs), "${Build.MODEL}-${context.packageName}")
    }

    override suspend fun runShell(host: String, port: Int, command: String): Pair<Int, String> =
            withContext(Dispatchers.IO) {
                val endpoints = mutableListOf<AdbEndpoint>()
                if (host.isNotBlank() && port > 0) endpoints += AdbEndpoint(host, port)
                val discovered = AdbMdnsLocator.findTlsConnectEndpoint(context)
                if (discovered != null) endpoints += discovered
                Log.d(TAG, "Wireless endpoints candidate: ${endpoints.joinToString { "${it.host}:${it.port}" }}")
                if (endpoints.isEmpty()) {
                    Log.e(TAG, "No wireless adb endpoint discovered")
                    return@withContext -1 to "未发现无线调试端口，请先开启并完成一次无线调试配对"
                }

                var lastError = "无线启动失败"
                for (endpoint in endpoints.distinctBy { "${it.host}:${it.port}" }) {
                    try {
                        Log.d(TAG, "Trying wireless adb endpoint ${endpoint.host}:${endpoint.port}")
                        AdbClient(endpoint.host, endpoint.port, adbKey).use { client ->
                            client.connect()
                            Log.d(TAG, "Connected to wireless adb ${endpoint.host}:${endpoint.port}")
                            val output = StringBuilder()
                            client.shellCommand(command) { bytes ->
                                output.append(bytes.toString(Charsets.UTF_8))
                            }
                            Log.d(TAG, "Wireless adb shell command finished on ${endpoint.host}:${endpoint.port}")
                            return@withContext 0 to output.toString()
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Wireless adb failed on ${endpoint.host}:${endpoint.port}", t)
                        lastError =
                                "连接 ${endpoint.host}:${endpoint.port} 失败: ${t.message ?: t.javaClass.simpleName}"
                    }
                }
                -1 to lastError
            }
}
