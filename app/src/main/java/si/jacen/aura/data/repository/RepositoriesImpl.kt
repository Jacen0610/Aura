package si.jacen.aura.data.repository

import si.jacen.aura.data.local.SettingsDataStore
import si.jacen.aura.data.network.AuraApi
import si.jacen.aura.data.network.dto.AirSnapshotDto
import si.jacen.aura.data.network.dto.WeatherSnapshotDto
import si.jacen.aura.data.network.dto.toAirQuality
import si.jacen.aura.data.network.dto.toCurrentWeather
import si.jacen.aura.data.network.dto.toDailyItems
import si.jacen.aura.data.network.dto.toDomain
import si.jacen.aura.data.network.dto.toHourlyItems
import si.jacen.aura.domain.model.AirQuality
import si.jacen.aura.domain.model.Coordinate
import si.jacen.aura.domain.model.GeoCity
import si.jacen.aura.domain.model.WeatherBundle
import si.jacen.aura.domain.repository.AiRepository
import si.jacen.aura.domain.repository.AirRepository
import si.jacen.aura.domain.repository.GeoRepository
import si.jacen.aura.domain.repository.WeatherRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeatherRepositoryImpl @Inject constructor(
    private val api: AuraApi,
    private val settings: SettingsDataStore,
) : WeatherRepository {

    override suspend fun fetch(coordinate: Coordinate): WeatherBundle {
        val location = coordinate.weatherQuery
        val now = api.weatherNow(location)
        val hourly = api.weather24h(location)
        val daily = api.weather7d(location)

        val snapshot = WeatherSnapshotDto(now, hourly, daily)
        runCatching { settings.saveWeatherSnapshot(snapshot) }

        return WeatherBundle(
            updatedAt = now.updateTime,
            current = now.toCurrentWeather(),
            hourly = hourly.toHourlyItems(),
            daily = daily.toDailyItems(),
        )
    }
}

@Singleton
class AirRepositoryImpl @Inject constructor(
    private val api: AuraApi,
    private val settings: SettingsDataStore,
) : AirRepository {

    override suspend fun fetch(coordinate: Coordinate): AirQuality {
        val path = coordinate.airPath
        val current = api.airCurrent(path)
        val hourly = runCatching { api.airHourly(path) }.getOrNull() ?: si.jacen.aura.data.network.dto.AirHourlyForecastDto()

        runCatching { settings.saveAirSnapshot(AirSnapshotDto(current, hourly)) }

        return AirSnapshotDto(current, hourly).toAirQuality()
    }
}

@Singleton
class GeoRepositoryImpl @Inject constructor(
    private val api: AuraApi,
) : GeoRepository {

    override suspend fun search(query: String): List<GeoCity> =
        api.cityLookup(query).location.mapNotNull { it.toDomain() }

    override suspend fun reverseGeocode(coordinate: Coordinate): GeoCity? =
        search(coordinate.weatherQuery).firstOrNull()
}


@Singleton
class AiRepositoryImpl @Inject constructor(
    private val api: AuraApi,
) : AiRepository {
    override suspend fun ask(coordinate: si.jacen.aura.domain.model.Coordinate, question: String): String =
        api.ask(coordinate.weatherQuery, question.take(100)).answer
}
