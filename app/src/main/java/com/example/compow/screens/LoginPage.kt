package com.example.compow.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.compow.ComPowApplication
import com.example.compow.network.SocketIOManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginPage(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = (context.applicationContext as ComPowApplication).database
    val userDao = database.userDao()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFB3E5FC)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Welcome Back",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2962FF)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                "Enter Email",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.Black,
                modifier = Modifier.align(Alignment.Start)
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("your.email@example.com") },
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = Color(0xFF2962FF),
                    unfocusedBorderColor = Color(0xFF2962FF)
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Password",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.Black,
                modifier = Modifier.align(Alignment.Start)
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Enter your password") },
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = Color(0xFF2962FF),
                    unfocusedBorderColor = Color(0xFF2962FF)
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Login Button
            Button(
                onClick = {
                    scope.launch {
                        isLoading = true

                        when {
                            email.isEmpty() || password.isEmpty() -> {
                                errorMessage = "Please fill all fields"
                                showError = true
                            }
                            else -> {
                                withContext(Dispatchers.IO) {
                                    // Get user by email
                                    val user = userDao.getUserByEmail(email)

                                    if (user != null) {
                                        // Save login state
                                        val prefs = context.getSharedPreferences("compow_prefs", Context.MODE_PRIVATE)
                                        prefs.edit().apply {
                                            putString("user_id", user.userId)
                                            putString("user_name", user.fullName)
                                            putString("user_email", user.email)
                                            putString("user_phone", user.phoneNumber)
                                            putBoolean("is_logged_in", true)
                                            apply()
                                        }

                                        withContext(Dispatchers.Main) {
                                            // Initialize Socket.IO connection
                                            val socketManager = SocketIOManager.getInstance()
                                            socketManager.connect()

                                            // Wait for connection then set user online
                                            scope.launch {
                                                socketManager.isConnected.collect { connected ->
                                                    if (connected) {
                                                        socketManager.joinUserRoom(user.userId)
                                                        socketManager.setUserOnline(user.userId, user.fullName)
                                                    }
                                                }
                                            }

                                            navController.navigate("home") {
                                                popUpTo("first") { inclusive = true }
                                            }
                                        }
                                    } else {
                                        withContext(Dispatchers.Main) {
                                            errorMessage = "Invalid email or password"
                                            showError = true
                                        }
                                    }
                                }
                            }
                        }

                        isLoading = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2962FF)
                ),
                shape = RoundedCornerShape(8.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White
                    )
                } else {
                    Text("Login", fontSize = 16.sp, color = Color.White)
                }
            }

            TextButton(onClick = { navController.navigate("signup") }) {
                Text("Don't have an account? Sign Up", color = Color(0xFF2962FF))
            }
        }
    }

    // Error Dialog
    val onDismiss = { showError = false }
    if (showError) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Login Failed") },
            text = { Text(errorMessage) },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("OK")
                }
            }
        )
    }
}