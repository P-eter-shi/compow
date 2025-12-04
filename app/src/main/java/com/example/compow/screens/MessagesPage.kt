package com.example.compow.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.navigation.NavController
import com.example.compow.ComPowApplication
import com.example.compow.SocketForegroundService
import com.example.compow.network.SocketIOManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesPage(navController: NavController) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("compow_prefs", Context.MODE_PRIVATE)
    val scope = rememberCoroutineScope()

    val database = (context.applicationContext as ComPowApplication).database
    val userDao = database.userDao()
    val contactDao = database.contactDao()

    val socketManager = remember { SocketIOManager.getInstance() }
    val isSocketConnected by socketManager.isConnected.collectAsState()

    var isInChatRoom by remember { mutableStateOf(prefs.getBoolean("is_in_chat_room", false)) }
    var isJoiningRoom by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var dialogMessage by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }

    var circleEnabled by remember { mutableStateOf(prefs.getBoolean("circle_enabled", true)) }
    var groupEnabled by remember { mutableStateOf(prefs.getBoolean("group_enabled", true)) }
    var communityEnabled by remember { mutableStateOf(prefs.getBoolean("community_enabled", false)) }

    var enabledContactCount by remember { mutableIntStateOf(0) }

    // Load enabled contact count
    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            var count = 0
            if (circleEnabled) {
                count += contactDao.getContactCountByCategory(com.example.compow.data.ContactCategory.CIRCLE)
            }
            if (groupEnabled) {
                count += contactDao.getContactCountByCategory(com.example.compow.data.ContactCategory.GROUP)
            }
            if (communityEnabled) {
                count += contactDao.getContactCountByCategory(com.example.compow.data.ContactCategory.COMMUNITY)
            }
            enabledContactCount = count
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
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color(0xFF2962FF)
                            )
                        }

                        Column {
                            Text(
                                "💬 Messages",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            if (isSocketConnected) Color(0xFF4CAF50) else Color.Red,
                                            CircleShape
                                        )
                                )
                                Text(
                                    if (isSocketConnected) "Connected" else "Offline",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }

                    Box {
                        IconButton(onClick = { showMenu = !showMenu }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Menu",
                                tint = Color.Black
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Home") },
                                onClick = {
                                    showMenu = false
                                    navController.navigate("home")
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Home, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = {
                                    showMenu = false
                                    navController.navigate("settings")
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Settings, contentDescription = null)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Chat Room Status Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Status Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(
                                    if (isInChatRoom && isSocketConnected)
                                        Color(0xFF4CAF50)
                                    else
                                        Color.Gray,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Chat,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Chat Room Status",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(
                                            if (isInChatRoom && isSocketConnected)
                                                Color(0xFF4CAF50)
                                            else
                                                Color.Gray,
                                            CircleShape
                                        )
                                )
                                Text(
                                    if (isInChatRoom) "✓ In Chat Room" else "Not in room",
                                    fontSize = 14.sp,
                                    color = if (isInChatRoom) Color(0xFF4CAF50) else Color.Gray,
                                    fontWeight = if (isInChatRoom) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    // Info Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        InfoCard(
                            icon = "👥",
                            label = "Contacts",
                            value = "$enabledContactCount",
                            color = Color(0xFF2962FF),
                            modifier = Modifier.weight(1f)
                        )
                        InfoCard(
                            icon = if (isSocketConnected) "🟢" else "🔴",
                            label = "Status",
                            value = if (isSocketConnected) "Online" else "Offline",
                            color = if (isSocketConnected) Color(0xFF4CAF50) else Color.Red,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider()

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Join Button
                        Button(
                            onClick = {
                                if (isSocketConnected) {
                                    isJoiningRoom = true
                                    scope.launch {
                                        try {
                                            val user = withContext(Dispatchers.IO) {
                                                userDao.getCurrentUser()
                                            }

                                            val userId = user?.userId ?: "unknown"
                                            val fullName = user?.fullName ?: "User"

                                            socketManager.joinUserRoom(userId, fullName)
                                            SocketForegroundService.joinChatRoom(context)

                                            prefs.edit {
                                                putBoolean("is_in_chat_room", true)
                                            }

                                            isInChatRoom = true
                                            dialogMessage = "✅ Successfully joined chat room!"
                                            showSuccessDialog = true
                                        } catch (e: Exception) {
                                            dialogMessage = "Failed to join room: ${e.message}"
                                            showErrorDialog = true
                                        } finally {
                                            isJoiningRoom = false
                                        }
                                    }
                                } else {
                                    dialogMessage = "Cannot join room. Please check your connection."
                                    showErrorDialog = true
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSocketConnected && !isInChatRoom)
                                    Color(0xFF4CAF50)
                                else
                                    Color.Gray
                            ),
                            shape = RoundedCornerShape(12.dp),
                            enabled = isSocketConnected && !isInChatRoom && !isJoiningRoom
                        ) {
                            if (isJoiningRoom) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White
                                )
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Login,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text("Join", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Leave Button
                        Button(
                            onClick = {
                                isJoiningRoom = true
                                scope.launch {
                                    try {
                                        val user = withContext(Dispatchers.IO) {
                                            userDao.getCurrentUser()
                                        }

                                        val userId = user?.userId ?: "unknown"

                                        socketManager.leaveUserRoom(userId)
                                        SocketForegroundService.leaveChatRoom(context)

                                        prefs.edit {
                                            putBoolean("is_in_chat_room", false)
                                        }

                                        isInChatRoom = false
                                        dialogMessage = "Left chat room. You'll still receive emergency alerts."
                                        showSuccessDialog = true
                                    } catch (e: Exception) {
                                        dialogMessage = "Failed to leave room: ${e.message}"
                                        showErrorDialog = true
                                    } finally {
                                        isJoiningRoom = false
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isInChatRoom) Color(0xFFF44336) else Color.Gray
                            ),
                            shape = RoundedCornerShape(12.dp),
                            enabled = isInChatRoom && !isJoiningRoom
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text("Leave", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Open Chat Button (only visible when in room)
                    if (isInChatRoom) {
                        Button(
                            onClick = {
                                navController.navigate("chat")
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2962FF)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Chat,
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    "Open Chat",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Emergency Resources Section
            Text(
                "🚨 Emergency Resources",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Actions Grid
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    EmergencyResourceCard(
                        icon = "🚔",
                        title = "Police",
                        description = "999 / 112",
                        color = Color(0xFF2962FF),
                        modifier = Modifier.weight(1f)
                    )
                    EmergencyResourceCard(
                        icon = "🏥",
                        title = "Ambulance",
                        description = "999 / 112",
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    EmergencyResourceCard(
                        icon = "🚒",
                        title = "Fire",
                        description = "999 / 112",
                        color = Color(0xFFF44336),
                        modifier = Modifier.weight(1f)
                    )
                    EmergencyResourceCard(
                        icon = "🆘",
                        title = "Red Cross",
                        description = "1199",
                        color = Color(0xFFFF5722),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Tips Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFE3F2FD)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFF2962FF),
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            "💡 Chat Room Tips",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2962FF)
                        )
                    }
                    Text(
                        "• Only enabled contacts can see your messages",
                        fontSize = 14.sp,
                        color = Color.Black
                    )
                    Text(
                        "• Chat room works even when app is in background",
                        fontSize = 14.sp,
                        color = Color.Black
                    )
                    Text(
                        "• Emergency alerts work independently of chat status",
                        fontSize = 14.sp,
                        color = Color.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Success Dialog
    val onDismiss = { showErrorDialog = false }
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                    Text("Success")
                }
            },
            text = { Text(dialogMessage) },
            confirmButton = {
                TextButton(onClick = { onDismiss() }) {
                    Text("OK", color = Color(0xFF2962FF))
                }
            }
        )
    }

    // Error Dialog
    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF9800))
                    Text("Notice")
                }
            },
            text = { Text(dialogMessage) },
            confirmButton = {
                TextButton(onClick = { onDismiss() }) {
                    Text("OK", color = Color(0xFF2962FF))
                }
            }
        )
    }
}

@Composable
fun InfoCard(
    icon: String,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                icon,
                fontSize = 28.sp
            )
            Text(
                value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                label,
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun EmergencyResourceCard(
    icon: String,
    title: String,
    description: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                icon,
                fontSize = 36.sp
            )
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Text(
                description,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}