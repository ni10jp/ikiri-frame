package jp.ni10.ikiriframe.logo

import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.view.View
import androidx.annotation.RequiresApi
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

/** Device-defined effects only: resistance, then one release click. */
internal class LogoDismissHaptics(private val view: View, private val scope: CoroutineScope) {
    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        view.context.getSystemService(VibratorManager::class.java)?.defaultVibrator else null
    @get:RequiresApi(Build.VERSION_CODES.S)
    private val primitive get() = VibrationEffect.Composition.PRIMITIVE_LOW_TICK
    private val resistanceSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        vibrator?.areAllPrimitivesSupported(primitive) == true
    private val primitiveDuration = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && resistanceSupported) {
        vibrator?.getPrimitiveDurations(primitive)?.firstOrNull()?.takeIf { it > 0 }?.toLong() ?: 20L
    } else 20L
    private var playing = false
    private var resistance = false
    private var resistanceJob: Job? = null
    private var resistanceProgress = 0f

    // USAGE_TOUCH leaves the system's touch-feedback setting to Android.
    private fun enabled() = view.hasWindowFocus() && view.isHapticFeedbackEnabled

    fun resist(progress: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !enabled() || !resistanceSupported || progress <= 0f) {
            stopResistance()
            return
        }
        val device = vibrator ?: return
        val attributes = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)
        resistanceProgress = progress.coerceIn(0f, 1f)
        if (resistanceJob?.isActive == true) return
        resistance = true
        resistanceJob = scope.launch {
            try {
                var effort = 0f
                while (isActive && enabled()) {
                    // Smooth changes in drag effort; do not restart the effect on every pointer event.
                    effort += (resistanceProgress - effort) * .3f
                    val intensity = effort * effort * (3f - 2f * effort)
                    device.vibrate(VibrationEffect.startComposition()
                        .addPrimitive(primitive, .10f + .36f * intensity).compose(), attributes)
                    playing = true
                    // Closely spaced device primitives make a continuous resistance texture.
                    // Let each primitive finish before sending another one.
                    delay((32f - 12f * intensity).roundToLong().coerceAtLeast(primitiveDuration + 2L))
                }
            } finally {
                // A cancelled resistance job must never cancel the subsequent release click.
                if (resistanceJob === coroutineContext[Job]) {
                    resistanceJob = null
                    if (playing) device.cancel()
                    playing = false
                    resistance = false
                }
            }
        }
    }

    fun detach() {
        stop()
        if (!enabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && vibrator != null) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK),
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
            playing = true
        } else {
            ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CONFIRM)
        }
    }

    fun stopResistance() {
        if (resistance) stop()
    }

    fun stop() {
        val job = resistanceJob
        resistanceJob = null
        job?.cancel()
        if (playing) vibrator?.cancel()
        playing = false
        resistance = false
        resistanceProgress = 0f
    }
}
