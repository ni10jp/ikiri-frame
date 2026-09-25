package jp.ni10.ikiriframe.home

import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibrationEffect.Composition.PRIMITIVE_THUD
import android.os.VibratorManager
import android.provider.Settings
import android.view.View
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat

internal class RollingHaptics(private val view: View) {
    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        view.context.getSystemService(VibratorManager::class.java)?.defaultVibrator else null
    private val impactSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        vibrator?.areAllPrimitivesSupported(PRIMITIVE_THUD) == true
    private var hasPlayback = false
    private var lastImpactAt = 0L

    fun update(ball: RollingBall) {
        if (!view.hasWindowFocus() || !view.isHapticFeedbackEnabled ||
            Settings.System.getInt(view.context.contentResolver,
                Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0) {
            stop()
            return
        }
        val now = SystemClock.uptimeMillis()
        if (ball.impactSpeed >= 60f && now - lastImpactAt >= 80L) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && impactSupported) {
                val scale = (ball.impactSpeed / 1600f).coerceIn(.12f, .65f)
                // Android classifies on-screen physical animations as media vibration.
                vibrator?.vibrate(VibrationEffect.startComposition()
                    .addPrimitive(PRIMITIVE_THUD, scale).compose(),
                    VibrationAttributes.createForUsage(VibrationAttributes.USAGE_MEDIA))
                hasPlayback = true
            } else {
                ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CONFIRM)
            }
            lastImpactAt = now
        }
    }

    fun stop() {
        if (hasPlayback) vibrator?.cancel()
        hasPlayback = false
        lastImpactAt = 0L
    }
}
