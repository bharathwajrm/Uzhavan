package com.example.uzhavan

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
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
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.uzhavan.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer

// ── Constants ─────────────────────────────────────────────────────────────────
// Order must match model training: ['Alluvial soil', 'Black Soil', 'Clay soil', 'Red soil']
private val SOIL_CLASSES = listOf("Alluvial Soil", "Black Soil", "Clay Soil", "Red Soil")
private val SOIL_EMOJIS  = listOf("🟤", "⚫", "🔵", "🔴")
private val SOIL_CROPS   = listOf(
    "Rice, Wheat, Sugarcane, Cotton, Jute, Maize, Pulses",
    "Cotton, Soybean, Sorghum, Wheat, Sunflower, Chickpea",
    "Rice, Cabbage, Broccoli, Brussels sprouts, Lettuce",
    "Groundnut, Millets, Tobacco, Potato, Maize, Pulses"
)
private val SOIL_PH      = listOf("6.5 – 8.0", "7.5 – 8.5", "6.0 – 7.0", "5.5 – 7.5")
private val SOIL_DESC    = listOf(
    "Fertile, well-drained soil found near river plains. Rich in minerals.",
    "Clay-rich soil with high water retention. Also called Regur or Cotton soil.",
    "Dense, heavy soil with poor drainage but high nutrient content.",
    "Iron-rich soil with good drainage but low fertility and moisture retention."
)
private val SOIL_TIPS    = listOf(
    "Add organic compost to maintain nutrient levels. Rotate crops to prevent nutrient depletion.",
    "Avoid waterlogging — use raised beds or ridges. Add gypsum to improve soil structure.",
    "Improve drainage by adding sand or organic matter. Avoid tilling when wet.",
    "Enrich with organic manure to boost fertility. Add lime to neutralize acidity."
)

private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
private val STD  = floatArrayOf(0.229f, 0.224f, 0.225f)

private var ortSession: OrtSession? = null

private fun getOrCreateSession(context: android.content.Context): OrtSession {
    ortSession?.let { return it }
    val modelFile = File(context.filesDir, "soil_model.onnx")
    val assetSize = context.assets.openFd("soil_model.onnx").use { it.length }
    if (!modelFile.exists() || modelFile.length() != assetSize) {
        context.assets.open("soil_model.onnx").use { input ->
            modelFile.outputStream().use { output -> input.copyTo(output) }
        }
    }
    return OrtEnvironment.getEnvironment()
        .createSession(modelFile.absolutePath, OrtSession.SessionOptions())
        .also { ortSession = it }
}

// ── Screen ────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoilPredictionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var imageUri    by remember { mutableStateOf<Uri?>(null) }
    var isRunning   by remember { mutableStateOf(false) }
    var result      by remember { mutableStateOf<SoilResult?>(null) }
    var errorMsg    by remember { mutableStateOf<String?>(null) }

    // Temp file URI for camera capture
    val cameraUri = remember {
        val file = File(context.cacheDir, "soil_capture.jpg")
        FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { imageUri = it; result = null; errorMsg = null }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) { imageUri = cameraUri; result = null; errorMsg = null }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Soil Prediction", fontWeight = FontWeight.Bold, color = AgriGreenDark) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = AgriGreenDark)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = AgriBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Image preview / placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .border(1.5.dp, AgriDivider, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (imageUri != null) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = "Soil image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp))
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🪱", fontSize = 52.sp)
                        Text("Upload or capture a soil photo", fontSize = 14.sp, color = AgriGray)
                    }
                }
            }

            // Pick / Camera buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AgriGreenDark),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AgriGreen)
                ) {
                    Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Gallery")
                }
                OutlinedButton(
                    onClick = { cameraLauncher.launch(cameraUri) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AgriGreenDark),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AgriGreen)
                ) {
                    Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Camera")
                }
            }

            // Predict button
            Button(
                onClick = {
                    val uri = imageUri ?: return@Button
                    isRunning = true
                    errorMsg  = null
                    result    = null
                    scope.launch {
                        try {
                            val bitmap = withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(uri)?.use {
                                    BitmapFactory.decodeStream(it)
                                }
                            } ?: throw Exception("Could not read image")
                            result = withContext(Dispatchers.Default) {
                                runSoilModel(context, bitmap)
                            }
                        } catch (e: Exception) {
                            errorMsg = e.message ?: "Prediction failed"
                        } finally {
                            isRunning = false
                        }
                    }
                },
                enabled = imageUri != null && !isRunning,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AgriGreen)
            ) {
                if (isRunning) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Analysing…", fontWeight = FontWeight.SemiBold)
                } else {
                    Text("Predict Soil Type", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }

            // Error
            errorMsg?.let {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                ) {
                    Text("⚠️ $it", modifier = Modifier.padding(14.dp), color = Color(0xFFB71C1C), fontSize = 13.sp)
                }
            }

            // Result card
            result?.let { r ->
                SoilResultCard(r)
            }
        }
    }
}

