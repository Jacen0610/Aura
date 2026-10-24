package si.jacen.aura

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AuraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 高德定位 SDK 合规要求：任何定位调用前必须完成隐私接口声明
        com.amap.api.location.AMapLocationClient.updatePrivacyShow(this, true, true)
        com.amap.api.location.AMapLocationClient.updatePrivacyAgree(this, true)
    }
}
