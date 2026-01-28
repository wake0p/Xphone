package com.safe.discipline.data.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.safe.discipline.MainActivity

class DisciplineService : Service() {

    override fun onCreate() {
        super.onCreate()
        Log.d("OutPhone", "守护服务已启动: 监控计划状态")

        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ 要求指定服务类型，使用 specialUse（不需要额外权限）
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // 被杀后自动重启
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun createNotification(): Notification {
        val channelId = "discipline_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                    NotificationChannel(channelId, "自动计划守护", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, channelId)
                .setContentTitle("自律模式运行中")
                .setContentText("正在守护您的自动禁闭计划...")
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 888

        fun start(context: Context) {
            try {
                // Android 14+ 严格限制：只能在前台启动前台服务
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val activityManager =
                            context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                    val appProcesses = activityManager.runningAppProcesses ?: emptyList()
                    val isInForeground =
                            appProcesses.any {
                                it.processName == context.packageName &&
                                        it.importance ==
                                                ActivityManager.RunningAppProcessInfo
                                                        .IMPORTANCE_FOREGROUND
                            }

                    if (!isInForeground) {
                        Log.w("OutPhone", "无法启动服务: App 不在前台。计划将在下次 App 打开时生效。")
                        return
                    }
                }

                val intent = Intent(context, DisciplineService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Log.d("OutPhone", "守护服务启动成功")
            } catch (e: Throwable) {
                Log.e("OutPhone", "启动服务失败（已静默处理）: ${e.message}", e)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, DisciplineService::class.java))
                Log.d("OutPhone", "守护服务已停止")
            } catch (e: Throwable) {
                Log.e("OutPhone", "停止服务失败: ${e.message}")
            }
        }
    }
}
