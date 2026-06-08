package com.example.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.SystemAlert
import com.example.data.WeatherUpload
import com.example.viewmodel.WeatherInfo
import com.example.viewmodel.WeatherViewModel
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow

// --- Custom Colors ---
object WeatherPalette {
    val DeepOcean = Color(0xFF0F1E36)
    val SeaSlate = Color(0xFF1E2D4A)
    val ActiveStormBlue = Color(0xFF3B5D9A)
    val GlowCyan = Color(0xFF29B6F6)
    val DangerRed = Color(0xFFEF5350)
    val AlertOrange = Color(0xFFFFA726)
    val WarningYellow = Color(0xFFFFD54F)
    val SunnyGold = Color(0xFFFFCA28)
    val SoftWhite = Color(0xFFF5F6F8)
    val DarkSurface = Color(0xFF121B2B)
    val CardBg = Color(0xFF1B263B)
    val NeonGreen = Color(0xFF66BB6A)
}

// --- Navigation Destinations ---
sealed class WeatherScreen(val route: String, val title: String, val icon: ImageVector) {
    object Home : WeatherScreen("home", "Live Weather", Icons.Default.Cloud)
    object Alerts : WeatherScreen("alerts", "Severe Alerts", Icons.Default.Warning)
    object Community : WeatherScreen("community", "Reports", Icons.Default.PhotoCamera)
    object Badges : WeatherScreen("badges", "Gamification", Icons.Default.EmojiEvents)
}

@Composable
fun WeatherApp(viewModel: WeatherViewModel) {
    val navController = rememberNavController()
    val isDarkMode by viewModel.notificationsEnabled.collectAsStateWithLifecycle() // mock notification dark style
    val snackbarHostState = remember { SnackbarHostState() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = WeatherPalette.DarkSurface
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                NavigationBar(
                    containerColor = WeatherPalette.DeepOcean,
                    tonalElevation = 8.dp,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route

                    val items = listOf(
                        WeatherScreen.Home,
                        WeatherScreen.Alerts,
                        WeatherScreen.Community,
                        WeatherScreen.Badges
                    )

                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.title,
                                    tint = if (currentRoute == screen.route) WeatherPalette.GlowCyan else Color.White.copy(0.6f)
                                )
                            },
                            label = {
                                Text(
                                    text = screen.title,
                                    fontSize = 11.sp,
                                    color = if (currentRoute == screen.route) WeatherPalette.GlowCyan else Color.White.copy(0.7f)
                                )
                            },
                            selected = currentRoute == screen.route,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = WeatherPalette.ActiveStormBlue
                            ),
                            modifier = Modifier.testTag("nav_item_${screen.route}")
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = WeatherScreen.Home.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(WeatherScreen.Home.route) {
                    HomeScreen(viewModel = viewModel, navController = navController)
                }
                composable(WeatherScreen.Alerts.route) {
                    AlertsScreen(viewModel = viewModel)
                }
                composable(WeatherScreen.Community.route) {
                    CommunityScreen(viewModel = viewModel)
                }
                composable(WeatherScreen.Badges.route) {
                    BadgesScreen(viewModel = viewModel)
                }
            }
        }
    }
}

