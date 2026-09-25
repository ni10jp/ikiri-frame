package jp.ni10.ikiriframe.ui

import android.content.Context
import android.os.Build
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import jp.ni10.ikiriframe.R
import jp.ni10.ikiriframe.photo.FramePalette

val MochiyPopOne = FontFamily(Font(R.font.mochiy_pop_one_regular))

/** Use the platform palette where available, with Material defaults on older Android versions. */
internal fun androidColorScheme(context: Context, darkTheme: Boolean): ColorScheme =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (darkTheme) darkColorScheme() else lightColorScheme()
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun IkiriTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = androidColorScheme(context, darkTheme)
    MaterialExpressiveTheme(colorScheme = colors, content = content)
}

fun ColorScheme.toFramePalette() = FramePalette(surfaceContainerLow.toArgb(), onSurface.toArgb(),
    onSurfaceVariant.toArgb(), outline.toArgb())

/** Animate the displayed footer only; export always receives the selected final palette. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun animateFramePalette(palette: FramePalette): FramePalette {
    val transition = updateTransition(palette, label = "frame-colors")
    val motion = MaterialTheme.motionScheme
    val surface by transition.animateColor(transitionSpec = { motion.fastEffectsSpec() }, label = "surface") { Color(it.surface) }
    val text by transition.animateColor(transitionSpec = { motion.fastEffectsSpec() }, label = "text") { Color(it.text) }
    val label by transition.animateColor(transitionSpec = { motion.fastEffectsSpec() }, label = "label") { Color(it.label) }
    val outline by transition.animateColor(transitionSpec = { motion.fastEffectsSpec() }, label = "outline") { Color(it.outline) }
    return FramePalette(surface.toArgb(), text.toArgb(), label.toArgb(), outline.toArgb())
}
