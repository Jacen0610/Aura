package si.jacen.aura.data.network

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import si.jacen.aura.data.network.dto.AirCurrentDto
import si.jacen.aura.data.network.dto.AirHourlyForecastDto
import si.jacen.aura.data.network.dto.AskDto
import si.jacen.aura.data.network.dto.GeoLookupDto
import si.jacen.aura.data.network.dto.MinutelyDto
import si.jacen.aura.data.network.dto.WeatherAlertDto
import si.jacen.aura.data.network.dto.WeatherDailyDto
import si.jacen.aura.data.network.dto.WeatherHourlyDto
import si.jacen.aura.data.network.dto.WeatherNowDto

/**
 * 全部请求指向自建代理（BuildConfig.AURA_API_BASE_URL），
 * 路径与和风官方 API 一致（代理透明转发）。
 */
interface AuraApi {

    @GET("/v7/weather/now")
    suspend fun weatherNow(@Query("location") location: String): WeatherNowDto

    @GET("/v7/weather/24h")
    suspend fun weather24h(@Query("location") location: String): WeatherHourlyDto

    @GET("/v7/weather/7d")
    suspend fun weather7d(@Query("location") location: String): WeatherDailyDto

    /** 实时气象预警（v1，纬度/经度路径） */
    @GET("/weatheralert/v1/current/{latLon}")
    suspend fun weatherAlert(@Path("latLon", encoded = true) latLon: String): WeatherAlertDto

    @GET("/v7/minutely/5m")
    suspend fun minutely5m(@Query("location") location: String): MinutelyDto

    /** 天气问答（自建 AI 服务） */
    @GET("/ai/ask")
    suspend fun ask(
        @Query("location") location: String,
        @Query("question") question: String,
    ): AskDto

    @GET("/geo/v2/city/lookup")
    suspend fun cityLookup(
        @Query("location") query: String,
        @Query("number") number: Int = 10,
    ): GeoLookupDto

    /** [latLon] 由 Coordinate.airPath 生成：纬度/经度 */
    @GET("/airquality/v1/current/{latLon}")
    suspend fun airCurrent(@Path("latLon", encoded = true) latLon: String): AirCurrentDto

    @GET("/airquality/v1/hourly/{latLon}")
    suspend fun airHourly(@Path("latLon", encoded = true) latLon: String): AirHourlyForecastDto
}
