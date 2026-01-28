package com.safe.discipline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.safe.discipline.ui.screens.HomeScreen
import com.safe.discipline.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onStart() {
        super.onStart()
        // 每次回到前台都刷新一下列表和计划，确保 UI 与后台同步
        viewModel.loadPlans()
        viewModel.loadApps()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            viewModel.handleNotificationClick(intent)
        }

        try {
            setContent {
                MaterialTheme {
                    Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                    ) { HomeScreen(viewModel) }
                }
            }
        } catch (e: Throwable) {
            android.util.Log.e("OutPhone", "UI Initial Render Failed", e)
            // 最后的兜底：如果 Compose 渲染引擎崩了，尝试显示一个极简提示（虽然这也很难奏效，但比直接消失强）
        }
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        // 关键：更新当前的 Intent，确保之后使用的是最新的 Intent 参数
        setIntent(intent)
        // 处理通知栏点击事件
        viewModel.handleNotificationClick(intent)
    }
}
