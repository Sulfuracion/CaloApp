package com.controlcal.app.camera

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Utility helper to trigger short haptic feedback vibrations when a barcode is accepted.
 */
object VibrationHelper {

    private const val SHORT_VIBRATION_DURATION_MS = 50L

    /**
     * Emits a short haptic vibration pulse.
     */
    fun vibrateShort(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator?.hasVibrator() == true) {
                val effect = VibrationEffect.createOneShot(
                    SHORT_VIBRATION_DURATION_MS,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
                vibrator.vibrate(effect)
            }
        } catch (_: Throwable) {
            // Ignore failure if vibration hardware is absent or disabled
        }
    }
}
