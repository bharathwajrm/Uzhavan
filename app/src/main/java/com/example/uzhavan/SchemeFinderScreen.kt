package com.example.uzhavan

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.uzhavan.ui.theme.*

data class GovScheme(
    val id: Int,
    val name: String,
    val description: String,
    val benefits: String,
    val eligibility: String,
    val lastDate: String?,
    val website: String,
    val category: String,   // "Central" | "Tamil Nadu" | "Subsidy" | "Insurance" | "Equipment" | "Irrigation"
    val state: String,
    val crops: List<String>
)

private val SCHEMES = listOf(
    GovScheme(1, "PM-KISAN", "Pradhan Mantri Kisan Samman Nidhi provides income support to all landholding farmers.", "₹6,000/year in 3 equal instalments directly to bank account.", "All landholding farmer families with cultivable land.", null, "https://pmkisan.gov.in", "Central", "All India", listOf("All")),
    GovScheme(2, "PM Fasal Bima Yojana", "Crop insurance scheme to provide financial support to farmers suffering crop loss.", "Full insurance coverage for crop loss due to natural calamities.", "All farmers growing notified crops in notified areas.", "31 Jul 2025", "https://pmfby.gov.in", "Insurance", "All India", listOf("Paddy","Wheat","Cotton","Maize")),
    GovScheme(3, "Kisan Credit Card", "Provides farmers with affordable credit for agricultural needs.", "Short-term credit up to ₹3 lakh at 4% interest rate.", "All farmers, sharecroppers, tenant farmers.", null, "https://www.nabard.org", "Central", "All India", listOf("All")),
    GovScheme(4, "PMKSY – Per Drop More Crop", "Promotes micro-irrigation to improve water use efficiency.", "50% subsidy for drip/sprinkler irrigation systems.", "All farmers with valid land records.", "30 Sep 2025", "https://pmksy.gov.in", "Irrigation", "All India", listOf("Vegetables","Fruits","Sugarcane")),
    GovScheme(5, "Sub-Mission on Agricultural Mechanization", "Promotes farm mechanization to reduce drudgery and cost.", "40–50% subsidy on tractors, power tillers, harvesters.", "Small and marginal farmers (land < 2 ha).", null, "https://agrimachinery.nic.in", "Equipment", "All India", listOf("All")),
    GovScheme(6, "Tamil Nadu Chief Minister's Farmer Security Scheme", "Life insurance for farmers in Tamil Nadu.", "₹2 lakh life insurance cover at zero premium.", "All registered farmers in Tamil Nadu.", null, "https://www.tn.gov.in/agriculture", "Tamil Nadu", "Tamil Nadu", listOf("All")),
    GovScheme(7, "TN Free Bore Well Scheme", "Free bore well drilling for small and marginal farmers.", "One free bore well up to 200 ft depth.", "Small/marginal farmers in Tamil Nadu with < 2.5 acres.", "31 Mar 2026", "https://www.tn.gov.in/agriculture", "Tamil Nadu", "Tamil Nadu", listOf("All")),
    GovScheme(8, "TN Uzhavar Sandhai", "Direct marketing scheme for farmers to sell produce.", "Farmers sell directly to consumers, eliminating middlemen.", "All farmers in Tamil Nadu.", null, "https://www.tn.gov.in/agriculture", "Tamil Nadu", "Tamil Nadu", listOf("Vegetables","Fruits")),
    GovScheme(9, "National Horticulture Mission", "Development of horticulture sector across India.", "50% subsidy on seeds, planting material, protected cultivation.", "Farmers growing fruits, vegetables, flowers.", null, "https://nhm.nic.in", "Subsidy", "All India", listOf("Vegetables","Fruits","Flowers")),
    GovScheme(10, "Soil Health Card Scheme", "Provides soil health cards to farmers with crop-wise recommendations.", "Free soil testing and nutrient management advice.", "All farmers.", null, "https://soilhealth.dac.gov.in", "Central", "All India", listOf("All")),
    GovScheme(11, "TN Paddy Procurement Scheme", "Government procurement of paddy at MSP.", "Minimum Support Price guaranteed for paddy.", "Paddy farmers registered in Tamil Nadu.", "Ongoing", "https://www.tn.gov.in/agriculture", "Tamil Nadu", "Tamil Nadu", listOf("Paddy")),
    GovScheme(12, "Rashtriya Krishi Vikas Yojana", "Holistic development of agriculture and allied sectors.", "Grants for infrastructure, technology, and capacity building.", "State governments and farmer groups.", null, "https://rkvy.nic.in", "Central", "All India", listOf("All")),
)

private val FILTER_TAGS = listOf("All", "Central", "Tamil Nadu", "Subsidy", "Insurance", "Equipment", "Irrigation")

