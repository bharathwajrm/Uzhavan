package com.example.uzhavan

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.uzhavan.ui.theme.*
import com.google.firebase.auth.FirebaseAuth

// ── Bottom nav items ──────────────────────────────────────────────────────────
sealed class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val label: String
) {
    object Home     : BottomNavItem("home",     Icons.Outlined.Home,              Icons.Filled.Home,        "Home")
    object Explore  : BottomNavItem("explore",  Icons.Outlined.Explore,           Icons.Filled.Explore,     "Explore")
    object Help     : BottomNavItem("help",     Icons.Outlined.Lightbulb,         Icons.Filled.Lightbulb,   "Help")
    object Devices  : BottomNavItem("devices",  Icons.Outlined.Router,            Icons.Filled.Router,      "Devices")
    object Profile  : BottomNavItem("profile",  Icons.Outlined.Person,            Icons.Filled.Person,      "Profile")
}

// ── Root scaffold ─────────────────────────────────────────────────────────────
@Composable
fun MainPage(parentNavController: NavController, viewModel: UserViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val username         by viewModel.username
    val incomingRequests by viewModel.incomingRequests

    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.Explore,
        BottomNavItem.Help,
        BottomNavItem.Devices,
        BottomNavItem.Profile
    )

    Scaffold(
        topBar = {
            if (currentRoute != null) {
                AgriTopBar(
                    username        = username,
                    notifCount      = incomingRequests.size,
                    onMessagesClick = {
                        navController.navigate("messages") {
                            popUpTo(BottomNavItem.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    },
                    onProfileClick  = {
                        navController.navigate(BottomNavItem.Profile.route) {
                            popUpTo(BottomNavItem.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    },
                    onSignOut = {
                        viewModel.signOut()
                        parentNavController.navigate("login") { popUpTo("main") { inclusive = true } }
                    }
                )
            }
        },
        bottomBar = {
            AgriBottomBar(
                items         = items,
                currentRoute  = currentRoute,
                messagesBadge = 0,
                onNavigate    = { route ->
                    navController.navigate(route) {
                        popUpTo(BottomNavItem.Home.route) { saveState = true }
                        launchSingleTop = true
                        restoreState    = true
                    }
                }
            )
        },
        containerColor = AgriBackground
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = BottomNavItem.Home.route,
            modifier         = Modifier.padding(innerPadding)
        ) {
            composable(BottomNavItem.Home.route)     { HomeScreen(viewModel) }
            composable(BottomNavItem.Explore.route)  { ExploreScreen(viewModel) }
            composable(BottomNavItem.Help.route)     {
                HelpScreen(
                    onSoilPrediction = { navController.navigate("soil_predictio") },
                    onWeather        = { navController.navigate("weather") },
                    onSchemeFinder   = { navController.navigate("scheme_finder") },
                    onShopLocator    = { navController.navigate("shop_locator") },
                    onVegPrices      = { navController.navigate("veg_prices") }
                )
            }
            composable(BottomNavItem.Devices.route)  { DevicesScreen() }
            composable("messages")                   { MessagesScreen(viewModel) }
            composable("soil_prediction")            { SoilPredictionScreen(onBack = { navController.popBackStack() }) }
            composable("weather")                    { WeatherScreen(onBack = { navController.popBackStack() }) }
            composable("scheme_finder")              { SchemeFinderScreen(onBack = { navController.popBackStack() }) }
            composable("shop_locator")               { AgriShopLocatorScreen(onBack = { navController.popBackStack() }) }
            composable("veg_prices")                 { VegetablePricesScreen(onBack = { navController.popBackStack() }) }
            composable(BottomNavItem.Profile.route)  {
                ProfileScreen(viewModel, onSignOut = {
                    viewModel.signOut()
                    parentNavController.navigate("login") { popUpTo("main") { inclusive = true } }
                })
            }
        }
    }
}

// ── Top App Bar ───────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgriTopBar(
    username: String?,
    notifCount: Int,
    onMessagesClick: () -> Unit,
    onProfileClick: () -> Unit,
    onSignOut: () -> Unit
) {
    Surface(color = Color.White, shadowElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Logo
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AgriGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Eco, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text("Uzhavan", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = AgriGreenDark)
            }

            // Actions
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Notifications
                Box {
                    IconButton(onClick = {}) {
                        Icon(Icons.Outlined.Notifications, "Notifications", tint = AgriGreenDark)
                    }
                    if (notifCount > 0) {
                        Badge(
                            containerColor = AgriYellow,
                            modifier = Modifier.align(Alignment.TopEnd).offset((-4).dp, 4.dp)
                        ) { Text("$notifCount", color = AgriGreenDark, fontSize = 9.sp) }
                    }
                }

                // Messages
                IconButton(onClick = onMessagesClick) {
                    Icon(Icons.AutoMirrored.Outlined.Chat, "Messages", tint = AgriGreenDark)
                }

                // Avatar
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(AgriGreen)
                        .border(2.dp, AgriGreenLight, CircleShape)
                        .clickable { onProfileClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = username?.firstOrNull()?.uppercaseChar()?.toString() ?: "U",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

// ── Bottom Navigation Bar (safe-area aware) ───────────────────────────────────
@Composable
fun AgriBottomBar(
    items: List<BottomNavItem>,
    currentRoute: String?,
    messagesBadge: Int,
    onNavigate: (String) -> Unit
) {
    Surface(color = Color.White, shadowElevation = 12.dp) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            modifier = Modifier.navigationBarsPadding()
        ) {
            items.forEach { item ->
                val selected = currentRoute == item.route
                val iconSize by animateDpAsState(
                    targetValue = if (selected) 26.dp else 22.dp,
                    animationSpec = tween(200), label = "iconSize"
                )
                NavigationBarItem(
                    icon = {
                        Box {
                            Icon(
                                imageVector = if (selected) item.selectedIcon else item.icon,
                                contentDescription = item.label,
                                modifier = Modifier.size(iconSize)
                            )
                            if (item.route == BottomNavItem.Devices.route && messagesBadge > 0) {
                                Badge(
                                    containerColor = AgriYellow,
                                    modifier = Modifier.align(Alignment.TopEnd)
                                ) { Text("$messagesBadge", color = AgriGreenDark, fontSize = 9.sp) }
                            }
                        }
                    },
                    label = { Text(item.label, fontSize = 10.sp) },
                    selected = selected,
                    onClick = { onNavigate(item.route) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor   = AgriGreen,
                        selectedTextColor   = AgriGreen,
                        unselectedIconColor = AgriGray,
                        unselectedTextColor = AgriGray,
                        indicatorColor      = AgriGreenSurface
                    )
                )
            }
        }
    }
}

// ── Explore Screen ───────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(viewModel: UserViewModel) {
    val posts by viewModel.posts
    val myUid = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "" }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val categories = listOf("All 🌿", "Crops 🌱", "Harvest 🌾", "Machinery 🚜", "Market 📊", "Livestock 🐄")
    var selectedCat by remember { mutableIntStateOf(0) }

    val filtered = remember(posts, selectedCat) {
        if (selectedCat == 0) posts
        else {
            val key = listOf("", "crops", "harvest", "machinery", "market", "livestock")[selectedCat]
            posts.filter { it.category == key }
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            viewModel.fetchFeed()
            scope.launch {
                delay(800)
                isRefreshing = false
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(AgriBackground),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Category filter chips
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories.size) { i ->
                        FilterChip(
                            selected = selectedCat == i,
                            onClick  = { selectedCat = i },
                            label    = { Text(categories[i], fontSize = 13.sp) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AgriGreen,
                                selectedLabelColor     = Color.White,
                                containerColor         = Color.White,
                                labelColor             = AgriGreenDark
                            )
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                item { AgriEmptyState(emoji = "🌾", title = "No posts yet", subtitle = "Be the first to share!") }
            } else {
                items(filtered, key = { it.postId }) { post ->
                    PostCard(post = post, myUid = myUid, viewModel = viewModel)
                }
            }
        }
    }
}

