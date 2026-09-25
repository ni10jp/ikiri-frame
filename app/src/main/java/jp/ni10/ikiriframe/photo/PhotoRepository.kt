package jp.ni10.ikiriframe.photo

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.ErrorResult
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import coil3.request.colorSpace
import coil3.request.maxBitmapSize
import coil3.size.Precision
import coil3.size.Scale
import coil3.size.Size
import coil3.toBitmap
import jp.ni10.ikiriframe.logo.LogoArtwork
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class Photo(val file: File, val preview: Bitmap, val metadata: PhotoMetadata, val theme: PhotoTheme? = null)

class PhotoRepository(private val context: Context) {
    private val directory = File(context.filesDir, "selected_photos").apply { mkdirs() }
    private val legacyDirectory = File(context.cacheDir, "selected_photos")
    private val legacyImageLoader by lazy {
        ImageLoader.Builder(context.applicationContext).memoryCache(null).diskCache(null).build()
    }

    suspend fun importPhoto(uri: Uri): Photo = withContext(Dispatchers.IO) {
        val file = File(directory, "${UUID.randomUUID()}.photo")
        try {
            context.contentResolver.openInputStream(uri)?.use { source ->
                file.outputStream().use { destination -> source.copyTo(destination) }
            } ?: throw IOException("The picker URI cannot be opened")
            currentCoroutineContext().ensureActive()
            loadPhoto(file)
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
    }

    suspend fun restorePhoto(path: String): Photo = withContext(Dispatchers.IO) {
        val file = File(path)
        val parent = file.canonicalFile.parentFile
        require(parent == directory.canonicalFile || parent == legacyDirectory.canonicalFile)
        val durableFile = if (parent == legacyDirectory.canonicalFile) file.copyTo(File(directory, file.name), overwrite = true) else file
        loadPhoto(durableFile)
    }

    private suspend fun loadPhoto(file: File): Photo {
        val metadata = readMetadata(file)
        val preview = decode(file, 1_500_000L)
        return try {
            Photo(file, preview, metadata, PhotoThemeExtractor.extract(context, preview))
        } catch (error: Throwable) {
            preview.recycle()
            throw error
        }
    }

    private fun readMetadata(file: File): PhotoMetadata = runCatching {
        val exif = ExifInterface(file)
        fun positive(tag: String) = exif.getAttributeDouble(tag, -1.0).takeIf { it.isFinite() && it > 0 }
        PhotoMetadata(
            model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim()?.takeIf { it.isNotEmpty() }
                ?: "",
            focalLength = positive(ExifInterface.TAG_FOCAL_LENGTH),
            aperture = positive(ExifInterface.TAG_F_NUMBER),
            exposureSeconds = positive(ExifInterface.TAG_EXPOSURE_TIME),
            iso = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, -1).takeIf { it > 0 },
            takenAt = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME),
            originalExif = PreservedTags.mapNotNull { tag -> exif.getAttribute(tag)?.let { tag to it } }.toMap(),
        )
    }.getOrDefault(PhotoMetadata())

    private suspend fun decode(file: File, pixelBudget: Long, barThickness: Float? = null): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return decodePlatform(file, pixelBudget, barThickness)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Unsupported image")
        val rotated = runCatching { ExifInterface(file).rotationDegrees % 180 != 0 }.getOrDefault(false)
        val width = if (rotated) bounds.outHeight else bounds.outWidth
        val height = if (rotated) bounds.outWidth else bounds.outHeight
        val extraHeight = barThickness?.let { FrameGeometry.barHeight(width, it) } ?: 0
        val pixels = width.toLong() * (height.toLong() + extraHeight)
        val scale = sqrt(pixelBudget.toDouble() / pixels).coerceAtMost(1.0)
        val targetWidth = (width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (height * scale).toInt().coerceAtLeast(1)
        // Coil handles EXIF rotation/reflection and sampled decoding on Android 7/8.
        val request = ImageRequest.Builder(context.applicationContext).data(file)
            .size(targetWidth, targetHeight).maxBitmapSize(Size(targetWidth, targetHeight))
            .precision(Precision.EXACT).scale(Scale.FIT)
            .allowHardware(false).bitmapConfig(Bitmap.Config.ARGB_8888)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) colorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
            }.build()
        return when (val result = legacyImageLoader.execute(request)) {
            is SuccessResult -> result.image.toBitmap()
            is ErrorResult -> throw result.throwable
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodePlatform(file: File, pixelBudget: Long, barThickness: Float?): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
            val extraHeight = barThickness?.let { FrameGeometry.barHeight(info.size.width, it) } ?: 0
            val pixels = info.size.width.toLong() * (info.size.height.toLong() + extraHeight)
            if (pixels > pixelBudget) {
                val scale = sqrt(pixelBudget.toDouble() / pixels)
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt().coerceAtLeast(1),
                    (info.size.height * scale).roundToInt().coerceAtLeast(1),
                )
            }
            // ImageDecoder applies all EXIF rotations/reflections exactly once.
        }

    suspend fun export(photo: Photo, options: FrameOptions, palette: FramePalette, logo: LogoArtwork? = null,
        destination: Uri? = null): Uri =
        withContext(Dispatchers.IO) {
            // Budget for both the decoded source and the larger composited bitmap, leaving
            // room for the UI, preview and encoder on low-memory devices.
            val budget = minOf(24_000_000L, Runtime.getRuntime().maxMemory() / 16)
            var source: Bitmap? = null
            var output: Bitmap? = null
            var pendingUri: Uri? = destination
            var encoded: File? = null
            val resolver = context.contentResolver
            try {
                currentCoroutineContext().ensureActive()
                source = decode(photo.file, budget, options.thickness)
                output = Bitmap.createBitmap(source.width,
                    source.height + FrameGeometry.barHeight(source.width, options.thickness),
                    Bitmap.Config.ARGB_8888)
                FrameRenderer(context).draw(Canvas(output), source, photo.metadata, options, palette, logo)
                // Build a complete JPEG locally: document providers need not expose seekable files.
                val jpeg = File.createTempFile("frame-export-", ".jpg", context.cacheDir)
                encoded = jpeg
                jpeg.outputStream().use { stream ->
                    if (!output.compress(Bitmap.CompressFormat.JPEG, 98, stream)) throw IOException("JPEG encode failed")
                }
                ExifInterface(jpeg).apply {
                    photo.metadata.originalExif.forEach { (tag, value) -> setAttribute(tag, value) }
                    setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                    setAttribute(ExifInterface.TAG_SOFTWARE, "IkiriFrame")
                    setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, output.width.toString())
                    setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION, output.height.toString())
                    saveAttributes()
                }
                currentCoroutineContext().ensureActive()
                val uri = destination ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, exportFileName())
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/IkiriFrame")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        ?: throw IOException("MediaStore insert failed")
                } else throw IOException("A document destination is required")
                pendingUri = uri
                resolver.openOutputStream(uri, "w")?.use { stream ->
                    jpeg.inputStream().use { it.copyTo(stream) }
                } ?: throw IOException("Output stream unavailable")
                currentCoroutineContext().ensureActive()
                if (destination == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    check(resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) == 1)
                }
                pendingUri = null
                uri
            } finally {
                pendingUri?.let { uri -> runCatching {
                    if (destination != null) DocumentsContract.deleteDocument(resolver, uri)
                    else resolver.delete(uri, null, null)
                } }
                encoded?.delete()
                source?.recycle()
                output?.recycle()
            }
        }

    companion object {
        fun exportFileName(): String =
            "IkiriFrame_${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"))}.jpg"

        private val PreservedTags = listOf(
            ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL,
            ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED, ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
            ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
            ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, ExifInterface.TAG_LENS_MODEL,
        )
    }
}
