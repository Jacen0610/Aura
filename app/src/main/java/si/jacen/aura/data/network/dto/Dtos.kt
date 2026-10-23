package si.jacen.aura.data.network.dto

import kotlinx.serialization.Serializable

/**
 * 与自建代理（透明转发和风）返回结构一一对应的 DTO。
 * 所有字段可空并带默认值：字段增删不应导致解析崩溃。
 */

// ---------- 天气 v7 ----------

@Serializable
data class WeatherNowDto(
    val code: String? = null,
    val updateTime: String? = null,
    val now: NowDto? = null,
)

@Serializable
data class NowDto(
    val obsTime: String? = null,
    val temp: String? = null,
    val feelsLike: String? = null,
    val text: String? = null,
    val icon: String? = null,
    val windDir: String? = null,
    val windScale: String? = null,
    val humidity: String? = null,
    val precip: String? = null,
    val pressure: String? = null,
    val vis: String? = null,
    val cloud: String? = null,
)

@Serializable
data class WeatherHourlyDto(
    val code: String? = null,
    val updateTime: String? = null,
    val hourly: List<HourlyDto> = emptyList(),
)

@Serializable
data class HourlyDto(
    val fxTime: String? = null,
    val temp: String? = null,
    val text: String? = null,
    val icon: String? = null,
    val pop: String? = null,
)

@Serializable
data class WeatherDailyDto(
    val code: String? = null,
    val daily: List<DailyDto> = emptyList(),
)

@Serializable
data class DailyDto(
    val fxDate: String? = null,
    val textDay: String? = null,
    val textNight: String? = null,
    val iconDay: String? = null,
    val tempMax: String? = null,
    val tempMin: String? = null,
    val precipProb: String? = null,
    val uvIndex: String? = null,
    val sunrise: String? = null,
    val sunset: String? = null,
)

// ---------- 城市 / Geo v2 ----------

@Serializable
data class GeoLookupDto(
    val code: String? = null,
    val location: List<GeoLocationDto> = emptyList(),
)

@Serializable
data class GeoLocationDto(
    val name: String? = null,
    val id: String? = null,
    val lat: String? = null,
    val lon: String? = null,
    val adm1: String? = null,
    val adm2: String? = null,
    val country: String? = null,
)

// ---------- 空气质量 v1 ----------

@Serializable
data class AirCurrentDto(
    val indexes: List<AirIndexDto> = emptyList(),
    val pollutants: List<AirPollutantDto> = emptyList(),
)

@Serializable
data class AirIndexDto(
    val code: String? = null,
    val name: String? = null,
    val aqi: Int? = null,
    val aqiDisplay: String? = null,
    val level: String? = null,
    val category: String? = null,
    val color: AirColorDto? = null,
    val primaryPollutant: AirPollutantRefDto? = null,
    val health: AirHealthDto? = null,
)

@Serializable
data class AirColorDto(val red: Int = 0, val green: Int = 0, val blue: Int = 0)

@Serializable
data class AirPollutantRefDto(val code: String? = null, val name: String? = null)

@Serializable
data class AirHealthDto(val effect: String? = null, val advice: AirAdviceDto? = null)

@Serializable
data class AirAdviceDto(val generalPopulation: String? = null, val sensitivePopulation: String? = null)

@Serializable
data class AirPollutantDto(
    val code: String? = null,
    val name: String? = null,
    val concentration: AirConcentrationDto? = null,
)

@Serializable
data class AirConcentrationDto(val value: Double? = null, val unit: String? = null)

@Serializable
data class AirHourlyForecastDto(
    val hourly: List<AirHourlyEntryDto> = emptyList(),
)

@Serializable
data class AirHourlyEntryDto(
    val fxTime: String? = null,
    val aqi: Int? = null,
    val aqiDisplay: String? = null,
    val level: String? = null,
    val category: String? = null,
)

// ---------- 离线快照包装 ----------

@Serializable
data class WeatherSnapshotDto(
    val now: WeatherNowDto = WeatherNowDto(),
    val hourly: WeatherHourlyDto = WeatherHourlyDto(),
    val daily: WeatherDailyDto = WeatherDailyDto(),
)

@Serializable
data class AirSnapshotDto(
    val current: AirCurrentDto = AirCurrentDto(),
    val hourly: AirHourlyForecastDto = AirHourlyForecastDto(),
)
