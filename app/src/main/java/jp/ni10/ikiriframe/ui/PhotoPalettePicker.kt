@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package jp.ni10.ikiriframe.ui

import androidx.compose.animation.animateColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle
import com.valentinilk.shimmer.shimmer
import jp.ni10.ikiriframe.R
import jp.ni10.ikiriframe.photo.PhotoTheme
import kotlin.math.roundToInt

@Composable
internal fun PhotoPalettePicker(theme: PhotoTheme?, selectedId: String?, extractTheme: Boolean,
    enabled: Boolean, loading: Boolean, onSelect: (String?) -> Unit) {
    // Missing palette data is not a selection of the Android palette.
    // Create the choices only once the photo and its palettes are ready together.
    if (loading || theme == null) {
        PalettePlaceholders(loading)
        return
    }
    val darkTheme = isSystemInDarkTheme()
    val currentId = if (extractTheme) theme.palette(selectedId).id else null
    val androidColors = androidColorScheme(LocalContext.current, darkTheme)
    LazyRow(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        item(key = "android") {
            PaletteChoice("Android", androidColors, !extractTheme, enabled) { onSelect(null) }
        }
        items(theme.palettes, key = { it.id }) { palette ->
            PaletteChoice(palette.name, palette.colors(darkTheme), palette.id == currentId, enabled) { onSelect(palette.id) }
        }
    }
}

@Composable
private fun PalettePlaceholders(loading: Boolean) {
    val placeholderColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val labelHeight = with(LocalDensity.current) { MaterialTheme.typography.titleSmall.lineHeight.toDp() }
    val description = stringResource(if (loading) R.string.color_loading else R.string.load_error)
    LazyRow(
        modifier = Modifier.fillMaxWidth().testTag("palette-placeholders")
            .then(if (loading) Modifier.shimmer() else Modifier)
            .clearAndSetSemantics {
                contentDescription = description
                if (loading) progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            },
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
    ) {
        items(12) {
            Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(72.dp).background(placeholderColor, MaterialTheme.shapes.medium))
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(.75f).height(labelHeight)
                    .background(placeholderColor, MaterialTheme.shapes.extraSmall))
            }
        }
    }
}

@Composable
private fun PaletteChoice(name: String, colors: ColorScheme, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val transition = updateTransition(selected, label = "palette-selection")
    val motion = MaterialTheme.motionScheme
    val progress by transition.animateFloat(transitionSpec = { motion.fastEffectsSpec() }, label = "shape") {
        if (it) 1f else 0f
    }
    val primary by animateColorAsState(colors.primary, animationSpec = motion.fastEffectsSpec(), label = "primary")
    val secondary by transition.animateColor(transitionSpec = { motion.fastEffectsSpec() }, label = "secondary") {
        if (it) colors.primary else colors.secondaryContainer
    }
    val tertiary by transition.animateColor(transitionSpec = { motion.fastEffectsSpec() }, label = "tertiary") {
        if (it) colors.primary else colors.tertiaryFixedDim
    }
    val onPrimary by animateColorAsState(colors.onPrimary, animationSpec = motion.fastEffectsSpec(), label = "on-primary")
    val selection = progress.coerceIn(0f, 1f)
    val density = LocalDensity.current
    val squareShape = MaterialTheme.shapes.medium
    val tileSize = with(density) { 72.dp.toPx() }
    val radius = squareShape.topStart.toPx(Size(tileSize, tileSize), density) / tileSize
    val morph = remember(radius) {
        Morph(
            RoundedPolygon.rectangle(width = 1f, height = 1f, rounding = CornerRounding(radius),
                centerX = 0.5f, centerY = 0.5f),
            MaterialShapes.Cookie12Sided,
        )
    }
    val selectionShape = remember(morph, selection) { PaletteMorphShape(morph, selection) }
    // Keep M3's built-in pressed-corner animation while the tile is an unselected square.
    val shapes = if (selection == 0f) {
        ButtonDefaults.shapes(shape = squareShape, pressedShape = ButtonDefaults.pressedShape)
    } else {
        ButtonDefaults.shapes(shape = selectionShape, pressedShape = selectionShape)
    }
    val weight = (200 + 400 * selection).roundToInt()
    val roundness = (100 * selection).roundToInt()
    val font = remember(weight, roundness) {
        FontFamily(Font(R.font.google_sans_flex, weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(
                FontVariation.slant(0f), FontVariation.width(72f), FontVariation.weight(weight),
                FontVariation.Setting("ROND", roundness.toFloat()),
            )))
    }
    val view = LocalView.current
    val lowerHalf = remember(secondary, tertiary) {
        Brush.horizontalGradient(
            0.5f to secondary,
            0.5f to tertiary,
        )
    }
    // Tiles follow the system theme; the photo footer has its own light/dark setting.
    Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = {
                if (!selected) {
                    performPaletteSelectionHaptic(view)
                    onClick()
                }
            },
            enabled = enabled,
            modifier = Modifier.size(72.dp).semantics {
                this.selected = selected
                role = Role.RadioButton
                contentDescription = name
            },
            shapes = shapes,
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent),
            elevation = null,
        ) {
            // Composite the palette first, then let the M3 button clip its outline once.
            Box(Modifier.fillMaxSize().graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }, contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(primary)
                    // A single rectangle with a hard color stop has no independently blended seam.
                    drawRect(lowerHalf, topLeft = Offset(0f, size.height / 2),
                        size = Size(size.width, size.height / 2))
                }
                Icon(painterResource(R.drawable.ic_palette_check), contentDescription = null,
                    tint = onPrimary, modifier = Modifier.size(32.dp).alpha(selection))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(name, modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
            style = MaterialTheme.typography.titleSmall.copy(fontFamily = font, fontWeight = FontWeight(weight)),
            color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Adapts the official AndroidX polygon morph to a Compose button's outline. */
private class PaletteMorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(morph.toPath(progress).apply {
            transform(Matrix().apply { scale(size.width, size.height) })
        })
}
