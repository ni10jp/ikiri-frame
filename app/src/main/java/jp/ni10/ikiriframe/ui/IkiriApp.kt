@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package jp.ni10.ikiriframe.ui

import android.net.Uri
import android.os.Build
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.collectFoldingFeaturesAsState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalConfiguration
import jp.ni10.ikiriframe.photo.FrameText
import jp.ni10.ikiriframe.logo.LogoArtwork
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.window.layout.FoldingFeature
import com.valentinilk.shimmer.shimmer
import jp.ni10.ikiriframe.LabelDraft
import jp.ni10.ikiriframe.EditorState
import jp.ni10.ikiriframe.EditorViewModel
import jp.ni10.ikiriframe.R
import jp.ni10.ikiriframe.photo.FrameGeometry
import jp.ni10.ikiriframe.photo.FrameOptions
import jp.ni10.ikiriframe.photo.FramePalette
import jp.ni10.ikiriframe.photo.FrameRenderer
import jp.ni10.ikiriframe.photo.Photo
import jp.ni10.ikiriframe.photo.PhotoRepository
import me.saket.telephoto.zoomable.ZoomableContentLocation
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import kotlin.math.roundToInt

@Composable
fun IkiriHome(onExit: () -> Unit, onPhotoPicked: (Uri) -> Unit) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onPhotoPicked)
    }
    HomeScreen(busy = false, loading = false, onBack = onExit,
        onPick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
}

@Composable
fun IkiriEditor(onExit: () -> Unit, model: EditorViewModel = viewModel(), onSelectLogo: () -> Unit = {}) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = !showDiscard && state.labelDraft == null) { showDiscard = true }
    val photoTheme = state.photo?.theme.takeIf { state.options.extractTheme }
    val palette = (photoTheme?.palette(state.options.paletteId)?.colors(state.options.darkTheme)
        ?: androidColorScheme(context, state.options.darkTheme)).toFramePalette()
    val saveDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
        uri?.let { model.export(palette, it) }
    }
    LaunchedEffect(model, resources) {
        model.events.collect { message -> snackbar.showSnackbar(resources.getString(message)) }
    }
    IkiriTheme() {
        Box(Modifier.fillMaxSize()) {
            EditorScreen(state, palette,
                onBack = { showDiscard = true },
                onSelectLogo = onSelectLogo,
                onLabelDraftChange = model::setLabelDraft, onSaveLabel = model::saveLabel,
                onThicknessChange = model::setThickness,
                onExport = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) model.export(palette)
                    else saveDocument.launch(PhotoRepository.exportFileName())
                },
                onSelectPalette = model::setPalette,
                onDarkThemeChange = model::setDarkTheme)
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp))
        }
        if (showDiscard) DiscardDialog(
            onReturnToEditing = { showDiscard = false },
            onDiscard = {
                showDiscard = false
                model.finishEditing()
                onExit()
            },
        )
    }
}

@Composable
internal fun SmallAppBar(title: String, onBack: () -> Unit, onInfo: (() -> Unit)? = null,
    transparent: Boolean = false) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
            }
        },
        actions = {
            onInfo?.let { open -> IconButton(onClick = open) {
                Icon(painterResource(R.drawable.ic_info), "ライセンス")
            } }
        },
        colors = if (transparent) TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ) else TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    )
}

