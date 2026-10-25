package si.jacen.aura

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import si.jacen.aura.domain.model.AirQuality
import si.jacen.aura.domain.model.Coordinate
import si.jacen.aura.domain.model.CurrentWeather
import si.jacen.aura.domain.model.GeoCity
import si.jacen.aura.domain.model.TempUnit
import si.jacen.aura.domain.model.WeatherBundle
import si.jacen.aura.domain.repository.AirRepository
import si.jacen.aura.domain.repository.GeoRepository
import si.jacen.aura.domain.repository.SettingsRepository
import si.jacen.aura.domain.repository.WeatherRepository
import si.jacen.aura.domain.usecase.GetWeatherUseCase
import si.jacen.aura.domain.usecase.LocateAndResolveCityUseCase
import si.jacen.aura.domain.usecase.LocationProvider
import si.jacen.aura.ui.weather.WeatherViewModel
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val fakeCity = GeoCity(
        id = "101011600", name = "东城", adm1 = "北京市", adm2 = "北京",
        country = "中国", latitude = 39.92, longitude = 116.41,
    )

    private val fakeBundle = WeatherBundle(
        updatedAt = "2026-10-19T00:18+08:00",
        current = CurrentWeather(
            observedAt = null, temperature = 6, feelsLike = 4, text = "晴", iconCode = "150",
            windDir = "东北风", windScale = "1", humidity = 89, precip = 0.0,
            pressure = 1017, visibilityKm = 18.0, cloud = 0,
        ),
        hourly = emptyList(),
        daily = emptyList(),
    )

    private class FakeWeatherRepo(
        var behavior: suspend (Coordinate) -> WeatherBundle = { throw IOException() },
    ) : WeatherRepository {
        override suspend fun fetch(coordinate: Coordinate): WeatherBundle = behavior(coordinate)
    }

    private class FakeGeoRepo : GeoRepository {
        override suspend fun search(query: String): List<GeoCity> = listOf(fakeCityHolder)
        override suspend fun reverseGeocode(coordinate: Coordinate): GeoCity? = fakeCityHolder
    }

    private class FakeLocationProvider(var coordinate: Coordinate? = null) : LocationProvider {
        override suspend fun currentCoordinate(): Coordinate? = coordinate
    }

    private class FakeSettingsRepo(
        initialCity: GeoCity? = null,
        initialWeather: WeatherBundle? = null,
    ) : SettingsRepository {
        override val unit = MutableStateFlow(TempUnit.CELSIUS)
        private val cityFlow = MutableStateFlow(initialCity)
        override val lastCity: Flow<GeoCity?> = cityFlow
        override val cachedWeather = MutableStateFlow(initialWeather)
        override val cachedAir = MutableStateFlow<AirQuality?>(null)
        override val calendarEnabled = MutableStateFlow(false)
        var savedCity: GeoCity? = null
        var savedUnit: TempUnit? = null

        override suspend fun setUnit(unit: TempUnit) {
            savedUnit = unit
            this.unit.value = unit
        }

        override suspend fun setLastCity(city: GeoCity?) {
            savedCity = city
            cityFlow.value = city
        }

        override suspend fun setCalendarEnabled(enabled: Boolean) {
            calendarEnabled.value = enabled
        }
    }

    companion object {
        lateinit var fakeCityHolder: GeoCity
    }

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        fakeCityHolder = fakeCity
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createVm(
        weatherRepo: FakeWeatherRepo,
        settings: FakeSettingsRepo,
        locationProvider: FakeLocationProvider = FakeLocationProvider(),
        geoRepo: GeoRepository = FakeGeoRepo(),
    ) = WeatherViewModel(
        getWeather = GetWeatherUseCase(weatherRepo),
        locateAndResolveCity = LocateAndResolveCityUseCase(locationProvider, geoRepo),
        settings = settings,
    )

    @Test
    fun `定位反查网络异常 - 不崩溃且引导手动搜索`() = runTest(dispatcher) {
        val repo = FakeWeatherRepo { fakeBundle }
        val failingGeo = object : GeoRepository {
            override suspend fun search(query: String): List<GeoCity> = throw IOException("backend down")
            override suspend fun reverseGeocode(coordinate: Coordinate): GeoCity? =
                throw IOException("backend down")
        }
        val vm = createVm(
            repo,
            FakeSettingsRepo(),
            locationProvider = FakeLocationProvider(Coordinate(39.92, 116.41)),
            geoRepo = failingGeo,
        )

        advanceUntilIdle()
        vm.onLocationPermission(true)
        advanceUntilIdle()

        // 未捕获异常会直接让测试进程崩溃；走到这里即证明异常被正确吞掉并降级
        assertTrue(vm.uiState.value.needsSetup)
        assertNull(vm.uiState.value.weather)
        assertNotNull(vm.uiState.value.error)
    }

    @Test
    fun `有历史城市 - 首次加载成功`() = runTest(dispatcher) {
        val repo = FakeWeatherRepo { fakeBundle }
        val vm = createVm(repo, FakeSettingsRepo(initialCity = fakeCity))

        advanceUntilIdle()

        assertEquals(fakeCity, vm.uiState.value.city)
        assertNotNull(vm.uiState.value.weather)
        assertFalse(vm.uiState.value.initialLoading)
        assertFalse(vm.uiState.value.isOfflineData)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `无历史城市 - 初始不加载等待定位`() = runTest(dispatcher) {
        val repo = FakeWeatherRepo { fakeBundle }
        val vm = createVm(repo, FakeSettingsRepo())

        advanceUntilIdle()

        assertNull(vm.uiState.value.city)
        assertFalse(vm.uiState.value.initialLoading)
        assertNull(vm.uiState.value.weather)
    }

    @Test
    fun `加载失败且无缓存 - 显示错误`() = runTest(dispatcher) {
        val repo = FakeWeatherRepo { throw IOException("network down") }
        val vm = createVm(repo, FakeSettingsRepo(initialCity = fakeCity))

        advanceUntilIdle()

        assertNull(vm.uiState.value.weather)
        assertEquals("无法连接服务器：请检查网络；若网络正常则后端已失效，请联系管理员修复", vm.uiState.value.error)
    }

    @Test
    fun `加载失败但有缓存 - 显示离线数据`() = runTest(dispatcher) {
        val repo = FakeWeatherRepo { throw IOException("network down") }
        val vm = createVm(repo, FakeSettingsRepo(initialCity = fakeCity, initialWeather = fakeBundle))

        advanceUntilIdle()

        assertNotNull(vm.uiState.value.weather)
        assertTrue(vm.uiState.value.isOfflineData)
    }

    @Test
    fun `定位成功 - 反查城市并加载`() = runTest(dispatcher) {
        val repo = FakeWeatherRepo { fakeBundle }
        val settings = FakeSettingsRepo()
        val provider = FakeLocationProvider(Coordinate(39.92, 116.41))
        val vm = createVm(repo, settings, provider)

        advanceUntilIdle()
        vm.onLocationPermission(true)
        advanceUntilIdle()

        assertEquals(fakeCity, settings.savedCity)
        assertNotNull(vm.uiState.value.weather)
        assertFalse(vm.uiState.value.needsSetup)
    }

    @Test
    fun `拒绝权限 - 引导手动搜索`() = runTest(dispatcher) {
        val repo = FakeWeatherRepo { fakeBundle }
        val vm = createVm(repo, FakeSettingsRepo())

        advanceUntilIdle()
        vm.onLocationPermission(false)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.needsSetup)
        assertNull(vm.uiState.value.weather)
    }
}
