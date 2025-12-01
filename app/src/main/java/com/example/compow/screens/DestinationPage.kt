package com.example.compow.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import com.example.compow.data.ContactEntity
import com.example.compow.data.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DestinationPage(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = (context.applicationContext as ComPowApplication).database
    val contactDao = database.contactDao()
    val userDao = database.userDao()
    val alertLogDao = database.alertLogDao()

    var showMenu by remember { mutableStateOf(false) }
    var showUserProfile by remember { mutableStateOf(false) }
    var showStatsDialog by remember { mutableStateOf(false) }

    // Load contacts from database - USES: getAllContacts()
    var circleContacts by remember { mutableStateOf<List<ContactEntity>>(emptyList()) }
    var groupContacts by remember { mutableStateOf<List<ContactEntity>>(emptyList()) }
    var communityContacts by remember { mutableStateOf<List<ContactEntity>>(emptyList()) }

    // User info - USES: getCurrentUser(), getUserById()
    var currentUser by remember { mutableStateOf<UserEntity?>(null) }

    // Statistics - USES: getAlertLogCount(), getUserCount()
    var totalAlerts by remember { mutableIntStateOf(0) }
    var totalUsers by remember { mutableIntStateOf(0) }

    var showContactDetailsDialog by remember { mutableStateOf(false) }
    var selectedContactDetails by remember { mutableStateOf<ContactEntity?>(null) }

    // Load all data
    LaunchedEffect(Unit) {
        scope.launch {
            withContext(Dispatchers.IO) {
                // USES: getAllContacts()
                val allContacts = contactDao.getAllContacts()

                // Separate by category
                circleContacts = allContacts.filter { it.category == ContactCategory.CIRCLE }
                groupContacts = allContacts.filter { it.category == ContactCategory.GROUP }
                communityContacts = allContacts.filter { it.category == ContactCategory.COMMUNITY }

                // USES: getCurrentUser()
                currentUser = userDao.getCurrentUser()

                // If no current user from database, try getting by ID from preferences
                if (currentUser == null) {
                    val prefs = context.getSharedPreferences("compow_prefs", Context.MODE_PRIVATE)
                    val userId = prefs.getString("user_id", null)
                    if (userId != null) {
                        // USES: getUserById()
                        currentUser = userDao.getUserById(userId)
                    }
                }

                // USES: getAlertLogCount()
                totalAlerts = alertLogDao.getAlertLogCount()

                // USES: getUserCount()
                totalUsers = userDao.getUserCount()
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
            // Top Bar with User Info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // User Profile Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .clickable { showUserProfile = true }
                        .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
                        .padding(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF2962FF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "Profile",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            currentUser?.fullName ?: "User",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Text(
                            currentUser?.email ?: "",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }

                // Menu Button
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
                            leadingIcon = { Icon(Icons.Default.Home, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = {
                                showMenu = false
                                navController.navigate("settings")
                            },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                        )
                        Divider()
                        DropdownMenuItem(
                            text = {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Statistics")
                                    Badge { Text("$totalAlerts") }
                                }
                            },
                            onClick = {
                                showMenu = false
                                showStatsDialog = true
                            },
                            leadingIcon = { Icon(Icons.Default.Analytics, contentDescription = null) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Statistics Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.8f)),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(
                        icon = Icons.Default.Contacts,
                        label = "Contacts",
                        value = "${circleContacts.size + groupContacts.size + communityContacts.size}",
                        color = Color(0xFF2962FF)
                    )
                    StatItem(
                        icon = Icons.Default.Warning,
                        label = "Alerts",
                        value = "$totalAlerts",
                        color = Color(0xFFFF9800)
                    )
                    StatItem(
                        icon = Icons.Default.Person,
                        label = "Users",
                        value = "$totalUsers",
                        color = Color(0xFF4CAF50)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Circle Section - USES: getContactById()
            ContactListSection(
                title = "Circle",
                contacts = circleContacts,
                borderColor = Color(0xFF2962FF),
                onContactClick = { contact ->
                    scope.launch {
                        // Fetch the full contact details
                        val fullContact = withContext(Dispatchers.IO) {
                            contactDao.getContactById(contact.id)
                        }

                        // --- FIX APPLIED HERE ---
                        // Use the fetched data to update the state, which will show a dialog.
                        if (fullContact != null) {
                            selectedContactDetails = fullContact
                            showContactDetailsDialog = true
                        }

                    }
                },
                onDeleteContact = { contact ->
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            // USES: deleteContactById()
                            contactDao.deleteContactById(contact.id)

                            // Refresh list
                            val allContacts = contactDao.getAllContacts()
                            circleContacts = allContacts.filter { it.category == ContactCategory.CIRCLE }
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Group Section
            ContactListSection(
                title = "Group",
                contacts = groupContacts,
                borderColor = Color(0xFF4CAF50),
                onContactClick = { contact ->
                    scope.launch {
                        val fullContact = withContext(Dispatchers.IO) {
                            contactDao.getContactById(contact.id)
                        }

                        // --- FIX APPLIED HERE ---
                        // Use the fetched data to update the state, which will show a dialog.
                        if (fullContact != null) {
                            selectedContactDetails = fullContact
                            showContactDetailsDialog = true
                        }
                    }
                },
                onDeleteContact = { contact ->
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            contactDao.deleteContactById(contact.id)
                            val allContacts = contactDao.getAllContacts()
                            groupContacts = allContacts.filter { it.category == ContactCategory.GROUP }
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Community Section
            ContactListSection(
                title = "Community",
                contacts = communityContacts,
                borderColor = Color(0xFFFF9800),
                onContactClick = { contact ->
                    scope.launch {
                        val fullContact = withContext(Dispatchers.IO) {
                            contactDao.getContactById(contact.id)
                        }

                        // --- FIX APPLIED HERE ---
                        // Use the fetched data to update the state, which will show a dialog.
                        if (fullContact != null) {
                            selectedContactDetails = fullContact
                            showContactDetailsDialog = true
                        }
                    }
                },
                onDeleteContact = { contact ->
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            contactDao.deleteContactById(contact.id)
                            val allContacts = contactDao.getAllContacts()
                            communityContacts = allContacts.filter { it.category == ContactCategory.COMMUNITY }
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Place this at the end of the DestinationPage composable function's body
    val onDismisser = { showContactDetailsDialog = false }
    if (showContactDetailsDialog && selectedContactDetails != null) {
        AlertDialog(
            onDismissRequest = onDismisser,
            title = { Text(selectedContactDetails!!.name) },
            text = {
                Column {
                    Text("Phone: ${selectedContactDetails!!.phoneNumber}")
                    Text("Category: ${selectedContactDetails!!.category}")
                    // You can add more details here if needed
                }
            },
            confirmButton = {
                TextButton(onClick = onDismisser) {
                    Text("Close")
                }
            }
        )
    }


    // User Profile Dialog - USES: updateUser(), updateUserName(), updateEmail(), updatePhoneNumber()
    val dismissUserProfileDialog = { showUserProfile = false }
    if (showUserProfile && currentUser != null) {
        var editedName by remember { mutableStateOf(currentUser!!.fullName) }
        var editedEmail by remember { mutableStateOf(currentUser!!.email) }
        var editedPhone by remember { mutableStateOf(currentUser!!.phoneNumber) }

        AlertDialog(
            onDismissRequest = dismissUserProfileDialog,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF2962FF))
                    Text("User Profile")
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        label = { Text("Full Name") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                    )

                    OutlinedTextField(
                        value = editedEmail,
                        onValueChange = { editedEmail = it },
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) }
                    )

                    OutlinedTextField(
                        value = editedPhone,
                        onValueChange = { editedPhone = it },
                        label = { Text("Phone") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) }
                    )

                    Text(
                        "Course: ${currentUser!!.courseOfStudy}",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )

                    Text(
                        "Year: ${currentUser!!.yearOfStudy}",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                // Individual updates if needed
                                userDao.updateUserName(currentUser!!.userId, editedName)
                                userDao.updateEmail(currentUser!!.userId, editedEmail)
                                userDao.updatePhoneNumber(currentUser!!.userId, editedPhone)

                                // Create updated user object for UI
                                val updatedUser = currentUser!!.copy(
                                    fullName = editedName,
                                    email = editedEmail,
                                    phoneNumber = editedPhone
                                )

                                // Update UI on main thread
                                withContext(Dispatchers.Main) {
                                    currentUser = updatedUser
                                    dismissUserProfileDialog() // Only set once
                                }
                            }
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = dismissUserProfileDialog) {
                    Text("Cancel")
                }
            }
        )
    }

    // Statistics Dialog - USES: getAllAlertLogs()
    val onDismiss = { showStatsDialog = false }
    if (showStatsDialog) {
        var allAlerts by remember { mutableStateOf<List<com.example.compow.data.AlertLogEntity>>(emptyList()) }

        LaunchedEffect(Unit) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    // USES: getAllAlertLogs()
                    allAlerts = alertLogDao.getAllAlertLogs()
                }
            }
        }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Statistics")
                    Badge { Text("$totalAlerts") }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatRow("Total Contacts", "${circleContacts.size + groupContacts.size + communityContacts.size}")
                    StatRow("Circle Contacts", "${circleContacts.size}")
                    StatRow("Group Contacts", "${groupContacts.size}")
                    StatRow("Community Contacts", "${communityContacts.size}")
                    Divider()
                    StatRow("Total Alerts", "$totalAlerts")
                    StatRow("Registered Users", "$totalUsers")

                    if (allAlerts.isNotEmpty()) {
                        Divider()
                        Text(
                            "Recent Alerts",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                        ) {
                            items(allAlerts.take(5)) { alert ->
                                val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                                Text(
                                    "${alert.alertType.name} - ${dateFormat.format(Date(alert.timestamp))}",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        )
    }
}


@Composable
fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(color.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
        Text(
            label,
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

@Composable
fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp, color = Color.Gray)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactListSection(
    title: String,
    contacts: List<ContactEntity>,
    borderColor: Color,
    onContactClick: (ContactEntity) -> Unit,
    onDeleteContact: (ContactEntity) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Badge(
                containerColor = borderColor,
                contentColor = Color.White
            ) {
                Text("${contacts.size}")
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Set a specific maximum height for the scrollable list
                .heightIn(max = 250.dp) // Adjusted max height for scrolling to be visible
                .border(2.dp, borderColor, RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            if (contacts.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.PersonOff,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        "No contacts added",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            } else {
                // *** REPLACED Column with LazyColumn for scrollable list ***
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(contacts) { contact ->
                        ContactItem(
                            contact = contact,
                            onClick = { onContactClick(contact) },
                            onDelete = { onDeleteContact(contact) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ContactItem(
    contact: ContactEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFF2962FF).copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        contact.name.take(1).uppercase(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2962FF)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        contact.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    )
                    Text(
                        contact.phoneNumber,
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }

            IconButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color(0xFFF44336),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
    val onDismiss = { showDeleteDialog = false }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Remove Contact") },
            text = { Text("Remove ${contact.name} from your emergency contacts?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = {onDismiss()}) {
                    Text("Cancel")
                }
            }
        )
    }
}