@Composable
fun HomeScreen(busy: Boolean, loading: Boolean, onBack: () -> Unit, onPick: () -> Unit) {
    var showLicense by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = { SmallAppBar(stringResource(R.string.app_name), onBack,
            onInfo = { showLicense = true }) },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if (maxWidth > maxHeight) {
                Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Hero(Modifier.weight(1f).fillMaxHeight())
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                        PickButton(busy, loading, onPick)
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Hero(Modifier.weight(1f).fillMaxWidth())
                    PickButton(busy, loading, onPick)
                }
            }
        }
    }
    // The decorative model and shadow extend behind system bars; controls keep Scaffold's insets.
    jp.ni10.ikiriframe.home.RollingEmoji(Modifier.fillMaxSize())
    }
    if (showLicense) PlatformDialog("3Dモデルのライセンス", onDismissRequest = { showLicense = false }) {
        val haptics = LocalHapticFeedback.current
        Surface(shape = AlertDialogDefaults.shape, color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = AlertDialogDefaults.TonalElevation) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Text("3Dモデルのライセンス", style = MaterialTheme.typography.headlineSmall,
                    color = AlertDialogDefaults.titleContentColor)
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text("Star-Struck Emoji - Star Eyes - Free Sample\n\n" +
                        "作者: mtmediaofficial\n" +
                        "CGTrader Royalty Free License (no AI)\n\n" +
                        "https://www.cgtrader.com/free-3d-models/character/other/star-struck-emoji-star-eyes-free-sample\n\n" +
                        "星形の目の色を赤に変更し、アプリの描画形式に変換しています。\n" +
                        "モデルデータ単体の再配布はできません。",
                        style = MaterialTheme.typography.bodyMedium, color = AlertDialogDefaults.textContentColor)
                }
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Reject)
                        showLicense = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shapes = ButtonDefaults.shapes(),
                    colors = errorDialogButtonColors(),
                ) { Text("閉じる") }
            }
        }
    }
}

