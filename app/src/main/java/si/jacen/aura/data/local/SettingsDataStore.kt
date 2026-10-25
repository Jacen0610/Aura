package si.jacen.aura.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import si.jacen.aura.data.network.dto.AirSnapshotDto
import si.jacen.aura.data.network.dto.GeoLocationDto
import si.jacen.aura.data.network.dto.WeatherSnapshotDto
import si.jacen.aura.domain.model.AirQuality
import si.jacen.aura.domain.model.GeoCity
import si.jacen.aura.domain.model.TempUnit
import si.jacen.aura.domain.model.WeatherBundle
import si.jacen.aura.data.network.dto.toAirQuality
import si.jacen.aura.data.network.dto.toDomain
import si.jacen.aura.data.network.dto.toWeatherBundle
import si.jacen.aura.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "aura_settings")

private object Keys {
    val UNIT = stringPreferencesKey("unit")
    val LAST_CITY = stringPreferencesKey("last_city")
    val WEATHER_SNAPSHOT = stringPreferencesKey("weather_snapshot")
    val AIR_SNAPSHOT = stringPreferencesKey("air_snapshot")
    val CALENDAR_ENABLED = stringPreferencesKey("calendar_enabled")
}

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override val unit: Flow<TempUnit> = context.dataStore.data.map { prefs ->
        when (prefs[Keys.UNIT]) {
            TempUnit.FAHRENHEIT.name -> TempUnit.FAHRENHEIT
            else -> TempUnit.CELSIUS
        }
    }

    override val lastCity: Flow<GeoCity?> = context.dataStore.data.map { prefs ->
        prefs[Keys.LAST_CITY]?.let {
            runCatching { json.decodeFromString<GeoLocationDto>(it).toDomain() }.getOrNull()
        }
    }

    override val cachedWeather: Flow<WeatherBundle?> = context.dataStore.data.map { prefs ->
        prefs[Keys.WEATHER_SNAPSHOT]?.let {
            runCatching { json.decodeFromString<WeatherSnapshotDto>(it).toWeatherBundle() }.getOrNull()
        }
    }

    override val cachedAir: Flow<AirQuality?> = context.dataStore.data.map { prefs ->
        prefs[Keys.AIR_SNAPSHOT]?.let {
            runCatching { json.decodeFromString<AirSnapshotDto>(it).toAirQuality() }.getOrNull()
        }
    }

    override suspend fun setUnit(unit: TempUnit) {
        context.dataStore.edit { it[Keys.UNIT] = unit.name }
    }

    override val calendarEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.CALENDAR_ENABLED] == "1" }

    override suspend fun setCalendarEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CALENDAR_ENABLED] = if (enabled) "1" else "0" }
    }

    override suspend fun setLastCity(city: GeoCity?) {
        context.dataStore.edit { prefs ->
            if (city == null) {
                prefs.remove(Keys.LAST_CITY)
            } else {
                prefs[Keys.LAST_CITY] = json.encodeToString(
                    GeoLocationDto(
                        name = city.name, id = city.id,
                        lat = city.latitude.toString(), lon = city.longitude.toString(),
                        adm1 = city.adm1, adm2 = city.adm2, country = city.country,
                    ),
                )
            }
        }
    }

    /** 供 Repository 实现在拉取成功后写入离线快照。 */
    suspend fun saveWeatherSnapshot(snapshot: WeatherSnapshotDto) {
        context.dataStore.edit { it[Keys.WEATHER_SNAPSHOT] = json.encodeToString(snapshot) }
    }

    suspend fun saveAirSnapshot(snapshot: AirSnapshotDto) {
        context.dataStore.edit { it[Keys.AIR_SNAPSHOT] = json.encodeToString(snapshot) }
    }
}
