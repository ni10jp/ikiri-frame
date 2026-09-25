@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package jp.ni10.ikiriframe.ui

import android.graphics.RectF
import androidx.compose.animation.core.animateFloatAsState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.valentinilk.shimmer.shimmer
import jp.ni10.ikiriframe.R
import jp.ni10.ikiriframe.logo.LogoArtwork
import jp.ni10.ikiriframe.logo.LogoViewModel
import jp.ni10.ikiriframe.logo.RegisteredLogo
import kotlin.math.roundToInt

@Composable
fun LogoLibraryScreen(onBack: () -> Unit, model: LogoViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val direction = LocalLayoutDirection.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(model::register)
    }
    LaunchedEffect(model) { model.events.collect { snackbar.showSnackbar(it) } }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = { SmallAppBar("ロゴを選択", onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        val listPadding = PaddingValues(
            start = padding.calculateStartPadding(direction) + 16.dp,
            end = padding.calculateEndPadding(direction) + 16.dp,
            top = 16.dp, bottom = 96.dp,
        )
        Box(Modifier.fillMaxSize()
            .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())
            .consumeWindowInsets(padding)) {
            if (state.initialLoading) {
                LazyColumn(Modifier.fillMaxSize().clearAndSetSemantics {
                    contentDescription = "ロゴを読み込み中"
                    progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                }, contentPadding = listPadding) {
                    items(3) { index ->
                        Surface(Modifier.fillMaxWidth().padding(bottom = 2.dp).height(72.dp),
                            shape = groupedShape(index, 3), color = MaterialTheme.colorScheme.surfaceBright) {
                            LogoLoadingContent(Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            } else {
                LogoDismissList(state.logos, state.selectedId, state.busy, listPadding,
                    onSelect = model::select,
                    onNew = { picker.launch(arrayOf("image/svg+xml", "image/png")) }, onDelete = model::delete)
            }
        }
    }
}

@Composable
internal fun logoSelectionProgress(selected: Boolean, previewProgress: Float = 0f): Float = animateFloatAsState(
    targetValue = if (selected) 1f else previewProgress.coerceIn(0f, 1f),
    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    label = "logo selection",
).value

@Composable
internal fun LogoChoiceRow(logo: RegisteredLogo?, selected: Boolean, enabled: Boolean,
    shape: RoundedCornerShape, selectionProgress: Float, modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val labelColor = lerp(colors.onSurfaceVariant, colors.onPrimaryContainer, selectionProgress)
    val containerColor = lerp(colors.surfaceBright, colors.primaryContainer, selectionProgress)
    val height = 72.dp
    val labelStyle = MaterialTheme.typography.titleMedium
    val weight = (labelStyle.fontWeight ?: FontWeight.Medium).weight
    val pressedShape = if (selected) ButtonDefaults.shapesFor(height).pressedShape
        else MaterialTheme.shapes.largeIncreased
    Button(onClick = {
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        onClick()
    }, enabled = enabled,
        shapes = ButtonDefaults.shapes(shape = shape, pressedShape = pressedShape),
        modifier = modifier.fillMaxWidth().height(height).semantics {
            role = Role.RadioButton
            this.selected = selected
            onDelete?.let { delete ->
                customActions = listOf(CustomAccessibilityAction("削除") { delete(); true })
            }
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor, contentColor = labelColor,
            disabledContainerColor = containerColor, disabledContentColor = labelColor,
        ),
        elevation = null,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                logo?.let { LogoPreview(it.artwork) }
                Text(logo?.name ?: "なし", Modifier.weight(1f, fill = false),
                    style = labelStyle, color = labelColor,
                    fontWeight = FontWeight((weight + 200 * selectionProgress).roundToInt().coerceIn(1, 1000)),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (selectionProgress > 0f) {
                Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null,
                    modifier = Modifier.size(24.dp).graphicsLayer { alpha = selectionProgress })
            }
        }
    }
}

@Composable
internal fun NewLogoRow(enabled: Boolean, shape: RoundedCornerShape,
    modifier: Modifier = Modifier, loading: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    Button(onClick = {
        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
        onClick()
    }, enabled = enabled,
        shapes = ButtonDefaults.shapes(shape = shape, pressedShape = MaterialTheme.shapes.largeIncreased),
        modifier = modifier.fillMaxWidth().height(72.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.surfaceBright,
            contentColor = colors.onSurfaceVariant,
            disabledContainerColor = colors.surfaceBright,
            disabledContentColor = colors.onSurfaceVariant,
        ),
        elevation = null,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        if (loading) {
            LogoLoadingContent(Modifier.clearAndSetSemantics {
                contentDescription = "ロゴを登録中"
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            })
        } else {
            Row(Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                Text("新規", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun LogoLoadingContent(modifier: Modifier = Modifier) {
    val placeholderColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(modifier.fillMaxSize().shimmer(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(28.dp).background(placeholderColor, MaterialTheme.shapes.extraSmall))
        Box(Modifier.fillMaxWidth(.6f).height(20.dp).background(placeholderColor, MaterialTheme.shapes.extraSmall))
    }
}

@Composable
internal fun groupedShape(index: Int, count: Int): RoundedCornerShape {
    val large = MaterialTheme.shapes.largeIncreased.topStart
    val small = MaterialTheme.shapes.extraSmall.topStart
    return RoundedCornerShape(if (index == 0) large else small, if (index == 0) large else small,
        if (index == count - 1) large else small, if (index == count - 1) large else small)
}

@Composable
fun LogoPreview(artwork: LogoArtwork, modifier: Modifier = Modifier) {
    Canvas(modifier.width((28f * artwork.aspectRatio).coerceIn(1f, 96f).dp).height(28.dp).clipToBounds().testTag("logo-preview")) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val height = minOf(size.height, size.width / artwork.aspectRatio)
        val width = height * artwork.aspectRatio
        val left = (size.width - width) / 2
        val top = (size.height - height) / 2
        artwork.draw(drawContext.canvas.nativeCanvas, RectF(left, top, left + width, top + height))
    }
}
