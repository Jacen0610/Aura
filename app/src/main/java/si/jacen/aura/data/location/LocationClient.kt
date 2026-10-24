package si.jacen.aura.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import si.jacen.aura.domain.model.Coordinate
import si.jacen.aura.domain.usecase.LocationProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 统一定位入口（LocationProvider 唯一实现），面向国内网络环境：
 *
 * 主路径：高德定位 SDK 单次定位——WiFi/基站数据库与服务器均在国内，
 * 不依赖 Google 服务，室内秒级返回；
 * 兜底：平台 LocationManager（GPS 与 ROM 自带网络定位双通道并发），
 * 高德服务异常时仍可定位（户外 GPS 场景）。
 */
@Singleton
class LocationClient @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocationProvider {

    private val platformLocationManager: LocationManager
        get() = context.getSystemService(LocationManager::class.java)

    @SuppressLint("MissingPermission")
    override suspend fun currentCoordinate(): Coordinate? {
        amapOnce()?.let {
            android.util.Log.d(TAG, "路径=高德SDK lat=${it.latitude} lon=${it.longitude}")
            return it
        }
        freshLastKnown()?.let {
            android.util.Log.d(TAG, "路径=系统缓存 lat=${it.latitude} lon=${it.longitude}")
            return it
        }
        android.util.Log.d(TAG, "路径=LocationManager兜底(最长${PLATFORM_TIMEOUT_MS / 1000}秒)")
        return withTimeoutOrNull(PLATFORM_TIMEOUT_MS) { firstFix(enabledProviders()) }
    }

    // ---------- 主路径：高德 ----------

    /** 高德单次定位，12 秒内无结果视为失败（转入兜底）。 */
    @SuppressLint("MissingPermission")
    private suspend fun amapOnce(): Coordinate? = withTimeoutOrNull(AMAP_TIMEOUT_MS) {
        suspendCancellableCoroutine { cont ->
            var finished = false
            val client = runCatching { AMapLocationClient(context) }.getOrNull()
            if (client == null) {
                cont.resumeWith(Result.success(null))
                return@suspendCancellableCoroutine
            }
            client.setLocationOption(
                AMapLocationClientOption().apply {
                    isOnceLocation = true
                    locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                }
            )
            client.setLocationListener { location ->
                if (finished) return@setLocationListener
                finished = true
                client.stopLocation()
                client.onDestroy()
                android.util.Log.d(
                    TAG,
                    "高德SDK结果 errorCode=${location.errorCode} type=${location.locationType} " +
                        "lat=${location.latitude} lon=${location.longitude} city=${location.city}${location.district ?: ""}",
                )
                if (cont.isActive) {
                    val coordinate = if (location.errorCode == 0) {
                        Coordinate(location.latitude, location.longitude)
                    } else null
                    cont.resumeWith(Result.success(coordinate))
                }
            }
            cont.invokeOnCancellation {
                runCatching {
                    client.stopLocation()
                    client.onDestroy()
                }
            }
            client.startLocation()
        }
    }

    // ---------- 兜底：平台 LocationManager ----------

    /** GPS 与网络双通道并发，任一通道先出结果即返回；全部无果返回 null。 */
    @SuppressLint("MissingPermission")
    private suspend fun firstFix(providers: List<String>): Coordinate? = coroutineScope {
        if (providers.isEmpty()) return@coroutineScope null
        val winner = CompletableDeferred<Coordinate?>()
        val listens = providers.map { provider ->
            async {
                val coordinate = runCatching { awaitProvider(provider) }.getOrNull()
                if (coordinate != null) winner.complete(coordinate)
            }
        }
        launch {
            listens.joinAll()
            // 所有通道都结束仍无人成功
            winner.complete(null)
        }
        val result = winner.await()
        coroutineContext.cancelChildren()
        result
    }

    /** 监听单个 provider 直到出第一个结果；注册失败立即返回 null。 */
    @SuppressLint("MissingPermission")
    private suspend fun awaitProvider(provider: String): Coordinate? =
        suspendCancellableCoroutine { cont ->
            var finished = false
            val listener = object : LocationListener {
                override fun onLocationChanged(location: android.location.Location) {
                    if (finished) return
                    finished = true
                    runCatching { platformLocationManager.removeUpdates(this) }
                    if (cont.isActive) {
                        cont.resumeWith(Result.success(Coordinate(location.latitude, location.longitude)))
                    }
                }
            }
            val registered = runCatching {
                @Suppress("DEPRECATION")
                platformLocationManager.requestLocationUpdates(
                    provider, 0L, 0f, listener, Looper.getMainLooper(),
                )
            }
            if (registered.isFailure) {
                finished = true
                if (cont.isActive) cont.resumeWith(Result.success(null))
                return@suspendCancellableCoroutine
            }
            cont.invokeOnCancellation {
                runCatching { platformLocationManager.removeUpdates(listener) }
            }
        }

    /** 任何 provider 的新鲜缓存（1 小时内）直接采用，免去整个等待。 */
    @SuppressLint("MissingPermission")
    private fun freshLastKnown(): Coordinate? {
        var best: android.location.Location? = null
        listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        ).forEach { provider ->
            val location = runCatching {
                platformLocationManager.getLastKnownLocation(provider)
            }.getOrNull()
            if (location != null &&
                System.currentTimeMillis() - location.time < LAST_KNOWN_MAX_AGE_MS &&
                (best == null || location.time > best!!.time)
            ) {
                best = location
            }
        }
        return best?.let { Coordinate(it.latitude, it.longitude) }
    }

    private fun enabledProviders(): List<String> =
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { platformLocationManager.isProviderEnabled(it) }.getOrDefault(false) }

    private companion object {
        const val TAG = "AuraLocation"
        const val AMAP_TIMEOUT_MS = 12_000L
        const val PLATFORM_TIMEOUT_MS = 60_000L
        const val LAST_KNOWN_MAX_AGE_MS = 60 * 60 * 1000L
    }
}
