package si.jacen.aura.data.network.dto

import si.jacen.aura.domain.model.AirHourlyItem
import si.jacen.aura.domain.model.AirIndex
import si.jacen.aura.domain.model.AirPollutant
import si.jacen.aura.domain.model.AirQuality
import si.jacen.aura.domain.model.CurrentWeather
import si.jacen.aura.domain.model.DailyItem
import si.jacen.aura.domain.model.GeoCity
import si.jacen.aura.domain.model.HourlyItem
import si.jacen.aura.domain.model.MinutelyPoint
import si.jacen.aura.domain.model.MinutelyPrecip
import si.jacen.aura.domain.model.WeatherBundle
import si.jacen.aura.domain.model.WeatherWarning

private fun String?.toIntOr() = this?.trim()?.toIntOrNull()
private fun String?.toDoubleOr() = this?.trim()?.toDoubleOrNull()

fun GeoLocationDto.toDomain(): GeoCity? {
    val cityName = name ?: return null
    val cityId = id ?: return null
    val lat = lat?.toDoubleOrNull() ?: return null
    val lon = lon?.toDoubleOrNull() ?: return null
    return GeoCity(
        id = cityId,
        name = cityName,
        adm1 = adm1,
        adm2 = adm2,
        country = country,
        latitude = lat,
        longitude = lon,
    )
}

fun WeatherNowDto.toCurrentWeather(): CurrentWeather {
    val n = now
    return CurrentWeather(
        observedAt = n?.obsTime,
        temperature = n?.temp.toIntOr(),
        feelsLike = n?.feelsLike.toIntOr(),
        text = n?.text ?: "未知",
        iconCode = n?.icon ?: "",
        windDir = n?.windDir,
        windScale = n?.windScale,
        humidity = n?.humidity.toIntOr(),
        precip = n?.precip.toDoubleOr(),
        pressure = n?.pressure.toIntOr(),
        visibilityKm = n?.vis.toDoubleOr(),
        cloud = n?.cloud.toIntOr(),
    )
}

fun WeatherHourlyDto.toHourlyItems(): List<HourlyItem> = hourly.map {
    HourlyItem(
        time = it.fxTime ?: "",
        temperature = it.temp.toIntOr(),
        text = it.text ?: "",
        iconCode = it.icon ?: "",
        precipProbability = it.pop.toIntOr(),
    )
}

fun WeatherDailyDto.toDailyItems(): List<DailyItem> = daily.map {
    DailyItem(
        date = it.fxDate ?: "",
        textDay = it.textDay ?: "",
        textNight = it.textNight,
        iconDay = it.iconDay ?: "",
        tempMax = it.tempMax.toIntOr(),
        tempMin = it.tempMin.toIntOr(),
        precipProbability = it.precipProb.toIntOr(),
        uvIndex = it.uvIndex.toIntOr(),
        sunrise = it.sunrise,
        sunset = it.sunset,
    )
}

fun WeatherSnapshotDto.toWeatherBundle(): WeatherBundle = WeatherBundle(
    updatedAt = now.updateTime,
    current = now.toCurrentWeather(),
    hourly = hourly.toHourlyItems(),
    daily = daily.toDailyItems(),
)

fun AirIndexDto.toDomain(): AirIndex? {
    val aqiValue = aqi ?: return null
    return AirIndex(
        code = code ?: "",
        name = name ?: "",
        aqi = aqiValue,
        display = aqiDisplay ?: aqiValue.toString(),
        level = level,
        category = category ?: "未知",
        primaryPollutantName = primaryPollutant?.name,
        red = color?.red ?: 0,
        green = color?.green ?: 0,
        blue = color?.blue ?: 0,
        effect = health?.effect,
        generalAdvice = health?.advice?.generalPopulation,
        sensitiveAdvice = health?.advice?.sensitivePopulation,
    )
}

fun AirPollutantDto.toDomain(): AirPollutant? {
    val v = concentration?.value ?: return null
    return AirPollutant(
        code = code ?: "",
        name = name ?: code ?: "",
        value = v,
        unit = concentration?.unit ?: "",
    )
}

fun AirHourlyEntryDto.toDomain(): AirHourlyItem? {
    val aqiValue = aqi ?: return null
    return AirHourlyItem(
        time = fxTime ?: "",
        aqi = aqiValue,
        category = category ?: "",
    )
}

fun AirSnapshotDto.toAirQuality(): AirQuality = AirQuality(
    indexes = current.indexes.mapNotNull { it.toDomain() },
    pollutants = current.pollutants.mapNotNull { it.toDomain() },
    hourly = hourly.hourly.mapNotNull { it.toDomain() },
)

private val ALERT_LEVEL_CN = mapOf(
    "blue" to "蓝色", "yellow" to "黄色", "amber" to "橙色", "orange" to "橙色",
    "red" to "红色", "purple" to "紫色", "black" to "黑色", "white" to "白色",
    "gray" to "灰色", "green" to "绿色",
)

fun AlertDto.toDomain(): WeatherWarning? {
    val name = eventType?.name ?: return null
    val isCancel = messageType?.code == "cancel"
    return WeatherWarning(
        typeName = name,
        level = color?.code?.let { ALERT_LEVEL_CN[it] } ?: severity ?: "",
        headline = headline ?: "$name 预警",
        description = description ?: "",
        instruction = instruction,
        effectiveTime = effectiveTime,
        expireTime = expireTime,
        issuedTime = issuedTime,
        sender = senderName,
        isCancel = isCancel,
        red = color?.red ?: 0,
        green = color?.green ?: 0,
        blue = color?.blue ?: 0,
    )
}

fun MinutelyDto.toDomain(): MinutelyPrecip? {
    val s = summary ?: return null
    return MinutelyPrecip(
        summary = s,
        points = minutely.mapNotNull { m ->
            val time = m.fxTime ?: return@mapNotNull null
            MinutelyPoint(
                fxTime = time,
                precip = m.precip?.toDoubleOrNull() ?: 0.0,
                prob = m.prob?.toIntOrNull(),
            )
        },
    )
}
