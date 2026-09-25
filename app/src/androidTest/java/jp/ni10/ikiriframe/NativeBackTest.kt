package jp.ni10.ikiriframe

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.provider.MediaStore
import android.view.InputDevice
import android.view.MotionEvent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeBackTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun systemGestureCanCancelThenReturnFromEditorToHome() {
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "IkiriBackTest_${System.nanoTime()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        })!!
        val bitmap = Bitmap.createBitmap(960, 640, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(94, 122, 113)) }
        context.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        bitmap.recycle()
        context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        val monitor = instrumentation.addMonitor(EditorActivity::class.java.name, null, false)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { home ->
                home.onActivity { it.startActivity(EditorActivity.createIntent(it, uri)) }
                val editor = instrumentation.waitForMonitorWithTimeout(monitor, 10_000) as EditorActivity
                lateinit var model: EditorViewModel
                instrumentation.runOnMainSync { model = ViewModelProvider(editor)[EditorViewModel::class.java] }
                await { !model.state.value.loading && model.state.value.photo != null }
                val photo = model.state.value.photo
                instrumentation.runOnMainSync {
                    assertFalse("Editor must leave back animation to Android", editor.onBackPressedDispatcher.hasEnabledCallbacks())
                }
                instrumentation.waitForIdleSync()
                // Allow the platform's forward Activity transition to finish before starting back.
                SystemClock.sleep(600)
                gesture(cancel = true, rightEdge = false)
                SystemClock.sleep(600)
                instrumentation.runOnMainSync {
                    assertFalse(editor.isFinishing)
                    assertEquals(Lifecycle.State.RESUMED, editor.lifecycle.currentState)
                    assertSame(photo, model.state.value.photo)
                }
                capture("predictive-back-cancelled")
                gesture(cancel = false, rightEdge = true)
                await { editor.isDestroyed }
                home.onActivity {
                    assertEquals(Lifecycle.State.RESUMED, it.lifecycle.currentState)
                    assertFalse("Home must retain back-to-launcher animation", it.onBackPressedDispatcher.hasEnabledCallbacks())
                }
                capture("predictive-back-home")
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            context.contentResolver.delete(uri, null, null)
        }
    }

    private fun gesture(cancel: Boolean, rightEdge: Boolean) {
        val screen = instrumentation.uiAutomation.takeScreenshot()!!
        val width = screen.width.toFloat()
        val y = screen.height * 0.45f
        screen.recycle()
        val start = if (rightEdge) width - 1f else 1f
        val distance = width * 0.45f * if (rightEdge) -1f else 1f
        val downTime = SystemClock.uptimeMillis()
        fun send(action: Int, x: Float) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) }
            finally { event.recycle() }
        }
        send(MotionEvent.ACTION_DOWN, start)
        for (step in 1..20) {
            SystemClock.sleep(16)
            send(MotionEvent.ACTION_MOVE, start + distance * step / 20)
        }
        SystemClock.sleep(150)
        capture(if (rightEdge) "predictive-back-right-progress" else "predictive-back-left-progress")
        if (cancel) {
            for (step in 19 downTo 0) {
                SystemClock.sleep(16)
                send(MotionEvent.ACTION_MOVE, start + distance * step / 20)
            }
        }
        send(MotionEvent.ACTION_UP, if (cancel) start else start + distance)
    }

    private fun capture(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()!!
        val directory = File(context.filesDir, "qa").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(25)
        assertTrue("Activity operation timed out", condition())
        instrumentation.waitForIdleSync()
    }
}
