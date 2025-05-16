package com.sdevprem.runtrack.background.tracking.service

import android.content.Intent
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.sdevprem.runtrack.background.tracking.service.notification.TrackingNotificationHelper
import com.sdevprem.runtrack.domain.tracking.TrackingManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import javax.inject.Inject

@AndroidEntryPoint
class TrackingService : LifecycleService() {

    companion object {
        const val TAG = "TrackingService"
        const val ACTION_PAUSE_TRACKING = "action_pause_tracking"
        const val ACTION_RESUME_TRACKING = "action_resume_tracking"
        const val ACTION_START_SERVICE = "action_start_service"
    }

    @Inject
    lateinit var trackingManager: TrackingManager

    @Inject
    lateinit var notificationHelper: TrackingNotificationHelper
    private var job: Job? = null
    private var isServiceRunning = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        isServiceRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "Service started with action: ${intent?.action}")
        
        try {
            when (intent?.action) {
                ACTION_PAUSE_TRACKING -> {
                    Log.d(TAG, "Pausing tracking")
                    trackingManager.pauseTracking()
                }
                ACTION_RESUME_TRACKING -> {
                    Log.d(TAG, "Resuming tracking")
                    trackingManager.startResumeTracking()
                }
                ACTION_START_SERVICE -> {
                    Log.d(TAG, "Starting service")
                    startForeground(
                        TrackingNotificationHelper.TRACKING_NOTIFICATION_ID,
                        notificationHelper.getDefaultNotification()
                    )

                    if (job == null) {
                        Log.d(TAG, "Starting notification updates")
                        job = combine(
                            trackingManager.trackingDurationInMs,
                            trackingManager.currentRunState
                        ) { duration, currentRunState ->
                            try {
                                notificationHelper.updateTrackingNotification(
                                    durationInMillis = duration,
                                    isTracking = currentRunState.isTracking
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Error updating notification", e)
                            }
                        }.launchIn(lifecycleScope)
                    }
                }
                else -> Log.w(TAG, "Unknown action: ${intent?.action}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand", e)
            // Restart the service if there's an error
            return START_REDELIVER_INTENT
        }

        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "Service being destroyed")
        try {
            notificationHelper.removeTrackingNotification()
            job?.cancel()
            job = null
            isServiceRunning = false
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        }
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "Task removed, restarting service")
        // Restart the service if the app is removed from recent apps
        val restartServiceIntent = Intent(applicationContext, TrackingService::class.java).apply {
            action = ACTION_START_SERVICE
        }
        startService(restartServiceIntent)
        super.onTaskRemoved(rootIntent)
    }
}