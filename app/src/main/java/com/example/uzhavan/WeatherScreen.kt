package com.example.uzhavan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.example.uzhavan.ui.theme.*
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume
import kotlin.math.roundToInt

// ── API key ───────────────────────────────────────────────────────────────────
private const val OWM_KEY = "497e08737a22a9e27474a40d0c5cd66e"

// ── Data models ───────────────────────────────────────────────────────────────
data class CurrentWeather(
    val city: String,
    val tempC: Double,
    val feelsLikeC: Double,
    val humidity: Int,
    val windKph: Double,
    val description: String,
    val icon: String,
    val cloudPct: Int,
    val rainMm: Double,       // last 1h rain
    val uvIndex: Double,
    val visibility: Int       // metres
)

data class ForecastDay(
    val dateLabel: String,
    val dayName: String,
    val minC: Double,
    val maxC: Double,
    val humidity: Int,
    val rainMm: Double,
    val description: String,
    val icon: String,
    val pop: Double           // probability of precipitation 0-1
)

data class WeatherState(
    val current: CurrentWeather? = null,
    val forecast: List<ForecastDay> = emptyList(),
    val irrigation: IrrigationAdvice? = null,
    val tips: List<FarmingTip> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

data class IrrigationAdvice(
    val shouldIrrigate: Boolean,
    val urgency: Urgency,
    val reason: String,
    val nextCheckIn: String
)

data class FarmingTip(val emoji: String, val title: String, val body: String)

enum class Urgency { HIGH, MEDIUM, LOW, NONE }

// ── Screen ────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    var state   by remember { mutableStateOf(WeatherState()) }

    fun load() {
        scope.launch {
            state = state.copy(loading = true, error = null)
            try {
                val loc = withContext(Dispatchers.IO) { getLocation(context) }
                val (current, forecast) = withContext(Dispatchers.IO) {
                    fetchWeather(loc.first, loc.second)
                }
                state = state.copy(
                    current    = current,
                    forecast   = forecast,
                    irrigation = buildIrrigationAdvice(current, forecast),
                    tips       = buildFarmingTips(current, forecast),
                    loading    = false
                )
            } catch (e: Exception) {
                state = state.copy(loading = false, error = e.message ?: "Failed to fetch weather")
            }
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) load()
        else state = state.copy(error = "Location permission is required for weather data")
    }

    LaunchedEffect(Unit) {
        val fine   = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            load()
        } else {
            permLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weather & Irrigation", fontWeight = FontWeight.Bold, color = AgriGreenDark) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = AgriGreenDark)
                    }
                },
                actions = {
                    IconButton(onClick = { load() }) {
                        Icon(Icons.Default.Refresh, null, tint = AgriGreenDark)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = AgriBackground
    ) { padding ->
        when {
            state.loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(color = AgriGreen, strokeWidth = 3.dp)
                    Text("Fetching weather data…", color = AgriGray, fontSize = 14.sp)
                }
            }

            state.error != null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text("🌧️", fontSize = 56.sp)
                    Text("Could not load weather", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AgriGreenDark)
                    Text(state.error!!, fontSize = 13.sp, color = AgriGray, textAlign = TextAlign.Center)
                    Button(
                        onClick = { load() },
                        colors = ButtonDefaults.buttonColors(containerColor = AgriGreen),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Retry") }
                }
            }

            state.current != null -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { CurrentWeatherCard(state.current!!) }
                item { IrrigationCard(state.irrigation!!) }
                item { ForecastRow(state.forecast) }
                if (state.tips.isNotEmpty()) {
                    item {
                        Text("🌾 Smart Farming Tips", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AgriGreenDark)
                    }
                    items(state.tips) { tip -> FarmingTipCard(tip) }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

// ── Current weather card ──────────────────────────────────────────────────────
@Composable
private fun CurrentWeatherCard(w: CurrentWeather) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AgriGreen),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(w.city, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                    Text(
                        w.description.replaceFirstChar { it.uppercase() },
                        fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f)
                    )
                }
                Text(weatherEmoji(w.icon), fontSize = 52.sp)
            }

            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${w.tempC.roundToInt()}°C", fontWeight = FontWeight.ExtraBold, fontSize = 56.sp, color = Color.White)
                Text("Feels ${w.feelsLikeC.roundToInt()}°C", fontSize = 14.sp, color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.padding(bottom = 10.dp))
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.2f))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                WeatherStat("💧", "Humidity", "${w.humidity}%")
                WeatherStat("🌬️", "Wind", "${w.windKph.roundToInt()} km/h")
                WeatherStat("☁️", "Cloud", "${w.cloudPct}%")
                WeatherStat("🌧️", "Rain 1h", "${w.rainMm} mm")
            }
        }
    }
}

