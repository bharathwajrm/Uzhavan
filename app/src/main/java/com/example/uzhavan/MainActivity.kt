package com.example.uzhavan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.uzhavan.ui.theme.UzhavanTheme
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: UserViewModel = viewModel()
            viewModel.initPreferences(applicationContext)

            // ── Notification permission ───────────────────────────────────────
            var notifPermissionAsked by remember { mutableStateOf(false) }
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) startChatNotificationService()
            }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notifPermissionAsked) {
                    notifPermissionAsked = true
                    val already = ContextCompat.checkSelfPermission(
                        this@MainActivity, Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                    if (already) startChatNotificationService()
                    else permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    // Android < 13 — no runtime permission needed
                    startChatNotificationService()
                }
            }

            UzhavanTheme {
                val navController = rememberNavController()
                val auth = FirebaseAuth.getInstance()

                val startDestination = if (auth.currentUser != null) {
                    viewModel.fetchUsername(auth.currentUser!!.uid)
                    "main"
                } else {
                    "login"
                }

                NavHost(navController = navController, startDestination = startDestination) {
                    composable("login") { LoginScreen(navController, viewModel) }
                    composable("signup") { SignupScreen(navController, viewModel) }
                    composable("main") { MainPage(navController, viewModel) }
                }
            }
        }
    }

    private fun startChatNotificationService() {
        val intent = Intent(this, ChatNotificationService::class.java)
        startService(intent)
    }
}
