package jp.ni10.ikiriframe.photo

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import jp.ni10.ikiriframe.color.dynamiccolor.DynamicScheme
import jp.ni10.ikiriframe.color.hct.Hct
import jp.ni10.ikiriframe.color.quantize.QuantizerCelebi
import jp.ni10.ikiriframe.color.scheme.SchemeContent
import jp.ni10.ikiriframe.color.scheme.SchemeExpressive
import jp.ni10.ikiriframe.color.scheme.SchemeNeutral
import jp.ni10.ikiriframe.color.scheme.SchemeMonochrome
import jp.ni10.ikiriframe.color.scheme.SchemeTonalSpot
import jp.ni10.ikiriframe.color.scheme.SchemeVibrant
import jp.ni10.ikiriframe.color.score.Score
import jp.ni10.ikiriframe.ui.androidColorScheme
import kotlin.math.roundToInt

data class PhotoPalette(val id: String, val name: String, val light: ColorScheme, val dark: ColorScheme) {
    fun colors(darkTheme: Boolean): ColorScheme = if (darkTheme) dark else light
}

/** Fixed seeds in hue order, independent of the imported photo. */
object BuiltInPhotoPalettes {
    private val seeds = listOf(
        "Red" to 0xffff0000.toInt(), "Orange" to 0xffff8000.toInt(),
        "Yellow" to 0xffffff00.toInt(), "Lime" to 0xff80ff00.toInt(),
        "Green" to 0xff00ff00.toInt(), "Mint" to 0xff00ff80.toInt(),
        "Cyan" to 0xff00ffff.toInt(), "Azure" to 0xff0080ff.toInt(),
        "Blue" to 0xff0000ff.toInt(), "Purple" to 0xff8000ff.toInt(),
        "Magenta" to 0xffff00ff.toInt(), "Pink" to 0xffff0080.toInt(),
    )
    private val ids = seeds.map { "custom-${Integer.toHexString(it.second)}" }.toSet() + "monochrome-ff808080"
    fun contains(id: String?): Boolean = id in ids

    // Initialized on the photo-loading worker along with the extracted palettes.
    val palettes: List<PhotoPalette> by lazy {
        listOf(candidate(0xff808080.toInt(), "monochrome", "Monochrome", ::SchemeMonochrome)) +
            seeds.map { (name, seed) -> candidate(seed, "custom", name, ::SchemeTonalSpot) }
    }
}

data class PhotoTheme(val palettes: List<PhotoPalette>) {
    init { require(palettes.isNotEmpty()) }

    fun palette(id: String?): PhotoPalette = palettes.firstOrNull { it.id == id } ?: palettes.first()
    val light: ColorScheme get() = palettes.first().light
    val dark: ColorScheme get() = palettes.first().dark
}

/** Google's image quantizer, seed ranking and scheme variants; runs on the image-loading worker. */
object PhotoThemeExtractor {
    fun extract(context: Context, bitmap: Bitmap): PhotoTheme {
        val scale = (128f / maxOf(bitmap.width, bitmap.height)).coerceAtMost(1f)
        val sample = Bitmap.createScaledBitmap(bitmap,
            (bitmap.width * scale).roundToInt().coerceAtLeast(1),
            (bitmap.height * scale).roundToInt().coerceAtLeast(1), true)
        val pixels = try {
            IntArray(sample.width * sample.height).also {
                sample.getPixels(it, 0, sample.width, 0, 0, sample.width, sample.height)
            }
        } finally {
            if (sample !== bitmap) sample.recycle()
        }
        val opaque = pixels.filter { (it ushr 24) == 255 }.toIntArray()
        val populations = if (opaque.isEmpty()) emptyMap() else QuantizerCelebi.quantize(opaque, 128)
        // An achromatic photo keeps its own seed; an empty image falls back to device color.
        val fallback = populations.maxByOrNull { it.value }?.key
            ?: androidColorScheme(context, darkTheme = false).primary.toArgb()
        val seeds = Score.score(populations, 3, fallback, true)
        val first = seeds.first()
        val candidates = buildList {
            add(candidate(first, "content", "Content", ::SchemeContent))
            add(candidate(first, "tonal", "Tonal", ::SchemeTonalSpot))
            add(candidate(first, "vibrant", "Vibrant", ::SchemeVibrant))
            add(candidate(first, "expressive", "Expressive", ::SchemeExpressive))
            add(candidate(first, "neutral", "Neutral", ::SchemeNeutral))
            seeds.drop(1).forEachIndexed { index, seed ->
                add(candidate(seed, "tonal", "Tonal ${index + 2}", ::SchemeTonalSpot))
            }
        }
        return PhotoTheme(candidates + BuiltInPhotoPalettes.palettes)
    }
}

