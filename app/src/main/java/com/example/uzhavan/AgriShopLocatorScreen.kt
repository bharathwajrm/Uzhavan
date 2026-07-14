package com.example.uzhavan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.uzhavan.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.*

data class AgriShop(
    val id: Long,
    val name: String,
    val category: String,
    val lat: Double,
    val lon: Double,
    val address: String,
    val phone: String,
    val distanceKm: Double
)

private val SHOP_CATEGORIES = listOf("All", "Fertilizer & Seeds", "Vegetable Market", "Hardware & Tools", "Nursery", "Govt Office")

private val CATEGORY_ICONS = mapOf(
    "Fertilizer & Seeds" to "🌱",
    "Vegetable Market"   to "🥬",
    "Hardware & Tools"   to "🔧",
    "Nursery"            to "🌿",
    "Govt Office"        to "🏛️"
)

// Maps OSM tags → our category
private fun tagToCategory(tags: JSONObject): String {
    val shop    = tags.optString("shop", "")
    val amenity = tags.optString("amenity", "")
    val office  = tags.optString("office", "")
    val name    = tags.optString("name", "").lowercase()
    return when {
        shop == "greengrocer" || amenity == "marketplace" || name.contains("vegetable") || name.contains("market") -> "Vegetable Market"
        shop == "garden_centre" || name.contains("nursery") || name.contains("plant") -> "Nursery"
        office == "government" || name.contains("agriculture dept") || name.contains("krishi") -> "Govt Office"
        shop == "hardware" || name.contains("pump") || name.contains("equipment") -> "Hardware & Tools"
        else -> "Fertilizer & Seeds"
    }
}

private val httpClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

// Working Overpass mirror — maps.mail.ru doesn't have 406 issues
private const val OVERPASS_URL = "https://maps.mail.ru/osm/tools/overpass/api/interpreter"

private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return r * 2 * atan2(sqrt(a), sqrt(1 - a))
}

private suspend fun fetchShopsFromOSM(lat: Double, lon: Double): List<AgriShop> =
    withContext(Dispatchers.IO) {
        // Broad query: agri-related shop types + name-based search
        val query = """
            [out:json][timeout:30];
            (
              node["shop"~"agrarian|seeds|garden_centre|greengrocer|hardware|florist"](around:15000,$lat,$lon);
              node["amenity"~"marketplace"](around:15000,$lat,$lon);
              node["office"="government"]["name"~"agri|krishi|farm",i](around:20000,$lat,$lon);
              node["name"~"agri|farm|seed|fertilizer|nursery|krishi|pesticide|urea|manure",i](around:15000,$lat,$lon);
            );
            out body;
        """.trimIndent()

        try {
            val body = query.toRequestBody("text/plain".toMediaType())
            val request = Request.Builder()
                .url(OVERPASS_URL)
                .post(body)
                .header("User-Agent", "UzhavanApp/1.0 (Android)")
                .header("Accept", "application/json")
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(responseBody)
            val elements: JSONArray = json.optJSONArray("elements") ?: return@withContext emptyList()

            val results = mutableListOf<AgriShop>()
            val seenIds = mutableSetOf<Long>()

            for (i in 0 until elements.length()) {
                val el = elements.getJSONObject(i)
                val elLat = el.optDouble("lat", 0.0)
                val elLon = el.optDouble("lon", 0.0)
                if (elLat == 0.0 && elLon == 0.0) continue

                val tags = el.optJSONObject("tags") ?: continue
                val name = tags.optString("name").ifEmpty { continue }
                val id = el.getLong("id")
                if (!seenIds.add(id)) continue

                val phone = tags.optString("phone").ifEmpty {
                    tags.optString("contact:phone").ifEmpty {
                        tags.optString("contact:mobile", "")
                    }
                }
                val street  = tags.optString("addr:street", "")
                val city    = tags.optString("addr:city").ifEmpty { tags.optString("addr:district", "") }
                val address = listOf(street, city).filter { it.isNotEmpty() }.joinToString(", ").ifEmpty { "Tamil Nadu" }
                val category = tagToCategory(tags)
                val dist = haversineKm(lat, lon, elLat, elLon)

                results.add(AgriShop(
                    id = id, name = name, category = category,
                    lat = elLat, lon = elLon,
                    address = address, phone = phone,
                    distanceKm = dist
                ))
            }
            results.sortedBy { it.distanceKm }
        } catch (e: Exception) {
            emptyList()
        }
    }

