package com.example.compow.screens

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.navigation.NavController
import com.example.compow.ComPowApplication
import com.example.compow.data.UserEntity
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupPage(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = (context.applicationContext as ComPowApplication).database
    val userDao = database.userDao()

    var phoneNumber by remember { mutableStateOf("+254") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    // Store Google account info
    var googleUserName by remember { mutableStateOf<String?>(null) }
    var googleEmail by remember { mutableStateOf<String?>(null) }
    var googlePhotoUrl by remember { mutableStateOf<String?>(null) }
    var googleId by remember { mutableStateOf<String?>(null) }

    val credentialManager = remember { CredentialManager.create(context) }

    // Function to handle Google Sign-In with Credential Manager
    fun signInWithGoogle() {
        scope.launch {
            isLoading = true
            try {
                // Generate a nonce for security
                val rawNonce = UUID.randomUUID().toString()
                val bytes = rawNonce.toByteArray()
                val md = MessageDigest.getInstance("SHA-256")
                val digest = md.digest(bytes)
                val hashedNonce = digest.fold("") { str, it -> str + "%02x".format(it) }

                // Configure Google ID option
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId("992438554168-1bnkv8rmerfluj7s7nj0t7ucqpl6bupt.apps.googleusercontent.com") // Replace with your Web Client ID from Firebase
                    .setNonce(hashedNonce)
                    .build()

                // Build credential request
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                // Get credential
                val result = credentialManager.getCredential(
                    request = request,
                    context = context
                )

                // Handle the successfully returned credential
                val credential = result.credential
                when (credential.type) {
                    GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL -> {
                        val googleIdTokenCredential = GoogleIdTokenCredential
                            .createFrom(credential.data)

                        googleUserName = googleIdTokenCredential.displayName
                        googleEmail = googleIdTokenCredential.id
                        googlePhotoUrl = googleIdTokenCredential.profilePictureUri?.toString()
                        googleId = googleIdTokenCredential.id

                        Log.d("SignupPage", "Google Sign-In successful: $googleEmail")
                    }
                    else -> {
                        Log.e("SignupPage", "Unexpected credential type: ${credential.type}")
                        errorMessage = "Unexpected credential type"
                        showError = true
                    }
                }
            } catch (e: GetCredentialException) {
                Log.e("SignupPage", "Google Sign-In failed", e)
                errorMessage = "Google Sign-In failed: ${e.message}"
                showError = true
            } catch (e: Exception) {
                Log.e("SignupPage", "Unexpected error", e)
                errorMessage = "An unexpected error occurred"
                showError = true
            } finally {
                isLoading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFB3E5FC))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "Create Account",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2962FF)
            )

            Text(
                "Sign up with Google and add your phone number",
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Google Sign-In Button
            if (googleEmail == null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = { signInWithGoogle() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                            enabled = !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color(0xFF2962FF)
                                )
                            } else {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "G",
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF4285F4)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        "Sign in with Google",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Show Google account info
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "✓ Signed in as:",
                            fontSize = 14.sp,
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            googleUserName ?: "Unknown",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Text(
                            googleEmail ?: "",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                googleUserName = null
                                googleEmail = null
                                googlePhotoUrl = null
                                googleId = null
                            }
                        ) {
                            Text("Change Account", color = Color(0xFF2962FF))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Phone Number Input
                Text(
                    "Phone Number (Required for SMS alerts)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.Black
                )

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { if (it.startsWith("+254")) phoneNumber = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("+254712345678", fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Phone,
                            contentDescription = null,
                            tint = Color(0xFF2962FF)
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = Color(0xFF2962FF),
                        unfocusedBorderColor = Color(0xFF2962FF)
                    )
                )

                Text(
                    "This number will receive emergency SMS alerts",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Complete Signup Button
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            var success = false

                            withContext(Dispatchers.IO) {
                                when {
                                    googleEmail == null -> {
                                        errorMessage = "Please sign in with Google first"
                                        showError = true
                                    }
                                    phoneNumber.length < 13 -> {
                                        errorMessage = "Please enter a valid phone number"
                                        showError = true
                                    }
                                    userDao.isEmailExists(googleEmail!!) -> {
                                        errorMessage = "This Google account is already registered"
                                        showError = true
                                    }
                                    userDao.isPhoneNumberExists(phoneNumber) -> {
                                        errorMessage = "This phone number is already registered"
                                        showError = true
                                    }
                                    else -> {
                                        val userId = UUID.randomUUID().toString()
                                        val user = UserEntity(
                                            userId = userId,
                                            fullName = googleUserName ?: "User",
                                            email = googleEmail ?: "",
                                            phoneNumber = phoneNumber,
                                            profilePictureUri = googlePhotoUrl,
                                            googleId = googleId
                                        )
                                        userDao.insertUser(user)

                                        context.getSharedPreferences("compow_prefs", Context.MODE_PRIVATE)
                                            .edit(commit = true) {
                                                putString("user_id", userId)
                                                putString("user_name", user.fullName)
                                                putString("user_email", user.email)
                                                putString("user_phone", phoneNumber)
                                                putBoolean("is_logged_in", true)
                                            }
                                        success = true
                                    }
                                }
                            }

                            if (success) {
                                withContext(Dispatchers.Main) {
                                    navController.navigate("home") {
                                        popUpTo("first") { inclusive = true }
                                    }
                                }
                            }
                            isLoading = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2962FF)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading && googleEmail != null
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White
                        )
                    } else {
                        Text("Complete Signup", fontSize = 16.sp, color = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = { navController.navigate("first") }) {
                Text("← Back", color = Color(0xFF2962FF), fontSize = 14.sp)
            }
        }

        // Error Dialog
        val onDismiss = { showError = false }
        if (showError) {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Signup Error") },
                text = { Text(errorMessage) },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text("OK")
                    }
                }
            )
        }
    }
}