// ── Result card ───────────────────────────────────────────────────────────────
@Composable
private fun SoilResultCard(r: SoilResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(r.emoji, fontSize = 40.sp)
                Column {
                    Text("Detected Soil", fontSize = 12.sp, color = AgriGray)
                    Text(r.label, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = AgriGreenDark)
                }
                Spacer(Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(20.dp), color = AgriGreenSurface) {
                    Text(
                        "${(r.confidence * 100).toInt()}%",
                        fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AgriGreen,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            HorizontalDivider(color = AgriDivider, thickness = 0.5.dp)

            // Confidence bars for all classes
            r.allScores.forEachIndexed { i, score ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${SOIL_EMOJIS[i]} ${SOIL_CLASSES[i]}", fontSize = 12.sp, color = AgriGreenDark)
                        Text("${(score * 100).toInt()}%", fontSize = 12.sp, color = AgriGray)
                    }
                    LinearProgressIndicator(
                        progress = { score },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
                        color = if (i == r.classIndex) AgriGreen else AgriGreenLight,
                        trackColor = AgriDivider
                    )
                }
            }

            HorizontalDivider(color = AgriDivider, thickness = 0.5.dp)

            // Description
            Text(r.description, fontSize = 13.sp, color = AgriGray, lineHeight = 20.sp)

            HorizontalDivider(color = AgriDivider, thickness = 0.5.dp)

            // Suitable crops
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("🌱 Suitable Crops", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = AgriGreenDark)
                Text(r.crops, fontSize = 13.sp, color = AgriGray)
            }

            // pH + Tips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(AgriGreenSurface)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🧪 pH Range", fontSize = 13.sp, color = AgriGreenDark, fontWeight = FontWeight.SemiBold)
                Text(r.ph, fontSize = 13.sp, color = AgriGreen, fontWeight = FontWeight.Bold)
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("💡 Tips", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = AgriGreenDark)
                Text(r.tips, fontSize = 13.sp, color = AgriGray, lineHeight = 20.sp)
            }
        }
    }
}

// ── Data class ────────────────────────────────────────────────────────────────
data class SoilResult(
    val classIndex: Int,
    val label: String,
    val emoji: String,
    val confidence: Float,
    val allScores: List<Float>,
    val description: String,
    val crops: String,
    val ph: String,
    val tips: String
)

// ── ONNX inference ────────────────────────────────────────────────────────────
private fun runSoilModel(context: android.content.Context, bitmap: Bitmap): SoilResult {
    // Scale to 224×224 and ensure ARGB_8888 format
    val resized = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
        .copy(Bitmap.Config.ARGB_8888, false)

    // HWC → CHW float32 with ImageNet normalisation
    val pixels = IntArray(224 * 224)
    resized.getPixels(pixels, 0, 224, 0, 0, 224, 224)

    val floatArray = FloatArray(3 * 224 * 224)
    val offset = 224 * 224
    for (i in pixels.indices) {
        val px = pixels[i]
        floatArray[i]              = (((px shr 16) and 0xFF) / 255f - MEAN[0]) / STD[0] // R plane
        floatArray[i + offset]     = (((px shr 8)  and 0xFF) / 255f - MEAN[1]) / STD[1] // G plane
        floatArray[i + offset * 2] = (( px         and 0xFF) / 255f - MEAN[2]) / STD[2] // B plane
    }

    // Copy model from assets to filesDir — ORT needs a real writable file path.
    // Always verify size matches to avoid stale copies from previous installs.
    val session = getOrCreateSession(context)

    val shape  = longArrayOf(1, 3, 224, 224)
    val buf    = FloatBuffer.wrap(floatArray)
    val tensor = OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), buf, shape)

    val logits: FloatArray
    try {
        session.run(mapOf("input" to tensor)).use { output ->
            logits = (output.get("output").get().value as Array<FloatArray>)[0]
        }
    } finally {
        tensor.close()
    }

    // Softmax
    val maxL   = logits.max()
    val exps   = logits.map { Math.exp((it - maxL).toDouble()).toFloat() }
    val sumExp = exps.sum()
    val probs  = exps.map { it / sumExp }

    val idx = probs.indices.maxByOrNull { probs[it] }!!

    return SoilResult(
        classIndex  = idx,
        label       = SOIL_CLASSES[idx],
        emoji       = SOIL_EMOJIS[idx],
        confidence  = probs[idx],
        allScores   = probs,
        description = SOIL_DESC[idx],
        crops       = SOIL_CROPS[idx],
        ph          = SOIL_PH[idx],
        tips        = SOIL_TIPS[idx]
    )
}
