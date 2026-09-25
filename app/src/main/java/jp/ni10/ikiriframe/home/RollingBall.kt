package jp.ni10.ikiriframe.home

import kotlin.math.*

/** A device corner in stage-local dp. The signs identify its outward-facing quadrant. */
internal data class ScreenCorner(val x: Float, val y: Float, val radius: Float,
    val outwardX: Float, val outwardY: Float)

/** Screen coordinates in dp; the ball rolls inside the actual display outline. */
internal class RollingBall {
    var x = 0f; private set
    var y = 0f; private set
    var vx = 0f; private set
    var vy = 0f; private set
    var radius = 44f; private set
    var rotation = floatArrayOf(0f, 0f, 0f, 1f); private set
    var travelled = 0f; private set
    var impactSpeed = 0f; private set
    var impactNormalX = 0f; private set
    var impactNormalY = 0f; private set
    private var width = 0f
    private var height = 0f
    private var corners = emptyList<ScreenCorner>()

    fun resize(width: Float, height: Float) {
        val previousWidth = this.width
        val previousHeight = this.height
        this.width = width.coerceAtLeast(1f)
        this.height = height.coerceAtLeast(1f)
        corners = emptyList()
        radius = minOf(44f, this.width / 5f, this.height / 5f)
        x = if (previousWidth > 0) x / previousWidth * this.width else this.width * .66f
        y = if (previousHeight > 0) y / previousHeight * this.height else this.height * .42f
        confine(reflectVelocity = false)
    }

    fun setCorners(corners: List<ScreenCorner>) {
        this.corners = corners
        confine(reflectVelocity = false)
    }

    fun stop() { vx = 0f; vy = 0f; travelled = 0f; impactSpeed = 0f }

    fun remapVelocity(quarterTurns: Int) {
        val previousX = vx
        when (quarterTurns) {
            1 -> { vx = vy; vy = -previousX }
            2 -> { vx = -vx; vy = -vy }
            3 -> { vx = -vy; vy = previousX }
        }
    }

    fun step(seconds: Float, gravityX: Float, gravityY: Float): Boolean {
        travelled = 0f
        impactSpeed = 0f
        if (width <= 0 || height <= 0) return false
        val duration = seconds.coerceIn(0f, .05f)
        val steps = ceil(duration / (1f / 120)).toInt().coerceAtLeast(1)
        val dt = duration / steps
        var moved = false
        repeat(steps) {
            // Stronger tilt response makes rolling faster without prolonging coasting on a level screen.
            val ax = if (abs(gravityX) < .045f) 0f else gravityX.coerceIn(-9.81f, 9.81f) * 260f * 5 / 7
            val ay = if (abs(gravityY) < .045f) 0f else gravityY.coerceIn(-9.81f, 9.81f) * 260f * 5 / 7
            val damping = exp(-1.25f * dt)
            vx = ((vx + ax * dt) * damping).coerceIn(-1600f, 1600f)
            vy = ((vy + ay * dt) * damping).coerceIn(-1600f, 1600f)
            if (ax == 0f && abs(vx) < .15f) vx = 0f
            if (ay == 0f && abs(vy) < .15f) vy = 0f
            val beforeX = x; val beforeY = y
            x += vx * dt; y += vy * dt
            confine(reflectVelocity = true)
            val dx = x - beforeX; val dy = y - beforeY
            val distance = hypot(dx, dy)
            travelled += distance
            if (distance > .0001f) {
                val halfAngle = distance / radius / 2
                val s = sin(halfAngle) / distance
                val a = dy * s; val b = dx * s; val w = cos(halfAngle)
                val q = rotation
                val next = floatArrayOf(w*q[0]+a*q[3]+b*q[2], w*q[1]-a*q[2]+b*q[3],
                    w*q[2]+a*q[1]-b*q[0], w*q[3]-a*q[0]-b*q[1])
                val norm = sqrt(next.sumOf { (it * it).toDouble() }).toFloat()
                for (i in next.indices) next[i] /= norm
                rotation = next
                moved = true
            }
        }
        return moved
    }

    private fun confine(reflectVelocity: Boolean) {
        // Only the body collides. The shadow can still extend into system-bar areas.
        val left = min(radius, width / 2); val right = max(left, width - radius)
        val top = min(radius, height / 2); val bottom = max(top, height - radius)
        if (x < left) { x = left; if (reflectVelocity) bounce(-1f, 0f) }
        if (x > right) { x = right; if (reflectVelocity) bounce(1f, 0f) }
        if (y < top) { y = top; if (reflectVelocity) bounce(0f, -1f) }
        if (y > bottom) { y = bottom; if (reflectVelocity) bounce(0f, 1f) }
        for (corner in corners) {
            val dx = x - corner.x; val dy = y - corner.y
            if (dx * corner.outwardX <= 0f || dy * corner.outwardY <= 0f) continue
            val distance = hypot(dx, dy)
            val limit = (corner.radius - radius).coerceAtLeast(0f)
            if (distance <= limit) continue
            val nx = dx / distance; val ny = dy / distance
            x = corner.x + nx * limit
            y = corner.y + ny * limit
            if (reflectVelocity) bounce(nx, ny)
        }
    }

    private fun bounce(nx: Float, ny: Float) {
        val normalSpeed = vx * nx + vy * ny
        if (normalSpeed <= 0f) return
        if (normalSpeed > impactSpeed) {
            impactSpeed = normalSpeed
            impactNormalX = nx
            impactNormalY = ny
        }
        // Reflect only the normal component so the ball can keep rolling along the curve.
        val impulse = normalSpeed * if (normalSpeed > 24f) 1.48f else 1f
        vx -= impulse * nx
        vy -= impulse * ny
    }
}

/** Convert natural sensor axes (X right, Y up) to downhill screen axes (X right, Y down).
 * Display rotations are Surface.ROTATION_* quarter turns; 90° maps gravity to (Y, X).
 */
internal fun screenGravity(x: Float, y: Float, rotation: Int): Pair<Float, Float> = when (rotation) {
    1 -> y to x
    2 -> x to -y
    3 -> -y to -x
    else -> -x to y
}
