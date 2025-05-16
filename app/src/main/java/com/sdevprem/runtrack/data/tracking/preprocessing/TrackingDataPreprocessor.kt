package com.sdevprem.runtrack.data.tracking.preprocessing

import com.sdevprem.runtrack.domain.tracking.model.LocationTrackingInfo
import java.util.LinkedList
import kotlin.math.abs

data class ProcessedTrackingData(
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Float,
    val acceleration: Float,
    val altitude: Float? = null,
    val recentPaces: List<Float> // last 5-10 seconds of pace data
)

class TrackingDataPreprocessor {
    private val recentLocations = LinkedList<LocationTrackingInfo>()
    private val maxHistorySize = 10 // Store last 10 seconds of data
    private var lastProcessedTime = 0L

    fun processTrackingData(
        currentLocation: LocationTrackingInfo,
        timestamp: Long,
        altitude: Float? = null
    ): ProcessedTrackingData {
        // Add current location to history
        recentLocations.addLast(currentLocation)
        if (recentLocations.size > maxHistorySize) {
            recentLocations.removeFirst()
        }

        // Calculate speed in km/h
        val speedKmh = currentLocation.speedInMS * 3.6f

        // Calculate acceleration (change in speed over time)
        val acceleration = if (recentLocations.size >= 2) {
            val timeDiff = (timestamp - lastProcessedTime) / 1000f // Convert to seconds
            val speedDiff = speedKmh - (recentLocations.first().speedInMS * 3.6f)
            if (timeDiff > 0) speedDiff / timeDiff else 0f
        } else 0f

        // Calculate recent paces (in min/km)
        val recentPaces = recentLocations.map { location ->
            if (location.speedInMS > 0) {
                // Convert m/s to min/km: (1000m/speed)/(60s) = 16.67/speed
                16.67f / location.speedInMS
            } else Float.POSITIVE_INFINITY
        }

        lastProcessedTime = timestamp

        return ProcessedTrackingData(
            timestamp = timestamp,
            latitude = currentLocation.locationInfo.latitude,
            longitude = currentLocation.locationInfo.longitude,
            speedKmh = speedKmh,
            acceleration = acceleration,
            altitude = altitude,
            recentPaces = recentPaces
        )
    }

    fun reset() {
        recentLocations.clear()
        lastProcessedTime = 0L
    }
} 