@Composable
private fun Hero(modifier: Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(top = 16.dp)) {
        Text(stringResource(R.string.home_copy), style = MaterialTheme.typography.displayLarge.copy(fontFamily = MochiyPopOne),
            color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun PickButton(busy: Boolean, loading: Boolean, onPick: () -> Unit) {
    val loadingDescription = stringResource(R.string.loading)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (loading) CircularProgressIndicator(Modifier.size(24.dp).semantics { contentDescription = loadingDescription })
        LargeFilledButton(stringResource(R.string.pick_photo), onPick, enabled = !busy)
    }
}

@Composable
fun LargeFilledButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    uploadIcon: Boolean = false,
    hapticFeedbackType: HapticFeedbackType = HapticFeedbackType.VirtualKey,
) {
    val height = ButtonDefaults.LargeContainerHeight
    val haptics = LocalHapticFeedback.current
    Button(
        onClick = {
            haptics.performHapticFeedback(hapticFeedbackType)
            onClick()
        },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = height),
        shapes = ButtonDefaults.shapesFor(height),
        contentPadding = ButtonDefaults.contentPaddingFor(height),
    ) {
        if (uploadIcon) {
            Icon(painterResource(R.drawable.ic_upload), contentDescription = null, Modifier.size(ButtonDefaults.iconSizeFor(height)))
            Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(height)))
        }
        Text(label, style = ButtonDefaults.textStyleFor(height))
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun EditorScreen(
    state: EditorState,
    palette: FramePalette,
    onBack: () -> Unit,
    onSelectLogo: () -> Unit,
    onThicknessChange: (Float) -> Unit,
    onExport: () -> Unit,
    onLabelDraftChange: (LabelDraft?) -> Unit = {},
    onSaveLabel: (LabelDraft) -> Unit = {},
    onSelectPalette: (String?) -> Unit = {},
    onDarkThemeChange: (Boolean) -> Unit = {},
) {
    // A newly loaded photo starts in its final palette; only user changes animate.
    val previewPalette = key(state.photo?.file?.absolutePath) { animateFramePalette(palette) }
    val originalText = FrameText.from(state.photo?.metadata ?: jp.ni10.ikiriframe.photo.PhotoMetadata())
    val openLabel = { onLabelDraftChange(LabelDraft((state.options.text ?: originalText).values(), state.options.tag)) }
    val foldingFeatures by collectFoldingFeaturesAsState()
    val paneStates = rememberSaveableStateHolder()
    val preview: @Composable (Modifier) -> Unit = { modifier ->
        paneStates.SaveableStateProvider("preview") {
            PhotoPreview(state.photo, state.options, previewPalette,
                modifier.recalculateWindowInsets().safeDrawingPadding(), state.loading, state.logo?.artwork)
        }
    }
    val properties: @Composable () -> Unit = {
        paneStates.SaveableStateProvider("properties") {
            Properties(state, onSelectLogo, onThicknessChange, onExport, openLabel, onSelectPalette, onDarkThemeChange)
        }
    }
    var contentPosition by remember { mutableStateOf(Offset.Zero) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = { SmallAppBar(stringResource(R.string.edit), onBack, transparent = true) },
        // Sheet surfaces extend to the window edges; their contents handle system insets.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
            .onGloballyPositioned { contentPosition = it.positionInWindow() }) {
            val density = LocalDensity.current
            val fold = foldingFeatures.firstOrNull { it.isSeparating }
            val tabletop = fold?.orientation == FoldingFeature.Orientation.HORIZONTAL
            // FoldingFeature bounds are in window coordinates, including the app bar and insets.
            val foldStart = with(density) {
                if (tabletop) (fold.bounds.top - contentPosition.y).toDp()
                else ((fold?.bounds?.left ?: 0) - contentPosition.x).toDp()
            }
            val foldEnd = with(density) {
                if (tabletop) (fold.bounds.bottom - contentPosition.y).toDp()
                else ((fold?.bounds?.right ?: 0) - contentPosition.x).toDp()
            }
            val foldExtent = if (tabletop) maxHeight else maxWidth
            val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE || maxWidth >= 840.dp
            val sideSheetWidth = minOf(400.dp, maxWidth * 0.46f)
            if (fold != null && foldStart > 0.dp && foldEnd < foldExtent) {
                val beforeHinge = if (!tabletop && LocalLayoutDirection.current == LayoutDirection.Rtl)
                    maxWidth - foldEnd else foldStart
                FoldedEditorPanes(tabletop, beforeHinge, foldEnd - foldStart, preview, properties)
            } else if (landscape) {
                Row(Modifier.fillMaxSize()) {
                    preview(Modifier.weight(1f).fillMaxHeight())
                    // A non-modal standard side sheet: the preview stays visible and interactive
                    // controls remain in the same pane when the window is resized.
                    Surface(
                        modifier = Modifier.width(sideSheetWidth).fillMaxHeight(),
                        shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shadowElevation = 12.dp,
                    ) {
                        properties()
                    }
                }
            } else {
                val fontScale = LocalDensity.current.fontScale
                val bottomInset = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
                // Keep the usable sheet height while extending its surface behind the system bar.
                val sheetHeight = minOf(418.dp * fontScale.coerceAtMost(1.4f) + bottomInset,
                    (maxHeight - 96.dp).coerceAtLeast(0.dp))
                BottomSheetScaffold(
                    sheetPeekHeight = sheetHeight,
                    sheetSwipeEnabled = false,
                    sheetDragHandle = null,
                    sheetContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    sheetShadowElevation = 12.dp,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    sheetContent = {
                        Box(Modifier.fillMaxWidth().height(sheetHeight)) {
                            properties()
                        }
                    },
                ) { sheetPadding ->
                    Box(Modifier.fillMaxSize().padding(sheetPadding).consumeWindowInsets(sheetPadding),
                        contentAlignment = Alignment.Center) {
                        preview(Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
    // Keep the dialog outside the responsive sheet's subcomposition. Its window owns input layout.
    state.labelDraft?.let { draft ->
        LabelDialog(draft, originalText, !state.exporting && !state.loading && !state.logoLoading,
            onLabelDraftChange, onSaveLabel)
    }
}

@Composable
private fun FoldedEditorPanes(tabletop: Boolean, beforeHinge: Dp, hingeSize: Dp,
    preview: @Composable (Modifier) -> Unit, properties: @Composable () -> Unit) {
    // Row/Column measure the sheet with finite constraints, without intrinsic queries.
    if (tabletop) {
        Column(Modifier.fillMaxSize()) {
            preview(Modifier.fillMaxWidth().height(beforeHinge))
            Spacer(Modifier.height(hingeSize))
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val sheetHeight = maxHeight
                BottomSheetScaffold(
                    sheetPeekHeight = sheetHeight,
                    sheetMaxWidth = Dp.Unspecified,
                    sheetSwipeEnabled = false,
                    sheetDragHandle = null,
                    sheetContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    sheetShadowElevation = 12.dp,
                    containerColor = Color.Transparent,
                    sheetContent = {
                        Box(Modifier.fillMaxWidth().height(sheetHeight)) { properties() }
                    },
                ) {}
            }
        }
    } else {
        Row(Modifier.fillMaxSize()) {
            preview(Modifier.width(beforeHinge).fillMaxHeight())
            Spacer(Modifier.width(hingeSize))
            Surface(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shadowElevation = 12.dp,
            ) { properties() }
        }
    }
}

@Composable
private fun Properties(state: EditorState, onSelectLogo: () -> Unit, onThicknessChange: (Float) -> Unit,
    onExport: () -> Unit, onEditLabel: () -> Unit, onSelectPalette: (String?) -> Unit,
    onDarkThemeChange: (Boolean) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val first = groupedShape(0, 5)
    val middle = groupedShape(1, 5)
    val last = groupedShape(4, 5)
    val enabled = !state.exporting && !state.loading && !state.logoLoading
    val sliderState = rememberSliderState(value = state.options.thickness,
        trackRange = FrameGeometry.MinimumThickness..FrameGeometry.MaximumThickness)
    SideEffect {
        if (sliderState.value != state.options.thickness) {
            sliderState.value = state.options.thickness
        }
    }
    Column(Modifier.fillMaxSize().recalculateWindowInsets().testTag("properties-pane")
        .verticalScroll(rememberScrollState()).testTag("properties-scroll")
        .safeDrawingPadding().padding(16.dp)) {
        Column(Modifier.fillMaxWidth().testTag("property-list"),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ActionProperty(stringResource(R.string.logo), enabled, first, onSelectLogo,
                Modifier.testTag("logo-select-button"))
            ActionProperty("ラベル", enabled && state.photo != null, middle, onEditLabel,
                Modifier.testTag("label-edit-button"))
            Surface(modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                shape = middle, color = MaterialTheme.colorScheme.surfaceBright) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    PropertyTitle(stringResource(R.string.thickness))
                    Slider(
                        state = sliderState,
                        onValueChange = { value ->
                            if (value != sliderState.value &&
                                (value == FrameGeometry.MinimumThickness || value == FrameGeometry.MaximumThickness)) {
                                haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                            }
                            sliderState.value = value
                            onThicknessChange(value)
                        },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth().semantics {
                            contentDescription = "バーの太さ"
                            stateDescription = "${(state.options.thickness * 100).roundToInt()}%"
                        },
                    )
                }
            }
            Surface(modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                shape = middle, color = MaterialTheme.colorScheme.surfaceBright) {
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PropertyTitle(stringResource(R.string.color), Modifier.padding(horizontal = 16.dp))
                    PhotoPalettePicker(state.photo?.theme, state.options.paletteId, state.options.extractTheme,
                        enabled = enabled && state.photo != null, loading = state.loading, onSelect = onSelectPalette)
                }
            }
            ListItem(
                onClick = {
                    val checked = !state.options.darkTheme
                    haptics.performHapticFeedback(if (checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                    onDarkThemeChange(checked)
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).semantics {
                    role = Role.Switch
                    toggleableState = ToggleableState(state.options.darkTheme)
                },
                enabled = enabled && state.photo != null,
                trailingContent = {
                    val switchEnabled = enabled && state.photo != null
                    Switch(checked = state.options.darkTheme, onCheckedChange = null,
                        enabled = switchEnabled,
                        thumbContent = {
                            Icon(painterResource(if (state.options.darkTheme) R.drawable.ic_check else R.drawable.ic_close),
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize))
                        })
                },
                shapes = fixedPropertyShapes(last),
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceBright,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            ) { Text("ダークテーマ", style = MaterialTheme.typography.titleMedium) }
        }
        Spacer(Modifier.height(20.dp))
        ExportButton(state, enabled, onExport)
    }
}

@Composable
private fun ExportButton(state: EditorState, enabled: Boolean, onExport: () -> Unit) {
    LargeFilledButton(
        label = stringResource(if (state.exporting) R.string.exporting else R.string.export),
        onClick = onExport,
        enabled = enabled && state.photo != null,
        uploadIcon = true,
        hapticFeedbackType = HapticFeedbackType.Confirm,
    )
}

@Composable
private fun PropertyTitle(label: String, modifier: Modifier = Modifier) {
    Text(label, modifier = modifier, style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun PhotoPreview(photo: Photo?, options: FrameOptions, palette: FramePalette, modifier: Modifier = Modifier, loading: Boolean = false, logo: LogoArtwork? = null) {
    val context = LocalContext.current
    val renderer = remember(context) { FrameRenderer(context) }
    val description = stringResource(R.string.preview)
    Box(modifier, contentAlignment = Alignment.Center) {
        if (photo == null) {
            if (loading) {
                val loadingDescription = stringResource(R.string.loading)
                Box(Modifier.fillMaxSize().shimmer()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .semantics {
                        contentDescription = loadingDescription
                        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                    })
            }
            else Text(stringResource(R.string.load_error), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else key(photo.file.absolutePath) {
            val outputWidth = photo.preview.width
            val outputHeight = photo.preview.height + FrameGeometry.barHeight(outputWidth, options.thickness)
            val zoomableState = rememberZoomableState(
                zoomSpec = ZoomSpec(maxZoomFactor = 8f),
            ).apply {
                setContentLocation(ZoomableContentLocation.scaledInsideAndCenterAligned(
                    Size(outputWidth.toFloat(), outputHeight.toFloat())))
            }
            // This viewport sets the initial fit and gestures; enlarged content draws under the UI.
            Canvas(Modifier.fillMaxSize().zoomable(zoomableState, clipToBounds = false)
                .semantics { contentDescription = description }) {
                if (size.width <= 0f || size.height <= 0f) return@Canvas
                // Match the content location supplied to Telephoto; it owns the gesture transform.
                val scale = minOf(1f, size.width / outputWidth, size.height / outputHeight)
                val left = (size.width - outputWidth * scale) / 2
                val top = (size.height - outputHeight * scale) / 2
                val canvas = drawContext.canvas.nativeCanvas
                canvas.save()
                canvas.translate(left, top)
                canvas.scale(scale, scale)
                canvas.clipRect(0f, 0f, outputWidth.toFloat(), outputHeight.toFloat())
                renderer.draw(canvas, photo.preview, photo.metadata, options, palette, logo)
                canvas.restore()
            }
        }
    }
}

@Composable
private fun ActionProperty(label: String, enabled: Boolean, shape: Shape, onClick: () -> Unit,
    modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    ListItem(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onClick()
        },
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 72.dp),
        trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
        shapes = fixedPropertyShapes(shape),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceBright,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            trailingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

private fun fixedPropertyShapes(shape: Shape) = ListItemShapes(
    shape = shape, selectedShape = shape, pressedShape = shape,
    focusedShape = shape, hoveredShape = shape, draggedShape = shape,
)

@Composable
private fun errorDialogButtonColors() = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.error,
    contentColor = MaterialTheme.colorScheme.onError,
)

@Composable
private fun DiscardDialog(onReturnToEditing: () -> Unit, onDiscard: () -> Unit) {
    PlatformDialog("破棄しますか？", onDismissRequest = onReturnToEditing) {
        val haptics = LocalHapticFeedback.current
        Surface(shape = AlertDialogDefaults.shape, color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = AlertDialogDefaults.TonalElevation) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Text("破棄しますか？", style = MaterialTheme.typography.headlineSmall,
                    color = AlertDialogDefaults.titleContentColor)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Reject)
                        onDiscard()
                    }, shapes = ButtonDefaults.shapes(), colors = errorDialogButtonColors(),
                        modifier = Modifier.weight(1f)) { Text("破棄") }
                    Button(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        onReturnToEditing()
                    }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) { Text("編集に戻る") }
                }
            }
        }
    }
}

