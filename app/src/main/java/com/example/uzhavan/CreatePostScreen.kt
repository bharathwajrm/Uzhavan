package com.example.uzhavan

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.uzhavan.ui.theme.*

private val postCategories = listOf(
    "general" to "🌿 General",
    "crops"   to "🌱 Crops",
    "harvest" to "🌾 Harvest",
    "machinery" to "🚜 Machinery",
    "market"  to "📊 Market",
    "livestock" to "🐄 Livestock",
    "organic" to "🌿 Organic",
    "irrigation" to "💧 Irrigation",
    "poultry" to "🐓 Poultry",
    "vegetables" to "🥬 Vegetables"
)

@Composable
fun CreatePostScreen(viewModel: UserViewModel) {
    val context = LocalContext.current
    var caption by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("general") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    val uploadState by viewModel.uploadState.collectAsState()

    // Reset on success
    LaunchedEffect(uploadState) {
        if (uploadState is UserViewModel.UploadState.Success) {
            caption = ""
            selectedImageUri = null
            viewModel.resetUploadState()
        }
    }

    // Gallery picker
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { selectedImageUri = it } }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) galleryLauncher.launch("image/*") }

    val isUploading = uploadState is UserViewModel.UploadState.Uploading
    val uploadProgress = (uploadState as? UserViewModel.UploadState.Uploading)?.progress ?: 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AgriBackground)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Create Post", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = AgriGreenDark)

        // Image picker area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(AgriGreenSurface)
                .border(2.dp, AgriDivider, RoundedCornerShape(18.dp))
                .clickable(enabled = !isUploading) {
                    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                        Manifest.permission.READ_MEDIA_IMAGES
                    else
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    permissionLauncher.launch(permission)
                },
            contentAlignment = Alignment.Center
        ) {
            if (selectedImageUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(selectedImageUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp))
                )
                // Change photo overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("Change Photo", color = Color.White, fontSize = 11.sp)
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AddPhotoAlternate, null, tint = AgriGreen, modifier = Modifier.size(52.dp))
                    Text("Tap to add photo", color = AgriGreen, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("Gallery · Camera", color = AgriGray, fontSize = 12.sp)
                }
            }
        }

        // Upload progress
        if (isUploading) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = AgriGreen, strokeWidth = 2.dp)
                    Text("Uploading… ${(uploadProgress * 100).toInt()}%", fontSize = 13.sp, color = AgriGreen)
                }
                LinearProgressIndicator(
                    progress = { uploadProgress },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                    color = AgriGreen,
                    trackColor = AgriGreenSurface
                )
            }
        }

        // Error state
        if (uploadState is UserViewModel.UploadState.Error) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFFFEBEE)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFE53935), modifier = Modifier.size(18.dp))
                    Text(
                        (uploadState as UserViewModel.UploadState.Error).message,
                        color = Color(0xFFE53935), fontSize = 13.sp
                    )
                }
            }
        }

        // Category
        Text("Category", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = AgriGreenDark)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(postCategories.size) { i ->
                val (key, label) = postCategories[i]
                FilterChip(
                    selected = category == key,
                    onClick  = { category = key },
                    label    = { Text(label, fontSize = 12.sp) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AgriGreen,
                        selectedLabelColor     = Color.White,
                        containerColor         = Color.White,
                        labelColor             = AgriGreenDark
                    )
                )
            }
        }

        // Caption
        OutlinedTextField(
            value = caption,
            onValueChange = { caption = it },
            label = { Text("Write a caption…", color = AgriGray) },
            modifier = Modifier.fillMaxWidth().height(120.dp),
            shape = RoundedCornerShape(14.dp),
            maxLines = 6,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = AgriGreen,
                unfocusedBorderColor = AgriDivider,
                focusedTextColor     = AgriGreenDark,
                unfocusedTextColor   = AgriGreenDark
            )
        )

        // Share button
        AgriButton(
            text    = if (isUploading) "Uploading…" else "Share Post",
            enabled = !isUploading && caption.trim().isNotEmpty(),
            onClick = {
                if (selectedImageUri != null) {
                    viewModel.uploadPostWithImage(context, selectedImageUri!!, caption.trim(), category)
                } else {
                    viewModel.createTextPost(caption.trim(), category)
                }
            }
        )

        Spacer(Modifier.height(8.dp))
    }
}
