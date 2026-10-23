package si.jacen.aura.domain.usecase

import si.jacen.aura.domain.model.AirQuality
import si.jacen.aura.domain.model.Coordinate
import si.jacen.aura.domain.model.GeoCity
import si.jacen.aura.domain.model.WeatherBundle
import si.jacen.aura.domain.repository.AirRepository
import si.jacen.aura.domain.repository.GeoRepository
import si.jacen.aura.domain.repository.WeatherRepository
import javax.inject.Inject

class GetWeatherUseCase @Inject constructor(
    private val weatherRepository: WeatherRepository,
) {
    suspend operator fun invoke(coordinate: Coordinate): WeatherBundle = weatherRepository.fetch(coordinate)
}

class GetAirQualityUseCase @Inject constructor(
    private val airRepository: AirRepository,
) {
    suspend operator fun invoke(coordinate: Coordinate): AirQuality = airRepository.fetch(coordinate)
}

class SearchCityUseCase @Inject constructor(
    private val geoRepository: GeoRepository,
) {
    suspend operator fun invoke(query: String): List<GeoCity> =
        if (query.isBlank()) emptyList() else geoRepository.search(query.trim())
}

/** 定位 → 坐标反查城市。任一环节失败返回 null（调用方降级到上次城市/手动搜索）。 */
class LocateAndResolveCityUseCase @Inject constructor(
    private val locationProvider: LocationProvider,
    private val geoRepository: GeoRepository,
) {
    suspend operator fun invoke(): GeoCity? {
        val coordinate = locationProvider.currentCoordinate() ?: return null
        return geoRepository.reverseGeocode(coordinate)
    }
}

interface LocationProvider {
    /** 获取一次当前坐标；无权限/服务不可用/超时返回 null。 */
    suspend fun currentCoordinate(): Coordinate?
}