@Composable
private fun LabelDialog(draft: LabelDraft, originalText: FrameText, enabled: Boolean,
    onDraftChange: (LabelDraft?) -> Unit, onSave: (LabelDraft) -> Unit) {
    val fields = draft.values.map { rememberTextFieldState(initialText = it) }
    val tagField = rememberTextFieldState(initialText = draft.tag)
    var resetRequested by rememberSaveable { mutableStateOf(draft.reset) }
    val originalValues = originalText.values()
    val currentOnDraftChange by rememberUpdatedState(onDraftChange)
    fun snapshotDraft(): LabelDraft {
        val values = fields.map { it.text.toString() }
        return LabelDraft(values, tagField.text.toString(), resetRequested && values == originalValues)
    }
    // Persist a one-way snapshot; never feed saved strings back into an active input connection.
    LaunchedEffect(fields, tagField) {
        snapshotFlow { snapshotDraft() }.collect { currentOnDraftChange(it) }
    }
    Dialog(onDismissRequest = { onDraftChange(null) }) {
        val haptics = LocalHapticFeedback.current
        val view = LocalView.current
        Surface(
            modifier = Modifier.widthIn(min = 280.dp, max = 560.dp).fillMaxWidth().heightIn(max = 480.dp),
            shape = AlertDialogDefaults.shape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Text("ラベル", style = MaterialTheme.typography.headlineSmall,
                    color = AlertDialogDefaults.titleContentColor)
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val labels = listOf("機種", "焦点距離", "F値", "シャッタースピード", "ISO値", "撮影日時")
                    fields.forEachIndexed { index, field ->
                        FrameTextField(labels[index], field, enabled, ImeAction.Next)
                    }
                    FrameTextField("右のやつ", tagField, enabled, ImeAction.Done)
                }
                Column(Modifier.fillMaxWidth().testTag("label-dialog-actions"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        performResetHaptic(view)
                        Snapshot.withMutableSnapshot {
                            fields.forEachIndexed { index, field -> field.setTextAndPlaceCursorAtEnd(originalValues[index]) }
                            tagField.setTextAndPlaceCursorAtEnd(FrameOptions().tag)
                            resetRequested = true
                        }
                    }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth(),
                        enabled = enabled) { Text("リセット") }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Reject)
                            onDraftChange(null)
                        }, shapes = ButtonDefaults.shapes(), colors = errorDialogButtonColors(),
                            modifier = Modifier.weight(1f)) { Text("キャンセル") }
                        Button(onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onSave(snapshotDraft())
                        }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f),
                            enabled = enabled) { Text("保存") }
                    }
                }
            }
        }
    }
}

@Composable
private fun FrameTextField(label: String, state: TextFieldState, enabled: Boolean, imeAction: ImeAction) {
    MaterialTheme(motionScheme = LabelTextFieldMotion) {
        OutlinedTextField(state = state, enabled = enabled,
            inputTransformation = InputTransformation.maxLength(120),
            keyboardOptions = KeyboardOptions(imeAction = imeAction),
            label = { Text(label) }, lineLimits = TextFieldLineLimits.SingleLine,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label })
    }
}

// Label movement and border thickness already use Fast Spatial in M3. Match their
// color/opacity transitions to that same spec, scoped only to these text fields.
private val LabelTextFieldMotion = object : MotionScheme by MotionScheme.expressive() {
    override fun <T> fastEffectsSpec() = fastSpatialSpec<T>()
    override fun <T> slowEffectsSpec() = fastSpatialSpec<T>()
}
