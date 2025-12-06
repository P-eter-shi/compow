package com.example.compow

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.compow.screens.*
import com.example.compow.ui.theme.ComPowTheme
import com.example.compow.utils.PermissionsManager
import androidx.core.content.edit

class MainActivity : ComponentActivity() {

    private lateinit var permissionsManager: PermissionsManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Toast.makeText(this, "✅ All permissions granted", Toast.LENGTH_SHORT).show()
            startSocketServiceIfLoggedIn()
        } else {
            Toast.makeText(
                this,
                "⚠️ Some permissions denied. App may not work properly.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionsManager = PermissionsManager(this)

        // Check permissions first
        if (permissionsManager.hasAllPermissions()) {
            startSocketServiceIfLoggedIn()
        } else {
            requestRequiredPermissions()
        }

        setContent {
            ComPowTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ComPowApp()
                }
            }
        }
    }

    /**
     * Start background socket service if user is logged in
     * This keeps the connection alive even when app is in background
     * Also auto-joins chat room if user was previously in one
     */
    private fun startSocketServiceIfLoggedIn() {
        try {
            val prefs = getSharedPreferences("compow_prefs", MODE_PRIVATE)
            val userId = prefs.getString("user_id", null)
            val userName = prefs.getString("user_name", null)
            val wasInChatRoom = prefs.getBoolean("is_in_chat_room", false)

            if (userId != null && userName != null) {
                Log.d("MainActivity", "🚀 Starting background socket service")
                Log.d("MainActivity", "👤 User: $userName ($userId)")

                // Start foreground service for persistent connection
                SocketForegroundService.startConnection(this, userId, userName)

                // Auto-join chat room if user was previously in one
                if (wasInChatRoom) {
                    Log.d("MainActivity", "🚪 Auto-joining chat room")
                    // Give socket time to connect, then join room
                    android.os.Handler(mainLooper).postDelayed({
                        SocketForegroundService.joinChatRoom(this)
                    }, 3000) // Wait 3 seconds for connection
                }
            } else {
                Log.w("MainActivity", "⚠️ No user logged in, skipping socket service")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ Error starting socket service: ${e.message}")
        }
    }

    private fun requestRequiredPermissions() {
        val requiredPermissions = mutableListOf(
            Manifest.permission.VIBRATE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CALL_PHONE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        permissionLauncher.launch(requiredPermissions.toTypedArray())
    }

    override fun onResume() {
        super.onResume()

        // Service handles connection, but restart if needed
        val prefs = getSharedPreferences("compow_prefs", MODE_PRIVATE)
        val userId = prefs.getString("user_id", null)

        if (userId != null) {
            // Check if service is running, restart if needed
            startSocketServiceIfLoggedIn()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Only disconnect if user explicitly logs out
        // Otherwise, keep service running in background
        val prefs = getSharedPreferences("compow_prefs", MODE_PRIVATE)
        val shouldDisconnect = prefs.getBoolean("user_logged_out", false)

        if (shouldDisconnect) {
            Log.d("MainActivity", "🔴 User logged out, stopping socket service")
            SocketForegroundService.stopConnection(this)
            prefs.edit {
                putBoolean("user_logged_out", false)
                putBoolean("is_in_chat_room", false)
            }
        } else {
            Log.d("MainActivity", "✅ Keeping socket service alive in background")
        }
    }
}

@Composable
fun ComPowApp() {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = context.getSharedPreferences("compow_prefs", Context.MODE_PRIVATE)
    val isLoggedIn = prefs.getBoolean("is_logged_in", false)

    val startDestination = if (isLoggedIn) "home" else "first"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("first") { FirstPage(navController) }
        composable("signup") { SignupPage(navController) }
        composable("home") { HomePage(navController) }
        composable("settings") { SettingsPage(navController) }
        composable("destination") { DestinationPage(navController) }
        composable("messages") { MessagesPage(navController) }
        composable("chat") { ChatPage(navController) }
    }
}