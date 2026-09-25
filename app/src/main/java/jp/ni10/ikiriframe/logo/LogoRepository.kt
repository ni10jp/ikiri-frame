package jp.ni10.ikiriframe.logo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Picture
import android.graphics.RectF
import android.net.Uri
import android.provider.OpenableColumns
import com.caverock.androidsvg.RenderOptions
import com.caverock.androidsvg.SVG
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.ceil

class LogoArtwork(
    val aspectRatio: Float,
    private val picture: Picture? = null,
    private val bitmap: Bitmap? = null,
    private val pictureViewport: RectF? = null,
) {
    fun draw(canvas: Canvas, bounds: RectF) {
        if (bounds.width() <= 0f || bounds.height() <= 0f) return
        val checkpoint = canvas.save()
        try {
            canvas.clipRect(bounds)
            if (picture != null) {
                val viewport = pictureViewport ?: RectF(0f, 0f, picture.width.toFloat(), picture.height.toFloat())
                // Picture dimensions are integers, but the SVG viewport can be fractional.
                // Scale the actual viewport so rounding cannot add padding or distort wide logos.
                canvas.translate(bounds.left, bounds.top)
                canvas.scale(bounds.width() / viewport.width(), bounds.height() / viewport.height())
                canvas.translate(-viewport.left, -viewport.top)
                canvas.drawPicture(picture)
            } else bitmap?.let {
                canvas.drawBitmap(it, null, bounds, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            }
        } finally {
            canvas.restoreToCount(checkpoint)
        }
    }
}

data class RegisteredLogo(val id: String, val name: String, val artwork: LogoArtwork)

/** Keep a private copy, so the registered logo survives moves of the original document. */
class LogoRepository(private val context: Context) {
    private val directory = File(context.filesDir, "logos").apply { mkdirs() }
    private val preferences = context.getSharedPreferences("frame_options", Context.MODE_PRIVATE)
    val selectedId: String? get() = preferences.getString("selected_logo", null)

    fun select(id: String?) {
        preferences.edit().putString("selected_logo", id).apply()
    }

    suspend fun list(): List<RegisteredLogo> = withContext(Dispatchers.IO) {
        directory.listFiles().orEmpty().filter { it.extension == "json" }.mapNotNull { file ->
            runCatching { read(file.nameWithoutExtension, thumbnail = true) }.getOrNull()
        }.sortedBy { it.name.lowercase() }
    }

    suspend fun selected(): RegisteredLogo? = withContext(Dispatchers.IO) {
        selectedId?.let { id -> runCatching { read(id) }.getOrNull() }
    }

    suspend fun register(uri: Uri): RegisteredLogo = withContext(Dispatchers.IO) {
        val displayName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: uri.lastPathSegment ?: "ロゴ"
        val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                require(output.size() + count <= MaximumBytes) { "Logo is too large" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: throw IOException("Cannot open logo")
        val png = bytes.take(8) == PngSignature.toList()
        val artwork = decode(bytes, png)
        val id = UUID.randomUUID().toString()
        val name = displayName.ifBlank { "ロゴ.${if (png) "png" else "svg"}" }
        val data = File(directory, "$id.${if (png) "png" else "svg"}")
        val meta = File(directory, "$id.json")
        try {
            data.writeBytes(bytes)
            meta.writeText(JSONObject().put("name", name).put("fileName", name).put("png", png).toString())
            RegisteredLogo(id, name, artwork)
        } catch (error: Throwable) {
            data.delete()
            meta.delete()
            throw error
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        requireValidId(id)
        if (selectedId == id) select(null)
        File(directory, "$id.json").delete()
        File(directory, "$id.png").delete()
        File(directory, "$id.svg").delete()
        Unit
    }

    private fun read(id: String, thumbnail: Boolean = false): RegisteredLogo {
        requireValidId(id)
        val metadata = JSONObject(File(directory, "$id.json").readText())
        val png = metadata.getBoolean("png")
        val bytes = File(directory, "$id.${if (png) "png" else "svg"}").readBytes()
        // Older registrations stored the base name only. Recover their extension from the saved format.
        val name = if (metadata.has("fileName")) metadata.getString("fileName")
            else metadata.getString("name") + if (png) ".png" else ".svg"
        return RegisteredLogo(id, name, decode(bytes, png, thumbnail = thumbnail, validate = false))
    }

    private fun requireValidId(id: String) { require(UUID.fromString(id).toString() == id) }

    private fun decode(bytes: ByteArray, png: Boolean, thumbnail: Boolean = false, validate: Boolean = true): LogoArtwork {
        if (png) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0 &&
                bounds.outWidth.toLong() * bounds.outHeight <= 4_000_000) { "PNG dimensions are too large" }
            val options = BitmapFactory.Options()
            if (thumbnail) while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize.coerceAtLeast(1) > 512) {
                options.inSampleSize = options.inSampleSize.coerceAtLeast(1) * 2
            }
            val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options))
            val row = IntArray(bitmap.width)
            val transparent = !validate || bitmap.hasAlpha() && (0 until bitmap.height).any { y ->
                bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
                row.any { it ushr 24 < 255 }
            }
            if (!transparent) {
                bitmap.recycle()
                throw IllegalArgumentException("PNG must have transparency")
            }
            return LogoArtwork(bounds.outWidth.toFloat() / bounds.outHeight, bitmap = bitmap)
        }
        val svg = SVG.getFromInputStream(bytes.inputStream())
        val aspect = svg.documentAspectRatio
        require(aspect.isFinite() && aspect > 0) { "SVG needs width/height or viewBox" }
        if (svg.documentViewBox == null) {
            // Capture the original coordinate system before replacing the root viewport size.
            // AndroidSVG converts physical units (pt, mm, etc.) to CSS pixels here.
            val width = svg.documentWidth
            val height = svg.documentHeight
            require(width.isFinite() && height.isFinite() && width > 0f && height > 0f) {
                "SVG needs absolute dimensions when viewBox is missing"
            }
            svg.setDocumentViewBox(0f, 0f, width, height)
        }
        val height = minOf(512f, 4096f / aspect)
        val width = height * aspect
        val viewport = RectF(0f, 0f, width, height)
        // renderToPicture sets the recording area, not the root SVG dimensions.
        // Fit that root to the viewport while retaining its viewBox origin and aspect-ratio rules.
        svg.setDocumentWidth("100%")
        svg.setDocumentHeight("100%")
        val picture = svg.renderToPicture(
            ceil(width).toInt().coerceAtLeast(1),
            ceil(height).toInt().coerceAtLeast(1),
            RenderOptions.create().viewPort(0f, 0f, width, height),
        )
        return LogoArtwork(aspect, picture = picture, pictureViewport = viewport)
    }

    companion object {
        private const val MaximumBytes = 8 * 1024 * 1024
        private val PngSignature = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
    }
}
