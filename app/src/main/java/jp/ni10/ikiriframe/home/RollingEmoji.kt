package jp.ni10.ikiriframe.home

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Choreographer
import android.view.RoundedCorner
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.roundToInt

@Composable
internal fun RollingEmoji(modifier: Modifier = Modifier) {
    val host = LocalView.current as? ViewGroup ?: return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val controller = remember(host) { EmojiMotionController(host.context) }
    DisposableEffect(host, lifecycle, controller) {
        // ViewGroupOverlay is decorative: it never participates in touch dispatch or accessibility.
        host.overlay.add(controller.view)
        val observer = LifecycleEventObserver { _, _ ->
            controller.setActive(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
        lifecycle.addObserver(observer)
        controller.setActive(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose {
            lifecycle.removeObserver(observer)
            controller.setActive(false)
            host.overlay.remove(controller.view)
            controller.close()
        }
    }
    Box(modifier.testTag("home-emoji-stage").onGloballyPositioned { coordinates ->
        val position = coordinates.positionInRoot()
        controller.layout(position.x.roundToInt(), position.y.roundToInt(), coordinates.size.width, coordinates.size.height)
    })
}

internal class EmojiMotionController(context: Context) : SensorEventListener, Choreographer.FrameCallback {
    val view = TextureView(context).apply {
        isOpaque = false
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val renderer = EmojiRenderer(context)
    private val manager = context.getSystemService(SensorManager::class.java)
    // The game rotation vector fuses gyroscope and accelerometer, without magnetic-heading drift.
    private val sensor = manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: manager.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val choreographer = Choreographer.getInstance()
    private val ball = RollingBall()
    private val deformation = BallDeformation()
    private val haptics = RollingHaptics(view)
    private val density = context.resources.displayMetrics.density
    private val rotationMatrix = FloatArray(9)
    private var deviceGravityX = 0f
    private var deviceGravityY = 0f
    private var lastDisplayRotation: Int? = null
    private var lastTime = 0L
    private var active = false
    private var scheduled = false
    private var laidOut = false
    private var lastInsets: WindowInsets? = null
    private var cornersDirty = true
    private val windowLocation = IntArray(2)

    init { view.surfaceTextureListener = renderer }

    fun layout(left: Int, top: Int, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(left, top, left + width, top + height)
        ball.resize(width / density, height / density)
        cornersDirty = true
        updateCorners()
        laidOut = true
        draw()
        schedule()
    }

    fun setActive(enabled: Boolean) {
        if (active == enabled) return
        active = enabled
        view.visibility = if (enabled) View.VISIBLE else View.INVISIBLE
        lastTime = 0L
        lastDisplayRotation = null
        haptics.stop()
        if (enabled) {
            cornersDirty = true
            sensor?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
            schedule()
        } else {
            manager.unregisterListener(this)
            choreographer.removeFrameCallback(this)
            scheduled = false
            deviceGravityX = 0f; deviceGravityY = 0f
            ball.stop()
            deformation.reset()
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!active) return
        val gx: Float; val gy: Float
        if (event.sensor.type == Sensor.TYPE_GAME_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            gx = rotationMatrix[6] * SensorManager.GRAVITY_EARTH
            gy = rotationMatrix[7] * SensorManager.GRAVITY_EARTH
        } else { gx = event.values[0]; gy = event.values[1] }
        deviceGravityX = gx; deviceGravityY = gy
        schedule()
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun doFrame(frameTimeNanos: Long) {
        scheduled = false
        if (!active || !laidOut) return
        val dt = if (lastTime == 0L) 0f else (frameTimeNanos - lastTime) / 1_000_000_000f
        lastTime = frameTimeNanos
        val displayRotation = view.display?.rotation ?: 0
        lastDisplayRotation?.let { previous ->
            if (previous != displayRotation) {
                ball.remapVelocity((displayRotation - previous + 4) % 4)
                deformation.remap((displayRotation - previous + 4) % 4)
                cornersDirty = true
            }
        }
        lastDisplayRotation = displayRotation
        // Apply the current display orientation at the physics frame, not the last sensor event.
        val gravity = screenGravity(deviceGravityX, deviceGravityY, displayRotation)
        val boundsChanged = updateCorners()
        val deforming = deformation.advance(dt)
        val moved = ball.step(dt, gravity.first, gravity.second)
        deformation.impact(ball.impactSpeed, ball.radius, ball.impactNormalX, ball.impactNormalY)
        haptics.update(ball)
        if (moved || deforming || boundsChanged || dt == 0f) draw()
        if (moved || ball.vx != 0f || ball.vy != 0f || deformation.isRunning) schedule()
    }

    private fun updateCorners(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val insets = view.rootWindowInsets ?: return false
        if (!cornersDirty && insets == lastInsets) return false
        view.getLocationInWindow(windowLocation)
        val corners = listOf(
            Triple(RoundedCorner.POSITION_TOP_LEFT, -1f, -1f),
            Triple(RoundedCorner.POSITION_TOP_RIGHT, 1f, -1f),
            Triple(RoundedCorner.POSITION_BOTTOM_RIGHT, 1f, 1f),
            Triple(RoundedCorner.POSITION_BOTTOM_LEFT, -1f, 1f),
        ).mapNotNull { (position, outwardX, outwardY) ->
            insets.getRoundedCorner(position)?.let { corner ->
                ScreenCorner((corner.center.x - windowLocation[0]) / density,
                    (corner.center.y - windowLocation[1]) / density, corner.radius / density,
                    outwardX, outwardY)
            }
        }
        ball.setCorners(corners)
        lastInsets = insets
        cornersDirty = false
        return true
    }

    private fun draw() {
        renderer.render(BallPose(ball.x * density, ball.y * density, ball.radius * density,
            ball.rotation, deformation.normalX, deformation.normalY, deformation.compression))
    }
    private fun schedule() {
        if (active && laidOut && !scheduled) {
            scheduled = true
            choreographer.postFrameCallback(this)
        }
    }
    fun close() { setActive(false); renderer.close() }
}
