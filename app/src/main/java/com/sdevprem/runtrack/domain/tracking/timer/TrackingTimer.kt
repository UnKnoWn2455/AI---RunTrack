package com.sdevprem.runtrack.domain.tracking.timer

import android.os.Handler
import android.os.Looper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackingTimer @Inject constructor() {
    private var isTracking = false
    private var timeStarted = 0L
    private var lastSecondTimestamp = 0L
    private var timeRun = 0L

    private val timeRunInMillis = mutableListOf<Long>()
    private val handler = Handler(Looper.getMainLooper())

    private var onTick: ((Long) -> Unit)? = null

    private val timeRunnable = object : Runnable {
        override fun run() {
            val currentTimeMillis = System.currentTimeMillis()
            timeRun += currentTimeMillis - lastSecondTimestamp
            onTick?.invoke(timeRun)
            lastSecondTimestamp = currentTimeMillis
            handler.postDelayed(this, TIMER_UPDATE_INTERVAL)
        }
    }

    fun start(onTick: (Long) -> Unit) {
        if (!isTracking) {
            isTracking = true
            timeStarted = System.currentTimeMillis()
            lastSecondTimestamp = timeStarted
            this.onTick = onTick
            handler.postDelayed(timeRunnable, TIMER_UPDATE_INTERVAL)
        }
    }

    fun pause() {
        isTracking = false
        handler.removeCallbacks(timeRunnable)
        timeRunInMillis.add(timeRun)
    }

    fun reset() {
        timeRun = 0L
        timeStarted = 0L
        lastSecondTimestamp = 0L
        timeRunInMillis.clear()
        isTracking = false
        onTick = null
        handler.removeCallbacks(timeRunnable)
    }

    companion object {
        private const val TIMER_UPDATE_INTERVAL = 50L // Update every 50ms
    }
} 