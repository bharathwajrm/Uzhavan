package com.example.uzhavan

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.uzhavan.ui.theme.*
import com.google.firebase.auth.FirebaseAuth

@Composable
fun LoginScreen(navController: NavController, viewModel: UserViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    val auth = FirebaseAuth.getInstance()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AgriTheme.GreenGradient)
    ) {
        // Decorative circles
        Box(
            modifier = Modifier
                .size(200.dp)
                .offset((-60).dp, (-60).dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
        )
        Box(
            modifier = Modifier
                .size(150.dp)
                .align(Alignment.BottomEnd)
                .offset(50.dp, 50.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(80.dp))

            // Logo area
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Eco,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Uzhavan",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
            Text(
                text = "Connecting Farmers, Growing Together",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(modifier = Modifier.padding(28.dp)) {
                    Text(
                        text = "Welcome Back 🌾",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgriGreenDark
                    )
                    Text(
                        text = "Sign in to your farm community",
                        fontSize = 13.sp,
                        color = AgriGray,
                        modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                    )

                    AgriTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = "Email Address",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    AgriTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Password",
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = AgriGray
                                )
                            }
                        }
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {}) {
                            Text("Forgot Password?", color = AgriGreen, fontSize = 13.sp)
                        }
                    }

                    if (errorMsg.isNotEmpty()) {
                        Text(
                            text = errorMsg,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    AgriButton(
                        text = if (isLoading) "Signing in..." else "Sign In",
                        enabled = !isLoading,
                        onClick = {
                            if (email.isNotEmpty() && password.isNotEmpty()) {
                                isLoading = true
                                errorMsg = ""
                                auth.signInWithEmailAndPassword(email, password)
                                    .addOnCompleteListener {
                                        isLoading = false
                                        if (it.isSuccessful) {
                                            navController.navigate("main") {
                                                popUpTo("login") { inclusive = true }
                                            }
                                        } else {
                                            errorMsg = it.exception?.message ?: "Login failed"
                                        }
                                    }
                            } else {
                                errorMsg = "Please fill all fields"
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("New to Uzhavan? ", color = AgriGray, fontSize = 14.sp)
                        TextButton(onClick = { navController.navigate("signup") }, contentPadding = PaddingValues(0.dp)) {
                            Text("Join Now", color = AgriGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Feature pills
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("🌱 Crops", "🚜 Machinery", "📊 Market").forEach { tag ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = tag,
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun SignupScreen(navController: NavController, viewModel: UserViewModel) {
    var fullName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var farmName by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var cropType by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    val auth = FirebaseAuth.getInstance()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AgriTheme.GreenGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Eco, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Uzhavan", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            }
            Text(
                text = "Join the farming community",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 28.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(modifier = Modifier.padding(28.dp)) {
                    Text("Create Account 🌿", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AgriGreenDark)
                    Text("Fill in your details to get started", fontSize = 13.sp, color = AgriGray, modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))

                    // Section: Basic Info
                    SectionLabel("Basic Information")
                    AgriTextField(value = fullName, onValueChange = { fullName = it }, label = "Full Name")
                    Spacer(modifier = Modifier.height(12.dp))
                    AgriTextField(value = username, onValueChange = { username = it.lowercase().replace(" ", "") }, label = "Username")
                    Spacer(modifier = Modifier.height(12.dp))
                    AgriTextField(
                        value = email, onValueChange = { email = it }, label = "Email Address",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    AgriTextField(
                        value = password, onValueChange = { password = it }, label = "Password",
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = AgriGray)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    AgriTextField(
                        value = confirmPassword, onValueChange = { confirmPassword = it }, label = "Confirm Password",
                        visualTransformation = PasswordVisualTransformation()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Section: Farm Info
                    SectionLabel("Farm Details (Optional)")
                    AgriTextField(value = farmName, onValueChange = { farmName = it }, label = "Farm Name")
                    Spacer(modifier = Modifier.height(12.dp))
                    AgriTextField(value = location, onValueChange = { location = it }, label = "Location / Village")
                    Spacer(modifier = Modifier.height(12.dp))
                    AgriTextField(value = cropType, onValueChange = { cropType = it }, label = "Primary Crop Type")

                    Spacer(modifier = Modifier.height(20.dp))

                    if (errorMsg.isNotEmpty()) {
                        Text(text = errorMsg, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                    }

                    AgriButton(
                        text = if (isLoading) "Creating Account..." else "Create Account",
                        enabled = !isLoading,
                        onClick = {
                            when {
                                fullName.isEmpty() || username.isEmpty() || email.isEmpty() || password.isEmpty() ->
                                    errorMsg = "Please fill all required fields"
                                password != confirmPassword ->
                                    errorMsg = "Passwords do not match"
                                password.length < 6 ->
                                    errorMsg = "Password must be at least 6 characters"
                                else -> {
                                    isLoading = true
                                    errorMsg = ""
                                    auth.createUserWithEmailAndPassword(email, password)
                                        .addOnCompleteListener {
                                            isLoading = false
                                            if (it.isSuccessful) {
                                                val uid = it.result?.user?.uid
                                                if (uid != null) {
                                                    viewModel.saveUserProfile(
                                                        uid, fullName, username, email,
                                                        farmName, location, cropType
                                                    )
                                                }
                                                navController.navigate("main") {
                                                    popUpTo("signup") { inclusive = true }
                                                }
                                            } else {
                                                errorMsg = it.exception?.message ?: "Signup failed"
                                            }
                                        }
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Already a member? ", color = AgriGray, fontSize = 14.sp)
                        TextButton(onClick = { navController.navigate("login") }, contentPadding = PaddingValues(0.dp)) {
                            Text("Sign In", color = AgriGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 12.dp)
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = AgriDivider)
        Text(
            text = "  $text  ",
            fontSize = 11.sp,
            color = AgriGray,
            fontWeight = FontWeight.Medium
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = AgriDivider)
    }
}
