package com.safe.discipline

import android.app.Application
import android.os.Build

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // 全局异常捕捉，防止启动瞬间崩溃导致的“闪退”看不见日志
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("OutPhone", "FATAL CRASH on thread ${thread.name}", throwable)
            // 允许系统继续处理，以便生成正常的崩溃对话框
        }

        try {
            // 关键步骤：绕过 Android 9+ 的隐藏 API 限制
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("")
            }
        } catch (e: Throwable) {
            android.util.Log.e("OutPhone", "HiddenApiBypass failed", e)
        }
    }
}
