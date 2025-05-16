package com.sdevprem.runtrack.ml.data

import kotlin.math.sin
import kotlin.random.Random

class PaceDataGenerator {
    companion object {
        const val SEQUENCE_LENGTH = 10 // Match with TrackingDataPreprocessor.maxHistorySize
        const val FEATURES = 4 // pace, speed, acceleration, altitude (if available)
        
        fun generateSyntheticData(numSamples: Int): Pair<Array<Array<FloatArray>>, Array<FloatArray>> {
            val input = Array(numSamples) { Array(SEQUENCE_LENGTH) { FloatArray(FEATURES) } }
            val output = Array(numSamples) { FloatArray(1) }
            
            for (i in 0 until numSamples) {
                // Generate a base pace pattern (simulating a realistic running pattern)
                val basePace = 5f + Random.nextFloat() * 3f // Base pace between 5-8 min/km
                
                // Generate sequence data
                for (j in 0 until SEQUENCE_LENGTH) {
                    // Add some realistic variations to pace
                    val timePoint = (i * SEQUENCE_LENGTH + j) / 60f
                    val variation = sin(timePoint) * 0.5f + Random.nextFloat() * 0.3f
                    
                    val currentPace = basePace + variation
                    val speed = 16.67f / currentPace // Convert pace to speed (km/h)
                    val acceleration = if (j > 0) {
                        (speed - (16.67f / input[i][j-1][0])) / 1f // 1 second intervals
                    } else 0f
                    
                    // Simulate altitude changes
                    val altitude = 100f + sin(timePoint * 0.1f) * 20f + Random.nextFloat() * 5f
                    
                    input[i][j] = floatArrayOf(currentPace, speed, acceleration, altitude)
                }
                
                // Generate target (next pace value)
                // Make it dependent on the trend of previous paces
                val lastPaces = input[i].map { it[0] }.takeLast(3)
                val trend = lastPaces.average().toFloat()
                output[i] = floatArrayOf(trend + Random.nextFloat() * 0.2f - 0.1f)
            }
            
            return Pair(input, output)
        }
    }
} 