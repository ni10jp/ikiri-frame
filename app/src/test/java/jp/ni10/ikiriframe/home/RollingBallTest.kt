package jp.ni10.ikiriframe.home

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class RollingBallTest {
    @Test fun tiltRollsInBothAxesAndChangesRealThreeDimensionalOrientation() {
        val ball = RollingBall().apply { resize(400f, 800f) }
        val x = ball.x; val y = ball.y
        repeat(30) { ball.step(1f / 60, 3f, -3f) }
        assertTrue(ball.x > x)
        assertTrue(ball.y < y)
        assertTrue(abs(ball.rotation[0]) > .01f && abs(ball.rotation[1]) > .01f)
        assertEquals(1f, ball.rotation.sumOf { (it * it).toDouble() }.toFloat(), .0001f)
    }

    @Test fun strongTiltCannotEscapeAnyWallIncludingAfterResizeAndPause() {
        val ball = RollingBall().apply { resize(400f, 800f) }
        for ((gx, gy) in listOf(100f to 100f, -100f to 100f, -100f to -100f, 100f to -100f)) {
            repeat(1000) {
                ball.step(.1f, gx, gy)
                assertTrue(ball.x >= ball.radius && ball.x <= 400 - ball.radius)
                assertTrue(ball.y >= ball.radius && ball.y <= 800 - ball.radius)
            }
        }
        ball.resize(240f, 100f)
        assertTrue(ball.x in ball.radius..(240 - ball.radius))
        assertTrue(ball.y in ball.radius..(100 - ball.radius))
        ball.stop()
        assertEquals(0f, ball.vx); assertEquals(0f, ball.vy)
    }

    @Test fun flatPhoneComesToRestAndFrameRateDoesNotChangeTrajectory() {
        fun simulate(hz: Int): RollingBall = RollingBall().apply {
            resize(2000f, 2000f)
            repeat(hz) { step(1f / hz, 1f, 2f) }
        }
        val a = simulate(60); val b = simulate(120)
        assertEquals(a.x, b.x, .01f); assertEquals(a.y, b.y, .01f)
        repeat(1200) { a.step(1f / 60, 0f, 0f) }
        assertEquals(0f, a.vx); assertEquals(0f, a.vy)
    }

    @Test fun gentleTiltRollsQuicklyWithoutExtendingCoasting() {
        val ball = RollingBall().apply { resize(2000f, 2000f) }
        val startX = ball.x
        repeat(60) { ball.step(1f / 60, 1f, 0f) }
        val speed = ball.vx
        val tiltedX = ball.x
        assertTrue("A slight tilt should travel at least 60dp in one second", tiltedX - startX > 60f)
        assertTrue("Increase rolling speed, not just momentum retention", speed > 100f)
        repeat(60) { ball.step(1f / 60, 0f, 0f) }
        assertTrue(ball.vx > speed * .25f)
        assertTrue(ball.vx < speed * .3f)
        assertTrue(ball.x - tiltedX > 50f)
    }

    @Test fun bodyReachesAllFourDisplayEdgesWithoutAnInvisibleInset() {
        for ((width, height) in listOf(400f to 800f, 800f to 400f)) {
            val ball = RollingBall().apply { resize(width, height) }
            for ((gx, gy) in listOf(3f to 3f, -3f to 3f, -3f to -3f, 3f to -3f)) {
                repeat(1200) { ball.step(1f / 60, gx, gy) }
                assertEquals(if (gx > 0) width else 0f, ball.x + if (gx > 0) ball.radius else -ball.radius, .01f)
                assertEquals(if (gy > 0) height else 0f, ball.y + if (gy > 0) ball.radius else -ball.radius, .01f)
            }
        }
    }

    @Test fun rotationKeepsTiltRelativeToTheDisplayedScreen() {
        assertEquals(-2f to 4f, screenGravity(2f, 4f, 0))
        assertEquals(4f to 2f, screenGravity(2f, 4f, 1))
        assertEquals(2f to -4f, screenGravity(2f, 4f, 2))
        assertEquals(-4f to -2f, screenGravity(2f, 4f, 3))
    }
}
