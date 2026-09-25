package jp.ni10.ikiriframe

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import jp.ni10.ikiriframe.photo.*
import jp.ni10.ikiriframe.ui.*
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val palette = FramePalette(Color.rgb(240, 246, 242), Color.rgb(25, 30, 27), Color.DKGRAY, Color.GRAY)
    private val photo = Photo(File(context.cacheDir, "ui-fixture"),
        Bitmap.createBitmap(1440, 960, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(94, 122, 113)) },
        PhotoMetadata("Pixel 10 Pro", 6.9, 1.7, 1.0 / 400, 50, "2026:09:24 15:30:45")).let {
            it.copy(theme = jp.ni10.ikiriframe.photo.PhotoThemeExtractor.extract(context, it.preview))
        }

    @Test fun portraitControlsUpdateAndExportIsAccessible() {
        var options by mutableStateOf(FrameOptions())
        var busy by mutableStateOf(false)
        var exported = false
        var selectingLogo = false
        val feedback = RecordingHaptics()
        compose.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides feedback) {
            IkiriTheme(darkTheme = false) {
                EditorScreen(EditorState(photo = photo, options = options, exporting = busy), palette, {},
                    { selectingLogo = true },
                    { options = options.copy(thickness = it) }, { exported = true },
                    onSelectPalette = { options = options.copy(extractTheme = it != null, paletteId = it) })
            }
            }
        }
        compose.onNodeWithContentDescription("ロゴを選択").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(selectingLogo); assertTrue(feedback.events.isEmpty()) }
        capture("editor-portrait-before")
        compose.onNodeWithContentDescription("Content").performScrollTo().assertIsNotSelected().performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(HapticFeedbackType.SegmentTick, feedback.events.last()) }
        compose.onNodeWithContentDescription("Android").performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(HapticFeedbackType.SegmentTick, feedback.events.last()) }
        compose.onNodeWithContentDescription("バーの太さ").performScrollTo().assertIsDisplayed()
            .performTouchInput { swipe(start = center, end = centerRight, durationMillis = 500) }
        capture("editor-portrait-after-slider")
        compose.runOnIdle { assertTrue("Slider value was ${options.thickness}", options.thickness > 1f) }
        compose.runOnIdle { assertTrue(feedback.events.contains(HapticFeedbackType.SegmentFrequentTick)) }
        compose.onNodeWithText("書き出し").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertTrue(exported)
            assertEquals(HapticFeedbackType.Confirm, feedback.events.last())
        }
        capture("editor-portrait")
        compose.runOnIdle {
            feedback.events.clear()
            options = options.copy(thickness = 0.9f)
            busy = true
        }
        compose.onNodeWithContentDescription("ロゴを選択").performScrollTo().assertIsNotEnabled().performTouchInput { click() }
        compose.onNodeWithContentDescription("Android").performScrollTo().assertIsNotEnabled().performTouchInput { click() }
        compose.onNodeWithContentDescription("バーの太さ").assertIsNotEnabled()
            .performTouchInput { swipe(start = centerLeft, end = centerRight, durationMillis = 300) }
        compose.onNodeWithText("書き出し中…").performScrollTo().assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertTrue("Disabled controls and state restoration must be silent", feedback.events.isEmpty()) }
    }

    @Test fun landscapeSheetSupportsDarkTheme() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                IkiriTheme(darkTheme = true) {
                    Box(Modifier.requiredSize(900.dp, 412.dp).consumeWindowInsets(WindowInsets.safeDrawing).testTag("landscape")) {
                        EditorScreen(EditorState(photo = photo), palette, {}, {}, {}, {})
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("ロゴを選択").assertIsDisplayed()
        compose.onNodeWithText("書き出し").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Android").performScrollTo().assertIsDisplayed()
        capture("editor-landscape-dark", "landscape")
    }

    @Test fun homeOffersNativePickerActionAndHandlesLoading() {
        var picked = false
        var busy by mutableStateOf(false)
        val feedback = RecordingHaptics()
        compose.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides feedback) {
                IkiriTheme(darkTheme = false) { HomeScreen(busy, false, {}, { picked = true }) }
            }
        }
        compose.onNodeWithText("イキる写真を選ぶ").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertTrue(picked)
            assertEquals(listOf(HapticFeedbackType.VirtualKey), feedback.events)
        }
        capture("home")
        compose.runOnIdle { busy = true; feedback.events.clear() }
        compose.onNodeWithText("イキる写真を選ぶ").assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertTrue(feedback.events.isEmpty()) }
    }

    private fun capture(name: String, tag: String? = null) {
        val node = if (tag == null) compose.onRoot() else compose.onNodeWithTag(tag)
        val bitmap = node.captureToImage().asAndroidBitmap()
        val directory = File(context.filesDir, "qa").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private class RecordingHaptics : HapticFeedback {
        val events = mutableListOf<HapticFeedbackType>()
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            events += hapticFeedbackType
        }
    }
}
