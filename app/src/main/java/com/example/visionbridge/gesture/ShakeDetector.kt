package com.example.visionbridge.gesture

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.sqrt

/**
 * Lightweight, lifecycle-aware device shake detector.
 *
 * Distinguishes intentional device shakes from normal everyday movements
 * (e.g. walking, phone tilt, table set-down, camera panning) by requiring:
 * 1. Peak acceleration magnitude above threshold (>13.5 m/s² dynamic acceleration).
 * 2. At least 3 directional reversals within a sliding 550ms time window.
 * 3. 1500ms cooldown to prevent multi-rebound activations.
 */
class ShakeDetector(
    private val onShakeDetected: () -> Unit
) : SensorEventListener {

    private var isListening = false
    private var lastShakeTimestamp = 0L

    // Sliding window tracking
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var lastDirectionX = 0
    private var lastDirectionY = 0
    private var directionChangeCount = 0
    private var windowStartTimestamp = 0L

    fun start(sensorManager: SensorManager?): Boolean {
        if (sensorManager == null || isListening) return false

        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer == null) {
            Log.w(TAG, "Accelerometer not available on this device")
            return false
        }

        // SENSOR_DELAY_UI (approx 60ms) provides sufficient sample rate with minimal battery impact
        val registered = sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        isListening = registered
        Log.d(TAG, "ShakeDetector started: registered=$registered")
        return registered
    }

    fun stop(sensorManager: SensorManager?) {
        if (!isListening) return
        try {
            sensorManager?.unregisterListener(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering ShakeDetector", e)
        } finally {
            isListening = false
            resetWindow()
            Log.d(TAG, "ShakeDetector stopped")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val now = System.currentTimeMillis()
        if (now - lastShakeTimestamp < SHAKE_COOLDOWN_MS) {
            return
        }

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Calculate total acceleration magnitude
        val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        val netAcceleration = Math.abs(magnitude - SensorManager.GRAVITY_EARTH)

        // Check if net acceleration exceeds deliberate shake threshold
        if (netAcceleration > SHAKE_ACCEL_THRESHOLD) {
            if (windowStartTimestamp == 0L || (now - windowStartTimestamp > SHAKE_WINDOW_MS)) {
                windowStartTimestamp = now
                directionChangeCount = 0
            }

            val dx = x - lastX
            val dy = y - lastY

            val currentDirectionX = if (dx > 2.0f) 1 else if (dx < -2.0f) -1 else 0
            val currentDirectionY = if (dy > 2.0f) 1 else if (dy < -2.0f) -1 else 0

            if (currentDirectionX != 0 && currentDirectionX != lastDirectionX) {
                directionChangeCount++
                lastDirectionX = currentDirectionX
            }
            if (currentDirectionY != 0 && currentDirectionY != lastDirectionY) {
                directionChangeCount++
                lastDirectionY = currentDirectionY
            }

            if (directionChangeCount >= MIN_DIRECTION_CHANGES) {
                lastShakeTimestamp = now
                resetWindow()
                Log.d(TAG, "Deliberate shake gesture detected! netAcceleration=$netAcceleration m/s²")
                onShakeDetected()
            }
        } else if (windowStartTimestamp != 0L && (now - windowStartTimestamp > SHAKE_WINDOW_MS)) {
            resetWindow()
        }

        lastX = x
        lastY = y
        lastZ = z
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun resetWindow() {
        windowStartTimestamp = 0L
        directionChangeCount = 0
        lastDirectionX = 0
        lastDirectionY = 0
    }

    companion object {
        private const val TAG = "VB_GESTURE"
        private const val SHAKE_ACCEL_THRESHOLD = 13.5f // m/s² above gravity
        private const val SHAKE_WINDOW_MS = 550L
        private const val SHAKE_COOLDOWN_MS = 1500L
        private const val MIN_DIRECTION_CHANGES = 3
    }
}
