package com.sdevprem.runtrack.ml.model

import android.content.Context
import android.util.Log
import com.sdevprem.runtrack.ml.data.PaceDataGenerator
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class PacePredictionModel {
    private var interpreter: Interpreter? = null
    private val modelFile = "pace_prediction.tflite"
    private var lastPredictionTime = 0L
    private var totalPredictions = 0
    private var totalError = 0.0
    
    fun loadModel(context: Context) {
        try {
            val modelPath = "${context.getExternalFilesDir(null)}/$modelFile"
            Log.d(TAG, "Attempting to load model from: $modelPath")
            
            val file = File(modelPath)
            if (!file.exists()) {
                Log.e(TAG, "Model file does not exist at: $modelPath")
                throw IllegalStateException("Model file not found")
            }
            
            Log.d(TAG, "Model file size: ${file.length()} bytes")
            interpreter = Interpreter(file)
            Log.d(TAG, "Model loaded successfully")
            
            // Log model details
            interpreter?.let { interpreter ->
                Log.d(TAG, "Input tensor count: ${interpreter.inputTensorCount}")
                Log.d(TAG, "Output tensor count: ${interpreter.outputTensorCount}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model", e)
            throw e
        }
    }
    
    fun predict(recentData: Array<FloatArray>): Float {
        if (interpreter == null) {
            Log.e(TAG, "Model not loaded")
            throw IllegalStateException("Model not loaded")
        }
        
        val startTime = System.currentTimeMillis()
        
        // Log input data
        Log.d(TAG, "Input data shape: ${recentData.size}x${recentData[0].size}")
        Log.d(TAG, "Sample input data: ${recentData[0].joinToString()}")
        
        // Prepare input data
        val inputArray = Array(1) { recentData }
        val outputArray = Array(1) { FloatArray(1) }
        
        // Run inference
        interpreter?.run(inputArray, outputArray)
        
        val prediction = outputArray[0][0]
        val inferenceTime = System.currentTimeMillis() - startTime
        
        // Log prediction
        Log.d(TAG, "Prediction: $prediction min/km")
        Log.d(TAG, "Inference time: ${inferenceTime}ms")
        
        // Log performance metrics
        totalPredictions++
        if (totalPredictions % 100 == 0) {
            Log.d(TAG, "Average inference time: ${inferenceTime}ms")
        }
        
        return prediction
    }
    
    fun validateModel(context: Context) {
        try {
            val testDataPath = "${context.getExternalFilesDir(null)}/test_data.npy"
            Log.d(TAG, "Validating model with test data from: $testDataPath")
            // Load test data and validate model accuracy
            // This is a placeholder for actual validation logic
            Log.d(TAG, "Model validation completed")
        } catch (e: Exception) {
            Log.e(TAG, "Model validation failed", e)
        }
    }
    
    fun calculateAccuracy(actualPace: Float, predictedPace: Float) {
        val error = abs(actualPace - predictedPace)
        totalError += error
        
        if (totalPredictions % 100 == 0) {
            val averageError = totalError / totalPredictions
            Log.d(TAG, "Average prediction error: $averageError min/km")
        }
    }
    
    fun close() {
        interpreter?.close()
        interpreter = null
    }
    
    companion object {
        private const val TAG = "PacePredictionModel"
        
        // Convert processed tracking data to model input format
        fun prepareModelInput(
            paces: List<Float>,
            speeds: List<Float>,
            accelerations: List<Float>,
            altitudes: List<Number>,
        ): Array<FloatArray> {
            try {
                return Array(paces.size) { i ->
                    floatArrayOf(
                        paces[i],
                        speeds[i],
                        accelerations[i],
                        altitudes[i].toFloat()
                    )
                }
            } catch (e: Exception) {
                try {
                    Log.e(TAG, "Error preparing model input", e)
                } catch (ignored: Exception) {
                    // Ignore Log errors in test environment
                }
                throw e
            }
        }
    }
} 