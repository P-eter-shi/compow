package com.example.compow.viewmodels

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.compow.BuildConfig
import com.example.compow.ComPowApplication
import com.example.compow.data.AlertLogEntity
import com.example.compow.data.NearbyPlace
import com.example.compow.utils.LocationHelper
import com.example.compow.utils.POICacheManager
import com.example.compow.utils.PlacesHelper
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUiState(
    val userLocation: LatLng? = null,
    val nearbyHospitals: List<NearbyPlace> = emptyList(),
    val nearbyPoliceStations: List<NearbyPlace> = emptyList(),
    val isSearching: Boolean = false,
    val locationStatus: String = "Getting location...",
    val recentAlerts: List<AlertLogEntity> = emptyList(),
    val activeAlert: AlertLogEntity? = null,
    val alertCount: Int = 0,
    val activeAlertCount: Int = 0,
    val alarmActive: Boolean = false,
    val circleEnabled: Boolean = true,
    val groupEnabled: Boolean = true,
    val communityEnabled: Boolean = false,
    val mapsApiKey: String = ""
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val database = (application as ComPowApplication).database
    private val alertLogDao = database.alertLogDao()

    private val locationHelper = LocationHelper(application)
    private val poiCacheManager = POICacheManager(application)
    private val placesHelper = PlacesHelper(application)

    init {
        // Initialize Places SDK with API key from BuildConfig
        try {
            if (!Places.isInitialized()) {
                val apiKey = try {
                    BuildConfig.MAPS_API_KEY
                } catch (e: Exception) {
                    // BuildConfig not generated yet, will be available after build
                    ""
                }

                if (apiKey.isNotEmpty()) {
                    Places.initialize(application, apiKey)
                    _uiState.value = _uiState.value.copy(mapsApiKey = apiKey)
                }
            }
        } catch (e: Exception) {
            Log.e("HomeViewModel", "Error initializing Places SDK", e)
        }

        loadInitialData()
    }

    fun loadInitialData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Load settings
                val prefs = getApplication<Application>().getSharedPreferences("compow_prefs", Context.MODE_PRIVATE)
                val circleEnabled = prefs.getBoolean("circle_enabled", true)
                val groupEnabled = prefs.getBoolean("group_enabled", true)
                val communityEnabled = prefs.getBoolean("community_enabled", false)

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        circleEnabled = circleEnabled,
                        groupEnabled = groupEnabled,
                        communityEnabled = communityEnabled
                    )
                }

                // Load alerts
                val activeAlert = alertLogDao.getActiveAlert()
                val recentAlerts = alertLogDao.getRecentAlerts(10)
                val alertCount = alertLogDao.getAlertLogCount()
                val activeAlertCount = alertLogDao.getActiveAlertCount()

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        activeAlert = activeAlert,
                        recentAlerts = recentAlerts,
                        alertCount = alertCount,
                        activeAlertCount = activeAlertCount,
                        alarmActive = activeAlert != null && !activeAlert.isResolved
                    )
                }

                // Load location and nearby places
                loadLocationAndPlaces()
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error loading initial data", e)
            }
        }
    }

    private suspend fun loadLocationAndPlaces() {
        withContext(Dispatchers.IO) {
            try {
                val location = locationHelper.getLocationWithFallback()
                if (location != null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    val status = if (locationHelper.isLocationAccurate(location)) {
                        "Location accurate (${location.accuracy.toInt()}m)"
                    } else {
                        "Low accuracy (${location.accuracy.toInt()}m)"
                    }

                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            userLocation = latLng,
                            locationStatus = status
                        )
                    }

                    // Load nearby places with cache-first strategy
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(isSearching = true)
                    }

                    val cachedResults = poiCacheManager.getCachedResults(location.latitude, location.longitude)
                    if (cachedResults != null) {
                        withContext(Dispatchers.Main) {
                            _uiState.value = _uiState.value.copy(
                                nearbyHospitals = cachedResults.hospitals,
                                nearbyPoliceStations = cachedResults.policeStations,
                                isSearching = false
                            )
                        }
                    } else {
                        // Fetch fresh data
                        try {
                            val (hospitals, police) = placesHelper.findNearbyPlaces()
                            poiCacheManager.cacheResults(location.latitude, location.longitude, hospitals, police)

                            withContext(Dispatchers.Main) {
                                _uiState.value = _uiState.value.copy(
                                    nearbyHospitals = hospitals,
                                    nearbyPoliceStations = police,
                                    isSearching = false
                                )
                            }
                        } catch (e: SecurityException) {
                            Log.e("HomeViewModel", "Location permission denied", e)
                            withContext(Dispatchers.Main) {
                                _uiState.value = _uiState.value.copy(isSearching = false)
                            }
                        } catch (e: Exception) {
                            Log.e("HomeViewModel", "Error fetching places", e)
                            withContext(Dispatchers.Main) {
                                _uiState.value = _uiState.value.copy(isSearching = false)
                            }
                        }
                    }
                } else {
                    // Use default location
                    val (defaultLat, defaultLng) = LocationHelper.getDefaultLocation()
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            userLocation = LatLng(defaultLat, defaultLng),
                            locationStatus = "Using default location",
                            isSearching = false
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error loading location and places", e)
                val (defaultLat, defaultLng) = LocationHelper.getDefaultLocation()
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        userLocation = LatLng(defaultLat, defaultLng),
                        locationStatus = "Error getting location",
                        isSearching = false
                    )
                }
            }
        }
    }

    fun refreshAlerts() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val activeAlert = alertLogDao.getActiveAlert()
                val recentAlerts = alertLogDao.getRecentAlerts(10)
                val activeAlertCount = alertLogDao.getActiveAlertCount()

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        activeAlert = activeAlert,
                        recentAlerts = recentAlerts,
                        activeAlertCount = activeAlertCount,
                        alarmActive = activeAlert != null && !activeAlert.isResolved
                    )
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error refreshing alerts", e)
            }
        }
    }

    fun setAlarmActive(active: Boolean) {
        _uiState.value = _uiState.value.copy(alarmActive = active)
    }

    fun getLocationHelper(): LocationHelper = locationHelper
}

