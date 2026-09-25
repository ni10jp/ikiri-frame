package jp.ni10.ikiriframe

import android.content.ContentValues
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.ni10.ikiriframe.logo.LogoRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Open the real IME; setting text through semantics alone does not exercise window insets. */
@RunWith(AndroidJUnit4::class)
class EditorKeyboardTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun portraitKeyboardKeepsInputVisibleAndNormalPropertiesUncluttered() = exercise(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
    @Test fun landscapeKeyboardKeepsInputVisibleAndNormalPropertiesUncluttered() = exercise(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)

    private fun exercise(orientation: Int): Unit = runBlocking {
        val name = if (orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) "portrait" else "landscape"
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "IkiriKeyboard_${System.nanoTime()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        })!!
        val bitmap = Bitmap.createBitmap(960, 640, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(74, 112, 145)) }
        context.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        val logos = LogoRepository(context)
        val previous = logos.selectedId
        val svg = File(context.cacheDir, "keyboard-logo.svg").apply {
            writeText("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 140 28"><rect width="140" height="28" fill="#00ff00"/></svg>""")
        }
        val logo = logos.register(Uri.fromFile(svg))
        logos.select(logo.id)
        val preferences = context.getSharedPreferences("frame_options", 0)
        val previousTag = preferences.getString("tag", "#TeamPixel")
        try {
            ActivityScenario.launch<EditorActivity>(EditorActivity.createIntent(context, uri)).use { scenario ->
                scenario.onActivity { it.requestedOrientation = orientation }
                compose.waitUntil(15_000) {
                    var ready = false
                    scenario.onActivity {
                        val state = ViewModelProvider(it)[EditorViewModel::class.java].state.value
                        val wanted = if (orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) Configuration.ORIENTATION_PORTRAIT else Configuration.ORIENTATION_LANDSCAPE
                        ready = !state.loading && !state.logoLoading && state.photo != null && it.resources.configuration.orientation == wanted
                    }
                    ready
                }
                compose.onNodeWithTag("logo-select-button").assertIsDisplayed()
                    .assert(hasAnyAncestor(hasTestTag("property-list")))
                compose.onNodeWithText("keyboard-logo.svg").assertDoesNotExist()
                compose.onAllNodesWithTag("logo-preview").assertCountEquals(0)
                capture("properties-$name-normal")
                compose.onNodeWithTag("label-edit-button").performScrollTo().performClick()
                assertDialogButtons()
                capture("keyboard-$name-exif-before")
                compose.onNodeWithText("機種").performScrollTo().performClick()
                awaitKeyboard(scenario, true)
                capture("keyboard-$name-exif-open")
                compose.onNode(isDialog()).assertExists()
                assertInputBounds("機種")
                compose.onNodeWithText("機種").performTextReplacement("Keyboard Camera")
                compose.waitForIdle()
                scenario.onActivity { assertNull("Dialog edits are drafts until saved", ViewModelProvider(it)[EditorViewModel::class.java].state.value.options.text) }
                capture("keyboard-$name-exif")
                compose.onNodeWithContentDescription("右のやつ").performScrollTo().performClick()
                awaitKeyboard(scenario, true)
                assertInputBounds("右のやつ")
                compose.onNodeWithContentDescription("右のやつ").performTextReplacement("#KeyboardTest")
                capture("keyboard-$name-label-last-field")
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                awaitKeyboard(scenario, false)
                compose.onNodeWithText("機種").assertTextContains("Keyboard Camera")
                compose.onNodeWithText("保存").performScrollTo().assertIsDisplayed().performClick()
                compose.onNode(isDialog()).assertDoesNotExist()
                scenario.onActivity { assertEquals("Keyboard Camera", ViewModelProvider(it)[EditorViewModel::class.java].state.value.options.text?.model) }
                compose.onNodeWithContentDescription("右のやつ").assertDoesNotExist()
                compose.onNodeWithText("タグ").assertDoesNotExist()
                compose.onNodeWithText("機種").assertDoesNotExist()
                compose.onNodeWithTag("logo-select-button").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("keyboard-logo.svg").assertDoesNotExist()
                compose.onAllNodesWithTag("logo-preview").assertCountEquals(0)
                val export = compose.onNodeWithText("書き出し")
                export.assert(hasAnyAncestor(hasScrollAction())).performScrollTo()
                export.assertIsDisplayed()
                capture("keyboard-$name-dismissed")
                scenario.onActivity {
                    val model = ViewModelProvider(it)[EditorViewModel::class.java]
                    assertEquals("Keyboard Camera", model.state.value.options.text!!.model)
                    assertEquals("#KeyboardTest", model.state.value.options.tag)
                    assertFalse(it.isFinishing)
                }
            }
        } finally {
            logos.delete(logo.id)
            logos.select(previous)
            preferences.edit().putString("tag", previousTag).commit()
            svg.delete()
            context.contentResolver.delete(uri, null, null)
        }
    }

    private fun awaitKeyboard(scenario: ActivityScenario<EditorActivity>, visible: Boolean) {
        compose.waitUntil(10_000) {
            var actual = false
            scenario.onActivity {
                actual = android.view.inspector.WindowInspector.getGlobalWindowViews().any { root ->
                    ViewCompat.getRootWindowInsets(root)?.isVisible(WindowInsetsCompat.Type.ime()) == true
                }
            }
            actual == visible
        }
        SystemClock.sleep(600)
        compose.waitForIdle()
    }

    private fun assertInputBounds(label: String) {
        val field = compose.onNodeWithContentDescription(label).assertIsDisplayed()
        val input = field.fetchSemanticsNode().boundsInRoot
        val fullBounds = field.getUnclippedBoundsInRoot()
        val fullHeight = (fullBounds.bottom - fullBounds.top).value * context.resources.displayMetrics.density
        assertTrue("The entire text field must be visible: $input, full height $fullHeight", input.height >= fullHeight - 1)
    }

    private fun assertDialogButtons() {
        compose.onNodeWithTag("label-dialog-actions").performScrollTo()
        val reset = compose.onNodeWithText("リセット").fetchSemanticsNode().boundsInRoot
        val cancel = compose.onNodeWithText("キャンセル").fetchSemanticsNode().boundsInRoot
        val save = compose.onNodeWithText("保存").fetchSemanticsNode().boundsInRoot
        assertTrue("Reset must have its own row", reset.bottom < cancel.top)
        assertEquals("Cancel and save share the second row", cancel.top, save.top, 1f)
        assertEquals("Both rows fill the dialog content width", reset.left, cancel.left, 1f)
        assertEquals(reset.right, save.right, 1f)
        assertEquals("Cancel and save have equal widths", cancel.width, save.width, 1f)
    }

    private fun capture(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()!!
        val dir = File(context.filesDir, "qa").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