// ── Help Screen ───────────────────────────────────────────────────────────────
@Composable
fun HelpScreen(
    onSoilPrediction: () -> Unit,
    onWeather: () -> Unit,
    onSchemeFinder: () -> Unit,
    onShopLocator: () -> Unit,
    onVegPrices: () -> Unit
) {
    data class Feature(val emoji: String, val title: String, val subtitle: String, val onClick: (() -> Unit)? = null)
    val features = listOf(
        Feature("🌿", "Plant Disease Prediction", "Identify crop diseases instantly from a photo"),
        Feature("🪱", "Soil Prediction", "Analyse soil condition and get treatment advice", onSoilPrediction),
        Feature("🌦️", "Weather Prediction", "Hyperlocal forecasts tailored for your farm", onWeather),
        Feature("💡", "Smart Recommendations", "AI-powered tips on seeds, fertilizers & timing"),
        Feature("📋", "Scheme Finder", "Discover government schemes you are eligible for", onSchemeFinder),
        Feature("🏪", "Shop Locator", "Find nearby agri shops with live product prices", onShopLocator),
        Feature("🌾", "Today's Vegetable Prices", "Live Tamil Nadu market prices updated daily", onVegPrices)
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(AgriBackground),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Farmer Tools",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp,
                color = AgriGreenDark,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                "Everything you need to grow smarter",
                fontSize = 13.sp,
                color = AgriGray,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        items(features) { feature ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp),
                onClick = { feature.onClick?.invoke() }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(AgriGreenSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(feature.emoji, fontSize = 26.sp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(feature.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = AgriGreenDark)
                        Text(feature.subtitle, fontSize = 12.sp, color = AgriGray, modifier = Modifier.padding(top = 2.dp))
                    }
                    Icon(
                        if (feature.onClick != null) Icons.Default.ChevronRight else Icons.Default.Lock,
                        null,
                        tint = if (feature.onClick != null) AgriGreen else AgriGray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ── Devices Screen ────────────────────────────────────────────────────────────
data class SmartDevice(
    val id: String,
    val name: String,
    val zone: String,
    val isOnline: Boolean,
    val batteryPct: Int?,
    val lastSync: String,
    val sensors: Map<String, String> = emptyMap()
)

@Composable
fun DevicesScreen() {
    var devices by remember { mutableStateOf<List<SmartDevice>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var expandedId by remember { mutableStateOf<String?>(null) }

    if (showAddDialog) {
        AddDeviceDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, zone ->
                devices = devices + SmartDevice(
                    id = System.currentTimeMillis().toString(),
                    name = name, zone = zone,
                    isOnline = false, batteryPct = null, lastSync = "Never"
                )
                showAddDialog = false
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = AgriGreen,
                contentColor = Color.White,
                shape = CircleShape
            ) { Icon(Icons.Default.Add, "Add Device") }
        },
        containerColor = AgriBackground
    ) { padding ->
        if (devices.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text("📡", fontSize = 64.sp)
                    Text(
                        "No Smart Agri Devices Connected",
                        fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AgriGreenDark
                    )
                    Text(
                        "Add your first Uzhavan Smart Node to monitor your farm in real time.",
                        fontSize = 13.sp, color = AgriGray,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { showAddDialog = true },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AgriGreen)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Smart Node")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "Smart Agri Devices",
                        fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = AgriGreenDark
                    )
                    Text(
                        "${devices.count { it.isOnline }} of ${devices.size} online",
                        fontSize = 12.sp, color = AgriGray,
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                    )
                }
                items(devices, key = { it.id }) { device ->
                    DeviceCard(
                        device = device,
                        expanded = expandedId == device.id,
                        onToggleExpand = {
                            expandedId = if (expandedId == device.id) null else device.id
                        },
                        onRemove = { devices = devices.filter { it.id != device.id } },
                        onRename = { newName ->
                            devices = devices.map { if (it.id == device.id) it.copy(name = newName) else it }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: SmartDevice,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onRemove: () -> Unit,
    onRename: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    if (showRenameDialog) {
        RenameDeviceDialog(
            current = device.name,
            onDismiss = { showRenameDialog = false },
            onConfirm = { onRename(it); showRenameDialog = false }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (device.isOnline) AgriGreenSurface else Color(0xFFF5F5F5)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Router, null,
                        tint = if (device.isOnline) AgriGreen else AgriGray,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(device.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = AgriGreenDark)
                    Text("📍 ${device.zone}", fontSize = 12.sp, color = AgriGray)
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (device.isOnline) Color(0xFFE8F5E9) else Color(0xFFF5F5F5)
                ) {
                    Text(
                        if (device.isOnline) "● Online" else "○ Offline",
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        color = if (device.isOnline) AgriGreen else AgriGray,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, null, tint = AgriGray)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = { showMenu = false; showRenameDialog = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Remove", color = Color(0xFFE53935)) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFE53935)) },
                            onClick = { showMenu = false; onRemove() }
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = AgriDivider, thickness = 0.5.dp)
            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                DeviceInfoChip("🕒", "Last Sync", device.lastSync)
                if (device.batteryPct != null) {
                    DeviceInfoChip(
                        if (device.batteryPct > 20) "🔋" else "🪫",
                        "Battery", "${device.batteryPct}%"
                    )
                } else {
                    DeviceInfoChip("⚡", "Power", "Wired")
                }
            }

            if (device.sensors.isNotEmpty()) {
                TextButton(onClick = onToggleExpand, contentPadding = PaddingValues(0.dp)) {
                    Text(
                        if (expanded) "Hide sensor readings" else "View live sensor readings",
                        color = AgriGreen, fontSize = 12.sp
                    )
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        null, tint = AgriGreen, modifier = Modifier.size(16.dp)
                    )
                }
                if (expanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        device.sensors.forEach { (label, value) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AgriGreenSurface)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(label, fontSize = 13.sp, color = AgriGreenDark)
                                Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AgriGreen)
                            }
                        }
                    }
                }
            } else if (device.isOnline) {
                Text("No sensor data yet", fontSize = 12.sp, color = AgriGray)
            }
        }
    }
}

