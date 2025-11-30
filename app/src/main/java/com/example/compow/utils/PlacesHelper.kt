package com.example.compow.utils

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.compow.data.NearbyPlace
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import kotlinx.coroutines.tasks.await

class PlacesHelper(context: Context) {

    private val placesClient: PlacesClient = Places.createClient(context)

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    @Suppress("DEPRECATION") // Suppressing because the Places SDK properties are marked deprecated
    suspend fun findNearbyPlaces(): Pair<List<NearbyPlace>, List<NearbyPlace>> {
        val placeFields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG, Place.Field.TYPES, Place.Field.OPENING_HOURS)
        // The request uses the device's current location. The location parameter was unused.
        val request = FindCurrentPlaceRequest.newInstance(placeFields)

        return try {
            val response = placesClient.findCurrentPlace(request).await()
            val hospitals = mutableListOf<NearbyPlace>()
            val policeStations = mutableListOf<NearbyPlace>()

            response.placeLikelihoods.forEach { placeLikelihood ->
                val place = placeLikelihood.place
                // Use the new, non-deprecated `placeTypes` which returns a List<String>
                val placeTypes = place.placeTypes ?: emptyList()

                val nearbyPlace = NearbyPlace(
                    name = place.name ?: "",
                    address = place.address ?: "",
                    latitude = place.latLng?.latitude ?: 0.0,
                    longitude = place.latLng?.longitude ?: 0.0,
                    distance = 0f, // This will be calculated later
                    placeId = place.id ?: "",
                    types = placeTypes, // Pass the list of strings directly
                    isOpen = place.isOpen ?: false
                )

                // Check for types using modern string identifiers
                if (placeTypes.contains("hospital")) {
                    hospitals.add(nearbyPlace)
                } else if (placeTypes.contains("police")) {
                    policeStations.add(nearbyPlace)
                }
            }
            Pair(hospitals, policeStations)
        } catch (e: Exception) {
            Log.e("PlacesHelper", "Error finding places: ${e.message}")
            Pair(emptyList(), emptyList())
        }
    }
}