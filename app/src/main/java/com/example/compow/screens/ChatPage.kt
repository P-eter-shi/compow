package com.example.compow.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.navigation.NavController
import com.example.compow.ComPowApplication
import com.example.compow.data.ContactCategory
import com.example.compow.network.SocketIOManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val fromUserId: String,
    val fromUserName: String,
    val message: String,
    val timestamp: Long,
    val isSentByMe: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatPage(navController: NavController) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("compow_prefs", Context.MODE_PRIVATE)
    val scope = rememberCoroutineScope()

    val database = (context.applicationContext as ComPowApplication).database
    val contactDao = database.contactDao()
    val userDao = database.userDao()

    val socketManager = remember { SocketIOManager.getInstance() }
    val isSocketConnected by socketManager.isConnected.collectAsState()

    var messageText by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var currentUserId by remember { mutableStateOf("") }
    var currentUserName by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Load user info
    LaunchedEffect(Unit) {
        scope.launch {
            val user = withContext(Dispatchers.IO) {
                userDao.getCurrentUser()
            }
            currentUserId = user?.userId ?: ""
            currentUserName = user?.fullName ?: "User"
        }
    }

    // Listen for incoming messages
    val receivedMessage by socketManager.chatMessageReceived.collectAsState()

    LaunchedEffect(receivedMessage) {
        receivedMessage?.let { msg ->
            // Don't add if it's from the current user (already added on send)
            if (msg.fromUserId != currentUserId) {
                messages = messages + ChatMessage(
                    fromUserId = msg.fromUserId,
                    fromUserName = msg.fromUserName,
                    message = msg.message,
                    timestamp = msg.timestamp,
                    isSentByMe = false
                )
            }
            // Clear the received message
            socketManager.clearChatMessage()
        }
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFB3E5FC))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
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
                                "Chat Room",
                                fontSize = 20.sp,
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
                                            if (isSocketConnected) Color.Green else Color.Red,
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
                                text = { Text("Settings") },
                                onClick = {
                                    showMenu = false
                                    navController.navigate("settings")
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Settings, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear Chat") },
                                onClick = {
                                    showMenu = false
                                    messages = emptyList()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                }
                            )
                        }
                    }
                }
            }

            // Messages List
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color.Gray
                        )
                        Text(
                            "No messages yet",
                            fontSize = 16.sp,
                            color = Color.Gray
                        )
                        Text(
                            "Start a conversation with your contacts",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(messages) { message ->
                        MessageBubble(message)
                    }
                }
            }

            // Message Input
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                "Type a message...",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                        },
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF5F5F5),
                            unfocusedContainerColor = Color(0xFFF5F5F5),
                            focusedBorderColor = Color(0xFF2962FF),
                            unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f)
                        ),
                        maxLines = 4,
                        enabled = isSocketConnected
                    )

                    IconButton(
                        onClick = {
                            if (messageText.isNotBlank() && !isSending) {
                                isSending = true
                                scope.launch {
                                    try {
                                        // Get enabled contacts
                                        val enabledContactIds = withContext(Dispatchers.IO) {
                                            val allContacts = mutableListOf<String>()

                                            if (prefs.getBoolean("circle_enabled", false)) {
                                                val circleContacts = contactDao.getContactsByCategory(ContactCategory.CIRCLE)
                                                allContacts.addAll(circleContacts.filter { it.isEnabled }.map { it.id.toString() })
                                            }
                                            if (prefs.getBoolean("group_enabled", false)) {
                                                val groupContacts = contactDao.getContactsByCategory(ContactCategory.GROUP)
                                                allContacts.addAll(groupContacts.filter { it.isEnabled }.map { it.id.toString() })
                                            }
                                            if (prefs.getBoolean("community_enabled", false)) {
                                                val communityContacts = contactDao.getContactsByCategory(ContactCategory.COMMUNITY)
                                                allContacts.addAll(communityContacts.filter { it.isEnabled }.map { it.id.toString() })
                                            }

                                            allContacts.distinct()
                                        }

                                        if (enabledContactIds.isEmpty()) {
                                            // Show toast: No contacts enabled
                                            return@launch
                                        }

                                        val timestamp = System.currentTimeMillis()

                                        // Send message via Socket.IO
                                        socketManager.sendChatMessage(
                                            fromUserId = currentUserId,
                                            fromUserName = currentUserName,
                                            message = messageText,
                                            contactIds = enabledContactIds
                                        ) { success, error ->
                                            if (success) {
                                                // Add message to local list
                                                messages = messages + ChatMessage(
                                                    fromUserId = currentUserId,
                                                    fromUserName = currentUserName,
                                                    message = messageText,
                                                    timestamp = timestamp,
                                                    isSentByMe = true
                                                )
                                                messageText = ""
                                            }
                                        }

                                    } catch (e: Exception) {
                                        // Handle error
                                    } finally {
                                        isSending = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                if (messageText.isNotBlank() && isSocketConnected)
                                    Color(0xFF2962FF)
                                else
                                    Color.Gray,
                                CircleShape
                            ),
                        enabled = messageText.isNotBlank() && isSocketConnected && !isSending
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White
                            )
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timeString = timeFormat.format(Date(message.timestamp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isSentByMe) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isSentByMe) 16.dp else 4.dp,
                bottomEnd = if (message.isSentByMe) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isSentByMe)
                    Color(0xFF2962FF)
                else
                    Color.White
            ),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!message.isSentByMe) {
                    Text(
                        message.fromUserName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2962FF)
                    )
                }

                Text(
                    message.message,
                    fontSize = 14.sp,
                    color = if (message.isSentByMe) Color.White else Color.Black
                )

                Text(
                    timeString,
                    fontSize = 10.sp,
                    color = if (message.isSentByMe)
                        Color.White.copy(alpha = 0.7f)
                    else
                        Color.Gray,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}