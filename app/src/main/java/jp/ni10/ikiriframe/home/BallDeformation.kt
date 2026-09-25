package jp.ni10.ikiriframe.home

import androidx.compose.animation.core.FloatSpringSpec
import androidx.compose.animation.core.Spring

/** Visual compression only; the collision body remains spherical. */
internal class BallDeformation {
    private val spring = FloatSpringSpec(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
        visibilityThreshold = .001f,
    )
    var compression = 0f; private set
    var normalX = 0f; private set
    var normalY = 0f; private set
    var isRunning = false; private set
    private var velocity = 0f
    private var startCompression = 0f
    private var startVelocity = 0f
    private var elapsed = 0L
    private var duration = 0L

    fun impact(speed: Float, radius: Float, nx: Float, ny: Float) {
        // Resting against a wall must not keep injecting tiny springs.
        if (speed < 60f || radius <= 0f) return
        normalX = nx; normalY = ny
        startCompression = compression
        startVelocity = (velocity + speed / radius * .22f).coerceIn(0f, 6f)
        elapsed = 0L
        duration = spring.getDurationNanos(startCompression, 0f, startVelocity)
        isRunning = true
    }

    /** Returns true for the final frame as well, so the undeformed shape is drawn. */
    fun advance(seconds: Float): Boolean {
        if (!isRunning) return false
        elapsed += (seconds.coerceIn(0f, .05f) * 1_000_000_000).toLong()
        if (elapsed >= duration) {
            reset()
        } else {
            compression = spring.getValueFromNanos(elapsed, startCompression, 0f, startVelocity)
                .coerceIn(-.025f, .10f)
            velocity = spring.getVelocityFromNanos(elapsed, startCompression, 0f, startVelocity)
        }
        return true
    }

    fun remap(quarterTurns: Int) {
        val previousX = normalX
        when (quarterTurns) {
            1 -> { normalX = normalY; normalY = -previousX }
            2 -> { normalX = -normalX; normalY = -normalY }
            3 -> { normalX = -normalY; normalY = previousX }
        }
    }

    fun reset() {
        compression = 0f; velocity = 0f
        normalX = 0f; normalY = 0f
        isRunning = false
    }
}
