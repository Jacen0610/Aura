package si.jacen.aura

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import si.jacen.aura.data.location.LocationClient

/**
 * 真机定位链路测试：高德主路径 → 系统缓存 → LocationManager 兜底。
 * 运行方式：AS 右键运行，或
 *   adb shell am instrument -w -g -e class si.jacen.aura.LocationClientInstrumentedTest \
 *     si.jacen.aura.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class LocationClientInstrumentedTest {

    @Test(timeout = 120_000L)
    fun locationChain_returnsCoordinateInChina() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // 确保运行时权限（-g 参数通常已自动授予，这里双保险）
        instrumentation.uiAutomation.executeShellCommand(
            "pm grant si.jacen.aura android.permission.ACCESS_FINE_LOCATION"
        )
        instrumentation.uiAutomation.executeShellCommand(
            "pm grant si.jacen.aura android.permission.ACCESS_COARSE_LOCATION"
        )
        Thread.sleep(500)

        val context = instrumentation.targetContext
        val coordinate = runBlocking { LocationClient(context).currentCoordinate() }

        assertNotNull("三级定位链（高德/缓存/系统）均未返回坐标", coordinate)
        coordinate!!
        // 中国大陆经纬度粗范围：纬 3~54，经 73~136
        assertTrue("纬度越界（非中国）: ${coordinate.latitude}", coordinate.latitude in 3.0..54.0)
        assertTrue("经度越界（非中国）: ${coordinate.longitude}", coordinate.longitude in 73.0..136.0)
        // 南京江宁一带（在其他城市运行请放宽或注释这两行）
        assertTrue("纬度偏离南京: ${coordinate.latitude}", Math.abs(coordinate.latitude - 31.9) < 1.0)
        assertTrue("经度偏离南京: ${coordinate.longitude}", Math.abs(coordinate.longitude - 118.8) < 1.0)
    }
}