private fun candidate(seed: Int, style: String, name: String,
    create: (Hct, Boolean, Double) -> DynamicScheme): PhotoPalette {
    val color = Hct.fromInt(seed)
    return PhotoPalette("$style-${Integer.toHexString(seed)}", name,
        scheme(create(color, false, 0.0)), scheme(create(color, true, 0.0)))
}

private fun scheme(source: DynamicScheme): ColorScheme =
    (if (source.isDark) darkColorScheme() else lightColorScheme()).copy(
        primary = Color(source.primary),
        onPrimary = Color(source.onPrimary),
        primaryContainer = Color(source.primaryContainer),
        onPrimaryContainer = Color(source.onPrimaryContainer),
        inversePrimary = Color(source.inversePrimary),
        secondary = Color(source.secondary),
        onSecondary = Color(source.onSecondary),
        secondaryContainer = Color(source.secondaryContainer),
        onSecondaryContainer = Color(source.onSecondaryContainer),
        tertiary = Color(source.tertiary),
        onTertiary = Color(source.onTertiary),
        tertiaryContainer = Color(source.tertiaryContainer),
        onTertiaryContainer = Color(source.onTertiaryContainer),
        background = Color(source.background),
        onBackground = Color(source.onBackground),
        surface = Color(source.surface),
        onSurface = Color(source.onSurface),
        surfaceVariant = Color(source.surfaceVariant),
        onSurfaceVariant = Color(source.onSurfaceVariant),
        surfaceTint = Color(source.surfaceTint),
        inverseSurface = Color(source.inverseSurface),
        inverseOnSurface = Color(source.inverseOnSurface),
        error = Color(source.error),
        onError = Color(source.onError),
        errorContainer = Color(source.errorContainer),
        onErrorContainer = Color(source.onErrorContainer),
        outline = Color(source.outline),
        outlineVariant = Color(source.outlineVariant),
        surfaceBright = Color(source.surfaceBright),
        surfaceDim = Color(source.surfaceDim),
        surfaceContainer = Color(source.surfaceContainer),
        surfaceContainerHigh = Color(source.surfaceContainerHigh),
        surfaceContainerHighest = Color(source.surfaceContainerHighest),
        surfaceContainerLow = Color(source.surfaceContainerLow),
        surfaceContainerLowest = Color(source.surfaceContainerLowest),
        primaryFixed = Color(source.primaryFixed),
        primaryFixedDim = Color(source.primaryFixedDim),
        onPrimaryFixed = Color(source.onPrimaryFixed),
        onPrimaryFixedVariant = Color(source.onPrimaryFixedVariant),
        secondaryFixed = Color(source.secondaryFixed),
        secondaryFixedDim = Color(source.secondaryFixedDim),
        onSecondaryFixed = Color(source.onSecondaryFixed),
        onSecondaryFixedVariant = Color(source.onSecondaryFixedVariant),
        tertiaryFixed = Color(source.tertiaryFixed),
        tertiaryFixedDim = Color(source.tertiaryFixedDim),
        onTertiaryFixed = Color(source.onTertiaryFixed),
        onTertiaryFixedVariant = Color(source.onTertiaryFixedVariant),
        scrim = Color(source.scrim),
    )