@Composable
private fun WeatherStat(emoji: String, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(emoji, fontSize = 18.sp)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
        Text(label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
    }
}

// ── Irrigation card ───────────────────────────────────────────────────────────
@Composable
private fun IrrigationCard(advice: IrrigationAdvice) {
    val (bg, border, textColor) = when (advice.urgency) {
        Urgency.HIGH   -> Triple(Color(0xFFFFF3E0), Color(0xFFFF6F00), Color(0xFFE65100))
        Urgency.MEDIUM -> Triple(Color(0xFFFFFDE7), Color(0xFFF9A825), Color(0xFFF57F17))
        Urgency.LOW    -> Triple(AgriGreenSurface,  AgriGreen,         AgriGreenDark)
        Urgency.NONE   -> Triple(Color(0xFFE3F2FD), Color(0xFF1565C0), Color(0xFF0D47A1))
    }
    val emoji = when (advice.urgency) {
        Urgency.HIGH   -> "🚨"
        Urgency.MEDIUM -> "⚠️"
        Urgency.LOW    -> "✅"
        Urgency.NONE   -> "💧"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(emoji, fontSize = 28.sp)
                Column {
                    Text(
                        if (advice.shouldIrrigate) "Irrigation Recommended" else "No Irrigation Needed",
                        fontWeight = FontWeight.Bold, fontSize = 16.sp, color = textColor
                    )
                    Surface(shape = RoundedCornerShape(20.dp), color = border.copy(alpha = 0.15f)) {
                        Text(
                            advice.urgency.name,
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = border,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }
            }
            Text(advice.reason, fontSize = 13.sp, color = textColor.copy(alpha = 0.85f), lineHeight = 20.sp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(border.copy(alpha = 0.1f))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🕐 Next check-in", fontSize = 12.sp, color = textColor, fontWeight = FontWeight.SemiBold)
                Text(advice.nextCheckIn, fontSize = 12.sp, color = textColor, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── 5-day forecast row ────────────────────────────────────────────────────────
@Composable
private fun ForecastRow(forecast: List<ForecastDay>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("📅 5-Day Forecast", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AgriGreenDark)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(forecast) { day ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp).width(80.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(day.dayName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AgriGreenDark)
                        Text(weatherEmoji(day.icon), fontSize = 26.sp)
                        Text("${day.maxC.roundToInt()}°", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = AgriGreenDark)
                        Text("${day.minC.roundToInt()}°", fontSize = 12.sp, color = AgriGray)
                        if (day.rainMm > 0 || day.pop > 0.2) {
                            Text(
                                "🌧 ${(day.pop * 100).roundToInt()}%",
                                fontSize = 10.sp, color = Color(0xFF1565C0)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Farming tip card ──────────────────────────────────────────────────────────
@Composable
private fun FarmingTipCard(tip: FarmingTip) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(AgriGreenSurface),
                contentAlignment = Alignment.Center
            ) { Text(tip.emoji, fontSize = 22.sp) }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(tip.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = AgriGreenDark)
                Text(tip.body, fontSize = 12.sp, color = AgriGray, lineHeight = 18.sp)
            }
        }
    }
}

// ── Location helper ───────────────────────────────────────────────────────────
private suspend fun getLocation(context: android.content.Context): Pair<Double, Double> {
    val playAvailable = GoogleApiAvailability.getInstance()
        .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    if (playAvailable) {
        val result = getLocationViaFused(context)
        if (result != null) return result
    }
    // Fallback: Android LocationManager (works on all devices / emulators)
    return getLocationViaManager(context)
        ?: throw Exception("Could not get location. Enable GPS and try again.")
}

private suspend fun getLocationViaFused(context: android.content.Context): Pair<Double, Double>? =
    suspendCancellableCoroutine { cont ->
        val client = LocationServices.getFusedLocationProviderClient(context)
        val cts    = CancellationTokenSource()
        try {
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    if (!cont.isCompleted)
                        cont.resume(loc?.let { it.latitude to it.longitude })
                }
                .addOnFailureListener { if (!cont.isCompleted) cont.resume(null) }
        } catch (e: Exception) {
            if (!cont.isCompleted) cont.resume(null)
        }
        cont.invokeOnCancellation { cts.cancel() }
    }

@Suppress("MissingPermission")
private fun getLocationViaManager(context: android.content.Context): Pair<Double, Double>? {
    val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    for (provider in providers) {
        if (!lm.isProviderEnabled(provider)) continue
        val loc = lm.getLastKnownLocation(provider)
        if (loc != null) return loc.latitude to loc.longitude
    }
    return null
}

// ── OWM fetch ─────────────────────────────────────────────────────────────────
private val httpClient = OkHttpClient()

private fun fetchWeather(lat: Double, lon: Double): Pair<CurrentWeather, List<ForecastDay>> {
    // Current weather
    val curUrl  = "https://api.openweathermap.org/data/2.5/weather?lat=$lat&lon=$lon&appid=$OWM_KEY&units=metric"
    val curJson = JSONObject(httpClient.newCall(Request.Builder().url(curUrl).build()).execute().body!!.string())

    val current = CurrentWeather(
        city        = curJson.optString("name", "Your Location"),
        tempC       = curJson.getJSONObject("main").getDouble("temp"),
        feelsLikeC  = curJson.getJSONObject("main").getDouble("feels_like"),
        humidity    = curJson.getJSONObject("main").getInt("humidity"),
        windKph     = curJson.getJSONObject("wind").getDouble("speed") * 3.6,
        description = curJson.getJSONArray("weather").getJSONObject(0).getString("description"),
        icon        = curJson.getJSONArray("weather").getJSONObject(0).getString("icon"),
        cloudPct    = curJson.getJSONObject("clouds").getInt("all"),
        rainMm      = curJson.optJSONObject("rain")?.optDouble("1h", 0.0) ?: 0.0,
        uvIndex     = 0.0,
        visibility  = curJson.optInt("visibility", 10000)
    )

    // 5-day / 3-hour forecast → aggregate to daily
    val fcUrl   = "https://api.openweathermap.org/data/2.5/forecast?lat=$lat&lon=$lon&appid=$OWM_KEY&units=metric"
    val fcJson  = JSONObject(httpClient.newCall(Request.Builder().url(fcUrl).build()).execute().body!!.string())
    val list    = fcJson.getJSONArray("list")

    // Group by date
    val dayMap  = LinkedHashMap<String, MutableList<JSONObject>>()
    val sdf     = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val today   = sdf.format(Date())

    for (i in 0 until list.length()) {
        val item = list.getJSONObject(i)
        val date = sdf.format(Date(item.getLong("dt") * 1000))
        if (date == today) continue          // skip today (already shown in current)
        dayMap.getOrPut(date) { mutableListOf() }.add(item)
    }

    val dayFmt  = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val nameFmt = SimpleDateFormat("EEE", Locale.getDefault())

    val forecast = dayMap.entries.take(5).map { (dateStr, items) ->
        val date    = dayFmt.parse(dateStr)!!
        val temps   = items.map { it.getJSONObject("main").getDouble("temp") }
        val humids  = items.map { it.getJSONObject("main").getInt("humidity") }
        val rains   = items.map { it.optJSONObject("rain")?.optDouble("3h", 0.0) ?: 0.0 }
        val pops    = items.map { it.optDouble("pop", 0.0) }
        val midItem = items[items.size / 2]
        ForecastDay(
            dateLabel   = dateStr,
            dayName     = nameFmt.format(date),
            minC        = temps.min(),
            maxC        = temps.max(),
            humidity    = humids.average().roundToInt(),
            rainMm      = rains.sum(),
            description = midItem.getJSONArray("weather").getJSONObject(0).getString("description"),
            icon        = midItem.getJSONArray("weather").getJSONObject(0).getString("icon"),
            pop         = pops.max()
        )
    }

    return current to forecast
}

// ── Irrigation logic ──────────────────────────────────────────────────────────
private fun buildIrrigationAdvice(w: CurrentWeather, forecast: List<ForecastDay>): IrrigationAdvice {
    val rainToday      = w.rainMm
    val rainNext24h    = forecast.take(1).sumOf { it.rainMm }
    val rainNext48h    = forecast.take(2).sumOf { it.rainMm }
    val highRainSoon   = forecast.take(2).any { it.pop > 0.6 }
    val tempHigh       = w.tempC > 35
    val humidityLow    = w.humidity < 40
    val humidityHigh   = w.humidity > 80

    return when {
        // Rain already falling or very high humidity — skip irrigation
        rainToday > 5 || humidityHigh -> IrrigationAdvice(
            shouldIrrigate = false,
            urgency        = Urgency.NONE,
            reason         = "Sufficient moisture present. Rain of ${rainToday} mm recorded in the last hour and humidity is ${w.humidity}%. Skip irrigation to avoid waterlogging.",
            nextCheckIn    = "Tomorrow morning"
        )
        // Heavy rain expected soon — hold off
        highRainSoon && rainNext24h > 10 -> IrrigationAdvice(
            shouldIrrigate = false,
            urgency        = Urgency.NONE,
            reason         = "Rain is forecast in the next 24–48 hours (${rainNext24h.roundToInt()} mm expected). Hold off on irrigation to conserve water.",
            nextCheckIn    = "After rain clears"
        )
        // Hot + dry + no rain coming — urgent
        tempHigh && humidityLow && rainNext48h < 2 -> IrrigationAdvice(
            shouldIrrigate = true,
            urgency        = Urgency.HIGH,
            reason         = "High temperature (${w.tempC.roundToInt()}°C) with low humidity (${w.humidity}%) and no rain expected for 48 hours. Crops are at risk of heat stress — irrigate immediately, preferably in the early morning.",
            nextCheckIn    = "Every 12 hours"
        )
        // Moderate conditions, some rain possible
        rainNext48h in 2.0..10.0 -> IrrigationAdvice(
            shouldIrrigate = false,
            urgency        = Urgency.LOW,
            reason         = "Light rain (${rainNext48h.roundToInt()} mm) is expected in the next 48 hours. Conditions are moderate — monitor soil moisture before irrigating.",
            nextCheckIn    = "In 24 hours"
        )
        // Dry conditions, irrigation needed
        humidityLow || rainNext48h < 2 -> IrrigationAdvice(
            shouldIrrigate = true,
            urgency        = Urgency.MEDIUM,
            reason         = "Humidity is ${w.humidity}% with minimal rainfall expected (${rainNext48h.roundToInt()} mm in 48h). Irrigate in the early morning or evening to minimise evaporation.",
            nextCheckIn    = "In 24 hours"
        )
        // Default — conditions are fine
        else -> IrrigationAdvice(
            shouldIrrigate = false,
            urgency        = Urgency.LOW,
            reason         = "Weather conditions are favourable. Temperature ${w.tempC.roundToInt()}°C, humidity ${w.humidity}%. Check soil moisture manually before deciding to irrigate.",
            nextCheckIn    = "In 48 hours"
        )
    }
}

// ── Smart farming tips ────────────────────────────────────────────────────────
private fun buildFarmingTips(w: CurrentWeather, forecast: List<ForecastDay>): List<FarmingTip> {
    val tips = mutableListOf<FarmingTip>()
    val rainSoon = forecast.take(2).any { it.pop > 0.5 }
    val rainNext = forecast.take(2).sumOf { it.rainMm }

    if (w.tempC > 35) tips += FarmingTip("🌡️", "Heat Stress Alert",
        "Temperatures above 35°C can damage crops. Apply mulch to retain soil moisture and consider shade nets for sensitive crops.")

    if (w.windKph > 30) tips += FarmingTip("🌬️", "High Wind Warning",
        "Wind speeds of ${w.windKph.roundToInt()} km/h may damage tall crops. Stake plants and avoid spraying pesticides or fertilizers today.")

    if (rainSoon) tips += FarmingTip("🌧️", "Rain Expected Soon",
        "Rain is forecast in the next 48 hours. Delay fertilizer application to prevent nutrient runoff. Good time to prepare drainage channels.")

    if (w.humidity > 80) tips += FarmingTip("🍄", "High Humidity — Disease Risk",
        "Humidity above 80% increases risk of fungal diseases. Inspect crops for early signs of blight or mildew and ensure good air circulation.")

    if (w.humidity < 40) tips += FarmingTip("💧", "Low Humidity",
        "Low humidity increases water demand. Drip irrigation is most efficient now. Water early morning to reduce evaporation losses.")

    if (w.cloudPct < 20 && w.tempC > 28) tips += FarmingTip("☀️", "High Solar Radiation",
        "Clear skies and high temperatures — ideal for drying harvested produce. Also a good day for solar-powered farm equipment.")

    if (rainNext > 20) tips += FarmingTip("🌊", "Heavy Rain Forecast",
        "More than 20 mm of rain expected. Check field drainage, protect stored produce, and avoid ploughing to prevent soil erosion.")

    if (w.tempC in 20.0..30.0 && w.humidity in 50..70) tips += FarmingTip("✅", "Ideal Growing Conditions",
        "Temperature and humidity are in the optimal range for most crops. Great time for transplanting seedlings or applying fertilizers.")

    if (tips.isEmpty()) tips += FarmingTip("🌿", "Conditions Normal",
        "Weather conditions are within normal range. Continue regular farm activities and monitor soil moisture levels.")

    return tips
}

// ── Weather emoji mapper ──────────────────────────────────────────────────────
private fun weatherEmoji(icon: String): String = when {
    icon.startsWith("01") -> "☀️"
    icon.startsWith("02") -> "🌤️"
    icon.startsWith("03") -> "⛅"
    icon.startsWith("04") -> "☁️"
    icon.startsWith("09") -> "🌧️"
    icon.startsWith("10") -> "🌦️"
    icon.startsWith("11") -> "⛈️"
    icon.startsWith("13") -> "❄️"
    icon.startsWith("50") -> "🌫️"
    else                  -> "🌡️"
}
