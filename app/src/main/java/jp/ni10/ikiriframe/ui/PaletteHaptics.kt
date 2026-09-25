package jp.ni10.ikiriframe.ui

import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibrationEffect.Composition.PRIMITIVE_SPIN
import android.os.Vibrator
import android.provider.Settings
import android.view.View
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat

/** A single low-intensity OS spin supplies the elastic back-and-forth sensation. */
@Suppress("DEPRECATION")
internal fun performPaletteSelectionHaptic(view: View) {
    if (!view.hasWindowFocus() || !view.isHapticFeedbackEnabled ||
        Settings.System.getInt(view.context.contentResolver,
            Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0) return
    val vibrator = view.context.getSystemService(Vibrator::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        vibrator?.areAllPrimitivesSupported(PRIMITIVE_SPIN) == true) {
        val effect = VibrationEffect.startComposition()
            .addPrimitive(PRIMITIVE_SPIN, .25f).compose()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else {
            vibrator.vibrate(effect, AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        }
    } else {
        ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CLOCK_TICK)
    }
}
