package com.example.compow.screens

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import com.example.compow.ComPowApplication
import com.example.compow.data.ContactCategory
import com.example.compow.data.ContactEntity
import com.example.compow.network.SocketIOManager
import com.example.compow.utils.AccessibilityHelper
import androidx.credentials.CredentialManager
import androidx.credentials.ClearCredentialStateRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(navController: NavController) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("compow_prefs", Context.MODE_PRIVATE)
    val scope = rememberCoroutineScope()

    val database = (context.applicationContext as ComPowApplication).database
    val contactDao = database.contactDao()
    val userDao = database.userDao()

    val socketManager = remember { SocketIOManager.getInstance() }

    // Collect StateFlow for reactive connection status
    val isSocketConnected by socketManager.isConnected.collectAsState()

    var showMenu by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var circleEnabled by remember { mutableStateOf(prefs.getBoolean("circle_enabled", true)) }
    var groupEnabled by remember { mutableStateOf(prefs.getBoolean("group_enabled", true)) }
    var communityEnabled by remember { mutableStateOf(prefs.getBoolean("community_enabled", false)) }
    var profileInNotification by remember { mutableStateOf(prefs.getBoolean("profile_in_notification", true)) }
    var messageText by remember { mutableStateOf(prefs.getString("default_message", "") ?: "") }
    var userName by remember { mutableStateOf(prefs.getString("user_name", "User") ?: "User") }
    var userEmail by remember { mutableStateOf(prefs.getString("user_email", "") ?: "") }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    var circleCount by remember { mutableIntStateOf(0) }
    var groupCount by remember { mutableIntStateOf(0) }
    var communityCount by remember { mutableIntStateOf(0) }

    var currentCategoryForAdd by remember { mutableStateOf<ContactCategory?>(null) }

    val dismissSuccessDialog = { showSuccessDialog = false }
    val dismissErrorDialog = { showErrorDialog = false }

    // Load initial data
    LaunchedEffect(Unit) {
        scope.launch {
            withContext(Dispatchers.IO) {
                circleCount = contactDao.getContactCountByCategory(ContactCategory.CIRCLE)
                groupCount = contactDao.getContactCountByCategory(ContactCategory.GROUP)
                communityCount = contactDao.getContactCountByCategory(ContactCategory.COMMUNITY)

                val user = userDao.getCurrentUser()
                if (user != null) {
                    userName = user.fullName
                    userEmail = user.email
                }
            }
        }
    }

    // Save settings changes
    LaunchedEffect(circleEnabled, groupEnabled, communityEnabled, profileInNotification) {
        prefs.edit {
            putBoolean("circle_enabled", circleEnabled)
            putBoolean("group_enabled", groupEnabled)
            putBoolean("community_enabled", communityEnabled)
            putBoolean("profile_in_notification", profileInNotification)
        }
    }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri ->
        uri?.let {
            currentCategoryForAdd?.let { category ->
                scope.launch {
                    handleContactSelection(
                        context,
                        uri,
                        contactDao,
                        category,
                        onComplete = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    when (it) {
                                        ContactCategory.CIRCLE -> circleCount = contactDao.getContactCountByCategory(ContactCategory.CIRCLE)
                                        ContactCategory.GROUP -> groupCount = contactDao.getContactCountByCategory(ContactCategory.GROUP)
                                        ContactCategory.COMMUNITY -> communityCount = contactDao.getContactCountByCategory(ContactCategory.COMMUNITY)
                                    }
                                }
                            }
                        },
                        onError = { error ->
                            errorMessage = error
                            showErrorDialog = true
                        }
                    )
                }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            prefs.edit {
                putString("profile_picture_uri", it.toString())
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
                .verticalScroll(rememberScrollState())
        ) {
            // Header with connection status
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color(0xFF2962FF), CircleShape)
                        .clickable { galleryLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "Profile",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        userName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Text(
                        userEmail,
                        fontSize = 12.sp,
                        color = Color.Gray
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

                Box {
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Menu",
                            tint = Color.Black,
                            modifier = Modifier.size(28.dp)
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
                            text = { Text("Contacts") },
                            onClick = {
                                showMenu = false
                                navController.navigate("destination")
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Group, contentDescription = "Contacts")
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Logout", color = Color.Red) },
                            onClick = {
                                showMenu = false
                                showLogoutDialog = true
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = "Logout",
                                    tint = Color.Red
                                )
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            BackgroundTriggerSetting()

            Spacer(modifier = Modifier.height(24.dp))

            // Emergency Contacts Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Emergency Contacts",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                ContactControlRow(
                    label = "Circle",
                    count = circleCount,
                    enabled = circleEnabled,
                    onToggle = { circleEnabled = it },
                    onAdd = {
                        currentCategoryForAdd = ContactCategory.CIRCLE
                        contactPickerLauncher.launch(null)
                    },
                    onRemove = {
                        scope.launch {
                            removeLastContact(contactDao, ContactCategory.CIRCLE) {
                                circleCount = maxOf(0, circleCount - 1)
                            }
                        }
                    }
                )

                ContactControlRow(
                    label = "Group",
                    count = groupCount,
                    enabled = groupEnabled,
                    onToggle = { groupEnabled = it },
                    onAdd = {
                        currentCategoryForAdd = ContactCategory.GROUP
                        contactPickerLauncher.launch(null)
                    },
                    onRemove = {
                        scope.launch {
                            removeLastContact(contactDao, ContactCategory.GROUP) {
                                groupCount = maxOf(0, groupCount - 1)
                            }
                        }
                    }
                )

                ContactControlRow(
                    label = "Community",
                    count = communityCount,
                    enabled = communityEnabled,
                    onToggle = { communityEnabled = it },
                    onAdd = {
                        currentCategoryForAdd = ContactCategory.COMMUNITY
                        contactPickerLauncher.launch(null)
                    },
                    onRemove = {
                        scope.launch {
                            removeLastContact(contactDao, ContactCategory.COMMUNITY) {
                                communityCount = maxOf(0, communityCount - 1)
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Profile in Notification Toggle
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.8f)
                ),
                elevation = CardDefaults.cardElevation(4.dp)
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
                        Icon(
                            Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = Color(0xFF2962FF),
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            "Show profile in alerts",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black
                        )
                    }
                    Switch(
                        checked = profileInNotification,
                        onCheckedChange = { profileInNotification = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF4CAF50),
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color.Gray
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Emergency Message Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Message,
                        contentDescription = null,
                        tint = Color(0xFF2962FF),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        "Emergency Message",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }

                Text(
                    "This message will be sent to your emergency contacts during an alert",
                    fontSize = 14.sp,
                    color = Color.Gray
                )

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Message starts with: \"$userName:\"",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF2962FF)
                        )

                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            placeholder = {
                                Text(
                                    "Enter your emergency message...\n\nExample: I'm in danger! Please help me immediately!\n\n(Your name will be automatically added)",
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFFF5F5F5),
                                unfocusedContainerColor = Color(0xFFF5F5F5),
                                focusedBorderColor = Color(0xFF2962FF),
                                unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                            ),
                            maxLines = 6
                        )

                        Text(
                            "${messageText.length}/300 characters",
                            fontSize = 12.sp,
                            color = if (messageText.length > 300) Color.Red else Color.Gray,
                            modifier = Modifier.align(Alignment.End)
                        )

                        if (messageText.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFE3F2FD)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        "Message Preview:",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1976D2)
                                    )
                                    Text(
                                        "$userName: $messageText",
                                        fontSize = 14.sp,
                                        color = Color.Black
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                if (messageText.length > 300) {
                                    errorMessage = "Message is too long. Maximum 300 characters."
                                    showErrorDialog = true
                                    return@Button
                                }

                                isSaving = true
                                prefs.edit {
                                    putString("default_message", messageText)
                                }

                                if (isSocketConnected) {
                                    showSuccessDialog = true
                                    errorMessage = "Message saved successfully! Socket.IO is connected - alerts will be delivered instantly."
                                } else {
                                    errorMessage = "Message saved! Note: Socket.IO is currently offline. Alerts will be sent via SMS until connection is restored."
                                    showErrorDialog = true
                                }
                                isSaving = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2962FF)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            enabled = !isSaving
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White
                                )
                            } else {
                                Icon(
                                    Icons.Default.Save,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save Message", fontSize = 16.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    suspend fun signOut(context: Context) {
        val credentialManager = CredentialManager.create(context)

        // This clears all Google identity tokens stored on device
        credentialManager.clearCredentialState(
            ClearCredentialStateRequest()
        )
    }

    // Logout Confirmation Dialog
    val onDismiss = { showLogoutDialog = false }
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = Color.Red)
                    Text("Logout")
                }
            },
            text = {
                Text("Are you sure you want to logout? You'll need to sign in again with Google.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            signOut(context)
                            // Clear preferences
                            prefs.edit {
                                clear()
                                putBoolean("user_logged_out", true)
                            }

                            // Navigate to first page
                            navController.navigate("first") {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Logout", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { onDismiss() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Success Dialog
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = dismissSuccessDialog,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Green)
                    Text("Success")
                }
            },
            text = {
                Text(errorMessage.ifEmpty {
                    "Operation completed successfully!"
                })
            },
            confirmButton = {
                TextButton(onClick = dismissSuccessDialog) {
                    Text("OK")
                }
            }
        )
    }

    // Error Dialog
    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = dismissErrorDialog,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF9800))
                    Text("Notice")
                }
            },
            text = { Text(errorMessage) },
            confirmButton = {
                TextButton(onClick = dismissErrorDialog) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun BackgroundTriggerSetting() {
    val context = LocalContext.current
    var isEnabled by remember { mutableStateOf(AccessibilityHelper.isAccessibilityServiceEnabled(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isEnabled = AccessibilityHelper.isAccessibilityServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Background Alarm Trigger",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isEnabled) "✓ Enabled" else "⚠ Disabled",
                    fontSize = 14.sp,
                    color = if (isEnabled) Color.Green else Color(0xFFFF9800)
                )
            }

            Button(
                onClick = { AccessibilityHelper.openAccessibilitySettings(context) }
            ) {
                Text(if (isEnabled) "Settings" else "Enable")
            }
        }

        if (!isEnabled) {
            Text(
                text = "Enable this to trigger alarm with double volume press (even with screen off)",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun ContactControlRow(
    label: String,
    count: Int,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.8f)
        ),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black
                )
                Text(
                    "$count contact${if (count != 1) "s" else ""}",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onAdd,
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color(0xFF4CAF50), CircleShape)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add contact",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color(0xFFF44336), CircleShape),
                    enabled = count > 0
                ) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = "Remove contact",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF4CAF50),
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color.Gray
                    )
                )
            }
        }
    }
}

