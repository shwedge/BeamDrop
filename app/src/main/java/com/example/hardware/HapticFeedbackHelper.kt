package com.example.hardware

import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class HapticFeedbackHelper(private val context: Context) {
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /**
     * Subtle pulse as two devices come into proximity
     */
    fun onApproachTick() {
        vibrate(30, 60)
    }

    /**
     * Crisp double-tap when the physical touch / top contact is locked
     */
    fun onTopContactLocked() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 35, 60, 45)
            val amplitudes = intArrayOf(0, 180, 0, 255)
            vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(70)
        }
    }

    /**
     * Intentionally no-op during active file streaming to prevent continuous actuator heat and battery drain.
     */
    fun onTransferChunkTick() {
        // No continuous vibration during streaming
    }

    /**
     * Satisfying triumphant vibration pulse on transfer completion
     */
    fun onTransferCompleted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 40, 50, 70, 50, 90)
            val amplitudes = intArrayOf(0, 120, 0, 200, 0, 255)
            vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(120)
        }
    }

    private fun vibrate(durationMs: Long, amplitude: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255)))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(durationMs)
        }
    }
}
