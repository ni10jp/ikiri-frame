package jp.ni10.ikiriframe.photo

import kotlin.math.min
import kotlin.math.roundToInt

data class FrameOptions(
    val thickness: Float = 1f,
    val extractTheme: Boolean = false,
    val text: FrameText? = null,
    val tag: String = "",
    val paletteId: String? = null,
    val darkTheme: Boolean = false,
)

/** All artwork units are independent of the editing device's density and font scale. */
object FrameGeometry {
    const val ReferenceWidth = 720f
    const val BaseHeight = 62f // 12 + 20 + 2 + 16 + 12
    const val MinimumThickness = 0.65f
    const val MaximumThickness = 1.75f
    const val HorizontalPadding = 24f
    const val LogoLeading = 12f
    const val LogoHeight = 28f
    const val LogoTrailing = 12f
    const val DividerWidth = 1f
    const val DividerTrailing = 8f
    const val TextGap = 8f

    fun barHeight(photoWidth: Int, thickness: Float): Int {
        require(photoWidth > 0)
        return (photoWidth * BaseHeight / ReferenceWidth *
            thickness.coerceIn(MinimumThickness, MaximumThickness)).roundToInt().coerceAtLeast(1)
    }

    fun contentScale(photoWidth: Int, barHeight: Int, requiredWidth: Float): Float =
        min(barHeight / BaseHeight, photoWidth / requiredWidth)

    fun logoBlockWidth(aspectRatio: Float?): Float = if (aspectRatio != null)
        LogoLeading + LogoHeight * aspectRatio + LogoTrailing + DividerWidth + DividerTrailing else 0f
}
