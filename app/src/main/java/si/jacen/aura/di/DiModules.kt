package si.jacen.aura.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import si.jacen.aura.BuildConfig
import si.jacen.aura.data.calendar.CalendarReaderImpl
import si.jacen.aura.data.local.SettingsDataStore
import si.jacen.aura.data.location.LocationClient
import si.jacen.aura.data.network.AuraApi
import si.jacen.aura.data.repository.AiRepositoryImpl
import si.jacen.aura.data.repository.AirRepositoryImpl
import si.jacen.aura.data.repository.GeoRepositoryImpl
import si.jacen.aura.data.repository.WeatherRepositoryImpl
import si.jacen.aura.domain.repository.AiRepository
import si.jacen.aura.domain.repository.AirRepository
import si.jacen.aura.domain.repository.CalendarEventReader
import si.jacen.aura.domain.repository.GeoRepository
import si.jacen.aura.domain.repository.SettingsRepository
import si.jacen.aura.domain.repository.WeatherRepository
import si.jacen.aura.domain.usecase.LocationProvider
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        // AI 问答的 LLM 生成可达 1~2 分钟（免费档高峰），普通天气接口毫秒级响应不受影响
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.AURA_API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideApi(retrofit: Retrofit): AuraApi = retrofit.create(AuraApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    abstract fun bindWeatherRepository(impl: WeatherRepositoryImpl): WeatherRepository

    @Binds
    abstract fun bindAirRepository(impl: AirRepositoryImpl): AirRepository

    @Binds
    abstract fun bindGeoRepository(impl: GeoRepositoryImpl): GeoRepository

    @Binds
    abstract fun bindSettingsRepository(impl: SettingsDataStore): SettingsRepository

    @Binds
    abstract fun bindLocationProvider(impl: LocationClient): LocationProvider

    @Binds
    abstract fun bindCalendarEventReader(impl: CalendarReaderImpl): CalendarEventReader

    @Binds
    abstract fun bindAiRepository(impl: AiRepositoryImpl): AiRepository
}
