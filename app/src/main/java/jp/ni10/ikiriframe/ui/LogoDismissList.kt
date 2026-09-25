package jp.ni10.ikiriframe.ui

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import jp.ni10.ikiriframe.R
import jp.ni10.ikiriframe.logo.LogoDismissHaptics
import jp.ni10.ikiriframe.logo.RegisteredLogo
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sign

private enum class DismissPhase { Idle, Dragging, Returning, Flying, Closing, Removing }

private const val StickyMovement = .32f

private fun followerFraction(distance: Int): Float = when (distance) {
    1 -> 1f / 8f
    2 -> 1f / 16f
    3 -> 1f / 32f
    else -> 0f
}

@Stable
private class LogoDismissMotion {
    var id by mutableStateOf<String?>(null)
    var phase by mutableStateOf(DismissPhase.Idle)
    var distance by mutableFloatStateOf(0f)
    var offset by mutableFloatStateOf(0f)
    var initialDragDirection by mutableFloatStateOf(0f)
    var corner by mutableFloatStateOf(0f)
    var morphCorners by mutableStateOf(false)
    var pull by mutableFloatStateOf(0f)
    var detached by mutableStateOf(false)
    var closure by mutableFloatStateOf(0f)
    var animation: Job? = null
    var pullAnimation: Job? = null
    val closing get() = phase == DismissPhase.Closing || phase == DismissPhase.Removing

    fun clear() {
        id = null
        phase = DismissPhase.Idle
        distance = 0f
        offset = 0f
        initialDragDirection = 0f
        pull = 0f
        detached = false
        morphCorners = false
        closure = 0f
    }
}

/** Clip only the contact edges. The content is always measured at its original 72dp height. */
private data class ContactShape(
    val topRadius: Float, val bottomRadius: Float,
    val topCrop: Float = 0f, val bottomCrop: Float = 0f,
) : Shape {
    val roundedCorners get() = RoundedCornerShape(topRadius, topRadius, bottomRadius, bottomRadius)

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
        Outline.Rounded(RoundRect(
            left = 0f, top = topCrop, right = size.width, bottom = size.height - bottomCrop,
            topLeftCornerRadius = CornerRadius(topRadius), topRightCornerRadius = CornerRadius(topRadius),
            bottomLeftCornerRadius = CornerRadius(bottomRadius), bottomRightCornerRadius = CornerRadius(bottomRadius),
        ))
}

private fun Modifier.clipContact(shape: ContactShape): Modifier =
    if (shape.topCrop > 0f || shape.bottomCrop > 0f) clip(shape) else this

private data class ContactClosure(val upperShift: Float, val lowerShift: Float, val crop: Float)

private fun contactClosure(progress: Float, stride: Float, cropLimit: Float, hasUpper: Boolean): ContactClosure {
    val remaining = (stride * (1f - progress)).coerceAtLeast(0f)
    val lower = (stride * (1f - progress)).coerceAtMost(0f)
    if (!hasUpper) return ContactClosure(0f, lower.coerceAtLeast(-cropLimit), (-lower).coerceAtMost(cropLimit))
    // Both sides move towards the contact. The upper block returns to its original anchor.
    val upper = (cropLimit * 4f * progress.coerceAtLeast(0f) * abs(1f - progress)).coerceAtMost(cropLimit)
    val boundedLower = max(lower, upper - remaining - 2f * cropLimit)
    val crop = ((upper - remaining - boundedLower) / 2f).coerceIn(0f, cropLimit)
    return ContactClosure(upper, boundedLower, crop)
}

