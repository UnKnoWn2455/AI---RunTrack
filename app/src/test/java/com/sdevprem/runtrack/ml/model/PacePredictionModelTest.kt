package com.sdevprem.runtrack.ml.model

import org.junit.Test
import kotlin.test.assertEquals

class PacePredictionModelTest {

    @Test
    fun `test model input preparation`() {
        val paces = listOf(5.0f, 5.2f, 5.1f)
        val speeds = listOf(12.0f, 11.8f, 12.2f)
        val accelerations = listOf(0.5f, 0.3f, 0.4f)
        val altitudes = listOf(100f, 102f, 101f)

        val input = PacePredictionModel.Companion.prepareModelInput(
            paces = paces,
            speeds = speeds,
            accelerations = accelerations,
            altitudes = altitudes.map { it.toInt() }
        )

        assertEquals(paces.size, input.size)
        assertEquals(4, input[0].size) // 4 features per data point

        // Verify first data point
        assertEquals(paces[0], input[0][0])
        assertEquals(speeds[0], input[0][1])
        assertEquals(accelerations[0], input[0][2])
        assertEquals(altitudes[0].toFloat(), input[0][3])
    }

    @Test
    fun `test input data ranges`() {
        val paces = listOf(3.0f, 10.0f, 5.0f) // min/km
        val speeds = listOf(20.0f, 6.0f, 12.0f) // km/h
        val accelerations = listOf(2.0f, -2.0f, 0.0f) // m/s²
        val altitudes = listOf(0, 2000, 1000) // meters

        val input = PacePredictionModel.Companion.prepareModelInput(
            paces = paces,
            speeds = speeds,
            accelerations = accelerations,
            altitudes = altitudes
        )

        // Test data dimensions
        assertEquals(3, input.size)
        assertEquals(4, input[0].size)

        // Test data ranges
        input.forEach { dataPoint ->
            assert(dataPoint[0] in 3.0f..10.0f) { "Pace should be between 3-10 min/km" }
            assert(dataPoint[1] in 6.0f..20.0f) { "Speed should be between 6-20 km/h" }
            assert(dataPoint[2] in -2.0f..2.0f) { "Acceleration should be between -2 and 2 m/s²" }
            assert(dataPoint[3] in 0.0f..2000.0f) { "Altitude should be between 0-2000m" }
        }
    }

    @Test
    fun `test input data consistency`() {
        val paces = listOf(5.0f, 5.2f)
        val speeds = listOf(12.0f, 11.8f)
        val accelerations = listOf(0.5f, 0.3f)
        val altitudes = listOf(100, 102)

        val input = PacePredictionModel.Companion.prepareModelInput(
            paces = paces,
            speeds = speeds,
            accelerations = accelerations,
            altitudes = altitudes
        )

        // Test that pace and speed are inversely related
        // Higher pace (min/km) should correspond to lower speed (km/h)
        for (i in 0 until input.size - 1) {
            if (input[i][0] > input[i + 1][0]) { // If pace increases
                assert(input[i][1] < input[i + 1][1]) { "Speed should decrease when pace increases" }
            } else if (input[i][0] < input[i + 1][0]) { // If pace decreases
                assert(input[i][1] > input[i + 1][1]) { "Speed should increase when pace decreases" }
            }
        }
    }
} 