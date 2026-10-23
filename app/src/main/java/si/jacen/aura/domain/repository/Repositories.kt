package si.jacen.aura.domain.repository

import kotlinx.coroutines.flow.Flow
import si.jacen.aura.domain.model.AirQuality
import si.jacen.aura.domain.model.Coordinate
import si.jacen.aura.domain.model.GeoCity
import si.jacen.aura.domain.model.TempUnit
import si.jacen.aura.domain.model.WeatherBundle

interface WeatherRepository {
    /** 拉取 now + 24h + 7d 并合并；成功时同时持久化快照（离线兜底）。失败抛出异常。 */
    suspend fun fetch(coordinate: Coordinate): WeatherBundle
}

interface AirRepository {
    /** 拉取实时 + 逐小时空气质量并合并；成功时同时持久化快照。失败抛出异常。 */
    suspend fun fetch(coordinate: Coordinate): AirQuality
}

interface GeoRepository {
    /** 城市名搜索，或坐标反查（query 直接透传和风 location 参数）。 */
    suspend fun search(query: String): List<GeoCity>

    suspend fun reverseGeocode(coordinate: Coordinate): GeoCity?
}

interface SettingsRepository {
    val unit: Flow<TempUnit>
    val lastCity: Flow<GeoCity?>
    val cachedWeather: Flow<WeatherBundle?>
    val cachedAir: Flow<AirQuality?>

    suspend fun setUnit(unit: TempUnit)
    suspend fun setLastCity(city: GeoCity?)
}