@Composable
internal fun LogoDismissList(
    logos: List<RegisteredLogo>, selectedId: String?, busy: Boolean, contentPadding: PaddingValues,
    onSelect: (String?) -> Unit, onNew: () -> Unit, onDelete: suspend (String) -> Boolean,
) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val haptics = remember(view, scope) { LogoDismissHaptics(view, scope) }
    val delete by rememberUpdatedState(onDelete)
    val fastSpatial = remember { MotionScheme.expressive().fastSpatialSpec<Float>() }
    val fastEffects = remember { MotionScheme.expressive().fastEffectsSpec<Float>() }
    val defaultSpatial = remember { MotionScheme.expressive().defaultSpatialSpec<Float>() }
    val small = MaterialTheme.shapes.extraSmall.topStart.toPx(Size.Zero, density)
    val medium = MaterialTheme.shapes.medium.topStart.toPx(Size.Zero, density)
    val large = MaterialTheme.shapes.largeIncreased.topStart.toPx(Size.Zero, density)
    val threshold = with(density) { 120.dp.toPx() }
    val reattachDistance = with(density) { 12.dp.toPx() }
    val gap = with(density) { 2.dp.toPx() }
    val height = with(density) { 72.dp.toPx() }
    val cropLimit = with(density) { 8.dp.toPx() }
    val stride = height + gap
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val leftInset = with(density) { contentPadding.calculateLeftPadding(direction).toPx() }
        val rightInset = with(density) { contentPadding.calculateRightPadding(direction).toPx() }
        val rowWidth = with(density) {
            (maxWidth - contentPadding.calculateLeftPadding(direction) - contentPadding.calculateRightPadding(direction)).toPx()
        }.coerceAtLeast(1f)
        val motion = remember(rowWidth, density.density) { LogoDismissMotion() }
        val animatedCorner = key(motion) {
            animateFloatAsState(
                targetValue = motion.corner,
                animationSpec = if (motion.morphCorners) fastEffects else snap(),
                label = "logo-contact-corner",
            )
        }
        DisposableEffect(motion, lifecycle, haptics) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE) {
                    haptics.stop()
                    if (motion.phase == DismissPhase.Dragging || motion.phase == DismissPhase.Returning) {
                        motion.animation?.cancel()
                        motion.pullAnimation?.cancel()
                        motion.clear()
                    }
                }
            }
            lifecycle.addObserver(observer)
            onDispose {
                lifecycle.removeObserver(observer)
                motion.animation?.cancel()
                motion.pullAnimation?.cancel()
                haptics.stop()
            }
        }
        LaunchedEffect(logos, motion.phase) {
            // Keep the removed row collapsed until this composition receives the updated list.
            if (motion.phase == DismissPhase.Removing && logos.none { it.id == motion.id }) motion.clear()
        }

        fun start(id: String): Boolean {
            if (busy || motion.phase != DismissPhase.Idle) return false
            motion.id = id
            motion.phase = DismissPhase.Dragging
            motion.initialDragDirection = 0f
            motion.corner = small
            return true
        }

        fun releasePull() {
            motion.pullAnimation?.cancel()
            motion.pullAnimation = scope.launch {
                AnimationState(motion.pull).animateTo(0f, fastSpatial) { motion.pull = value }
                motion.pull = 0f
            }
        }

        fun drag(amount: Float) {
            if (motion.phase != DismissPhase.Dragging) return
            // Latch the first actual drag, not the direction of the return spring.
            if (motion.initialDragDirection == 0f && amount != 0f) {
                motion.initialDragDirection = sign(amount)
            }
            if (motion.detached) {
                val side = sign(motion.offset)
                val nextOffset = (motion.offset + amount).coerceIn(-(rowWidth + leftInset), rowWidth + rightInset)
                // Reattach near the original position, preserving the visible position and
                // requiring a fresh resisted drag before the next release.
                if (amount * side < 0f && nextOffset * side <= reattachDistance) {
                    // Crossing home catches at the anchor before dragging in the other direction.
                    motion.offset = if (nextOffset * side < 0f) 0f else nextOffset
                    motion.detached = false
                    motion.distance = motion.offset / StickyMovement
                    motion.pullAnimation?.cancel()
                    motion.pull = motion.offset
                    haptics.stop()
                } else {
                    motion.offset = nextOffset
                    return
                }
            } else {
                motion.distance = (motion.distance + amount)
                    .coerceIn(-(rowWidth + threshold + leftInset), rowWidth + threshold + rightInset)
                val inInitialDirection = motion.distance * motion.initialDragDirection >= 0f
                // The opposite side has no delete pill: keep resisting, even beyond the release distance.
                val attachedDistance = if (inInitialDirection) motion.distance.coerceIn(-threshold, threshold)
                    else motion.distance
                motion.offset = attachedDistance * StickyMovement
                motion.pull = motion.offset
                if (motion.distance * motion.initialDragDirection >= threshold) {
                    motion.offset += motion.distance - attachedDistance
                    motion.detached = true
                    motion.morphCorners = true
                    motion.corner = large
                    haptics.detach()
                    releasePull()
                    return
                }
            }
            val progress = (abs(motion.distance) / threshold).coerceIn(0f, 1f)
            motion.corner = small + (medium - small) * progress
            haptics.resist(progress)
        }

        fun finish(cancelled: Boolean = false) {
            if (motion.phase != DismissPhase.Dragging) return
            val id = motion.id ?: return
            val dismiss = !cancelled && motion.detached && motion.offset * motion.initialDragDirection > 0f
            haptics.stopResistance()
            motion.phase = if (dismiss) DismissPhase.Flying else DismissPhase.Returning
            motion.animation = scope.launch {
                if (!dismiss) {
                    haptics.stop()
                    motion.pullAnimation?.cancel()
                    val startOffset = motion.offset
                    val startCorner = if (motion.morphCorners) animatedCorner.value else motion.corner
                    val startPull = motion.pull
                    // After release, the corner returns with Fast Effects independently of translation.
                    if (motion.morphCorners) motion.corner = small
                    AnimationState(0f).animateTo(1f, fastSpatial) {
                        motion.offset = startOffset * (1f - value)
                        if (!motion.morphCorners) {
                            // Before release, the attached corners follow the return displacement.
                            motion.corner = small + (startCorner - small) * abs(1f - value)
                        }
                        motion.pull = startPull * (1f - value)
                    }
                    motion.clear()
                    return@launch
                }
                // Fully clear the physical screen edge, including the list's horizontal inset.
                val destination = if (motion.offset < 0f) -(rowWidth + leftInset + 1f)
                    else rowWidth + rightInset + 1f
                // Use the same settling spec as Material 3 SwipeToDismissBox.
                AnimationState(motion.offset)
                    .animateTo(destination, AnchoredDraggableDefaults.SnapAnimationSpec) {
                        motion.offset = value
                    }
                motion.pullAnimation?.cancel()
                motion.pull = 0f
                val startCorner = animatedCorner.value
                motion.corner = startCorner
                motion.morphCorners = false
                motion.phase = DismissPhase.Closing
                AnimationState(0f).animateTo(1f, defaultSpatial) {
                    motion.closure = value
                    motion.corner = startCorner + (small - startCorner) * value.coerceIn(0f, 1f)
                }
                motion.closure = 1f
                motion.corner = small
                motion.phase = DismissPhase.Removing
                if (!delete(id) && motion.id == id && motion.phase == DismissPhase.Removing) {
                    motion.clear()
                }
            }
        }

        // The non-dismissible "none" choice precedes the registered logos.
        val activeIndex = logos.indexOfFirst { it.id == motion.id }.let { if (it < 0) -1 else it + 1 }
        val lastIndex = logos.size + 1
        val closure = if (motion.closing) contactClosure(motion.closure, stride, cropLimit, activeIndex > 0)
            else ContactClosure(0f, 0f, 0f)
        // Preview the fallback choice without changing the saved selection before deletion succeeds.
        val nonePreview = if (selectedId != null && motion.id == selectedId) {
            when (motion.phase) {
                DismissPhase.Dragging, DismissPhase.Flying -> {
                    val exitDistance = rowWidth + (if (motion.offset < 0f) leftInset else rightInset) + 1f
                    (motion.offset * motion.initialDragDirection / exitDistance).coerceIn(0f, 1f)
                }
                DismissPhase.Closing, DismissPhase.Removing -> 1f
                DismissPhase.Idle, DismissPhase.Returning -> 0f
            }
        } else 0f

        fun followerOffset(index: Int): Float = if (activeIndex < 0) 0f
            else motion.pull * followerFraction(abs(index - activeIndex))

        fun contactRadius(upperIndex: Int): Float {
            if (activeIndex < 0) return small
            if (upperIndex == activeIndex || upperIndex + 1 == activeIndex) {
                return if (motion.morphCorners) animatedCorner.value else motion.corner
            }
            // The two corners sharing an edge use the same relative displacement.
            val separation = abs(followerOffset(upperIndex) - followerOffset(upperIndex + 1))
            val progress = (separation / (threshold * StickyMovement * (1f - followerFraction(1)))).coerceIn(0f, 1f)
            return small + (medium - small) * progress
        }

        fun shape(index: Int, selectionProgress: Float = 0f): ContactShape {
            val topContact = if (index == 0) large else contactRadius(index - 1)
            val bottomContact = if (index == lastIndex) large else contactRadius(index)
            val top = topContact + (large - topContact) * selectionProgress
            val bottom = bottomContact + (large - bottomContact) * selectionProgress
            return ContactShape(top, bottom,
                topCrop = if (motion.closing && index == activeIndex + 1) closure.crop else 0f,
                bottomCrop = if (motion.closing && index == activeIndex - 1) closure.crop else 0f)
        }

        fun placement(index: Int) = Modifier.graphicsLayer {
            translationX = followerOffset(index)
            translationY = if (!motion.closing) 0f
                else if (index < activeIndex) closure.upperShift else closure.lowerShift
        }

        // Padding belongs to the content, not the viewport: the moving card can reach the screen edge.
        LazyColumn(Modifier.fillMaxSize().selectableGroup(), contentPadding = contentPadding,
            userScrollEnabled = motion.phase == DismissPhase.Idle) {
            item(key = "none") {
                val selection = logoSelectionProgress(selectedId == null, nonePreview)
                val contact = shape(0, selection)
                LogoChoiceRow(null, selected = selectedId == null,
                    enabled = !busy && motion.phase == DismissPhase.Idle,
                    shape = contact.roundedCorners, selectionProgress = selection,
                    modifier = Modifier.padding(bottom = 2.dp).then(placement(0)).clipContact(contact),
                    onClick = { onSelect(null) })
            }
            itemsIndexed(logos, key = { _, logo -> logo.id }) { logoIndex, logo ->
                val index = logoIndex + 1
                if (index == activeIndex && motion.closing) {
                    Spacer(Modifier.fillMaxWidth().height(with(density) {
                        (stride * (1f - motion.closure)).coerceAtLeast(0f).toDp()
                    }))
                } else {
                    val active = index == activeIndex
                    val selection = logoSelectionProgress(logo.id == selectedId)
                    SwipeLogoCard(logo = logo, selected = logo.id == selectedId,
                        shape = shape(index, selection), selectionProgress = selection,
                        modifier = Modifier.padding(bottom = 2.dp).then(placement(index)),
                        offset = if (active) motion.offset else 0f,
                        pillDirection = if (active) motion.initialDragDirection else 0f,
                        enabled = !busy && motion.phase == DismissPhase.Idle,
                        onSelect = { onSelect(logo.id) },
                        onStart = { start(logo.id) }, onDrag = ::drag,
                        onEnd = { finish() }, onCancel = { finish(cancelled = true) },
                        onAccessibleDelete = {
                            if (start(logo.id)) {
                                drag(-threshold)
                                finish()
                            }
                        })
                }
            }
            item(key = "new") {
                val contact = shape(lastIndex)
                NewLogoRow(enabled = !busy && motion.phase == DismissPhase.Idle,
                    loading = busy,
                    // Material buttons animate RoundedCornerShape through their standard interaction source.
                    shape = contact.roundedCorners,
                    modifier = placement(lastIndex).clipContact(contact),
                    onClick = onNew)
            }
        }
    }
}

