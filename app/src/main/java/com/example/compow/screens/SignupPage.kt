package com.example.compow.screens

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.core.content.edit
import androidx.navigation.NavController
import com.example.compow.ComPowApplication
import com.example.compow.data.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("DEPRECATION")
@Composable
fun SignupPage(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = (context.applicationContext as ComPowApplication).database
    val userDao = database.userDao()

    var fullName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("+254") }
    var yearOfStudy by remember { mutableStateOf("") }
    var courseOfStudy by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var expandedYear by remember { mutableStateOf(false) }
    var expandedCourse by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    val years = listOf("1", "2", "3", "4")
    val courses =
        listOf("BCSF", "BIT", "BCOM", "BA", "BSC", "Engineering", "Medicine", "BBA", "BSN", "BED")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFB3E5FC))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Create Account",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2962FF)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Full Name
            Text("Full Name", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                placeholder = { Text("Enter your full name", fontSize = 14.sp) },
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = Color(0xFF2962FF),
                    unfocusedBorderColor = Color(0xFF2962FF)
                )
            )

            // Phone Number
            Text("Phone number", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { if (it.startsWith("+254")) phoneNumber = it },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                placeholder = { Text("+254712345678", fontSize = 14.sp) },
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = Color(0xFF2962FF),
                    unfocusedBorderColor = Color(0xFF2962FF)
                )
            )

            // Year of Study
            Text("Year of Study", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            ExposedDropdownMenuBox(
                expanded = expandedYear,
                onExpandedChange = { expandedYear = !expandedYear }
            ) {
                OutlinedTextField(
                    value = yearOfStudy,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().height(52.dp).menuAnchor(),
                    placeholder = { Text("Select year", fontSize = 14.sp) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedYear)
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = Color(0xFF2962FF),
                        unfocusedBorderColor = Color(0xFF2962FF)
                    )
                )

                ExposedDropdownMenu(
                    expanded = expandedYear,
                    onDismissRequest = { expandedYear = false }
                ) {
                    years.forEach { year ->
                        DropdownMenuItem(
                            text = { Text(year) },
                            onClick = {
                                yearOfStudy = year
                                expandedYear = false
                            }
                        )
                    }
                }
            }

            // Course of Study
            Text("Course of study", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            ExposedDropdownMenuBox(
                expanded = expandedCourse,
                onExpandedChange = { expandedCourse = !expandedCourse }
            ) {
                OutlinedTextField(
                    value = courseOfStudy,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().height(52.dp).menuAnchor(),
                    placeholder = { Text("Select course", fontSize = 14.sp) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCourse)
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = Color(0xFF2962FF),
                        unfocusedBorderColor = Color(0xFF2962FF)
                    )
                )

                ExposedDropdownMenu(
                    expanded = expandedCourse,
                    onDismissRequest = { expandedCourse = false }
                ) {
                    courses.forEach { course ->
                        DropdownMenuItem(
                            text = { Text(course) },
                            onClick = {
                                courseOfStudy = course
                                expandedCourse = false
                            }
                        )
                    }
                }
            }

            // Email
            Text("Email", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                placeholder = { Text("example@student.ac.ke", fontSize = 14.sp) },
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = Color(0xFF2962FF),
                    unfocusedBorderColor = Color(0xFF2962FF)
                )
            )

            // Password
            Text("Password", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                placeholder = { Text("Minimum 6 characters", fontSize = 14.sp) },
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = Color(0xFF2962FF),
                    unfocusedBorderColor = Color(0xFF2962FF)
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Sign Up Button
            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        var success = false

                        // Validation and Local Database Logic
                        withContext(Dispatchers.IO) {
                            when {
                                fullName.isBlank() || email.isBlank() || password.isBlank() || yearOfStudy.isBlank() || courseOfStudy.isBlank() -> {
                                    errorMessage = "Please fill all fields"
                                    showError = true
                                }
                                password.length < 6 -> {
                                    errorMessage = "Password must be at least 6 characters"
                                    showError = true
                                }
                                !email.endsWith("@student.ac.ke") -> {
                                    errorMessage = "Please use a valid student email"
                                    showError = true
                                }
                                phoneNumber.length < 13 -> {
                                    errorMessage = "Invalid phone number"
                                    showError = true
                                }
                                userDao.isEmailExists(email) -> {
                                    errorMessage = "Email already registered"
                                    showError = true
                                }
                                userDao.isPhoneNumberExists(phoneNumber) -> {
                                    errorMessage = "Phone number already registered"
                                    showError = true
                                }
                                else -> {
                                    val userId = UUID.randomUUID().toString()
                                    val user = UserEntity(
                                        userId = userId,
                                        fullName = fullName,
                                        email = email,
                                        phoneNumber = phoneNumber,
                                        yearOfStudy = yearOfStudy.toIntOrNull() ?: 1,
                                        courseOfStudy = courseOfStudy
                                    )
                                    userDao.insertUser(user)

                                    // This write is now guaranteed to complete before proceeding.
                                    context.getSharedPreferences("compow_prefs", Context.MODE_PRIVATE).edit(commit = true) {
                                        putString("user_id", userId)
                                        putString("user_name", fullName)
                                        putBoolean("is_logged_in", true)
                                    }
                                    success = true
                                }
                            }
                        }

                        // Navigate immediately after successful local signup.
                        // MainActivity will handle the socket connection.
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
                    .height(48.dp),
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
                    Text("Sign Up", fontSize = 16.sp, color = Color.White)
                }
            }

            TextButton(onClick = { navController.navigate("login") }) {
                Text("Already have an account? Login", color = Color(0xFF2962FF), fontSize = 14.sp)
            }
        }

        // Error Dialog
        val onDismiss = { showError = false }
        if (showError) {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Signup Failed") },
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
