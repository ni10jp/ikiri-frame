package jp.ni10.ikiriframe

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.ni10.ikiriframe.logo.*
import jp.ni10.ikiriframe.photo.*
import jp.ni10.ikiriframe.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LogoLibraryTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun registeredSvgSurvivesSourceDeletionAndWideLogoUsesItsIntrinsicWidth() = runBlocking {
        val repository = LogoRepository(context)
        val previous = repository.selectedId
        val source = File(context.cacheDir, "wide-test.svg").apply {
            writeText("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 140 28"><rect width="140" height="28" rx="2" fill="#00ff00"/></svg>""")
        }
        val logo = repository.register(Uri.fromFile(source))
        try {
            source.delete()
            repository.select(logo.id)
            val restored = LogoRepository(context).selected()!!
            assertEquals("wide-test.svg", restored.name)
            assertEquals(5f, restored.artwork.aspectRatio)
            val photo = Bitmap.createBitmap(1440, 960, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
            val output = Bitmap.createBitmap(1440, 1084, Bitmap.Config.ARGB_8888)
            val palette = FramePalette(Color.WHITE, Color.BLACK, Color.BLACK, Color.GRAY)
            val text = FrameText("", "", "", "", "", "")
            FrameRenderer(context).draw(Canvas(output), photo, PhotoMetadata(), FrameOptions(text = text, tag = ""), palette, restored.artwork)
            // At 1440px, 28 units becomes 56px high and the 5:1 logo is 280px wide.
            assertEquals(Color.GREEN, output.getPixel(80, 1000))
            assertEquals(Color.GREEN, output.getPixel(340, 1000))
            assertEquals(Color.WHITE, output.getPixel(356, 1000))
            assertEquals(Color.WHITE, output.getPixel(100, 990))
            assertEquals(Color.GREEN, output.getPixel(100, 996))
            assertEquals(Color.WHITE, output.getPixel(100, 1052))
            save(output, "wide-logo-frame")
            photo.recycle(); output.recycle()
            repository.select(null)
            assertNull(LogoRepository(context).selected())
        } finally { repository.delete(logo.id); repository.select(previous); source.delete() }
    }

    @Test fun transparentPngIsAcceptedButOpaquePngAndInvalidFilesAreRejected() = runBlocking {
        val repository = LogoRepository(context)
        val source = File(context.cacheDir, "transparent-test.png")
        val bitmap = Bitmap.createBitmap(80, 20, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawRect(2f, 2f, 78f, 18f, Paint().apply { color = Color.RED })
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val logo = repository.register(Uri.fromFile(source))
        try {
            assertEquals("transparent-test.png", logo.name)
            assertEquals(4f, logo.artwork.aspectRatio)
            bitmap.eraseColor(Color.RED)
            source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertTrue(runCatching { repository.register(Uri.fromFile(source)) }.isFailure)
            source.writeText("Not an image")
            assertTrue(runCatching { repository.register(Uri.fromFile(source)) }.isFailure)
        } finally { repository.delete(logo.id); source.delete(); bitmap.recycle() }
    }

    @Test fun selectorUsesListWithFullFilenamesAndPersistsSelection() = runBlocking {
        val repository = LogoRepository(context)
        val previous = repository.selectedId
        val source = File(context.cacheDir, "Studio-test.svg").apply {
            writeText("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 28"><rect width="120" height="28" rx="8" fill="#2569cc"/></svg>""")
        }
        val logo = repository.register(Uri.fromFile(source))
        repository.select(null)
        val store = ViewModelStore()
        var returned = 0
        val feedback = mutableListOf<HapticFeedbackType>()
        val haptics = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { feedback += hapticFeedbackType }
        }
        lateinit var model: LogoViewModel
        instrumentation.runOnMainSync {
            model = LogoViewModel(context.applicationContext as Application)
            store.put("library-test", model)
        }
        try {
            compose.setContent { CompositionLocalProvider(LocalHapticFeedback provides haptics) { IkiriTheme(darkTheme = false) { LogoLibraryScreen({ returned++ }, model) } } }
            compose.waitUntil(10_000) { !model.state.value.busy }
            compose.onNodeWithText("なし").assertIsSelected().assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role, androidx.compose.ui.semantics.Role.RadioButton))
            compose.onNodeWithText("Studio-test.svg").performScrollTo().performClick().assertIsSelected()
            for (label in listOf("なし", "Studio-test.svg")) {
                compose.onNodeWithText(label).assertHeightIsEqualTo(72.dp)
            }
            assertEquals(logo.id, LogoRepository(context).selectedId)
            assertEquals(listOf(HapticFeedbackType.SegmentTick), feedback)
            val title = compose.onNodeWithText("Studio-test.svg", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val preview = compose.onAllNodesWithTag("logo-preview", useUnmergedTree = true).fetchSemanticsNodes()
                .map { it.boundsInRoot }.single { kotlin.math.abs(it.center.y - title.center.y) < 1f }
            assertTrue("Logo preview must precede its filename", preview.right <= title.left)
            assertEquals(0, returned)
            save(compose.onRoot().captureToImage().asAndroidBitmap(), "logo-selection")
            compose.onNodeWithText("なし").performScrollTo().performClick().assertIsSelected()
            assertNull(repository.selectedId)
            assertEquals(listOf(HapticFeedbackType.SegmentTick, HapticFeedbackType.SegmentTick), feedback)
            assertEquals(0, returned)
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            repository.delete(logo.id); repository.select(previous); source.delete()
        }
    }

    @Test fun oldRegistrationRecoversExtensionWithoutRenamingNewFilenames() = runBlocking {
        val repository = LogoRepository(context)
        val source = File(context.cacheDir, "old.name.SVG").apply {
            writeText("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20"><circle cx="10" cy="10" r="10"/></svg>""")
        }
        val logo = repository.register(Uri.fromFile(source))
        try {
            assertEquals("old.name.SVG", repository.list().first { it.id == logo.id }.name)
            File(context.filesDir, "logos/${logo.id}.json").writeText(
                org.json.JSONObject().put("name", "old.name").put("png", false).toString())
            assertEquals("old.name.svg", repository.list().first { it.id == logo.id }.name)
        } finally { repository.delete(logo.id); source.delete() }
    }

    private fun save(bitmap: Bitmap, name: String) {
        val directory = File(context.filesDir, "qa").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
