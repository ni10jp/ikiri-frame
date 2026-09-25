package jp.ni10.ikiriframe

import android.app.Application
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import jp.ni10.ikiriframe.photo.FrameText
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream

@RunWith(AndroidJUnit4::class)
class EditorResumeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun homeOtherAppLauncherAndRecreationKeepPhotoAndUnsavedDialog() {
        val uri = fixture()
        var sourceDeleted = false
        try {
            context.startActivity(EditorActivity.createIntent(context, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            awaitEditor()
            run {
                lateinit var model: EditorViewModel
                instrumentation.runOnMainSync { model = ViewModelProvider(currentEditor()!!)[EditorViewModel::class.java] }
                compose.waitUntil(10_000) { model.state.value.photo != null && !model.state.value.loading && !model.state.value.logoLoading }
                val photo = model.state.value.photo!!
                instrumentation.runOnMainSync { model.setThickness(1.3f); model.setPalette(photo.theme!!.palettes.first().id) }
                compose.onNodeWithTag("label-edit-button").performScrollTo().performClick()
                compose.onNodeWithContentDescription("機種").performTextReplacement("Still editing")
                compose.onNodeWithContentDescription("右のやつ").performScrollTo().performTextReplacement("#Unfinished")
                compose.onNodeWithContentDescription("右のやつ").performImeAction()
                val draft = model.state.value.labelDraft
                assertNotNull(draft)
                assertNull(model.state.value.options.text)
                context.contentResolver.delete(uri, null, null)
                sourceDeleted = true
                assertEquals(File(context.filesDir, "selected_photos"), photo.file.parentFile)

                shell("input keyevent KEYCODE_HOME")
                shell("am start -a android.settings.SETTINGS")
                SystemClock.sleep(500)
                shell("am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n jp.ni10.ikiriframe/.MainActivity")
                awaitEditor()
                instrumentation.runOnMainSync { model = ViewModelProvider(currentEditor()!!)[EditorViewModel::class.java] }
                compose.waitUntil(10_000) { model.state.value.photo != null && !model.state.value.loading && !model.state.value.logoLoading }
                compose.onNodeWithContentDescription("機種").assertTextContains("Still editing")
                compose.onNodeWithContentDescription("右のやつ").assertTextContains("#Unfinished")
                assertEquals(photo.file, model.state.value.photo!!.file)
                assertEquals(draft, model.state.value.labelDraft)

                lateinit var beforeRecreation: EditorActivity
                instrumentation.runOnMainSync { beforeRecreation = currentEditor()!!; beforeRecreation.recreate() }
                awaitEditor(beforeRecreation)
                instrumentation.runOnMainSync { model = ViewModelProvider(currentEditor()!!)[EditorViewModel::class.java] }
                compose.waitUntil(10_000) { model.state.value.photo != null && !model.state.value.loading && !model.state.value.logoLoading }
                compose.onNodeWithContentDescription("機種").assertTextContains("Still editing")
                compose.onNodeWithContentDescription("右のやつ").assertTextContains("#Unfinished")
                assertEquals(photo.file, model.state.value.photo!!.file)
                assertEquals(1.3f, model.state.value.options.thickness)
                assertTrue(model.state.value.options.extractTheme)
                compose.onNodeWithText("保存").performScrollTo().performClick()
                assertEquals("Still editing", model.state.value.options.text!!.model)
                assertEquals("#Unfinished", model.state.value.options.tag)
                assertNull(model.state.value.labelDraft)
                instrumentation.runOnMainSync { model.finishEditing(); currentEditor()?.finish() }
            }
        } finally {
            instrumentation.runOnMainSync {
                val screens = Stage.entries.flatMap { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it) }
                screens.filter { it is EditorActivity || it is MainActivity }.distinct().forEach { it.finish() }
            }
            if (!sourceDeleted) context.contentResolver.delete(uri, null, null)
        }
    }

    @Test fun durableJournalRestoresAfterLosingActivityStateAndSender() {
        val uri = fixture()
        var sourceDeleted = false
        var store = ViewModelStore()
        lateinit var model: EditorViewModel
        val application = context.applicationContext as Application
        val saved = SavedStateHandle()
        try {
            instrumentation.runOnMainSync {
                model = EditorViewModel(application, saved); store.put("editor", model)
                model.importSharedPhoto(uri)
            }
            compose.waitUntil(10_000) { model.state.value.photo != null && !model.state.value.loading }
            val photo = model.state.value.photo!!
            val values = FrameText.from(photo.metadata).copy(model = "Saved camera")
            val draft = LabelDraft(values.copy(model = "Draft camera").values(), "#Draft")
            // Simulate the older Activity snapshot Android can retain while an app is stopped.
            val oldSnapshot = SavedStateHandle(mapOf("edit_session_id" to saved.get<String>("edit_session_id"),
                "pending_shared_uri" to uri.toString()))
            instrumentation.runOnMainSync {
                model.setText(values); model.setTag("#Saved"); model.setThickness(1.4f)
                model.setLabelDraft(draft)
                store.clear()
            }
            store = ViewModelStore()
            instrumentation.runOnMainSync {
                model = EditorViewModel(application, oldSnapshot); store.put("restored", model)
            }
            compose.waitUntil(10_000) { model.state.value.photo != null && !model.state.value.loading }
            assertEquals(photo.file, model.state.value.photo!!.file)
            assertEquals(values, model.state.value.options.text)
            assertEquals("#Saved", model.state.value.options.tag)
            assertEquals(draft, model.state.value.labelDraft)
            assertNull("An old import intent must not overwrite the restored edit", oldSnapshot.get<String>("pending_shared_uri"))
            context.contentResolver.delete(uri, null, null)
            sourceDeleted = true
        } finally {
            instrumentation.runOnMainSync { model.finishEditing(); store.clear() }
            if (!sourceDeleted) context.contentResolver.delete(uri, null, null)
        }
    }

    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use {
            FileInputStream(it.fileDescriptor).use { stream -> stream.readBytes() }
        }
    }

    private fun currentEditor(): EditorActivity? = ActivityLifecycleMonitorRegistry.getInstance()
        .getActivitiesInStage(Stage.RESUMED).filterIsInstance<EditorActivity>().firstOrNull()

    private fun awaitEditor(previous: EditorActivity? = null) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            instrumentation.runOnMainSync { ready = currentEditor()?.let { it !== previous } == true }
            if (ready) return
            SystemClock.sleep(50)
        }
        fail("Editor did not resume from the launcher")
    }

    private fun fixture(): Uri {
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "IkiriResume_${System.nanoTime()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        })!!
        val bitmap = Bitmap.createBitmap(960, 640, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(72, 110, 140)) }
        context.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        return uri
    }
}