@Composable
fun SchemeFinderScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf("All") }
    val bookmarks = remember { mutableStateListOf<Int>() }

    val filtered = remember(query, activeFilter) {
        SCHEMES.filter { scheme ->
            val matchesFilter = activeFilter == "All" || scheme.category == activeFilter
            val q = query.trim().lowercase()
            val matchesQuery = q.isEmpty() ||
                scheme.name.lowercase().contains(q) ||
                scheme.description.lowercase().contains(q) ||
                scheme.state.lowercase().contains(q) ||
                scheme.crops.any { it.lowercase().contains(q) } ||
                scheme.category.lowercase().contains(q)
            matchesFilter && matchesQuery
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(AgriBackground)) {
        // Top bar
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
                    Text("Scheme Finder", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AgriGreenDark, modifier = Modifier.weight(1f))
                    Text("${filtered.size} schemes", fontSize = 12.sp, color = AgriGray)
                }
                // Search
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search by name, crop, state…", color = AgriGray, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 10.dp),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = AgriGray) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, null, tint = AgriGray)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AgriGreen, unfocusedBorderColor = AgriDivider,
                        focusedTextColor = AgriGreenDark, unfocusedTextColor = AgriGreenDark
                    )
                )
                // Filter chips
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp).padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FILTER_TAGS.forEach { tag ->
                        FilterChip(
                            selected = activeFilter == tag,
                            onClick = { activeFilter = tag },
                            label = { Text(tag, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AgriGreen,
                                selectedLabelColor = Color.White,
                                containerColor = Color.White,
                                labelColor = AgriGreenDark
                            )
                        )
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("📋", fontSize = 52.sp)
                    Text("No schemes found", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = AgriGreenDark)
                    Text("Try a different search or filter", fontSize = 13.sp, color = AgriGray)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(filtered, key = { it.id }) { scheme ->
                    SchemeCard(
                        scheme = scheme,
                        isBookmarked = bookmarks.contains(scheme.id),
                        onBookmark = {
                            if (bookmarks.contains(scheme.id)) bookmarks.remove(scheme.id)
                            else bookmarks.add(scheme.id)
                        },
                        onShare = { shareScheme(context, scheme) },
                        onApply = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(scheme.website)))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SchemeCard(
    scheme: GovScheme,
    isBookmarked: Boolean,
    onBookmark: () -> Unit,
    onShare: () -> Unit,
    onApply: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val categoryColor = when (scheme.category) {
        "Central"    -> Color(0xFF1565C0)
        "Tamil Nadu" -> Color(0xFF2E7D32)
        "Insurance"  -> Color(0xFFE65100)
        "Equipment"  -> Color(0xFF6A1B9A)
        "Irrigation" -> Color(0xFF00695C)
        else         -> Color(0xFF4E342E)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = categoryColor.copy(alpha = 0.1f)
                    ) {
                        Text(
                            scheme.category, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                            color = categoryColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(scheme.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AgriGreenDark)
                }
                IconButton(onClick = onBookmark, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        null, tint = if (isBookmarked) AgriGreen else AgriGray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(scheme.description, fontSize = 13.sp, color = Color(0xFF555555),
                maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)

            if (expanded) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = AgriDivider)
                Spacer(Modifier.height(10.dp))
                SchemeInfoRow("💰", "Benefits", scheme.benefits)
                Spacer(Modifier.height(6.dp))
                SchemeInfoRow("✅", "Eligibility", scheme.eligibility)
                if (!scheme.lastDate.isNullOrEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    SchemeInfoRow("📅", "Last Date", scheme.lastDate)
                }
                if (scheme.crops.isNotEmpty() && scheme.crops != listOf("All")) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Text("🌱 ", fontSize = 13.sp)
                        Column {
                            Text("Applicable Crops", fontSize = 11.sp, color = AgriGray, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                                scheme.crops.forEach { crop ->
                                    Surface(shape = RoundedCornerShape(8.dp), color = AgriGreenSurface) {
                                        Text(crop, fontSize = 11.sp, color = AgriGreen,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
                Text(if (expanded) "Show less" else "Show more", color = AgriGreen, fontSize = 12.sp)
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null, tint = AgriGreen, modifier = Modifier.size(16.dp))
            }

            HorizontalDivider(color = AgriDivider)
            Spacer(Modifier.height(10.dp))

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onApply,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AgriGreen),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Apply", fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = onShare,
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(14.dp), tint = AgriGreen)
                    Spacer(Modifier.width(4.dp))
                    Text("Share", fontSize = 13.sp, color = AgriGreen)
                }
            }
        }
    }
}

@Composable
private fun SchemeInfoRow(emoji: String, label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text("$emoji ", fontSize = 13.sp)
        Column {
            Text(label, fontSize = 11.sp, color = AgriGray, fontWeight = FontWeight.SemiBold)
            Text(value, fontSize = 13.sp, color = AgriGreenDark, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

private fun shareScheme(context: Context, scheme: GovScheme) {
    val text = "📋 ${scheme.name}\n\n${scheme.description}\n\n💰 Benefits: ${scheme.benefits}\n\n🔗 ${scheme.website}"
    context.startActivity(Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) },
        "Share Scheme"
    ))
}
