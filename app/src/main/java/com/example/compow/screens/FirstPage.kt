package com.example.compow.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

@Composable
fun FirstPage(navController: NavController) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFB3E5FC)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Welcome to ComPow",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2962FF)
            )

            Text(
                text = "Emergency Alert System",
                fontSize = 16.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { navController.navigate("signup") },
                modifier = Modifier
                    .width(280.dp)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2962FF)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Get Started", fontSize = 18.sp, color = Color.White)
            }
        }
    }
}