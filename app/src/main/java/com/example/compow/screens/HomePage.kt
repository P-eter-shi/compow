package com.example.compow.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.compow.AlarmService
import com.example.compow.ComPowApplication
import com.example.compow.data.AlertLogEntity
import com.example.compow.data.NearbyPlace
import com.example.compow.utils.LocationHelper
import com.example.compow.utils.POICacheManager
import com.example.compow.utils.PlacesHelper
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.maps.android.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePage(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = (context.applicationContext as ComPowApplication).database
    val alertLogDao = database.alertLogDao()

    val poiCacheManager = remember { POICacheManager(context) }
    val placesHelper = remember { PlacesHelper(context) }

    var showMenu by remember { mutableStateOf(false) }
    var showAlertHistory by remember { mutableStateOf(false) }
    var alarmActive by remember { mutableStateOf(false) }

    // Settings
    val prefs = context.getSharedPreferences("compow_prefs", Context.MODE_PRIVATE)
    val circleEnabled = prefs.getBoolean("circle_enabled", true)
    val groupEnabled = prefs.getBoolean("group_enabled", true)
    val communityEnabled = prefs.getBoolean("community_enabled", false)

    // Alert history
    var recentAlerts by remember { mutableStateOf<List<AlertLogEntity>>(emptyList()) }
    var activeAlert by remember { mutableStateOf<AlertLogEntity?>(null) }
    var alertCount by remember { mutableIntStateOf(0) }
    var activeAlertCount by remember { mutableIntStateOf(0) }

    // Location and nearby places
    val locationHelper = remember { LocationHelper(context) }
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var nearbyHospitals by remember { mutableStateOf<List<NearbyPlace>>(emptyList()) }
    var nearbyPoliceStations by remember { mutableStateOf<List<NearbyPlace>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var locationStatus by remember { mutableStateOf("Getting location...") }
    // Add this near your other state declarations in HomePage
    var isButtonTransitioning by remember { mutableStateOf(false) }
    var buttonColor by remember { mutableStateOf(Color(0xFF2962FF)) }

    // Update button color based on alarmActive and transitioning state
    LaunchedEffect(alarmActive, isButtonTransitioning) {
        buttonColor = when {
            alarmActive && !isButtonTransitioning -> Color.Red  // Alarm active, not pressed
            !alarmActive && isButtonTransitioning -> Color(0xFF2962FF)  // Just stopped, show blue
            else -> Color(0xFF2962FF)  // Default/not active
        }
    }

    // Load initial data
    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            activeAlert = alertLogDao.getActiveAlert()
            recentAlerts = alertLogDao.getRecentAlerts(10)
            alertCount = alertLogDao.getAlertLogCount()
            activeAlertCount = alertLogDao.getActiveAlertCount()

            // Get initial location
            val location = locationHelper.getLocationWithFallback()
            if (location != null) {
                userLocation = LatLng(location.latitude, location.longitude)
                withContext(Dispatchers.Main) {
                    locationStatus = if (locationHelper.isLocationAccurate(location)) {
                        "Location accurate (${location.accuracy.toInt()}m)"
                    } else {
                        "Low accuracy (${location.accuracy.toInt()}m)"
                    }
                }
            } else {
                val (defaultLat, defaultLng) = LocationHelper.getDefaultLocation()
                userLocation = LatLng(defaultLat, defaultLng)
                withContext(Dispatchers.Main) {
                    locationStatus = "Using default location"
                }
            }
        }
    }

    // Update alarm active state based on active alert
    LaunchedEffect(activeAlert) {
        alarmActive = activeAlert?.isResolved == false
    }

    // Automatic POI search triggered by alarm state
    LaunchedEffect(alarmActive, userLocation) {
        Log.d("HomePage", "🔍 POI Search Effect - alarmActive: $alarmActive, userLocation: ${userLocation != null}")

        if (alarmActive && userLocation != null) {
            scope.launch(Dispatchers.IO) {

                val currentLocation = userLocation ?: return@launch

                withContext(Dispatchers.Main) {
                    isSearching = true
                }

                Log.d("HomePage", "🔍 Searching for POIs at: ${currentLocation.latitude}, ${currentLocation.longitude}")

                val location = Location("").apply {
                    latitude = currentLocation.latitude
                    longitude = currentLocation.longitude
                }

                try {
                    // Cache-first strategy with integrated API
                    val cachedResults = poiCacheManager.getCachedResults(location.latitude, location.longitude)
                        ?: poiCacheManager.fetchAndCacheNearby(location.latitude, location.longitude)

                    nearbyHospitals = cachedResults.hospitals
                    nearbyPoliceStations = cachedResults.policeStations

                    Log.d("HomePage", "✅ POIs: ${nearbyHospitals.size} hospitals, ${nearbyPoliceStations.size} police")
                } catch (e: Exception) {
                    Log.e("HomePage", "❌ POI search error: ${e.message}")
                    e.printStackTrace()
                }

                withContext(Dispatchers.Main) { isSearching = false }
            }
        } else if (!alarmActive) {
            nearbyHospitals = emptyList()
            nearbyPoliceStations = emptyList()
            Log.d("HomePage", "🧹 Cleared POIs")
        }
    }

    // Permission launcher
    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val intent = Intent(Intent.ACTION_DIAL)
            context.startActivity(intent)
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
            // Top Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FlickeringContactIcon(Icons.Default.Person, "Circle", circleEnabled)
                    FlickeringContactIcon(Icons.Default.Group, "Group", groupEnabled)
                    FlickeringContactIcon(Icons.Default.Groups, "Community", communityEnabled)
                }

                Box {
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Badge(
                            containerColor = if (activeAlertCount > 0) Color.Red else Color.Transparent
                        ) {
                            if (activeAlertCount > 0) {
                                Text("$activeAlertCount", color = Color.White, fontSize = 10.sp)
                            }
                        }
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Menu",
                            tint = Color.Black,
                            modifier = Modifier.size(32.dp)
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
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Contact") },
                            onClick = {
                                showMenu = false
                                navController.navigate("destination")
                            },
                            leadingIcon = { Icon(Icons.Default.Contacts, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Alert History")
                                    Badge { Text("$alertCount") }
                                }
                            },
                            onClick = {
                                showMenu = false
                                showAlertHistory = true
                            },
                            leadingIcon = { Icon(Icons.Default.History, contentDescription = null) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Active Alert Banner
            if (alarmActive && activeAlert != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color.Red,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "🚨 ACTIVE EMERGENCY",
                                fontWeight = FontWeight.Bold,
                                color = Color.Red,
                                fontSize = 14.sp
                            )
                            Text(
                                "${activeAlert!!.contactsNotified} contacts notified • Live tracking active",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Map Section
            MapSection(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (alarmActive) 320.dp else 380.dp)
                    .padding(horizontal = 16.dp),
                userLocation = userLocation,
                hospitals = nearbyHospitals,
                policeStations = nearbyPoliceStations,
                isSearching = isSearching,
                locationStatus = locationStatus,
                locationHelper = locationHelper,
                showPOIs = alarmActive // Only show POIs during emergency
            )

            Spacer(modifier = Modifier.weight(1f))

            // Bottom Control Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (alarmActive) {
                            // Set transitioning state
                            isButtonTransitioning = true

                            // Stop alarm
                            AlarmService.stopAlarm(context)
                            Log.d("HomePage", "🛑 Stop alarm requested")

                            // After a short delay, reset transitioning state
                            scope.launch {
                                kotlinx.coroutines.delay(2000) // Keep blue for 2 seconds after pressing
                                isButtonTransitioning = false
                            }

                            // Update alarm state
                            alarmActive = false
                        } else {
                            // Trigger alarm - reset transitioning state
                            isButtonTransitioning = false
                            AlarmService.triggerAlarm(context)
                            Log.d("HomePage", "🚨 Emergency alarm triggered")
                        }

                        // Refresh UI state
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                activeAlert = alertLogDao.getActiveAlert()
                                recentAlerts = alertLogDao.getRecentAlerts(10)
                                activeAlertCount = alertLogDao.getActiveAlertCount()
                                alertCount = alertLogDao.getAlertLogCount()
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(65.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor  // Use the reactive buttonColor
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(8.dp)
                ) {
                    Text(
                        if (alarmActive) "STOP ALARM" else "ACTIVATE ALERT",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                FloatingActionButton(
                    onClick = {
                        when (PackageManager.PERMISSION_GRANTED) {
                            ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) -> {
                                val intent = Intent(Intent.ACTION_DIAL)
                                context.startActivity(intent)
                            }
                            else -> {
                                callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                            }
                        }
                    },
                    containerColor = Color(0xFF4CAF50),
                    modifier = Modifier.size(65.dp),
                    elevation = FloatingActionButtonDefaults.elevation(8.dp)
                ) {
                    Icon(
                        Icons.Default.Phone,
                        contentDescription = "Call",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    // Alert History Dialog
    val onDismiss = { showAlertHistory = false }
    if (showAlertHistory) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Alert History")
                    Badge { Text("$alertCount") }
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(recentAlerts) { alert ->
                        AlertHistoryItem(alert)
                    }

                    if (recentAlerts.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No alerts yet", color = Color.Gray, fontSize = 14.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Clear History Button
                    if (recentAlerts.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    // Clear database
                                    withContext(Dispatchers.IO) {
                                        alertLogDao.deleteAllAlerts()
                                    }
                                    // Update UI state
                                    recentAlerts = emptyList()
                                    alertCount = 0
                                    // Show confirmation
                                    android.widget.Toast.makeText(
                                        context,
                                        "History cleared",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = Color.Red
                            )
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear")
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        )
    }
}

@Composable
fun AlertHistoryItem(alert: AlertLogEntity) {
    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (alert.isResolved) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        if (alert.isResolved) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (alert.isResolved) Color(0xFF4CAF50) else Color.Red,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(alert.alertType.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text(
                    dateFormat.format(Date(alert.timestamp)),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            Text("${alert.contactsNotified} contacts notified", fontSize = 12.sp, color = Color.Gray)
            if (alert.isResolved && alert.resolvedAt != null) {
                Text(
                    "Resolved at ${dateFormat.format(Date(alert.resolvedAt))}",
                    fontSize = 11.sp,
                    color = Color(0xFF4CAF50)
                )
            }
        }
    }
}

@Composable
fun FlickeringContactIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "flicker_$label")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha_$label"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .background(
                    color = if (enabled)
                        Color(0xFF4CAF50).copy(alpha = alpha)
                    else
                        Color.Gray.copy(alpha = 0.5f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Text(label, fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun MapSection(
    modifier: Modifier = Modifier,
    userLocation: LatLng?,
    hospitals: List<NearbyPlace>,
    policeStations: List<NearbyPlace>,
    isSearching: Boolean,
    locationStatus: String,
    locationHelper: LocationHelper,
    showPOIs: Boolean = true
) {
    val defaultLocation = LatLng(-0.0469, 37.6494)
    val mapCenter = userLocation ?: defaultLocation

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(mapCenter, 13f)
    }

    LaunchedEffect(userLocation) {
        userLocation?.let {
            cameraPositionState.animate(update = CameraUpdateFactory.newLatLngZoom(it, 13f), durationMs = 1000)
        }
    }

    Box(
        modifier = modifier
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
    ) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = false),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = true,
                myLocationButtonEnabled = false
            )
        ) {
            // User location marker
            if (userLocation != null) {
                Marker(
                    state = rememberMarkerState(position = userLocation),
                    title = "Your Location",
                    snippet = locationHelper.formatLocation(Location("").apply {
                        latitude = userLocation.latitude
                        longitude = userLocation.longitude
                    }),
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                )
            }

            // Only show POIs during emergency
            if (showPOIs) {
                hospitals.forEach { hospital ->
                    Marker(
                        state = rememberMarkerState(position = LatLng(hospital.latitude, hospital.longitude)),
                        title = hospital.name,
                        snippet = hospital.address,
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                    )
                }

                policeStations.forEach { station ->
                    Marker(
                        state = rememberMarkerState(position = LatLng(station.latitude, station.longitude)),
                        title = station.name,
                        snippet = station.address,
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                    )
                }
            }
        }

        // Searching indicator
        if (isSearching) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text("Finding nearby help...", fontSize = 12.sp)
                }
            }
        }

        // Map legend
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.95f)
                ),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFF2962FF),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            if (showPOIs) "Emergency Facilities" else "Your Location",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(locationStatus, fontSize = 10.sp, color = Color.Gray)

                    if (showPOIs && !isSearching) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(Color(0xFF0099FF), CircleShape)
                                )
                                Text("🏥 ${hospitals.size}", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(Color(0xFF00FF00), CircleShape)
                                )
                                Text("🚔 ${policeStations.size}", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
}