// ==================== SCREEN 1: HOME SCREEN ====================

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: WeatherViewModel, navController: NavController) {
    val selectedLocName by viewModel.selectedLocationName.collectAsStateWithLifecycle()
    val weather by viewModel.selectedLocation.collectAsStateWithLifecycle()
    val isGpsEnabled by viewModel.isGpsEnabled.collectAsStateWithLifecycle()
    val tempUnitCelsius by viewModel.tempUnitCelsius.collectAsStateWithLifecycle()
    val aiAdvisory by viewModel.aiAdvisory.collectAsStateWithLifecycle()
    val isAdvisoryLoading by viewModel.isLoadingAdvisory.collectAsStateWithLifecycle()
    
    var showLocationPicker by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(WeatherPalette.DarkSurface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Branding / Location Selector Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "LIVE WEATHER ALERT",
                        style = MaterialTheme.typography.labelSmall,
                        color = WeatherPalette.GlowCyan,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Row(
                        modifier = Modifier
                            .clickable { showLocationPicker = true }
                            .testTag("location_picker_trigger"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedLocName,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select Location",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // C/F degree unit switcher tag
                    IconButton(
                        onClick = { viewModel.toggleTempUnit() },
                        modifier = Modifier
                            .background(WeatherPalette.SeaSlate, CircleShape)
                            .size(38.dp)
                            .testTag("temp_unit_toggle")
                    ) {
                        Text(
                            text = if (tempUnitCelsius) "°F" else "°C",
                            color = WeatherPalette.GlowCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // GPS simulation Toggle button
                    IconButton(
                        onClick = { viewModel.toggleGps(!isGpsEnabled) },
                        modifier = Modifier
                            .background(
                                if (isGpsEnabled) WeatherPalette.ActiveStormBlue else WeatherPalette.SeaSlate,
                                CircleShape
                            )
                            .size(38.dp)
                            .testTag("gps_toggle")
                    ) {
                        Icon(
                            imageVector = if (isGpsEnabled) Icons.Default.GpsFixed else Icons.Default.GpsOff,
                            contentDescription = "Simulate Location GPS",
                            tint = if (isGpsEnabled) WeatherPalette.GlowCyan else Color.White.copy(0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Location Drawer Dropdown List Dialogue-Overlay
        if (showLocationPicker) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = WeatherPalette.CardBg),
                    border = BorderStroke(1.dp, WeatherPalette.ActiveStormBlue)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Select Bangladesh District / Global Hub",
                            fontSize = 14.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            viewModel.locations.forEach { name ->
                                val isSelected = name == selectedLocName
                                Button(
                                    onClick = {
                                        viewModel.selectLocation(name)
                                        showLocationPicker = false
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSelected) WeatherPalette.GlowCyan else WeatherPalette.SeaSlate,
                                        contentColor = if (isSelected) Color.Black else Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text(text = name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Primary Weather Display block
        item {
            MainWeatherDisplay(weather = weather, tempCelsius = tempUnitCelsius)
        }

        // Live Satellite view with zoom pan gestures
        item {
            SatelliteSimulatorView(viewModel = viewModel)
        }

        // Gemini AI emergency action guidelines
        item {
            AIAgencyWarningView(
                location = selectedLocName,
                advisory = aiAdvisory,
                isLoading = isAdvisoryLoading,
                onRefresh = { viewModel.refreshAiAdvisory() }
            )
        }

        // Hourly Temperature trend visual line-graph canvas
        item {
            Text(
                text = "Hourly Temperature Curve (24 Hours)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))
            HourlyTrendCanvas(weather = weather)
        }

        // 7-day extended forecasts
        item {
            Text(
                text = "7-Day Pre-emptive Warning Forecast",
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))
            WeeklyForecastRow(weather = weather)
        }
    }
}

@Composable
fun MainWeatherDisplay(weather: WeatherInfo, tempCelsius: Boolean) {
    val displayTemp = if (tempCelsius) weather.temp else (weather.temp * 1.8f + 32f)
    val formattedTemp = String.format("%.1f", displayTemp)
    val unitSymbol = if (tempCelsius) "°C" else "°F"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("main_weather_display"),
        colors = CardDefaults.cardColors(containerColor = WeatherPalette.CardBg),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Threat Badge if appropriate
            if (weather.isCycloneThreat) {
                Row(
                    modifier = Modifier
                        .background(WeatherPalette.DangerRed, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Threat",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "ACTIVE CYCLONE DANGER SYSTEM",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Weather Animation Canvas (Static representations + beautiful vector offsets)
                AnimatedWeatherArt(condition = weather.description)

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$formattedTemp$unitSymbol",
                        fontSize = 46.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Text(
                        text = weather.description,
                        fontSize = 14.sp,
                        color = WeatherPalette.GlowCyan,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.End
                    )
                    Text(
                        text = "Barometric Humid Levels: ${weather.humidity}%",
                        fontSize = 10.sp,
                        color = Color.White.copy(0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = WeatherPalette.ActiveStormBlue, thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Stats Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    imageVector = Icons.Default.Air,
                    label = "Wind Speed",
                    value = "${weather.windSpeed} km/h (${weather.windDir})"
                )
                StatItem(
                    imageVector = Icons.Default.Umbrella,
                    label = "Rainfall",
                    value = "${weather.rainfall} mm"
                )
                StatItem(
                    imageVector = Icons.Default.WbSunny,
                    label = "Suns",
                    value = "${weather.sunrise}"
                )
            }
        }
    }
}

@Composable
fun StatItem(imageVector: ImageVector, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = imageVector,
            contentDescription = label,
            tint = WeatherPalette.GlowCyan,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, fontSize = 9.sp, color = Color.White.copy(0.5f))
        Text(text = value, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AnimatedWeatherArt(condition: String) {
    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = java.lang.Float.isNaN(0f).let { tween(6000, easing = LinearEasing) }
        )
    )

    val translationY by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = SineIntensityEasing()),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = Modifier
            .size(80.dp)
            .offset(y = translationY.dp),
        contentAlignment = Alignment.Center
    ) {
        if (condition.contains("Cyclone", true) || condition.contains("Storm", true)) {
            // Rotating swirling storm spiral
            Canvas(modifier = Modifier.size(70.dp).rotate(rotation)) {
                val center = Offset(size.width / 2, size.height / 2)
                for (i in 0..3) {
                    rotate(i * 90f) {
                        drawArc(
                            color = WeatherPalette.GlowCyan,
                            startAngle = 0f,
                            sweepAngle = 120f,
                            useCenter = false,
                            style = Stroke(width = 6f, cap = StrokeCap.Round)
                        )
                        drawCircle(
                            color = WeatherPalette.DangerRed,
                            radius = 6f,
                            center = Offset(center.x + 18f, center.y - 12f)
                        )
                    }
                }
                drawCircle(
                    color = Color.White,
                    radius = 8f,
                    center = center
                )
            }
        } else if (condition.contains("Rain", true) || condition.contains("Shower", true)) {
            // Rainfall cloud simulation drawing
            Canvas(modifier = Modifier.size(65.dp)) {
                // Cloud outline
                drawRoundRect(
                    color = Color.White.copy(0.8f),
                    topLeft = Offset(10f, 20f),
                    size = Size(45f, 25f),
                    cornerRadius = CornerRadius(12f, 12f)
                )
                // Droplets lines
                val animDropletOffset = (rotation % 30f) / 30f * 15f
                drawLine(
                    color = WeatherPalette.GlowCyan,
                    start = Offset(20f, 48f + animDropletOffset),
                    end = Offset(16f, 56f + animDropletOffset),
                    strokeWidth = 3f
                )
                drawLine(
                    color = WeatherPalette.GlowCyan,
                    start = Offset(35f, 48f + animDropletOffset),
                    end = Offset(31f, 56f + animDropletOffset),
                    strokeWidth = 3f
                )
            }
        } else {
            // Glow solar sun rays
            Canvas(modifier = Modifier.size(65.dp).rotate(rotation)) {
                val center = Offset(size.width / 2, size.height / 2)
                drawCircle(
                    color = WeatherPalette.SunnyGold,
                    radius = 16f,
                    center = center
                )
                for (ray in 0..7) {
                    rotate(ray * 45f) {
                        drawLine(
                            color = WeatherPalette.SunnyGold.copy(0.8f),
                            start = Offset(center.x, center.y - 22f),
                            end = Offset(center.x, center.y - 30f),
                            strokeWidth = 3f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }
    }
}

fun SineIntensityEasing(): Easing = Easing { fraction ->
    sin(fraction * Math.PI).toFloat()
}

// ==================== LIVE SATELLITE CANVAS CONTROLLER ====================

@Composable
fun SatelliteSimulatorView(viewModel: WeatherViewModel) {
    val zoom by viewModel.satelliteZoom.collectAsStateWithLifecycle()
    val panX by viewModel.satellitePanX.collectAsStateWithLifecycle()
    val panY by viewModel.satellitePanY.collectAsStateWithLifecycle()
    
    val infiniteTransition = rememberInfiniteTransition()
    val cloudRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing)
        )
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("satellite_view"),
        colors = CardDefaults.cardColors(containerColor = WeatherPalette.DeepOcean),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, WeatherPalette.ActiveStormBlue)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Satellite,
                        contentDescription = "Satellite Mode",
                        tint = WeatherPalette.GlowCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LIVE SATELLITE SIMULATOR",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Row {
                    Text(
                        text = "Zoom: ${String.format("%.1fx", zoom)}",
                        fontSize = 11.sp,
                        color = Color.White.copy(0.7f),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Button(
                        onClick = { viewModel.resetSatellite() },
                        colors = ButtonDefaults.buttonColors(containerColor = WeatherPalette.SeaSlate),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(20.dp)
                            .testTag("reset_satellite")
                    ) {
                        Text(text = "Reset", fontSize = 9.sp, color = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Interactive Map Canvas Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF070F1E))
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoomAmount, _ ->
                            viewModel.updateSatelliteCoords(
                                zoom = zoom * zoomAmount,
                                panX = panX + pan.x,
                                panY = panY + pan.y
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Drawing guidelines / grid coords
                    val gridOpacity = 0.15f
                    for (x in 0..w.toInt() step 50) {
                        drawLine(
                            color = WeatherPalette.GlowCyan.copy(gridOpacity),
                            start = Offset(x.toFloat(), 0f),
                            end = Offset(x.toFloat(), h)
                        )
                    }
                    for (y in 0..h.toInt() step 50) {
                        drawLine(
                            color = WeatherPalette.GlowCyan.copy(gridOpacity),
                            start = Offset(0f, y.toFloat()),
                            end = Offset(w, y.toFloat())
                        )
                    }

                    // Draw concentric radar lines
                    drawCircle(
                        color = WeatherPalette.GlowCyan.copy(0.08f),
                        radius = h / 2,
                        center = Offset(w / 2, h / 2),
                        style = Stroke(width = 2f)
                    )
                    drawCircle(
                        color = WeatherPalette.GlowCyan.copy(0.04f),
                        radius = h / 4,
                        center = Offset(w / 2, h / 2),
                        style = Stroke(width = 1f)
                    )

                    // Draw abstract Coastline outline representing Bangladesh and Bay of Bengal!
                    // Apply zoom & pan matrix translations
                    withTransform({
                        translate(left = panX, top = panY)
                        scale(scaleX = zoom, scaleY = zoom, pivot = Offset(w / 2, h / 2))
                    }) {
                        // Drawing abstract Bangladesh coastal paths
                        val linePaint = Paint().apply {
                            color = Color(0xFF425675)
                            style = PaintingStyle.Stroke
                            strokeWidth = 3f * (1 / zoom)
                        }
                        
                        val path = Path().apply {
                            // Sundarbans left side
                            moveTo(w * 0.2f, h * 0.3f)
                            lineTo(w * 0.25f, h * 0.4f)
                            lineTo(w * 0.35f, h * 0.42f)
                            // Hatiya, Bhola river delta bends
                            lineTo(w * 0.43f, h * 0.38f)
                            quadraticTo(w * 0.46f, h * 0.45f, w * 0.5f, h * 0.41f)
                            // Chittagong coast down to Cox's Bazar
                            lineTo(w * 0.58f, h * 0.45f)
                            lineTo(w * 0.65f, h * 0.65f)
                            lineTo(w * 0.7f, h * 0.8f) // Saint Martins
                        }
                        drawPath(path = path, color = Color(0xFF64B5F6), style = Stroke(width = 3f))

                        // Draw Bangladesh Capital Spot (Dhaka)
                        drawCircle(
                            color = Color.Yellow,
                            radius = 5f,
                            center = Offset(w * 0.45f, h * 0.28f)
                        )
                        
                        // Draw Cyclone Swirling patterns on Bay of Bengal (Lower center)
                        rotate(degrees = cloudRotation, pivot = Offset(w * 0.48f, h * 0.62f)) {
                            // Drawn cyclone spiral loops using Canvas drawing arcs
                            val cycloneCenter = Offset(w * 0.48f, h * 0.62f)
                            for (radius in 15..95 step 15) {
                                drawArc(
                                    color = Color.White.copy(0.25f),
                                    startAngle = (radius * 3).toFloat(),
                                    sweepAngle = 130f,
                                    useCenter = false,
                                    topLeft = Offset(cycloneCenter.x - radius, cycloneCenter.y - radius),
                                    size = Size(radius * 2f, radius * 2f),
                                    style = Stroke(width = 8f)
                                )
                            }
                            // Deep storm red center eye
                            drawCircle(
                                color = WeatherPalette.DangerRed,
                                radius = 7f,
                                center = cycloneCenter
                            )
                        }
                    }
                }

                // Map Indicator Tags Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Column(
                        modifier = Modifier
                            .background(Color.Black.copy(0.6f), RoundedCornerShape(4.dp))
                            .padding(6.dp)
                    ) {
                        Text(text = "SATELLITE SECTOR: 90.2°E - Bay of Bengal", fontSize = 8.sp, color = Color.White)
                        Text(text = "CY-TRACKING: Hurricane Active (Cat-2)", fontSize = 8.sp, color = WeatherPalette.GlowCyan)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "💡 Tap, drag, or pinch map canvas container to inspect active cloud bands.",
                    fontSize = 10.sp,
                    color = Color.White.copy(0.6f)
                )
            }
        }
    }
}

// ==================== EMERGENCY AI WARNING SECTION ====================

@Composable
fun AIAgencyWarningView(location: String, advisory: String, isLoading: Boolean, onRefresh: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_advisory"),
        colors = CardDefaults.cardColors(containerColor = WeatherPalette.CardBg),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, WeatherPalette.GlowCyan.copy(0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SmartButton,
                        contentDescription = "AI Intel",
                        tint = WeatherPalette.SunnyGold,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "GEMINI PRE-EMPTIVE INTELLIGENCE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = WeatherPalette.GlowCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = WeatherPalette.GlowCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Analyzing regional climate forecasts...",
                        fontSize = 12.sp,
                        color = Color.White.copy(0.7f)
                    )
                }
            } else {
                Text(
                    text = advisory,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = WeatherPalette.SoftWhite,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = WeatherPalette.ActiveStormBlue, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AI advisory target: $location district",
                    fontSize = 9.sp,
                    color = Color.White.copy(0.5f)
                )
                Text(
                    text = "Powered by Gemini 3.5-Flash",
                    fontSize = 9.sp,
                    color = WeatherPalette.GlowCyan,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ==================== HOURLY TEMPLATE LINE GRAPH ====================

@Composable
fun HourlyTrendCanvas(weather: WeatherInfo) {
    Card(
        modifier = Modifier,
        colors = CardDefaults.cardColors(containerColor = WeatherPalette.DeepOcean),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Draw custom temperatures trends line graph using Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                val dx = size.width / (weather.hourlyForecast.size - 1)
                val padding = 15f
                val h = size.height - (padding * 2)

                val temps = weather.hourlyForecast.map { it.temp }
                val minT = temps.minOrNull() ?: 20f
                val maxT = temps.maxOrNull() ?: 35f
                val deltaT = if (maxT == minT) 1f else (maxT - minT)

                val points = weather.hourlyForecast.mapIndexed { idx, hr ->
                    val x = idx * dx
                    val normalizedY = (hr.temp - minT) / deltaT
                    val y = size.height - padding - (normalizedY * h)
                    Offset(x, y)
                }

                // Draw filled gradient graph background path
                val gradientPath = Path().apply {
                    moveTo(0f, size.height)
                    points.forEach { lineTo(it.x, it.y) }
                    lineTo(size.width, size.height)
                    close()
                }
                drawPath(
                    path = gradientPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(WeatherPalette.GlowCyan.copy(0.3f), Color.Transparent)
                    )
                )

                // Draw temperature curve stroke
                val path = Path().apply {
                    moveTo(points[0].x, points[0].y)
                    for (i in 1 until points.size) {
                        lineTo(points[i].x, points[i].y)
                    }
                }
                drawPath(
                    path = path,
                    color = WeatherPalette.GlowCyan,
                    style = Stroke(width = 4f, cap = StrokeCap.Round)
                )

                // Highlight dot points with temperature string text labels on Canvas
                points.forEachIndexed { idx, pt ->
                    drawCircle(
                        color = Color.White,
                        radius = 4f,
                        center = pt
                    )
                    drawCircle(
                        color = WeatherPalette.ActiveStormBlue,
                        radius = 2f,
                        center = pt
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Forecast info tags
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                items(weather.hourlyForecast) { hr ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    ) {
                        Text(text = hr.time.replace(" 0", " "), fontSize = 10.sp, color = Color.White.copy(0.6f))
                        Text(text = "${hr.temp.toInt()}°", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(text = "${hr.rainProb}% rain", fontSize = 9.sp, color = WeatherPalette.GlowCyan)
                    }
                }
            }
        }
    }
}

// ==================== WEEKLY 7-DAY EXTENDED METRICS ====================

@Composable
fun WeeklyForecastRow(weather: WeatherInfo) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(weather.weeklyForecast) { item ->
            Card(
                modifier = Modifier.width(110.dp),
                colors = CardDefaults.cardColors(containerColor = WeatherPalette.CardBg)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = item.day, fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White)
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // Simple forecast visual representation icon
                    Icon(
                        imageVector = if (item.condition.contains("Cyclone")) Icons.Default.Cyclone 
                                      else if (item.condition.contains("Rain")) Icons.Default.Thunderstorm 
                                      else Icons.Default.CloudQueue,
                        contentDescription = "Forecast",
                        tint = if (item.condition.contains("Cyclone")) WeatherPalette.DangerRed else WeatherPalette.GlowCyan,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${item.tempMin}° / ${item.tempMax}°",
                        fontSize = 11.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = item.condition,
                        fontSize = 8.sp,
                        color = Color.White.copy(0.6f),
                        textAlign = TextAlign.Center,
                        lineHeight = 10.sp,
                        maxLines = 2
                    )
                }
            }
        }
    }
}

// ==================== SCREEN 2: ALERTS SCREEN (With Simulation & Stories Generator) ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(viewModel: WeatherViewModel) {
    val alerts by viewModel.systemAlerts.collectAsStateWithLifecycle()
    val listLocations = viewModel.locations
    
    // Dynamic story warning generator overlay dialog state
    var selectedAlertForSharing by remember { mutableStateOf<SystemAlert?>(null) }
    var shareSucceedPointsPopup by remember { mutableStateOf(false) }

    // Simulation fields
    var simDistrict by remember { mutableStateOf("Cox's Bazar") }
    var simSeverity by remember { mutableStateOf("RED") }
    var simTitle by remember { mutableStateOf("Cyclone Mora Upgrade") }
    var simMessage by remember { mutableStateOf("Maximum coastal wave surge registered above 12 feet.") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(WeatherPalette.DarkSurface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "EMERGENCY SEVERE WEATHER WARNINGS",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "Color-coded official weather alert signals synchronized dynamically.",
                fontSize = 11.sp,
                color = Color.White.copy(0.6f)
            )
        }

        // List Alerts
        items(alerts) { alert ->
            val colorIndicator = when(alert.severity) {
                "RED" -> WeatherPalette.DangerRed
                "ORANGE" -> WeatherPalette.AlertOrange
                else -> WeatherPalette.WarningYellow
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("alert_bar_${alert.severity}"),
                colors = CardDefaults.cardColors(containerColor = WeatherPalette.CardBg),
                border = BorderStroke(1.dp, colorIndicator.copy(0.5f))
            ) {
                Column {
                    // Color bar indicator at top of card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(colorIndicator)
                    )

                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SIGNAL INDEX: ${alert.severity}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorIndicator
                            )
                            Text(
                                text = "Sector: ${alert.district}",
                                fontSize = 10.sp,
                                color = Color.White.copy(0.6f)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = alert.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = alert.message,
                            fontSize = 12.sp,
                            color = WeatherPalette.SoftWhite,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Divider(color = WeatherPalette.ActiveStormBlue, thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(8.dp))

                        // Viral Snapshot sharing buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Shares count",
                                    tint = WeatherPalette.GlowCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${alert.sharedCount} Viral Shares",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(0.5f)
                                )
                            }

                            Button(
                                onClick = { selectedAlertForSharing = alert },
                                colors = ButtonDefaults.buttonColors(containerColor = WeatherPalette.ActiveStormBlue),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(28.dp).testTag("viral_share_trigger")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Stars, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.Black)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "Generate Share Story (+25 pts)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Pre-emptive Severe simulation controls
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("warning_sim_dashboard"),
                colors = CardDefaults.cardColors(containerColor = WeatherPalette.DeepOcean),
                border = BorderStroke(1.dp, WeatherPalette.ActiveStormBlue)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🛠️ CYCLONE WARNING SIMULATION DESK",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Inject live severe weather variables into selected districts and award public status.",
                        fontSize = 10.sp,
                        color = Color.White.copy(0.6f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Pick district
                    Text(text = "Target District:", fontSize = 11.sp, color = WeatherPalette.GlowCyan)
                    var expandedDistrictDropdown by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { expandedDistrictDropdown = true },
                            colors = ButtonDefaults.buttonColors(containerColor = WeatherPalette.SeaSlate),
                            modifier = Modifier.fillMaxWidth().height(36.dp)
                        ) {
                            Text(text = simDistrict, fontSize = 12.sp)
                        }
                        DropdownMenu(
                            expanded = expandedDistrictDropdown,
                            onDismissRequest = { expandedDistrictDropdown = false }
                        ) {
                            listLocations.take(10).forEach { loc ->
                                DropdownMenuItem(
                                    text = { Text(loc) },
                                    onClick = {
                                        simDistrict = loc
                                        expandedDistrictDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Pick Severity level
                    Text(text = "Severity Bar Indicator:", fontSize = 11.sp, color = WeatherPalette.GlowCyan)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("RED", "ORANGE", "YELLOW").forEach { lvl ->
                            val isSelected = lvl == simSeverity
                            Button(
                                onClick = { simSeverity = lvl },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) {
                                        when(lvl) {
                                            "RED" -> WeatherPalette.DangerRed
                                            "ORANGE" -> WeatherPalette.AlertOrange
                                            else -> WeatherPalette.WarningYellow
                                        }
                                    } else WeatherPalette.SeaSlate,
                                    contentColor = if (isSelected) Color.Black else Color.White
                                ),
                                modifier = Modifier.weight(1f).height(32.dp)
                            ) {
                                Text(lvl, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Input title and description
                    Text(text = "Custom Threat Title:", fontSize = 11.sp, color = Color.White)
                    TextField(
                        value = simTitle,
                        onValueChange = { simTitle = it },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Custom Threat Warning Message:", fontSize = 11.sp, color = Color.White)
                    TextField(
                        value = simMessage,
                        onValueChange = { simMessage = it },
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            viewModel.simulateExtremeWeatherWarning(
                                severity = simSeverity,
                                district = simDistrict,
                                title = simTitle,
                                message = simMessage
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WeatherPalette.DangerRed),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .testTag("simulate_alert_btn")
                    ) {
                        Text(text = "Broadcast Simulated Warning", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Modal Stories Card dialog generator
    if (selectedAlertForSharing != null) {
        val activeAlert = selectedAlertForSharing!!
        AlertDialog(
            onDismissRequest = { selectedAlertForSharing = null },
            containerColor = Color(0xFF0C1423),
            title = {
                Text(
                    text = "Generated Share Story overlay",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                // Instantly rendered Instagram/Facebook visual template card with Canvas effects
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WeatherPalette.CardBg, RoundedCornerShape(12.dp))
                        .border(2.dp, WeatherPalette.GlowCyan, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(45.dp)
                            .background(WeatherPalette.DangerRed, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cyclone,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "LIVE REGIONAL EVACUALLY REPORT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeatherPalette.GlowCyan
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .background(Color.Red.copy(0.1f), RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "CRITICAL CO_ORDINATES IN SECURITY DANGER",
                                fontSize = 9.sp,
                                color = WeatherPalette.DangerRed,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = activeAlert.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Region: ${activeAlert.district}, Bangladesh",
                                fontSize = 11.sp,
                                color = Color.White.copy(0.7f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = activeAlert.message,
                        fontSize = 10.sp,
                        color = Color.White,
                        lineHeight = 14.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "📲 Scan QR to open Live Weather Alert Tracker",
                        fontSize = 8.sp,
                        color = Color.White.copy(0.4f)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.shareAlert(activeAlert)
                        selectedAlertForSharing = null
                        shareSucceedPointsPopup = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WeatherPalette.GlowCyan)
                ) {
                    Text("Share To Social Stories (+25 PTS)", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedAlertForSharing = null }) {
                    Text("Close", color = Color.White)
                }
            }
        )
    }

    if (shareSucceedPointsPopup) {
        val context = LocalContext.current
        LaunchedEffect(Unit) {
            Toast.makeText(context, "Viral snapshot shared! +25 points awarded!", Toast.LENGTH_LONG).show()
            shareSucceedPointsPopup = false
        }
    }
}

// ==================== SCREEN 3: COMMUNITY PHOTOS UPLOAD LOG ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(viewModel: WeatherViewModel) {
    val uploads by viewModel.userUploads.collectAsStateWithLifecycle()
    val availableDistricts = viewModel.locations

    var showUploadDialog by remember { mutableStateOf(false) }

    // Upload Fields
    var inputDistrict by remember { mutableStateOf("Cox's Bazar") }
    var inputDesc by remember { mutableStateOf("Tidal wave spikes breaching the concrete embankment near Dolphin Mor.") }
    var inputPhotoType by remember { mutableStateOf("stormy") } // sunny, stormy, cloudy, cq_satellite
    var inputReporter by remember { mutableStateOf("Morshed") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(WeatherPalette.DarkSurface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Card header Upload Action
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = WeatherPalette.DeepOcean)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "COMMUNITY REPORTING FORUM",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Upload real-time weather logs and receive badges, community points.",
                            fontSize = 11.sp,
                            color = Color.White.copy(0.6f)
                        )
                    }

                    Button(
                        onClick = { showUploadDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = WeatherPalette.GlowCyan),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("upload_weather_report_trigger")
                    ) {
                        Icon(imageVector = Icons.Default.AddAPhoto, contentDescription = null, tint = Color.Black)
                    }
                }
            }
        }

        item {
            Text(
                text = "COMMUNITY GROUND FEED LOG",
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
        }

        if (uploads.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = WeatherPalette.CardBg)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            tint = Color.White.copy(0.4f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "No community photos posted yet.", fontSize = 12.sp, color = Color.White.copy(0.5f))
                        Text(text = "Be the first coastal sentinel to upload local status!", fontSize = 10.sp, color = WeatherPalette.GlowCyan)
                    }
                }
            }
        }

        // List Uploaded Reports
        items(uploads) { item ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("upload_report_card"),
                colors = CardDefaults.cardColors(containerColor = WeatherPalette.CardBg)
            ) {
                Column {
                    // Predefined aesthetic weather Canvas sketch acting as "User Weather Photo"!
                    WeatherSkyCanvasView(type = item.photoType)

                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = WeatherPalette.GlowCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = item.reporterName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = item.district,
                                fontSize = 11.sp,
                                color = WeatherPalette.GlowCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = item.description,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = WeatherPalette.SoftWhite
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = WeatherPalette.ActiveStormBlue, thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { viewModel.upvoteUpload(item.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = WeatherPalette.SeaSlate),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp).testTag("upvote_report_btn")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ThumbUp,
                                        contentDescription = "Upvote",
                                        tint = WeatherPalette.GlowCyan,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = "Verify Report (${item.upvotes})", fontSize = 10.sp, color = Color.White)
                                }
                            }

                            Text(
                                text = "Coastal verified status",
                                fontSize = 9.sp,
                                color = Color.White.copy(0.4f)
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal dialog to let users upload
    if (showUploadDialog) {
        AlertDialog(
            onDismissRequest = { showUploadDialog = false },
            containerColor = Color(0xFF0C1423),
            title = {
                Text(
                    text = "Upload Local Spotter Report",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(text = "Reporter Identity:", fontSize = 11.sp, color = Color.White)
                    TextField(
                        value = inputReporter,
                        onValueChange = { inputReporter = it },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    )

                    // Pick District
                    Text(text = "Target District:", fontSize = 11.sp, color = Color.White)
                    var expandedUploadFieldDropdown by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { expandedUploadFieldDropdown = true },
                            colors = ButtonDefaults.buttonColors(containerColor = WeatherPalette.SeaSlate),
                            modifier = Modifier.fillMaxWidth().height(36.dp)
                        ) {
                            Text(text = inputDistrict, fontSize = 12.sp)
                        }
                        DropdownMenu(
                            expanded = expandedUploadFieldDropdown,
                            onDismissRequest = { expandedUploadFieldDropdown = false }
                        ) {
                            availableDistricts.take(10).forEach { loc ->
                                DropdownMenuItem(
                                    text = { Text(loc) },
                                    onClick = {
                                        inputDistrict = loc
                                        expandedUploadFieldDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    // Pick sky Canvas type image simulation
                    Text(text = "Weather Sky Visual:", fontSize = 11.sp, color = Color.White)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "stormy" to "⛈️ Storm",
                            "sunny" to "☀️ Sun",
                            "cloudy" to "☁️ Cloud",
                            "cq_satellite" to "🛰️ Radar"
                        ).forEach { (type, label) ->
                            val isSelected = type == inputPhotoType
                            Button(
                                onClick = { inputPhotoType = type },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) WeatherPalette.GlowCyan else WeatherPalette.SeaSlate,
                                    contentColor = if (isSelected) Color.Black else Color.White
                                ),
                                modifier = Modifier.weight(1f).height(30.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Text(text = "Write description of local conditions:", fontSize = 11.sp, color = Color.White)
                    TextField(
                        value = inputDesc,
                        onValueChange = { inputDesc = it },
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addLocalUpload(
                            district = inputDistrict,
                            description = inputDesc,
                            photoType = inputPhotoType,
                            reporter = inputReporter
                        )
                        showUploadDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WeatherPalette.GlowCyan)
                ) {
                    Text("Publish to Ground (+50 PTS)", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUploadDialog = false }) {
                    Text("Close", color = Color.White)
                }
            }
        )
    }
}

@Composable
fun WeatherSkyCanvasView(type: String) {
    // Elegant Canvas background simulating weather condition photo
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
    ) {
        val w = size.width
        val h = size.height

        when(type) {
            "stormy" -> {
                // Flashy dark lightning rain Canvas representation
                drawRect(
                    brush = Brush.verticalGradient(listOf(Color(0xFF0D1422), Color(0xFF263238)))
                )
                
                // Lightning bolt jagged lines
                drawLine(
                    color = WeatherPalette.WarningYellow,
                    start = Offset(w * 0.4f, 0f),
                    end = Offset(w * 0.45f, h * 0.5f),
                    strokeWidth = 4f
                )
                drawLine(
                    color = WeatherPalette.WarningYellow,
                    start = Offset(w * 0.45f, h * 0.5f),
                    end = Offset(w * 0.38f, h * 0.45f),
                    strokeWidth = 4f
                )
                drawLine(
                    color = WeatherPalette.WarningYellow,
                    start = Offset(w * 0.38f, h * 0.45f),
                    end = Offset(w * 0.46f, h * 0.9f),
                    strokeWidth = 4f
                )

                // Rain drops
                for (i in 0..15) {
                    val rx = (i * 61) % w
                    val ry = (i * 12) % h
                    drawLine(
                        color = Color.Cyan.copy(0.4f),
                        start = Offset(rx, ry),
                        end = Offset(rx - 10f, ry + 15f),
                        strokeWidth = 2f
                    )
                }
            }
            "sunny" -> {
                // Beautiful blue yellow sunshine Canvas
                drawRect(
                    brush = Brush.verticalGradient(listOf(Color(0xFF81D4FA), Color(0xFFB3E5FC)))
                )
                // Draw warm solar core with radial waves
                drawCircle(
                    color = WeatherPalette.SunnyGold,
                    radius = 32f,
                    center = Offset(w * 0.5f, h * 0.5f)
                )
                drawCircle(
                    color = WeatherPalette.SunnyGold.copy(0.3f),
                    radius = 50f,
                    center = Offset(w * 0.5f, h * 0.5f)
                )
            }
            "cloudy" -> {
                // Gray skies
                drawRect(
                    brush = Brush.verticalGradient(listOf(Color(0xFF546E7A), Color(0xFFECEFF1)))
                )
                // Draw layered puffy clouds blobs on Canvas
                drawRoundRect(
                    color = Color.White.copy(0.6f),
                    topLeft = Offset(w * 0.15f, h * 0.3f),
                    size = Size(100f, 40f),
                    cornerRadius = CornerRadius(20f, 20f)
                )
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(w * 0.35f, h * 0.4f),
                    size = Size(140f, 50f),
                    cornerRadius = CornerRadius(25f, 25f)
                )
            }
            else -> {
                // Radar green sweeps
                drawRect(color = Color.Black)
                for (rad in 20..110 step 30) {
                    drawCircle(
                        color = WeatherPalette.NeonGreen.copy(0.3f),
                        radius = rad.toFloat(),
                        center = Offset(w / 2, h / 2),
                        style = Stroke(width = 2f)
                    )
                }
                drawLine(
                    color = WeatherPalette.NeonGreen,
                    start = Offset(w / 2, h / 2),
                    end = Offset(w / 2 + 80f, h / 2 - 80f),
                    strokeWidth = 3f
                )
            }
        }
    }
}

// ==================== SCREEN 4: BADGES & SETTINGS GAMIFICATION PROGRESS ====================

@Composable
fun BadgesScreen(viewModel: WeatherViewModel) {
    val totalPoints by viewModel.userPoints.collectAsStateWithLifecycle()
    val isNotificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(WeatherPalette.DarkSurface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Points visualizer dashboard card with progress indicator
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = WeatherPalette.CardBg)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "YOUR COMMUNITY REPORTER METRICS",
                        style = MaterialTheme.typography.labelSmall,
                        color = WeatherPalette.GlowCyan,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$totalPoints Total Points Accumulated",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // Progress bar representing level thresholds
                    val nextLevelPoints = when {
                        totalPoints < 50 -> 50
                        totalPoints < 150 -> 150
                        totalPoints < 300 -> 300
                        else -> 500
                    }
                    val currentProgressPercentage = (totalPoints.toFloat() / nextLevelPoints.toFloat()).coerceIn(0f, 1f)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Current Level Tracking", fontSize = 10.sp, color = Color.White.copy(0.6f))
                        Text(text = "Next Badge Target: $nextLevelPoints pts", fontSize = 10.sp, color = WeatherPalette.GlowCyan)
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = currentProgressPercentage,
                        color = WeatherPalette.GlowCyan,
                        trackColor = WeatherPalette.SeaSlate,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }
        }

        item {
            Text(
                text = "COMMUNITY RECOGNITION BADGES",
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
        }

        // List Grid of Badges
        items(viewModel.badgeList) { b ->
            val unlocked = totalPoints >= b.pointsNeeded

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("badge_item"),
                colors = CardDefaults.cardColors(
                    containerColor = if (unlocked) WeatherPalette.CardBg else WeatherPalette.CardBg.copy(0.4f)
                ),
                border = BorderStroke(1.dp, if (unlocked) Color(b.colorHex) else Color.White.copy(0.1f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(45.dp)
                            .background(
                                color = if (unlocked) Color(b.colorHex).copy(0.2f) else Color.White.copy(0.1f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when(b.iconName) {
                                "Thunderstorm" -> Icons.Default.Thunderstorm
                                "Shield" -> Icons.Default.Shield
                                "CrisisAlert" -> Icons.Default.Emergency
                                else -> Icons.Default.Radar
							},
                            contentDescription = b.title,
                            tint = if (unlocked) Color(b.colorHex) else Color.White.copy(0.4f),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = b.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (unlocked) Color.White else Color.White.copy(0.4f)
                        )
                        Text(
                            text = b.criteria,
                            fontSize = 10.sp,
                            color = Color.White.copy(0.5f)
                        )
                    }

                    if (unlocked) {
                        Text(
                            text = "UNLOCKED",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = WeatherPalette.NeonGreen,
                            modifier = Modifier
                                .background(WeatherPalette.NeonGreen.copy(0.1f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    } else {
                        Text(
                            text = "LOCKED",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(0.4f),
                            modifier = Modifier
                                .background(Color.White.copy(0.05f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // App Settings configuration items
        item {
            Text(
                text = "UTILITY PREFERENCES",
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = WeatherPalette.DeepOcean)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Real-time Warning Notifications", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(text = "Alert immediately on local system spikes", fontSize = 10.sp, color = Color.White.copy(0.6f))
                        }
                        Switch(
                            checked = isNotificationsEnabled,
                            onCheckedChange = { viewModel.toggleNotifications(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = WeatherPalette.GlowCyan),
                            modifier = Modifier.testTag("notification_settings_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Failsafe Cache Mode", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(text = "Display standard offline charts during grid disconnects", fontSize = 10.sp, color = Color.White.copy(0.6f))
                        }
                        Switch(
                            checked = true,
                            onCheckedChange = {},
                            enabled = false,
                            colors = SwitchDefaults.colors(checkedThumbColor = WeatherPalette.GlowCyan)
                        )
                    }
                }
            }
        }
    }
}
