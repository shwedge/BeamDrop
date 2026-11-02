package com.example.hardware

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

sealed interface TouchGestureEvent {
    data class Approaching(val distanceCm: Float) : TouchGestureEvent
    data class ContactTriggered(val source: String) : TouchGestureEvent
    object Cleared : TouchGestureEvent
}

/**
 * Lightweight gesture coordinator that avoids continuous high-frequency accelerometer/proximity
 * hardware polling to prevent phone heating and accidental false-positive transfers when a
 * user's hand passes near the screen. Real hardware tap-to-beam is handled by NfcBeamHelper.
 */
class TouchSensorManager(@Suppress("UNUSED_PARAMETER") private val context: Context) : SensorEventListener {
    private val _events = MutableSharedFlow<TouchGestureEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<TouchGestureEvent> = _events.asSharedFlow()

    private var isListening = false
    private val scope = CoroutineScope(Dispatchers.Default)

    fun startListening() {
        isListening = true
    }

    fun stopListening() {
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
        // Passive: NFC ReaderMode handles real physical device taps with zero thermal overhead
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
