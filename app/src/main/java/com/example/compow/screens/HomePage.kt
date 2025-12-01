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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.compow.AlarmService
import com.example.compow.data.AlertLogEntity
import com.example.compow.data.NearbyPlace
import com.example.compow.utils.LocationHelper
import com.example.compow.viewmodels.HomeViewModel
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePage(
    navController: NavController,
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Collect UI state from ViewModel
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showMenu by remember { mutableStateOf(false) }
    var showAlertHistory by remember { mutableStateOf(false) }

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
                    FlickeringContactIcon(Icons.Default.Person, "Circle", uiState.circleEnabled)
                    FlickeringContactIcon(Icons.Default.Group, "Group", uiState.groupEnabled)
                    FlickeringContactIcon(Icons.Default.Groups, "Community", uiState.communityEnabled)
                }

                Box {
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Badge(
                            containerColor = if (uiState.activeAlertCount > 0) Color.Red else Color.Transparent
                        ) {
                            if (uiState.activeAlertCount > 0) {
                                Text("${uiState.activeAlertCount}", color = Color.White, fontSize = 10.sp)
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
                                    Badge { Text("${uiState.alertCount}") }
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
            if (uiState.alarmActive && uiState.activeAlert != null) {
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
                                "${uiState.activeAlert!!.contactsNotified} contacts notified",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Map Section (shows nearby places on victim's device only)
            MapSection(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (uiState.alarmActive) 320.dp else 380.dp)
                    .padding(horizontal = 16.dp),
                userLocation = uiState.userLocation,
                hospitals = uiState.nearbyHospitals,
                policeStations = uiState.nearbyPoliceStations,
                isSearching = uiState.isSearching,
                locationStatus = uiState.locationStatus,
                locationHelper = viewModel.getLocationHelper()
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
                        viewModel.setAlarmActive(false)
                        AlarmService.stopAlarm(context)
                        viewModel.refreshAlerts()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(65.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.alarmActive) Color.Red else Color(0xFF2962FF)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(8.dp)
                ) {
                    Text(
                        if (uiState.alarmActive) "STOP ALARM" else "Stop Alarm",
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
    val onDismiss={ showAlertHistory = false }
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
                    Badge { Text("${uiState.alertCount}") }
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.recentAlerts) { alert ->
                        AlertHistoryItem(alert)
                    }

                    if (uiState.recentAlerts.isEmpty()) {
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
                TextButton(onClick = { onDismiss() }) {
                    Text("Close")
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
    locationHelper: LocationHelper
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

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = false),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = true,
                    myLocationButtonEnabled = false
                )
            ) {
                if (userLocation != null) {
                    Marker(
                        state = rememberMarkerState(position = userLocation),
                        title = "Your Location",
                        snippet = locationHelper.formatLocation(Location("").apply { latitude = userLocation.latitude; longitude = userLocation.longitude}),
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                    )
                }

                hospitals.forEach { hospital ->
                    Marker(
                        state = rememberMarkerState(position = LatLng(hospital.latitude, hospital.longitude)),
                        title = hospital.name,
                        snippet = "${hospital.address}",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                    )
                }

                policeStations.forEach { station ->
                    Marker(
                        state = rememberMarkerState(position = LatLng(station.latitude, station.longitude)),
                        title = station.name,
                        snippet = "${station.address}",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                    )
                }
            }

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
                                "Emergency Facilities",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(locationStatus, fontSize = 10.sp, color = Color.Gray)

                        if (!isSearching) {
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
}