@Composable
private fun DeviceInfoChip(emoji: String, label: String, value: String) {
    Column {
        Text("$emoji $label", fontSize = 10.sp, color = AgriGray)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AgriGreenDark)
    }
}

@Composable
private fun AddDeviceDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var zone by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Smart Node", fontWeight = FontWeight.Bold, color = AgriGreenDark) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Device Name (e.g. Smart Node 01)") },
                    singleLine = true, shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AgriGreen, unfocusedBorderColor = AgriDivider
                    )
                )
                OutlinedTextField(
                    value = zone, onValueChange = { zone = it },
                    label = { Text("Farm / Zone (e.g. Field A)") },
                    singleLine = true, shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AgriGreen, unfocusedBorderColor = AgriDivider
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank() && zone.isNotBlank()) onAdd(name.trim(), zone.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = AgriGreen),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = AgriGray) }
        }
    )
}

@Composable
private fun RenameDeviceDialog(current: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Device", fontWeight = FontWeight.Bold, color = AgriGreenDark) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Device Name") },
                singleLine = true, shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AgriGreen, unfocusedBorderColor = AgriDivider
                )
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = AgriGreen),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = AgriGray) }
        }
    )
}

// ── Profile Screen ────────────────────────────────────────────────────────────
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ProfileScreen(viewModel: UserViewModel, onSignOut: () -> Unit) {
    val profile     by viewModel.currentProfile
    val connections by viewModel.connections
    val posts       by viewModel.posts
    val myUid = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "" }
    val myPosts = remember(posts, myUid) { posts.filter { it.uid == myUid } }
    var showCreatePost by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (showCreatePost) {
        CreatePostSheet(viewModel = viewModel, onDismiss = { showCreatePost = false })
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            viewModel.fetchFeed()
            viewModel.fetchUsername(myUid)
            scope.launch {
                delay(800)
                isRefreshing = false
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AgriBackground)
            .verticalScroll(rememberScrollState())
    ) {
        // Header card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(0.dp),
            colors   = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(AgriGreen)
                        .border(3.dp, AgriGreenLight, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = profile?.fullName?.firstOrNull()?.uppercaseChar()?.toString() ?: "U",
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 38.sp
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(profile?.fullName ?: "Farmer", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = AgriGreenDark)
                Text("@${profile?.username ?: ""}", fontSize = 14.sp, color = AgriGray)
                if (!profile?.farmName.isNullOrEmpty())
                    Text("🌾 ${profile?.farmName}", fontSize = 13.sp, color = AgriGreen, modifier = Modifier.padding(top = 4.dp))
                if (!profile?.location.isNullOrEmpty())
                    Text("📍 ${profile?.location}", fontSize = 13.sp, color = AgriGray)
                if (!profile?.cropType.isNullOrEmpty())
                    Text("🌱 ${profile?.cropType}", fontSize = 13.sp, color = AgriGray)

                Spacer(Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ProfileStat(myPosts.size.toString(), "Posts")
                    ProfileStat(connections.size.toString(), "Connections")
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { showCreatePost = true },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AgriGreen)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Create Post")
                    }
                    OutlinedButton(
                        onClick = onSignOut,
                        shape  = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color(0xFFE53935))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, null, tint = Color(0xFFE53935), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sign Out", color = Color(0xFFE53935))
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (myPosts.isEmpty()) {
            AgriEmptyState(emoji = "🌱", title = "No posts yet", subtitle = "Share your first farming moment!")
        } else {
            Text("My Posts", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AgriGreenDark,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            myPosts.forEach { post ->
                PostCard(post = post, myUid = myUid, viewModel = viewModel)
            }
        }
    }
    }
}

@Composable
private fun ProfileStat(count: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = AgriGreenDark)
        Text(label, fontSize = 12.sp, color = AgriGray)
    }
}

// ── Create Post Sheet ─────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostSheet(viewModel: UserViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Box(modifier = Modifier.navigationBarsPadding()) {
            CreatePostScreen(viewModel = viewModel)
        }
    }
}

// ── Shared empty state ────────────────────────────────────────────────────────
@Composable
fun AgriEmptyState(emoji: String, title: String, subtitle: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(emoji, fontSize = 56.sp)
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = AgriGreenDark)
            Text(subtitle, fontSize = 13.sp, color = AgriGray)
        }
    }
}
