package com.sdevprem.runtrack.ui.screen.currentrun

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sdevprem.runtrack.data.model.Run
import com.sdevprem.runtrack.data.repository.AppRepository
import com.sdevprem.runtrack.di.ApplicationScope
import com.sdevprem.runtrack.di.IoDispatcher
import com.sdevprem.runtrack.domain.model.CurrentRunStateWithCalories
import com.sdevprem.runtrack.domain.tracking.TrackingManager
import com.sdevprem.runtrack.domain.usecase.GetCurrentRunStateWithCaloriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.RoundingMode
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class CurrentRunViewModel @Inject constructor(
    private val trackingManager: TrackingManager,
    private val repository: AppRepository,
    @ApplicationScope
    private val appCoroutineScope: CoroutineScope,
    @IoDispatcher
    private val ioDispatcher: CoroutineDispatcher,
    getCurrentRunStateWithCaloriesUseCase: GetCurrentRunStateWithCaloriesUseCase
) : ViewModel() {
    val currentRunState = trackingManager.currentRunState
    val trackingDurationInMs = trackingManager.trackingDurationInMs
    val predictedPace = trackingManager.predictedPace

    val currentRunStateWithCalories = getCurrentRunStateWithCaloriesUseCase()
        .stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            CurrentRunStateWithCalories()
        )

    fun startResumeTracking() {
        trackingManager.startResumeTracking()
    }

    fun pauseTracking() {
        trackingManager.pauseTracking()
    }

    fun stopTracking() {
        trackingManager.stop()
    }

    fun finishRun(bitmap: Bitmap) {
        trackingManager.pauseTracking()
        saveRun(
            Run(
                img = bitmap,
                avgSpeedInKMH = currentRunStateWithCalories.value.currentRunState.distanceInMeters
                    .toBigDecimal()
                    .multiply(3600.toBigDecimal())
                    .divide(trackingDurationInMs.value.toBigDecimal(), 2, RoundingMode.HALF_UP)
                    .toFloat(),
                distanceInMeters = currentRunStateWithCalories.value.currentRunState.distanceInMeters,
                durationInMillis = trackingDurationInMs.value,
                timestamp = Date(),
                caloriesBurned = currentRunStateWithCalories.value.caloriesBurnt
            )
        )
        trackingManager.stop()
    }

    private fun saveRun(run: Run) = appCoroutineScope.launch(ioDispatcher) {
        repository.insertRun(run)
    }
}