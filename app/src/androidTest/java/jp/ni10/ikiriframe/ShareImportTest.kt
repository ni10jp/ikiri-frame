package jp.ni10.ikiriframe

import android.app.Application
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.ni10.ikiriframe.photo.SharedImage
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareImportTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fixtures = mutableListOf<Uri>()

    @After fun cleanUp() {
        fixtures.forEach { context.contentResolver.delete(it, null, null) }
    }

    @Test fun sharesheetResolvesSingleImagesToTheApp() {
        val intent = Intent(Intent.ACTION_SEND).setType("image/jpeg").setPackage(context.packageName)
        assertTrue(context.packageManager.queryIntentActivities(intent, 0).any {
            it.activityInfo.name == EditorActivity::class.java.name
        })
        assertTrue(context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/jpeg").setPackage(context.packageName), 0).isEmpty())
    }

    @Test fun coldShareOpensEditorAndRecreationDoesNotImportAgain() {
        val source = image(160)
        ActivityScenario.launch<EditorActivity>(share(source)).use { scenario ->
            val model = model(scenario)
            await { !model.state.value.loading && model.state.value.photo?.preview?.width == 160 }
            val cachedFile = model.state.value.photo!!.file
            // Reading from our cache still works after the sender's original is unavailable.
            context.contentResolver.delete(source, null, null)
            fixtures.remove(source)
            scenario.recreate()
            val recreated = model(scenario)
            assertEquals(cachedFile, recreated.state.value.photo!!.file)
        }
    }

    @Test fun aNewShareReplacesTheImageInAnAlreadyRunningActivity() {
        ActivityScenario.launch<EditorActivity>(share(image(160))).use { scenario ->
            val firstModel = model(scenario)
            await { !firstModel.state.value.loading && firstModel.state.value.photo != null }
            val second = image(240)
            scenario.onActivity { activity -> activity.startActivity(share(second).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
            await { !firstModel.state.value.loading && firstModel.state.value.photo?.preview?.width == 240 }
            assertSame(firstModel, model(scenario))
        }
    }

    @Test fun aShareArrivingDuringImportIsProcessedAfterTheCurrentImage() {
        ActivityScenario.launch(EditorActivity::class.java).use { scenario ->
            val model = model(scenario)
            val first = image(160)
            val latest = image(320)
            scenario.onActivity {
                model.importSharedPhoto(first)
                assertTrue(model.state.value.loading)
                model.importSharedPhoto(latest)
            }
            await { !model.state.value.loading && model.state.value.photo?.preview?.width == 320 }
        }
    }

    @Test fun anUnavailableSharedUriDoesNotLoseTheExistingEdit() {
        ActivityScenario.launch<EditorActivity>(share(image(160))).use { scenario ->
            val model = model(scenario)
            await { !model.state.value.loading && model.state.value.photo != null }
            val original = model.state.value.photo
            scenario.onActivity { model.importSharedPhoto(Uri.parse("content://unavailable.ikiri.test/image/1")) }
            await { !model.state.value.loading }
            assertSame(original, model.state.value.photo)
        }
    }

    @Test fun restoredPendingShareFailureKeepsTheCachedEdit() {
        val cachedFile = ActivityScenario.launch<EditorActivity>(share(image(160))).use { scenario ->
            val model = model(scenario)
            await { !model.state.value.loading && model.state.value.photo != null }
            model.state.value.photo!!.file
        }
        val savedState = SavedStateHandle(mapOf(
            "photo_path" to cachedFile.absolutePath,
            "pending_shared_uri" to "content://unavailable.ikiri.test/image/1",
        ))
        val store = ViewModelStore()
        lateinit var restored: EditorViewModel
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            instrumentation.runOnMainSync {
                restored = EditorViewModel(context.applicationContext as Application, savedState)
                store.put("restored", restored)
            }
            await {
                !restored.state.value.loading && restored.state.value.photo != null &&
                    savedState.get<String>("pending_shared_uri") == null
            }
            assertEquals(cachedFile, restored.state.value.photo!!.file)
        } finally {
            instrumentation.runOnMainSync { store.clear() }
        }
    }

    @Test fun clipDataIsSupportedAndUnrelatedOrMalformedSharesAreRejected() {
        val uri = Uri.parse("content://example.test/image/1")
        assertEquals(uri, SharedImage.fromIntent(share(uri)))
        assertEquals(uri, SharedImage.fromIntent(Intent(Intent.ACTION_SEND).setType("image/png").apply {
            clipData = ClipData.newRawUri("Image", uri)
        }))
        assertNull(SharedImage.fromIntent(Intent(Intent.ACTION_MAIN)))
        assertNull(SharedImage.fromIntent(share(uri).setType("text/plain")))
        assertNull(SharedImage.fromIntent(share(Uri.parse("file:///data/private.jpg"))))
        assertNull(SharedImage.fromIntent(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, "broken")))
    }

    private fun share(uri: Uri) = Intent(context, EditorActivity::class.java).apply {
        action = Intent.ACTION_SEND
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri("Shared photo", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun model(scenario: ActivityScenario<EditorActivity>): EditorViewModel {
        lateinit var model: EditorViewModel
        scenario.onActivity { model = ViewModelProvider(it)[EditorViewModel::class.java] }
        return model
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(25)
        assertTrue("Shared image was not imported within 10 seconds", condition())
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun image(width: Int): Uri {
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "IkiriShareTest_${width}_${System.nanoTime()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        })!!
        fixtures += uri
        val bitmap = Bitmap.createBitmap(width, 90, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }
        context.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        bitmap.recycle()
        context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        return uri
    }
}
