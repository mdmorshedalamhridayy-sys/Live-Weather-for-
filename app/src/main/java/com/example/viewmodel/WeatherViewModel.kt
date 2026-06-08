package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.SystemAlert
import com.example.data.WeatherRepository
import com.example.data.WeatherUpload
import com.example.network.GeminiWeatherClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

// --- Domain Models ---

data class HourlyForecast(
    val time: String,
    val temp: Float,
    val condition: String,
    val rainProb: Int
)

data class WeeklyForecast(
    val day: String,
    val tempMin: Int,
    val tempMax: Int,
    val condition: String,
    val windSpeed: Float
)

data class WeatherInfo(
    val name: String,
    val country: String,
    val temp: Float,
    val humidity: Int,
    val windSpeed: Float,
    val windDir: String,
    val rainfall: Float, // mm
    val sunrise: String,
    val sunset: String,
    val description: String,
    val isCycloneThreat: Boolean,
    val hourlyForecast: List<HourlyForecast>,
    val weeklyForecast: List<WeeklyForecast>
)

data class Badge(
    val id: String,
    val title: String,
    val criteria: String,
    val pointsNeeded: Int,
    val iconName: String,
    val colorHex: Long
)

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: WeatherRepository

    init {
        val database = AppDatabase.getDatabase(application)
        repository = WeatherRepository(database)
        
        // Populate default warning alerts in Room on launch if none exist
        viewModelScope.launch {
            repository.allAlerts.collect { list ->
                if (list.isEmpty()) {
                    setupPrepopulatedAlerts()
                }
            }
        }
    }

    // --- Weather Data Definitions ---
    private val locationsMap = mapOf(
        // Bangladesh Districts
        "Dhaka" to createWeather("Dhaka", "Bangladesh", 31.5f, 65, 8f, "SE", 2.0f, "05:12 AM", "06:45 PM", "Tropical Humidity & Scattered Clouds", false),
        "Cox's Bazar" to createWeather("Cox's Bazar", "Bangladesh", 27.0f, 92, 42f, "S", 124.0f, "05:14 AM", "06:42 PM", "Severe Category 2 Cyclone Storm", true),
        "Chittagong" to createWeather("Chittagong", "Bangladesh", 28.5f, 85, 28f, "S", 72.0f, "05:13 AM", "06:43 PM", "Torrential Rain & Monsoon Squalls", false),
        "Sylhet" to createWeather("Sylhet", "Bangladesh", 26.2f, 98, 12f, "NE", 95.0f, "05:08 AM", "06:44 PM", "Extremely Heavy Rainfall & Thunderstorms", false),
        "Khulna" to createWeather("Khulna", "Bangladesh", 30.0f, 75, 15f, "SW", 14.0f, "05:17 AM", "06:49 PM", "Overcast Skies & Light Showers", false),
        "Barisal" to createWeather("Barisal", "Bangladesh", 28.0f, 88, 32f, "S", 55.0f, "05:15 AM", "06:47 PM", "Heavy Gale Warnings & Thundercloud Coast", true),
        "Rajshahi" to createWeather("Rajshahi", "Bangladesh", 36.8f, 42, 6f, "NW", 0.0f, "05:15 AM", "06:52 PM", "Dry Scorching Sunshine & Mild Heatwave", false),
        "Rangpur" to createWeather("Rangpur", "Bangladesh", 32.0f, 60, 9f, "E", 1.5f, "05:11 AM", "06:51 PM", "Partly Cloudy with Humid Breeze", false),
        "Bhola" to createWeather("Bhola", "Bangladesh", 26.8f, 95, 48f, "S", 85.0f, "05:15 AM", "06:46 PM", "High Waves & Tropical Flood Precedents", true),
        "Sunamganj" to createWeather("Sunamganj", "Bangladesh", 25.4f, 99, 14f, "NE", 110.0f, "05:07 AM", "06:45 PM", "Severe Flash Flood Waterlogging Warnings", false),
        "Noakhali" to createWeather("Noakhali", "Bangladesh", 27.5f, 90, 35f, "SSE", 64.0f, "05:14 AM", "06:45 PM", "Coastal Storm Surge Danger Signals", true),
        "Bagerhat" to createWeather("Bagerhat", "Bangladesh", 29.2f, 80, 22f, "SW", 18.0f, "05:17 AM", "06:48 PM", "Sundarbans Storm Protection Alert", false),

        // International Cities
        "London" to createWeather("London", "United Kingdom", 16.5f, 74, 18f, "W", 0.8f, "04:43 AM", "09:18 PM", "Passing Cool Shower & Wind Gradients", false),
        "New York" to createWeather("New York", "USA", 24.0f, 55, 12f, "NW", 0.0f, "05:24 AM", "08:29 PM", "Crisp Blue Sunny Afternoon", false),
        "Tokyo" to createWeather("Tokyo", "Japan", 22.8f, 82, 10f, "E", 4.5f, "04:25 AM", "06:57 PM", "Overcast Atmosphere & Light Rainy Mist", false),
        "Dubai" to createWeather("Dubai", "UAE", 41.2f, 25, 14f, "NE", 0.0f, "05:28 AM", "07:11 PM", "Intense Summer Sun & Dry Wind Dust", false),
        "Sydney" to createWeather("Sydney", "Australia", 14.0f, 68, 22f, "SW", 0.0f, "06:58 AM", "04:54 PM", "Brisk Autumn Wind & Sunny Interludes", false),
        "Singapore" to createWeather("Singapore", "Singapore", 29.5f, 84, 11f, "SSE", 12.0f, "06:58 AM", "07:05 PM", "Dynamic Equatorial Storm Cells", false)
    )

    val locations: List<String> = locationsMap.keys.toList()

    // --- State Observables ---

    private val _selectedLocationName = MutableStateFlow("Cox's Bazar")
    val selectedLocationName: StateFlow<String> = _selectedLocationName.asStateFlow()

    private val _selectedLocation = MutableStateFlow(locationsMap["Cox's Bazar"]!!)
    val selectedLocation: StateFlow<WeatherInfo> = _selectedLocation.asStateFlow()

    private val _gpsLocationName = MutableStateFlow("Cox's Bazar")
    val gpsLocationName: StateFlow<String> = _gpsLocationName.asStateFlow()

    private val _isGpsEnabled = MutableStateFlow(true)
    val isGpsEnabled: StateFlow<Boolean> = _isGpsEnabled.asStateFlow()

    // Satellite Controls
    private val _satelliteZoom = MutableStateFlow(1.2f)
    val satelliteZoom: StateFlow<Float> = _satelliteZoom.asStateFlow()

    private val _satellitePanX = MutableStateFlow(0f)
    val satellitePanX: StateFlow<Float> = _satellitePanX.asStateFlow()
    
    private val _satellitePanY = MutableStateFlow(0f)
    val satellitePanY: StateFlow<Float> = _satellitePanY.asStateFlow()

    // Warnings and interactive states
    val systemAlerts: StateFlow<List<SystemAlert>> = repository.allAlerts.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val userUploads: StateFlow<List<WeatherUpload>> = repository.allUploads.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _userPoints = MutableStateFlow(0)
    val userPoints: StateFlow<Int> = _userPoints.asStateFlow()

    private val _aiAdvisory = MutableStateFlow("")
    val aiAdvisory: StateFlow<String> = _aiAdvisory.asStateFlow()

    private val _isLoadingAdvisory = MutableStateFlow(false)
    val isLoadingAdvisory: StateFlow<Boolean> = _isLoadingAdvisory.asStateFlow()

    private val _tempUnitCelsius = MutableStateFlow(true)
    val tempUnitCelsius: StateFlow<Boolean> = _tempUnitCelsius.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(true)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    // Badges definitions
    val badgeList = listOf(
        Badge("b1", "Storm Chaser", "Upload 1 weather report (Gain 50 pts)", 50, "Thunderstorm", 0xFFE57373),
        Badge("b2", "Sentinel of the Coast", "Report active weather & gain 150 pts", 150, "Shield", 0xFF64B5F6),
        Badge("b3", "Safety Beacon", "Contribute warnings & hit 300 pts", 300, "CrisisAlert", 0xFFFFB74D),
        Badge("b4", "Cyclone Tracker Elite", "Recognized reporter at 500 pts", 500, "Radar", 0xFF81C784)
    )

    init {
        updatePoints()
        refreshAiAdvisory()
    }

    private fun updatePoints() {
        viewModelScope.launch {
            val pts = repository.getUserPoints()
            _userPoints.value = pts
        }
    }

    // --- Action Handlers ---

    fun selectLocation(name: String) {
        val weather = locationsMap[name]
        if (weather != null) {
            _selectedLocationName.value = name
            _selectedLocation.value = weather
            refreshAiAdvisory()
        }
    }

    fun toggleGps(enabled: Boolean) {
        _isGpsEnabled.value = enabled
        if (enabled) {
            // Revert selected weather to current simulated GPS location
            selectLocation(_gpsLocationName.value)
        }
    }

    fun updateSatelliteCoords(zoom: Float, panX: Float, panY: Float) {
        _satelliteZoom.value = zoom.coerceIn(0.5f, 3.5f)
        _satellitePanX.value = panX.coerceIn(-500f, 500f)
        _satellitePanY.value = panY.coerceIn(-500f, 500f)
    }

    fun resetSatellite() {
        _satelliteZoom.value = 1.2f
        _satellitePanX.value = 0f
        _satellitePanY.value = 0f
    }

    fun refreshAiAdvisory() {
        viewModelScope.launch {
            _isLoadingAdvisory.value = true
            _aiAdvisory.value = ""
            val district = _selectedLocationName.value
            val currentStatus = _selectedLocation.value.description
            
            val advisory = withContext(Dispatchers.IO) {
                GeminiWeatherClient.fetchEmergencyAdvisory(district, currentStatus)
            }
            _aiAdvisory.value = advisory
            _isLoadingAdvisory.value = false
        }
    }

    fun addLocalUpload(district: String, description: String, photoType: String, reporter: String) {
        viewModelScope.launch {
            val upload = WeatherUpload(
                district = district,
                description = description,
                photoType = photoType,
                reporterName = reporter.ifEmpty { "Community Reporter" }
            )
            repository.insertUpload(upload)
            updatePoints()
        }
    }

    fun upvoteUpload(id: Int) {
        viewModelScope.launch {
            repository.upvoteUpload(id)
            updatePoints()
        }
    }

    fun shareAlert(alert: SystemAlert) {
        viewModelScope.launch {
            repository.incrementShareCount(alert.id)
            updatePoints()
        }
    }

    fun toggleTempUnit() {
        _tempUnitCelsius.value = !_tempUnitCelsius.value
    }

    fun toggleNotifications(enabled: Boolean) {
        _notificationsEnabled.value = enabled
    }

    // Custom Simulation trigger to inject a Live Cyclone warning alert
    fun simulateExtremeWeatherWarning(severity: String, district: String, title: String, message: String) {
        viewModelScope.launch {
            val customAlert = SystemAlert(
                title = title,
                message = message,
                severity = severity,
                district = district
            )
            repository.insertAlert(customAlert)
            
            // Auto update selected weather to showcase warning in action
            val targetLoc = locationsMap[district]
            if (targetLoc != null) {
                val updatedLoc = targetLoc.copy(
                    description = "$title: $message",
                    isCycloneThreat = severity == "RED" || severity == "ORANGE",
                    rainfall = targetLoc.rainfall + 50.0f,
                    windSpeed = targetLoc.windSpeed + 35.0f
                )
                // Select to immediately notify user in Simulator
                _selectedLocationName.value = district
                _selectedLocation.value = updatedLoc
                refreshAiAdvisory()
            }
        }
    }

    // Prepopulate some realistic warning conditions for active monitoring
    private suspend fun setupPrepopulatedAlerts() {
        repository.insertAlert(
            SystemAlert(
                title = "Danger Signal No. 10 - Cyclone Mora",
                message = "Cyclone Mora intensifies to high-impact landfall. Coastlines of Cox's Bazar and Chittagong advised extreme evacuation.",
                severity = "RED",
                district = "Cox's Bazar"
            )
        )
        repository.insertAlert(
            SystemAlert(
                title = "Severe Flash Flood Warnings",
                message = "Upstream hill torrents trigger waterlogging risk in Sunamganj and Sylhet lowlands. Essential assets relocation recommended.",
                severity = "ORANGE",
                district = "Sunamganj"
            )
        )
        repository.insertAlert(
            SystemAlert(
                title = "Sundarbans Storm Surge",
                message = "High tide combined with strong convective wind warnings across Bagerhat and Bhola shorelines.",
                severity = "YELLOW",
                district = "Bhola"
            )
        )
    }

    companion object {
        private fun createWeather(
            name: String,
            country: String,
            baseTemp: Float,
            humidity: Int,
            windSpeed: Float,
            windDir: String,
            rainfall: Float,
            sunrise: String,
            sunset: String,
            desc: String,
            cyclone: Boolean
        ): WeatherInfo {
            // Generate some hourly conditions sequentially
            val hours = listOf("08:00 AM", "11:00 AM", "02:00 PM", "05:00 PM", "08:00 PM", "11:00 PM")
            val hourly = hours.mapIndexed { idx, hr ->
                val tempOffset = when(idx) {
                    0 -> -2f
                    1 -> 1f
                    2 -> 3f
                    3 -> 2f
                    4 -> -1f
                    5 -> -3f
                    else -> 0f
                }
                HourlyForecast(
                    time = hr,
                    temp = baseTemp + tempOffset,
                    condition = if (rainfall > 30f) "Heavy Storm" else if (rainfall > 5f) "Rain Shower" else "Passing Clouds",
                    rainProb = if (rainfall > 50f) 95 else if (rainfall > 5f) 75 else 20
                )
            }

            // Generate weekly forecasts
            val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            val weekly = days.mapIndexed { idx, d ->
                val waveOffset = (Math.sin(idx * 0.8) * 3).toInt()
                WeeklyForecast(
                    day = d,
                    tempMin = (baseTemp - 4 + waveOffset).toInt(),
                    tempMax = (baseTemp + 4 + waveOffset).toInt(),
                    condition = if (cyclone && idx == 0) "Cyclone Storm" else if (rainfall > 20f && idx < 3) "Monsoon Heavy Rain" else "Overcast Sky",
                    windSpeed = windSpeed + (Math.cos(idx.toDouble()) * 5).toFloat()
                )
            }

            return WeatherInfo(
                name = name,
                country = country,
                temp = baseTemp,
                humidity = humidity,
                windSpeed = windSpeed,
                windDir = windDir,
                rainfall = rainfall,
                sunrise = sunrise,
                sunset = sunset,
                description = desc,
                isCycloneThreat = cyclone,
                hourlyForecast = hourly,
                weeklyForecast = weekly
            )
        }
    }
}
