package jp.ni10.ikiriframe

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.ni10.ikiriframe.photo.*
import jp.ni10.ikiriframe.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class CustomizationTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun allDisplayValuesAreEditableRestoredAndExportedWithoutChangingSourceExif() = runBlocking {
        val fixture = File(context.cacheDir, "customization.png")
        val bitmap = Bitmap.createBitmap(1440, 960, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(72, 110, 140)) }
        fixture.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val preferences = context.getSharedPreferences("frame_options", 0)
        val oldTag = preferences.getString("tag", "#TeamPixel")
        val saved = SavedStateHandle(mapOf("tag" to "#TeamPixel"))
        val store = ViewModelStore()
        lateinit var model: EditorViewModel
        instrumentation.runOnMainSync {
            model = EditorViewModel(context.applicationContext as Application, saved)
            store.put("edit-test", model)
            model.importSharedPhoto(Uri.fromFile(fixture))
        }
        val feedback = mutableListOf<HapticFeedbackType>()
        val haptics = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { feedback += hapticFeedbackType }
        }
        var exported: Uri? = null
        try {
            compose.setContent { CompositionLocalProvider(LocalHapticFeedback provides haptics) { IkiriTheme(darkTheme = false) { IkiriEditor({}, model) } } }
            compose.waitUntil(10_000) { model.state.value.photo != null && !model.state.value.loading && !model.state.value.logoLoading }
            compose.onNodeWithTag("label-edit-button").performScrollTo().performClick()
            val values = listOf("My Camera", "50 mm", "ƒ/2", "1/250 s", "ISO 100", "2026/09/24 12:00:00")
            val labels = listOf("機種", "焦点距離", "F値", "シャッタースピード", "ISO値", "撮影日時")
            labels.zip(values).forEach { (label, value) ->
                compose.onNodeWithText(label).performScrollTo().performTextReplacement(value)
                compose.onNodeWithText(label).performImeAction()
            }
            compose.onNodeWithContentDescription("右のやつ").performScrollTo().performTextReplacement("#MyPhoto")
            compose.onNodeWithContentDescription("右のやつ").performImeAction()
            assertEquals("", model.state.value.options.tag)
            compose.onNodeWithText("保存").performScrollTo().assertIsDisplayed().performClick()
            assertEquals(HapticFeedbackType.Confirm, feedback.last())
            compose.onNodeWithText("Exif").assertDoesNotExist()
            compose.onNodeWithText("タグ").assertDoesNotExist()
            compose.onNodeWithContentDescription("右のやつ").assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(values, model.state.value.options.text!!.values())
                assertEquals("#MyPhoto", model.state.value.options.tag)
                assertEquals("", model.state.value.photo!!.metadata.model)
            }
            save(compose.onRoot().captureToImage().asAndroidBitmap(), "custom-tag-editor")
            val snapshot = model.state.value
            val photo = snapshot.photo!!
            val palette = dynamicLightColorScheme(context).toFramePalette()
            exported = PhotoRepository(context).export(photo, snapshot.options, palette)
            val actual = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, exported)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val expected = Bitmap.createBitmap(actual.width, actual.height, Bitmap.Config.ARGB_8888)
            FrameRenderer(context).draw(Canvas(expected), photo.preview, photo.metadata, snapshot.options, palette)
            var error = 0L
            var samples = 0
            for (y in photo.preview.height until actual.height) for (x in 0 until actual.width) {
                val a = actual.getPixel(x, y); val e = expected.getPixel(x, y)
                error += abs(Color.red(a) - Color.red(e)) + abs(Color.green(a) - Color.green(e)) + abs(Color.blue(a) - Color.blue(e))
                samples += 3
            }
            assertTrue("Export differs from customized preview: ${error.toDouble() / samples}", error.toDouble() / samples < 3)
            save(expected, "custom-text-frame")
            actual.recycle(); expected.recycle()
            lateinit var restored: EditorViewModel
            instrumentation.runOnMainSync {
                restored = EditorViewModel(context.applicationContext as Application,
                    SavedStateHandle(saved.keys().associateWith { saved.get<Any>(it) }))
                store.put("restored-edit", restored)
            }
            compose.waitUntil(10_000) { restored.state.value.photo != null && !restored.state.value.loading }
            assertEquals(snapshot.options.text, restored.state.value.options.text)
            assertEquals("#MyPhoto", restored.state.value.options.tag)
            compose.onNodeWithTag("label-edit-button").performScrollTo().performClick()
            compose.onNodeWithText("機種").performTextReplacement("Discarded")
            compose.onNodeWithContentDescription("右のやつ").performScrollTo().performTextReplacement("#Discarded")
            compose.onNodeWithText("キャンセル").performScrollTo().assertIsDisplayed().performClick()
            assertEquals(HapticFeedbackType.VirtualKey, feedback.last())
            compose.runOnIdle {
                assertEquals(values, model.state.value.options.text!!.values())
                assertEquals("#MyPhoto", model.state.value.options.tag)
            }
            compose.onNodeWithTag("label-edit-button").performScrollTo().performClick()
            compose.onNodeWithText("リセット").performScrollTo().assertIsDisplayed().performClick()
            assertEquals(HapticFeedbackType.VirtualKey, feedback.last())
            compose.runOnIdle { assertEquals("", model.state.value.labelDraft!!.tag) }
            compose.onNodeWithText("保存").performScrollTo().assertIsDisplayed().performClick()
            compose.runOnIdle { assertNull(model.state.value.options.text) }
            compose.runOnIdle { model.setText(FrameText.from(photo.metadata).copy(model = "Override")); model.importSharedPhoto(Uri.fromFile(fixture)) }
            compose.waitUntil(10_000) { !model.state.value.loading && model.state.value.photo !== photo }
            assertNull(model.state.value.options.text)
            assertEquals("", model.state.value.options.tag)
        } finally {
            instrumentation.runOnMainSync { model.finishEditing(); store.clear() }
            exported?.let { context.contentResolver.delete(it, null, null) }
            preferences.edit().putString("tag", oldTag).commit()
            fixture.delete()
        }
    }

    private fun save(bitmap: Bitmap, name: String) {
        val directory = File(context.filesDir, "qa").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
