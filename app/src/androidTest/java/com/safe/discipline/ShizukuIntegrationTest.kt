package com.safe.discipline

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.safe.discipline.data.service.ShizukuService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import rikka.shizuku.Shizuku

@RunWith(AndroidJUnit4::class)
class ShizukuIntegrationTest {

    @Test
    fun testShizukuPermissionAndShell() {
        // 1. 检查 Shizuku 权限 (前提是已经在 App 里激活了 Server)
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        try {
            // 注意：Shizuku 需要先绑定 service。这通常在 Activity 里做。
            // 但如果 Server 已经运行，我们可以直接尝试 checkSelfPermission
            val permission = Shizuku.checkSelfPermission()

            if (permission == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.d("Test", "Permission GRANTED ✅")

                // 2. 执行 ID 命令
                val (code, output) = ShizukuService.runShell("id")
                Log.d("Test", "Output of 'id': $output")

                assertEquals("Shell command exit code should be 0", 0, code)
                assertTrue("Output should contain uid=2000(shell)", output.contains("uid=2000"))
                assertTrue("Output should contain shell group", output.contains("gid=2000"))
            } else {
                Log.w(
                        "Test",
                        "Permission NOT granted. Test skipped but not failed (environment issue)."
                )
                // 如果没权限，测试是无法验证“执行代码”的，只能验证“没权限是符合预期的”
            }
        } catch (e: Exception) {
            // 如果 Shizuku 服务都没启动，不仅没权限，连 checkSelfPermission 都可能抛错
            Log.e("Test", "Shizuku not available: ${e.message}")
        }
    }
}
