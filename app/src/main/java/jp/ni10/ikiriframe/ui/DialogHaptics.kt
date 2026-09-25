package jp.ni10.ikiriframe.ui

import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.View

/** The label reset alone uses a short custom buzz, as opposed to a predefined click. */
@Suppress("DEPRECATION")
internal fun performResetHaptic(view: View) {
    if (!view.hasWindowFocus() || !view.isHapticFeedbackEnabled ||
        Settings.System.getInt(view.context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0) return
    val vibrator = view.context.getSystemService(Vibrator::class.java) ?: return
    if (!vibrator.hasVibrator()) return
    val audio = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val amplitude = if (vibrator.hasAmplitudeControl()) 100 else VibrationEffect.DEFAULT_AMPLITUDE
        val effect = VibrationEffect.createOneShot(35, amplitude)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else {
            vibrator.vibrate(effect, audio)
        }
    } else {
        vibrator.vibrate(35, audio)
    }
}