@Composable
private fun SwipeLogoCard(
    logo: RegisteredLogo, selected: Boolean, shape: ContactShape, selectionProgress: Float,
    modifier: Modifier, offset: Float, pillDirection: Float,
    enabled: Boolean, onSelect: () -> Unit, onStart: () -> Boolean, onDrag: (Float) -> Unit,
    onEnd: () -> Unit, onCancel: () -> Unit, onAccessibleDelete: () -> Unit,
) {
    val density = LocalDensity.current
    val start by rememberUpdatedState(onStart)
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onEnd)
    val cancel by rememberUpdatedState(onCancel)
    BoxWithConstraints(modifier.fillMaxWidth().height(72.dp).pointerInput(logo.id) {
        var accepted = false
        detectHorizontalDragGestures(
            onDragStart = { accepted = start() },
            onHorizontalDrag = { change, amount ->
                if (accepted) {
                    change.consume()
                    drag(amount)
                }
            },
            onDragEnd = { if (accepted) end(); accepted = false },
            onDragCancel = { if (accepted) cancel(); accepted = false },
        )
    }) {
        // Only reveal the original side. Crossing home during overshoot hides the pill.
        val revealedWidth = (offset * pillDirection).coerceAtLeast(0f)
        if (revealedWidth > 0f) {
            // Meet the moving edge directly; round towards overlap to avoid a subpixel seam.
            val width = with(density) { ceil(revealedWidth).coerceAtMost(maxWidth.toPx()).toDp() }
            val alignment = if (pillDirection > 0f) AbsoluteAlignment.CenterLeft else AbsoluteAlignment.CenterRight
            Box(Modifier.align(alignment).width(width).fillMaxHeight()
                .background(MaterialTheme.colorScheme.error, CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_delete), null,
                    Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onError)
            }
        }
        LogoChoiceRow(logo, selected = selected, enabled = enabled, shape = shape.roundedCorners,
            selectionProgress = selectionProgress,
            modifier = Modifier.fillMaxSize().graphicsLayer { translationX = offset }.clipContact(shape),
            onDelete = if (enabled) onAccessibleDelete else null, onClick = onSelect)
    }
}
