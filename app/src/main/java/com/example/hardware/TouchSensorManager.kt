package com.example.hardware

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlin.math.sqrt

sealed interface TouchGestureEvent {
    data class Approaching(val distanceCm: Float) : TouchGestureEvent
    data class ContactTriggered(val source: String) : TouchGestureEvent
    object Cleared : TouchGestureEvent
}

class TouchSensorManager(private val context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _events = MutableSharedFlow<TouchGestureEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<TouchGestureEvent> = _events.asSharedFlow()

    private var isNear = false
    private var lastNearTimestamp = 0L
    private var isListening = false
    private val scope = CoroutineScope(Dispatchers.Default)

    fun startListening() {
        if (isListening || sensorManager == null) return
        proximitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        isListening = true
    }

    fun stopListening() {
        if (!isListening || sensorManager == null) return
        sensorManager.unregisterListener(this)
        isListening = false
    }

    fun triggerSimulatedTopTouch() {
        scope.launch {
            _events.emit(TouchGestureEvent.Approaching(2.0f))
            kotlinx.coroutines.delay(120)
            _events.emit(TouchGestureEvent.ContactTriggered("Top Touch Handoff"))
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> {
                val distance = event.values[0]
                val maxRange = proximitySensor?.maximumRange ?: 5f
                val wasNear = isNear
                isNear = distance < maxRange

                if (isNear) {
                    lastNearTimestamp = System.currentTimeMillis()
                    scope.launch {
                        _events.emit(TouchGestureEvent.Approaching(distance))
                        // If near, trigger contact handoff
                        _events.emit(TouchGestureEvent.ContactTriggered("Proximity Bezel Touch"))
                    }
                } else if (wasNear) {
                    scope.launch {
                        _events.emit(TouchGestureEvent.Cleared)
                    }
                }
            }
            Sensor.TYPE_ACCELEROMETER -> {
                // Check for subtle tap/bump impulse while device is near
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
                val delta = kotlin.math.abs(magnitude - SensorManager.GRAVITY_EARTH)

                // If device experiences a micro-bump while near proximity, reinforce top-touch
                if (isNear && delta > 2.8f) {
                    scope.launch {
                        _events.emit(TouchGestureEvent.ContactTriggered("Physical Bump"))
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