private suspend fun handleContactSelection(
    context: Context,
    uri: Uri,
    contactDao: com.example.compow.data.ContactDao,
    category: ContactCategory,
    onComplete: (ContactCategory) -> Unit,
    onError: (String) -> Unit
) {
    withContext(Dispatchers.IO) {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)

                val name = it.getString(nameIndex)
                val contactId = it.getString(idIndex)

                val phoneCursor = context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    null,
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                    arrayOf(contactId),
                    null
                )

                phoneCursor?.use { pc ->
                    if (pc.moveToFirst()) {
                        val phoneIndex = pc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        var phoneNumber = pc.getString(phoneIndex)

                        phoneNumber = formatPhoneNumber(phoneNumber)

                        val existingContact = contactDao.getContactByNumberAndCategory(phoneNumber, category)

                        if (existingContact == null) {
                            val contact = ContactEntity(
                                name = name,
                                phoneNumber = phoneNumber,
                                category = category,
                                isEnabled = true
                            )

                            contactDao.insertContact(contact)
                            withContext(Dispatchers.Main) {
                                onComplete(category)
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                onError("Contact '$name' with number '$phoneNumber' is already in your $category list.")
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun removeLastContact(
    contactDao: com.example.compow.data.ContactDao,
    category: ContactCategory,
    onComplete: () -> Unit
) {
    withContext(Dispatchers.IO) {
        val contacts = contactDao.getContactsByCategory(category)
        if (contacts.isNotEmpty()) {
            contactDao.deleteContact(contacts.last())
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }
}

private fun formatPhoneNumber(phoneNumber: String): String {
    var formatted = phoneNumber.replace("\\s".toRegex(), "")

    if (formatted.startsWith("0")) {
        formatted = "+254${formatted.substring(1)}"
    } else if (formatted.startsWith("254")) {
        formatted = "+$formatted"
    } else if (!formatted.startsWith("+254")) {
        formatted = "+254$formatted"
    }

    return formatted
}