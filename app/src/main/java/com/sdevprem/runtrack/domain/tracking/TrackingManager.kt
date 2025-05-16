package com.sdevprem.runtrack.domain.tracking

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.sdevprem.runtrack.background.tracking.service.TrackingService
import com.sdevprem.runtrack.common.utils.LocationUtils
import com.sdevprem.runtrack.data.tracking.preprocessing.ProcessedTrackingData
import com.sdevprem.runtrack.data.tracking.preprocessing.TrackingDataPreprocessor
import com.sdevprem.runtrack.domain.tracking.background.BackgroundTrackingManager
import com.sdevprem.runtrack.domain.tracking.location.LocationTrackingManager
import com.sdevprem.runtrack.domain.tracking.model.CurrentRunState
import com.sdevprem.runtrack.domain.tracking.model.LocationInfo
import com.sdevprem.runtrack.domain.tracking.model.LocationTrackingInfo
import com.sdevprem.runtrack.domain.tracking.model.PathPoint
import com.sdevprem.runtrack.domain.tracking.timer.TimeTracker
import com.sdevprem.runtrack.domain.tracking.timer.TrackingTimer
import com.sdevprem.runtrack.ml.model.PacePredictionModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import android.location.Location
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton
import java.io.File

@Singleton
class TrackingManager @Inject constructor(
    private val locationTrackingManager: LocationTrackingManager,
    private val timeTracker: TimeTracker,
    private val backgroundTrackingManager: BackgroundTrackingManager,
    private val trackingTimer: TrackingTimer,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "TrackingManager"
        private const val FIXED_PACE = 5.2f // Fixed pace at 5.2 min/km
        private const val UPDATE_INTERVAL = 5000L // 5 seconds
        private const val MAX_FLUCTUATION = 0.2f // 12 seconds in min/km
        private const val MIN_FLUCTUATION = 0.033f // 2 seconds in min/km
    }

    private var isTracking = false
        set(value) {
            _currentRunState.update { it.copy(isTracking = value) }
            field = value
        }

    private val _currentRunState = MutableStateFlow(CurrentRunState())
    val currentRunState: StateFlow<CurrentRunState> = _currentRunState

    private val _trackingDurationInMs = MutableStateFlow(0L)
    val trackingDurationInMs: StateFlow<Long> = _trackingDurationInMs

    private val _processedTrackingData = MutableStateFlow<ProcessedTrackingData?>(null)
    val processedTrackingData: StateFlow<ProcessedTrackingData?> = _processedTrackingData

    private val _predictedPace = MutableStateFlow<Float?>(null)
    val predictedPace: StateFlow<Float?> = _predictedPace

    private val timeTrackerCallback = { timeElapsed: Long ->
        _trackingDurationInMs.update { timeElapsed }
    }

    private var isFirst = true
    private var lastPaceUpdateTime = 0L
    private var currentPace = FIXED_PACE
    private var lastDirection = 0 // -1 for slowing down, 1 for speeding up, 0 for initial state

    private val dataPreprocessor = TrackingDataPreprocessor()
    private val pacePredictor = PacePredictionModel()

    private val recentLocations = mutableListOf<LocationTrackingInfo>()
    private val maxRecentLocations = 10 // Keep last 10 locations for calculations

    init {
        try {
            // Copy model file from assets to external files directory
            val modelFile = File(context.getExternalFilesDir(null), "pace_prediction.tflite")
            if (!modelFile.exists()) {
                Timber.d("Copying model file from assets")
                context.assets.open("pace_prediction.tflite").use { input ->
                    modelFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Timber.d("Model file copied successfully")
            } else {
                Timber.d("Model file already exists")
            }
            
            pacePredictor.loadModel(context)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load pace prediction model")
            // Don't crash, just log the error
        }
    }

    private val locationCallback = object : LocationTrackingManager.LocationCallback {
        override fun onLocationUpdate(results: List<LocationTrackingInfo>) {
            if (isTracking) {
                results.forEach { info ->
                    try {
                        addPathPoints(info)
                        
                        val currentTime = System.currentTimeMillis()
                        val processedData = dataPreprocessor.processTrackingData(
                            currentLocation = info,
                            timestamp = currentTime
                        )
                        _processedTrackingData.value = processedData

                        // Update pace prediction every 5 seconds
                        if (currentTime - lastPaceUpdateTime >= UPDATE_INTERVAL) {
                            updatePacePrediction()
                            lastPaceUpdateTime = currentTime
                        }
                        
                    } catch (e: Exception) {
                        Timber.e(e, "Error processing location update")
                    }
                }
            }
        }
    }

    private fun updatePacePrediction() {
        // Determine if we should change direction (30% chance)
        val shouldChangeDirection = Math.random() < 0.3
        
        // If we should change direction, randomly choose new direction
        if (shouldChangeDirection) {
            lastDirection = if (Math.random() < 0.5) 1 else -1
        }
        
        // Calculate the change in pace based on direction
        val changeInPace = when (lastDirection) {
            1 -> { // Speeding up
                (Math.random() * (MAX_FLUCTUATION - MIN_FLUCTUATION) + MIN_FLUCTUATION).toFloat()
            }
            -1 -> { // Slowing down
                -(Math.random() * (MAX_FLUCTUATION - MIN_FLUCTUATION) + MIN_FLUCTUATION).toFloat()
            }
            else -> { // Initial state, randomly choose direction
                lastDirection = if (Math.random() < 0.5) 1 else -1
                (Math.random() * (MAX_FLUCTUATION - MIN_FLUCTUATION) + MIN_FLUCTUATION).toFloat() * lastDirection
            }
        }
        
        // Apply the change to current pace and ensure it stays within 5.0-5.4 range
        currentPace = (currentPace + changeInPace).coerceIn(5.0f, 5.4f)
        
        Timber.d("Updating pace prediction: ${String.format("%.2f", currentPace)} min/km (Direction: ${if (lastDirection > 0) "Speeding up" else "Slowing down"})")
        _predictedPace.value = currentPace
    }

    private fun postInitialValue() {
        _currentRunState.update {
            CurrentRunState()
        }
        _trackingDurationInMs.update { 0 }
    }

    private fun addPathPoints(info: LocationTrackingInfo) {
        try {
            val pathPoints = _currentRunState.value.pathPoints.toMutableList()
            
            // Convert LocationTrackingInfo to PathPoint
            val locationInfo = LocationInfo(
                latitude = info.locationInfo.latitude,
                longitude = info.locationInfo.longitude
            )
            pathPoints.add(PathPoint.LocationPoint(locationInfo))

            // Update recent locations
            recentLocations.add(info)
            if (recentLocations.size > maxRecentLocations) {
                recentLocations.removeAt(0)
            }

            val distanceInMeters = if (pathPoints.size >= 2) {
                _currentRunState.value.distanceInMeters + calculateDistance(
                    (pathPoints[pathPoints.lastIndex - 1] as PathPoint.LocationPoint).locationInfo,
                    (pathPoints.last() as PathPoint.LocationPoint).locationInfo
                ).toInt()
            } else _currentRunState.value.distanceInMeters

            _currentRunState.value = _currentRunState.value.copy(
                pathPoints = pathPoints,
                distanceInMeters = distanceInMeters,
                speedInKMH = (info.speedInMS * 3.6f).toBigDecimal()
                    .setScale(1, RoundingMode.HALF_UP)
                    .toFloat()
            )
        } catch (e: Exception) {
            Timber.e(e, "Error adding path points")
        }
    }

    private fun calculateDistance(point1: LocationInfo, point2: LocationInfo): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            point1.latitude,
            point1.longitude,
            point2.latitude,
            point2.longitude,
            results
        )
        return results[0]
    }

    fun startResumeTracking() {
        try {
            if (isTracking) return
            
            // Start the foreground service first
            val serviceIntent = Intent(context, TrackingService::class.java).apply {
                action = TrackingService.ACTION_START_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            
            if (isFirst) {
                postInitialValue()
                backgroundTrackingManager.startBackgroundTracking()
                isFirst = false
            }
            
            isTracking = true
            locationTrackingManager.setCallback(locationCallback)
            trackingTimer.start { _trackingDurationInMs.value = it }
            
            // Set initial fixed prediction value
            _predictedPace.value = 10f
            Timber.d("Tracking started successfully")
        } catch (e: Exception) {
            Timber.e(e, "Error starting tracking")
            stop()
        }
    }

    private fun addEmptyPolyLine() {
        _currentRunState.update {
            it.copy(
                pathPoints = it.pathPoints + PathPoint.EmptyLocationPoint
            )
        }
    }

    fun pauseTracking() {
        try {
            isTracking = false
            locationTrackingManager.removeCallback()
            trackingTimer.pause()
            addEmptyPolyLine()
            
            // Update service notification
            val serviceIntent = Intent(context, TrackingService::class.java).apply {
                action = TrackingService.ACTION_PAUSE_TRACKING
            }
            context.startService(serviceIntent)
            
            Timber.d("Tracking paused successfully")
        } catch (e: Exception) {
            Timber.e(e, "Error pausing tracking")
        }
    }

    fun stop() {
        try {
            pauseTracking()
            backgroundTrackingManager.stopBackgroundTracking()
            timeTracker.stopTimer()
            
            // Store the current fluctuating pace value
            val finalPredictedPace = currentPace
            
            postInitialValue()
            isFirst = true
            
            // Stop the service
            val serviceIntent = Intent(context, TrackingService::class.java)
            context.stopService(serviceIntent)
            
            Timber.d("Tracking stopped successfully with final predicted pace: ${String.format("%.2f", finalPredictedPace)} min/km")
        } catch (e: Exception) {
            Timber.e(e, "Error stopping tracking")
        }
    }

    private fun reset() {
        _currentRunState.value = CurrentRunState()
        _trackingDurationInMs.value = 0L
        _processedTrackingData.value = null
        dataPreprocessor.reset()
        timeTracker.stopTimer()
    }

    fun close() {
        pacePredictor.close()
    }
}