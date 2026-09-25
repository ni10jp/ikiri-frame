package jp.ni10.ikiriframe.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import androidx.core.content.res.ResourcesCompat
import jp.ni10.ikiriframe.logo.LogoArtwork
import jp.ni10.ikiriframe.R

data class FramePalette(val surface: Int, val text: Int, val label: Int, val outline: Int)

/** Used for both the on-screen preview and the full-resolution export. */
class FrameRenderer(context: Context) {
    private val font = requireNotNull(ResourcesCompat.getFont(context, R.font.google_sans_flex))
    private val modelPaint = textPaint(14f, 600, 14, 100)
    private val detailsPaint = textPaint(12f, 400, 12, 100)
    private val labelPaint = textPaint(22f, 400, 18, 0)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val photoBackgroundPaint = Paint().apply { color = Color.WHITE }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    private fun textPaint(size: Float, weight: Int, optical: Int, roundness: Int) =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(font, weight, false)
                else Typeface.create(font, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
            textSize = size
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                fontVariationSettings = "'slnt' 0, 'wdth' 100, 'wght' $weight, 'GRAD' 0, 'ROND' $roundness, 'opsz' $optical"
            }
        }

    fun draw(
        canvas: Canvas,
        photo: Bitmap,
        metadata: PhotoMetadata,
        options: FrameOptions,
        palette: FramePalette,
        logo: LogoArtwork? = null,
    ) {
        val width = photo.width
        val height = photo.height
        val barHeight = FrameGeometry.barHeight(width, options.thickness)
        // The bar continues underneath the photo, avoiding a white seam at fractional preview scales.
        canvas.drawColor(palette.surface)
        if (photo.hasAlpha()) {
            // Preserve the white matte for transparent photos, only within the photo's bounds.
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), photoBackgroundPaint)
        }
        canvas.drawBitmap(photo, 0f, 0f, bitmapPaint)

        val text = options.text ?: FrameText.from(metadata)
        val detail = text.detailLine
        val modelWidth = modelPaint.measureText(text.model)
        val detailWidth = detailsPaint.measureText(detail)
        val labelWidth = labelPaint.measureText(options.tag)
        val logoWidth = FrameGeometry.logoBlockWidth(logo?.aspectRatio)
        val requiredWidth = FrameGeometry.HorizontalPadding * 2 + logoWidth +
            maxOf(modelWidth, detailWidth) + FrameGeometry.TextGap + labelWidth
        val scale = FrameGeometry.contentScale(width, barHeight, requiredWidth)
        val virtualWidth = width / scale
        val virtualHeight = barHeight / scale

        canvas.save()
        canvas.translate(0f, height.toFloat())
        canvas.scale(scale, scale)
        val centerY = virtualHeight / 2
        val textTop = centerY - 19f
        var x = FrameGeometry.HorizontalPadding
        if (logo != null) {
            x += FrameGeometry.LogoLeading
            val logoHeight = FrameGeometry.LogoHeight
            val logoWidthActual = logoHeight * logo.aspectRatio
            logo.draw(canvas, RectF(x, centerY - 14f, x + logoWidthActual, centerY + 14f))
            x += logoWidthActual + FrameGeometry.LogoTrailing
            fill.color = palette.outline
            // The separator spans the entire inner (38-unit) content height.
            canvas.drawRect(x, textTop, x + FrameGeometry.DividerWidth, textTop + 38f, fill)
            x += FrameGeometry.DividerWidth + FrameGeometry.DividerTrailing
        }
        modelPaint.color = palette.text
        detailsPaint.color = palette.text
        labelPaint.color = palette.label
        drawLine(canvas, text.model, x, textTop, 20f, modelPaint)
        drawLine(canvas, detail, x, textTop + 22f, 16f, detailsPaint)
        drawLine(canvas, options.tag, virtualWidth - 24f - labelWidth, centerY - 14f, 28f, labelPaint)
        canvas.restore()
    }

    private fun drawLine(canvas: Canvas, text: String, x: Float, top: Float, lineHeight: Float, paint: Paint) {
        val metrics = paint.fontMetrics
        val baseline = top + (lineHeight - (metrics.descent - metrics.ascent)) / 2 - metrics.ascent
        canvas.drawText(text, x, baseline, paint)
    }
}
