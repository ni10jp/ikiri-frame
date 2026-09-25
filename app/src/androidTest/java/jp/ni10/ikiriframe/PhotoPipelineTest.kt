package jp.ni10.ikiriframe

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import jp.ni10.ikiriframe.photo.FrameGeometry
import jp.ni10.ikiriframe.photo.FrameOptions
import jp.ni10.ikiriframe.photo.FramePalette
import jp.ni10.ikiriframe.photo.FrameRenderer
import jp.ni10.ikiriframe.photo.PhotoMetadata
import jp.ni10.ikiriframe.photo.PhotoRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoPipelineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val palette = FramePalette(Color.rgb(240, 246, 242), Color.rgb(25, 30, 27), Color.DKGRAY, Color.GRAY)

    @Test fun importedExifRotationIsAppliedExactlyOnceAndExportIsPublished() = runBlocking {
        val file = fixture("rotated.jpg", 120, 80)
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            setAttribute(ExifInterface.TAG_MODEL, "Pixel 10 Pro")
            setAttribute(ExifInterface.TAG_FOCAL_LENGTH, "69/10")
            setAttribute(ExifInterface.TAG_F_NUMBER, "17/10")
            setAttribute(ExifInterface.TAG_EXPOSURE_TIME, "1/400")
            setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, "50")
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:09:24 15:30:45")
            saveAttributes()
        }
        val repository = PhotoRepository(context)
        val photo = repository.importPhoto(Uri.fromFile(file))
        assertEquals(80, photo.preview.width)
        assertEquals(120, photo.preview.height)
        assertEquals("Pixel 10 Pro", photo.metadata.model)
        assertTrue(photo.metadata.detailLine.contains("1/400 s"))
        val outputUri = repository.export(photo, FrameOptions(), palette)
        try {
            val output = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, outputUri)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            assertEquals(80, output.width)
            assertEquals(120 + FrameGeometry.barHeight(80, 1f), output.height)
            // Original blue right half rotates into the bottom half; no double rotation.
            val pixel = output.getPixel(40, 90)
            assertTrue(Color.blue(pixel) > Color.red(pixel))
            context.contentResolver.openInputStream(outputUri)!!.use {
                val exif = ExifInterface(it)
                assertEquals(ExifInterface.ORIENTATION_NORMAL, exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0))
                assertEquals("Pixel 10 Pro", exif.getAttribute(ExifInterface.TAG_MODEL))
            }
            context.contentResolver.query(outputUri, arrayOf(MediaStore.Images.Media.IS_PENDING), null, null, null)!!.use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
            output.recycle()
        } finally {
            context.contentResolver.delete(outputUri, null, null)
            photo.file.delete()
            file.delete()
        }
    }

    @Test fun missingExifStillLoadsAndSourcePixelsRemainUntouched() = runBlocking {
        val file = fixture("plain.jpg", 720, 480)
        val photo = PhotoRepository(context).importPhoto(Uri.fromFile(file))
        assertEquals("", photo.metadata.model)
        assertEquals("", photo.metadata.detailLine)
        val output = Bitmap.createBitmap(720, 542, Bitmap.Config.ARGB_8888)
        FrameRenderer(context).draw(Canvas(output), photo.preview, photo.metadata, FrameOptions(), palette)
        assertEquals(photo.preview.getPixel(100, 100), output.getPixel(100, 100))
        assertEquals(palette.surface, output.getPixel(1, 500))
        photo.file.delete()
        file.delete()
        output.recycle()
    }

    @Test fun rendererCoversThinAndThickBarsWithoutBundledLogo() {
        val source = Bitmap.createBitmap(1440, 960, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(94, 122, 113)) }
        val metadata = PhotoMetadata("Pixel 10 Pro", 6.9, 1.7, 1.0 / 400, 50, "2026:09:24 15:30:45")
        val directory = File(context.filesDir, "qa").apply { mkdirs() }
        for ((name, options) in listOf("frame" to FrameOptions(), "frame-no-logo" to FrameOptions(),
            "frame-thin" to FrameOptions(thickness = 0.65f), "frame-thick" to FrameOptions(thickness = 1.75f))) {
            val height = 960 + FrameGeometry.barHeight(1440, options.thickness)
            val output = Bitmap.createBitmap(1440, height, Bitmap.Config.ARGB_8888)
            FrameRenderer(context).draw(Canvas(output), source, metadata, options, palette)
            assertEquals(palette.surface, output.getPixel(1, height - 1))
            File(directory, "$name.png").outputStream().use { output.compress(Bitmap.CompressFormat.PNG, 100, it) }
            output.recycle()
        }
        source.recycle()
    }

    @Test fun suppliedAdaptiveIconRendersWithFullySaturatedRainbowBackground() {
        val directory = File(context.filesDir, "qa/v1.4.2").apply { mkdirs() }
        val icons = listOf(
            "app-icon" to context.packageManager.getApplicationIcon(context.packageName),
            "app-icon-round" to context.getDrawable(R.mipmap.ic_launcher_round)!!,
        )
        for ((name, drawable) in icons) {
            assertTrue("The installed launcher icon must be adaptive", drawable is AdaptiveIconDrawable)
            val icon = drawable as AdaptiveIconDrawable
            // Validate the layers actually referenced by the packaged icon, not an unused resource.
            renderIcon(icon.background).also { bitmap ->
                for ((x, y) in listOf(216 to 20, 400 to 216, 216 to 400, 20 to 216)) {
                    val color = bitmap.getPixel(x, y)
                    val hsv = FloatArray(3)
                    Color.colorToHSV(color, hsv)
                    assertEquals("Background must be opaque", 255, Color.alpha(color))
                    assertEquals("Rainbow saturation", 1f, hsv[1], 0.005f)
                    assertEquals("Rainbow brightness", 1f, hsv[2], 0.005f)
                }
                bitmap.recycle()
            }
            renderIcon(icon.foreground).also { bitmap ->
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                assertTrue("The supplied emoji must have red star eyes", pixels.count {
                    Color.alpha(it) > 240 && Color.red(it) > 200 && Color.green(it) < 80 && Color.blue(it) < 80
                } > 1000)
                assertTrue("The supplied emoji must have its yellow face", pixels.count {
                    Color.alpha(it) > 240 && Color.red(it) > 210 && Color.green(it) > 130 && Color.blue(it) < 100
                } > 1000)
                bitmap.recycle()
            }
            val mono = icon.monochrome
            assertNotNull("Keep the separate themed icon", mono)
            renderIcon(mono!!).also { bitmap ->
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                assertTrue(pixels.any { Color.alpha(it) > 0 })
                assertTrue(pixels.all { Color.alpha(it) == 0 || (Color.red(it) == Color.green(it) && Color.green(it) == Color.blue(it)) })
                File(directory, "$name-monochrome.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            renderIcon(icon).also { bitmap ->
                val center = bitmap.getPixel(216, 216)
                assertTrue("The normal icon must not render as a black silhouette", Color.red(center) > 150 && Color.green(center) > 80)
                File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
    }

    private fun renderIcon(drawable: Drawable): Bitmap =
        Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888).also {
            drawable.setBounds(0, 0, it.width, it.height)
            drawable.draw(Canvas(it))
        }

    private fun fixture(name: String, width: Int, height: Int): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.RED)
            drawRect(width / 2f, 0f, width.toFloat(), height.toFloat(), Paint().apply { color = Color.BLUE })
        }
        return File(context.cacheDir, name).also { file ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
            bitmap.recycle()
        }
    }
}