private suspend fun geocodeLocation(query: String): Pair<Double, Double>? =
    withContext(Dispatchers.IO) {
        try {
            val url = "https://nominatim.openstreetmap.org/search?q=${Uri.encode(query)}&format=json&limit=1&countrycodes=in"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "UzhavanApp/1.0 (Android)")
                .build()
            val resp = httpClient.newCall(request).execute()
            val body = resp.body?.string() ?: return@withContext null
            val arr = JSONArray(body)
            if (arr.length() == 0) return@withContext null
            val obj = arr.getJSONObject(0)
            obj.getDouble("lat") to obj.getDouble("lon")
        } catch (_: Exception) { null }
    }

@Composable
fun AgriShopLocatorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var shops           by remember { mutableStateOf<List<AgriShop>>(emptyList()) }
    var loading         by remember { mutableStateOf(false) }
    var error           by remember { mutableStateOf<String?>(null) }
    var activeCategory  by remember { mutableStateOf("All") }
    var searchText      by remember { mutableStateOf("") }
    var userLat         by remember { mutableStateOf<Double?>(null) }
    var userLon         by remember { mutableStateOf<Double?>(null) }
    var locationLabel   by remember { mutableStateOf("") }

    val filteredShops = remember(shops, activeCategory, searchText) {
        shops.filter { shop ->
            val catMatch = activeCategory == "All" || shop.category == activeCategory
            val qMatch   = searchText.isEmpty() || shop.name.lowercase().contains(searchText.lowercase())
            catMatch && qMatch
        }
    }

    fun loadShops(lat: Double, lon: Double) {
        scope.launch {
            loading = true; error = null
            val result = fetchShopsFromOSM(lat, lon)
            if (result.isEmpty()) error = "No agri shops found nearby.\nTry searching a different district or town."
            else shops = result
            loading = false
        }
    }

    val locationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            // Default to Chennai since we can't use FusedLocationProvider without Google Play Services
            userLat = 13.0827; userLon = 80.2707; locationLabel = "Chennai, Tamil Nadu"
            loadShops(13.0827, 80.2707)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(AgriBackground)) {

        // ── Top bar ──────────────────────────────────────────────────────────
        Surface(color = Color.White, shadowElevation = 3.dp) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = AgriGreenDark)
                    }
                    Text(
                        "Agri Shop Locator",
                        fontWeight = FontWeight.Bold, fontSize = 18.sp,
                        color = AgriGreenDark, modifier = Modifier.weight(1f)
                    )
                    if (userLat != null && !loading) {
                        IconButton(onClick = { loadShops(userLat!!, userLon!!) }) {
                            Icon(Icons.Default.Refresh, null, tint = AgriGreen)
                        }
                    }
                }

                // Search bar
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp).padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        placeholder = { Text("Search shops or enter district/pincode…", fontSize = 12.sp, color = AgriGray) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = AgriGray, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchText.isNotEmpty()) {
                                IconButton(onClick = { searchText = "" }) {
                                    Icon(Icons.Default.Close, null, tint = AgriGray)
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = AgriGreen,
                            unfocusedBorderColor = AgriDivider,
                            focusedTextColor     = AgriGreenDark,
                            unfocusedTextColor   = AgriGreenDark
                        )
                    )
                    Button(
                        onClick = {
                            val q = searchText.trim()
                            if (q.isNotBlank()) {
                                scope.launch {
                                    loading = true; error = null
                                    val coords = geocodeLocation(q)
                                    if (coords != null) {
                                        userLat = coords.first; userLon = coords.second
                                        locationLabel = q
                                        loadShops(coords.first, coords.second)
                                    } else {
                                        error = "Location \"$q\" not found.\nTry district name or pincode."
                                        loading = false
                                    }
                                }
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AgriGreen),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp)
                    ) { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) }
                }

                // Category filter chips
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp).padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SHOP_CATEGORIES.forEach { cat ->
                        FilterChip(
                            selected = activeCategory == cat,
                            onClick  = { activeCategory = cat },
                            label    = {
                                Text(
                                    if (cat == "All") cat else "${CATEGORY_ICONS[cat]} $cat",
                                    fontSize = 12.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AgriGreen,
                                selectedLabelColor     = Color.White,
                                containerColor         = Color.White,
                                labelColor             = AgriGreenDark
                            )
                        )
                    }
                }
            }
        }

        // ── Body ─────────────────────────────────────────────────────────────
        when {
            userLat == null && !loading -> {
                // Landing state
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text("🗺️", fontSize = 56.sp)
                        Text(
                            "Find Agri Shops Near You",
                            fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AgriGreenDark
                        )
                        Text(
                            "Use your location or search by district, village, or pincode.",
                            fontSize = 13.sp, color = AgriGray
                        )
                        Button(
                            onClick = {
                                locationPermLauncher.launch(arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ))
                            },
                            shape  = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AgriGreen)
                        ) {
                            Icon(Icons.Default.MyLocation, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Use My Location")
                        }
                        // Quick district shortcuts
                        Text("Or try a district:", fontSize = 12.sp, color = AgriGray)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Chennai", "Coimbatore", "Madurai", "Salem").forEach { city ->
                                OutlinedButton(
                                    onClick = {
                                        searchText = city
                                        scope.launch {
                                            loading = true; error = null
                                            val coords = geocodeLocation("$city Tamil Nadu")
                                            if (coords != null) {
                                                userLat = coords.first; userLon = coords.second
                                                locationLabel = city
                                                loadShops(coords.first, coords.second)
                                            } else { error = "Not found"; loading = false }
                                        }
                                    },
                                    shape = RoundedCornerShape(20.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) { Text(city, fontSize = 12.sp, color = AgriGreen) }
                            }
                        }
                    }
                }
            }

            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = AgriGreen)
                        Text("Finding agri shops nearby…", color = AgriGray, fontSize = 14.sp)
                        if (locationLabel.isNotEmpty())
                            Text("📍 $locationLabel", fontSize = 12.sp, color = AgriGreen)
                    }
                }
            }

            error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text("😕", fontSize = 48.sp)
                        Text(error!!, color = AgriGray, fontSize = 14.sp)
                        Button(
                            onClick = { userLat?.let { loadShops(it, userLon!!) } },
                            shape  = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AgriGreen)
                        ) { Text("Retry") }
                    }
                }
            }

            else -> {
                // Location label + count
                if (locationLabel.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.LocationOn, null, tint = AgriGreen, modifier = Modifier.size(16.dp))
                        Text(locationLabel, fontSize = 13.sp, color = AgriGreenDark, fontWeight = FontWeight.Medium)
                        Text("· ${filteredShops.size} shops found", fontSize = 12.sp, color = AgriGray)
                    }
                }

                if (filteredShops.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("🔍", fontSize = 48.sp)
                            Text("No shops in this category", color = AgriGray, fontSize = 14.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredShops, key = { it.id }) { shop ->
                            ShopCard(shop = shop, context = context)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShopCard(shop: AgriShop, context: Context) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp))
                        .background(AgriGreenSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Text(CATEGORY_ICONS[shop.category] ?: "🏪", fontSize = 22.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        shop.name,
                        fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                        color = AgriGreenDark, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(shop.category, fontSize = 11.sp, color = AgriGray)
                }
                Surface(shape = RoundedCornerShape(20.dp), color = AgriGreenSurface) {
                    Text(
                        "%.1f km".format(shop.distanceKm),
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AgriGreen,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.LocationOn, null, tint = AgriGray, modifier = Modifier.size(13.dp))
                Text(
                    shop.address, fontSize = 12.sp, color = AgriGray,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (shop.phone.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Phone, null, tint = AgriGray, modifier = Modifier.size(13.dp))
                    Text(shop.phone, fontSize = 12.sp, color = AgriGray)
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val uri = Uri.parse("geo:${shop.lat},${shop.lon}?q=${shop.lat},${shop.lon}(${Uri.encode(shop.name)})")
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    },
                    shape  = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AgriGreen),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Directions, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Directions", fontSize = 12.sp)
                }
                if (shop.phone.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${shop.phone}")))
                        },
                        shape  = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Call, null, modifier = Modifier.size(14.dp), tint = AgriGreen)
                        Spacer(Modifier.width(4.dp))
                        Text("Call", fontSize = 12.sp, color = AgriGreen)
                    }
                }
            }
        }
    }
}
