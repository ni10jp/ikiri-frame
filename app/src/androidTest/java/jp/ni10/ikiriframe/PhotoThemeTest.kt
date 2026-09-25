package jp.ni10.ikiriframe

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import jp.ni10.ikiriframe.photo.PhotoThemeExtractor
import jp.ni10.ikiriframe.ui.IkiriEditor
import jp.ni10.ikiriframe.ui.toFramePalette
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoThemeTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun extractedColorsFollowTheImageAndKeepTextReadableWithoutChangingDeviceColors() {
        val deviceBefore = dynamicLightColorScheme(context).surfaceContainer
        val primaries = mutableSetOf<Int>()
        for (sourceColor in listOf(Color.RED, Color.GREEN, Color.BLUE, Color.GRAY, Color.TRANSPARENT)) {
            val bitmap = Bitmap.createBitmap(720, 480, Bitmap.Config.ARGB_8888).apply { eraseColor(sourceColor) }
            val theme = PhotoThemeExtractor.extract(context, bitmap)
            bitmap.recycle()
            if (sourceColor in listOf(Color.RED, Color.GREEN, Color.BLUE)) primaries += theme.light.primary.toArgb()
            for (scheme in listOf(theme.light, theme.dark)) {
                assertTrue(ColorUtils.calculateContrast(scheme.onSurface.toArgb(), scheme.surfaceContainerLow.toArgb()) >= 4.5)
                assertTrue(ColorUtils.calculateContrast(scheme.onSurfaceVariant.toArgb(), scheme.surfaceContainerLow.toArgb()) >= 4.5)
                assertTrue(ColorUtils.calculateContrast(scheme.onPrimary.toArgb(), scheme.primary.toArgb()) >= 4.5)
            }
            assertTrue(ColorUtils.calculateLuminance(theme.light.surfaceContainerLow.toArgb()) > 0.8)
            assertTrue(ColorUtils.calculateLuminance(theme.dark.surfaceContainerLow.toArgb()) < 0.1)
        }
        assertEquals(3, primaries.size)
        assertEquals(deviceBefore, dynamicLightColorScheme(context).surfaceContainer)
    }

    @Test fun paletteOnlyChangesLightFooterAndExportWhileEditorKeepsDeviceTheme() {
        val preferences = context.getSharedPreferences("frame_options", Context.MODE_PRIVATE)
        val previousOption = preferences.getBoolean("extract_theme", false)
        val redFile = fixture("theme-red.png", Color.rgb(225, 45, 35))
        val blueFile = fixture("theme-blue.png", Color.rgb(20, 80, 225))
        val savedState = SavedStateHandle(mapOf("extract_theme" to false))
        val store = ViewModelStore()
        lateinit var model: EditorViewModel
        var exported: Uri? = null
        try {
            instrumentation.runOnMainSync {
                model = EditorViewModel(context.applicationContext as Application, savedState)
                store.put("theme-test", model)
                model.importSharedPhoto(Uri.fromFile(redFile))
            }
            compose.setContent {
                val darkConfiguration = Configuration(context.resources.configuration).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
                }
                CompositionLocalProvider(LocalConfiguration provides darkConfiguration) {
                    IkiriEditor(onExit = {}, model = model)
                }
            }
            compose.waitUntil(10_000) { !model.state.value.loading && !model.state.value.logoLoading && model.state.value.photo != null }
            val red = model.state.value.photo!!
            compose.onNodeWithContentDescription("Android").performScrollTo().assertIsSelected()
            compose.onNodeWithContentDescription("Content").performScrollTo().assertIsNotSelected().performClick().assertIsSelected()
            assertEditorColor(dynamicDarkColorScheme(context).surfaceContainer.toArgb())
            capture("theme-extracted-red-dark")
            compose.runOnIdle { assertEquals(true, savedState.get<Boolean>("extract_theme")) }
            compose.runOnIdle { model.importSharedPhoto(Uri.fromFile(blueFile)) }
            compose.waitUntil(10_000) { !model.state.value.loading && model.state.value.photo !== red }
            val blue = model.state.value.photo!!
            assertNotEquals(red.theme!!.light.primary, blue.theme!!.light.primary)
            compose.onNodeWithContentDescription("Content").assertIsSelected()
            assertEditorColor(dynamicDarkColorScheme(context).surfaceContainer.toArgb())
            capture("theme-extracted-blue-dark")
            compose.onNodeWithText("書き出し").performScrollTo().performClick()
            compose.waitUntil(10_000) { !model.state.value.exporting }
            compose.onNodeWithText("「Pictures/IkiriFrame」に保存しました").assertIsDisplayed()
            context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID), "${MediaStore.Images.Media.RELATIVE_PATH} = ?",
                arrayOf("Pictures/IkiriFrame/"), "${MediaStore.Images.Media._ID} DESC")!!.use { cursor ->
                assertTrue(cursor.moveToFirst())
                exported = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0).toString())
            }
            val output = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, exported!!)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val actual = output.getPixel(2, output.height - 2)
            val expected = blue.theme.light.toFramePalette().surface
            for (channel in listOf(Color::red, Color::green, Color::blue)) {
                assertEquals(channel(expected).toDouble(), channel(actual).toDouble(), 3.0)
            }
            output.recycle()
            val qa = File(context.filesDir, "qa").apply { mkdirs() }
            context.contentResolver.openInputStream(exported!!)!!.use { source ->
                File(qa, "theme-blue-export.jpg").outputStream().use(source::copyTo)
            }
            compose.onNodeWithContentDescription("Android").performScrollTo().performClick().assertIsSelected()
            assertEditorColor(dynamicDarkColorScheme(context).surfaceContainer.toArgb())
            capture("theme-device-dark")
            compose.runOnIdle {
                assertEquals(false, savedState.get<Boolean>("extract_theme"))
                assertEquals(false, preferences.getBoolean("extract_theme", true))
            }
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            exported?.let { context.contentResolver.delete(it, null, null) }
            preferences.edit().putBoolean("extract_theme", previousOption).commit()
            redFile.delete()
            blueFile.delete()
        }
    }

    private fun assertEditorColor(expected: Int) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        assertEquals(expected, bitmap.getPixel(2, 2))
    }

    private fun capture(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = File(context.filesDir, "qa").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun fixture(name: String, color: Int): File {
        val bitmap = Bitmap.createBitmap(720, 480, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        return File(context.cacheDir, name).also { file ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
