package com.kyovo.cents.ui.home

import android.os.SystemClock
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kyovo.cents.domain.model.AccountId
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------------------------
// The arithmetic of dragging a row to another place in a list, kept free of Compose so it can be
// tested: rows have different heights (a description takes a second line), so positions are
// computed from measured heights rather than from a fixed row size.
// ---------------------------------------------------------------------------------------------

/** Where each row starts, from the top of the list, given the rows' heights and the gap between them. */
internal fun rowTops(heights: List<Float>, spacing: Float): List<Float>
{
    val tops = ArrayList<Float>(heights.size)
    var y = 0f
    for (height in heights)
    {
        tops += y
        y += height + spacing
    }
    return tops
}

/**
 * The position row [from] would take if dropped now, having been dragged by [dragOffset] pixels.
 * Dragging down, it goes after every later row whose centre its *bottom edge* has passed; dragging
 * up, before every earlier row whose centre its *top edge* has passed. (Comparing centres instead
 * would leave a tall row unable to reach the end of a list that finishes with a shorter one: kept
 * inside the list, its centre never gets past that row's centre.) Always within the list.
 */
internal fun reorderTargetIndex(heights: List<Float>, spacing: Float, from: Int, dragOffset: Float): Int
{
    val tops = rowTops(heights, spacing)
    fun centre(index: Int) = tops[index] + heights[index] / 2f
    val top = tops[from] + dragOffset
    val bottom = top + heights[from]
    return if (dragOffset >= 0f)
    {
        from + ((from + 1) until heights.size).count { centre(it) < bottom }
    } else
    {
        from - (0 until from).count { centre(it) > top }
    }
}

/** [items] with the item at [from] taken out and put back at [to]. */
internal fun <T> moveItem(items: List<T>, from: Int, to: Int): List<T>
{
    if (from == to) return items
    val moved = items.toMutableList()
    moved.add(to, moved.removeAt(from))
    return moved
}

/**
 * How far row [index] steps aside while row [from] is being dragged towards [to]: the rows it is
 * about to jump over move by its own [extent] (its height plus the gap) the other way, to open the
 * slot it will land in. Every other row stays where it is.
 */
internal fun reorderShift(index: Int, from: Int, to: Int, extent: Float): Float
{
    return when
    {
        index == from                    -> 0f
        from < to && index in (from + 1)..to -> -extent
        to < from && index in to until from  -> extent
        else                             -> 0f
    }
}

/** How far row [from] can be dragged, up (negative) and down, without leaving the list. */
internal fun dragRange(heights: List<Float>, spacing: Float, from: Int): ClosedFloatingPointRange<Float>
{
    val tops = rowTops(heights, spacing)
    val total = tops.last() + heights.last()
    return -tops[from]..(total - tops[from] - heights[from])
}

/**
 * How fast to scroll the screen while a row is held near its top or bottom edge, so a row can be
 * carried past what is visible: 0 in the middle, up to [maxSpeed] (negative = up) right at the
 * edge, growing as the finger goes deeper into the [edge]-wide border zone.
 */
internal fun edgeScrollSpeed(pointerY: Float, top: Float, bottom: Float, edge: Float, maxSpeed: Float): Float
{
    return when
    {
        pointerY < top + edge    -> -maxSpeed * ((top + edge - pointerY) / edge).coerceIn(0f, 1f)
        pointerY > bottom - edge -> maxSpeed * ((pointerY - (bottom - edge)) / edge).coerceIn(0f, 1f)
        else                     -> 0f
    }
}

// ---------------------------------------------------------------------------------------------
// The state and the composable
// ---------------------------------------------------------------------------------------------

internal enum class ReorderPhase
{
    /** Nothing is being dragged. */
    Idle,

    /** A row follows the finger; the others step aside. */
    Dragging,

    /** The finger is up: the dropped row glides into its slot. */
    Settling,
}

/** What the rows of one list share while one of them is being dragged. */
@Stable
internal class AccountReorderState
{
    var phase by mutableStateOf(ReorderPhase.Idle)
    var draggedId by mutableStateOf<AccountId?>(null)

    /** How far the dragged row has moved from its slot, in pixels. */
    var dragOffset by mutableFloatStateOf(0f)

    /** Where the finger is on screen, and the visible area of the scrolling list: for auto-scroll. */
    var pointerRootY by mutableFloatStateOf(0f)
    var viewportTop by mutableFloatStateOf(0f)
    var viewportBottom by mutableFloatStateOf(0f)

    /** Measured height of every row, by account. */
    val heights = mutableStateMapOf<AccountId, Int>()

    private var clicksSuppressedUntil = 0L

    /**
     * A drag ends with the finger lifting, and the row under it would take that for a tap. Set
     * while dragging and shortly after, so the drop never opens the account.
     */
    fun suppressClicks(forMillis: Long? = null)
    {
        clicksSuppressedUntil = if (forMillis == null) Long.MAX_VALUE else SystemClock.uptimeMillis() + forMillis
    }

    val clicksSuppressed: Boolean get() = SystemClock.uptimeMillis() < clicksSuppressedUntil

    fun finish()
    {
        draggedId = null
        phase = ReorderPhase.Idle
        dragOffset = 0f
    }
}

/**
 * Makes one row of a list draggable: a long press picks it up (with a tick under the finger), then
 * it follows the finger vertically while the other rows step aside; letting go drops it where it
 * is. [onReorder] receives the whole new order of [ids].
 *
 * Views used `ItemTouchHelper` on a RecyclerView for this. Here the list is a plain `Column`, so
 * the movement is done by hand with `graphicsLayer` (which moves a row on screen without
 * re-laying-out the list, cheap enough to do on every pointer event).
 */
@Composable
internal fun ReorderableRow(
    state: AccountReorderState,
    ids: List<AccountId>,
    index: Int,
    scrollState: ScrollState,
    spacing: Dp,
    onReorder: (List<AccountId>) -> Unit,
    onDragStart: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
)
{
    val id = ids[index]
    val density = LocalDensity.current
    val spacingPx = with(density) { spacing.toPx() }
    val edgePx = with(density) { 72.dp.toPx() }
    val maxScrollSpeed = with(density) { 14.dp.toPx() }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    // The gesture below is set up once per row, so it must read the latest values through these.
    val currentIds by rememberUpdatedState(ids)
    val currentOnReorder by rememberUpdatedState(onReorder)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var scrollJob by remember { mutableStateOf<Job?>(null) }

    fun heightsOf(list: List<AccountId>): List<Float> = list.map { (state.heights[it] ?: 0).toFloat() }

    val isDragged = state.draggedId == id
    val dragging = state.phase == ReorderPhase.Dragging
    // The rows around the dragged one step aside; eased, or they would jump as it passes them.
    val shiftTarget = run {
        val draggedId = state.draggedId
        if (draggedId == null || isDragged || !dragging) 0f
        else
        {
            val from = ids.indexOf(draggedId)
            val heights = heightsOf(ids)
            val to = reorderTargetIndex(heights, spacingPx, from, state.dragOffset)
            reorderShift(index, from, to, heights[from] + spacingPx)
        }
    }
    val animatedShift by animateFloatAsState(shiftTarget, tween(durationMillis = 120), label = "reorderShift")
    // Once dropped, the list is already in its new order and every row sits where it belongs, so
    // their shifts must vanish at once, not ease out.
    val translationY = when
    {
        isDragged -> state.dragOffset
        dragging  -> animatedShift
        else      -> 0f
    }

    fun clampedOffset(offset: Float): Float
    {
        val list = currentIds
        val from = list.indexOf(id)
        if (from < 0) return offset
        val range = dragRange(heightsOf(list), spacingPx, from)
        return offset.coerceIn(range.start, range.endInclusive)
    }

    fun drop(cancelled: Boolean)
    {
        scrollJob?.cancel()
        state.suppressClicks(forMillis = 250)
        val list = currentIds
        val from = list.indexOf(id)
        if (from < 0 || state.draggedId != id)
        {
            state.finish()
            return
        }
        val heights = heightsOf(list)
        val to = if (cancelled) from else reorderTargetIndex(heights, spacingPx, from, state.dragOffset)
        state.phase = ReorderPhase.Settling
        if (to != from)
        {
            // The list flips to its new order in this same frame; the dragged row is re-based on
            // its new slot so that it stays exactly under the finger, then glides home.
            val visualTop = rowTops(heights, spacingPx)[from] + state.dragOffset
            val newTop = rowTops(moveItem(heights, from, to), spacingPx)[to]
            state.dragOffset = visualTop - newTop
            currentOnReorder(moveItem(list, from, to))
        }
        scope.launch {
            animate(state.dragOffset, 0f, animationSpec = tween(durationMillis = 160)) { value, _ ->
                state.dragOffset = value
            }
            state.finish()
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { state.heights[id] = it.height }
            .onGloballyPositioned { coordinates = it }
            .zIndex(if (isDragged) 1f else 0f)
            // Before graphicsLayer on purpose: the gesture must be measured in the row's resting
            // frame. Inside the moving layer it would see the finger standing still relative to a
            // row that follows it, and get no movement at all.
            .pointerInput(id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        if (currentIds.size < 2) return@detectDragGesturesAfterLongPress
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        state.suppressClicks()
                        state.draggedId = id
                        state.dragOffset = 0f
                        state.phase = ReorderPhase.Dragging
                        coordinates?.let { state.pointerRootY = it.localToRoot(offset).y }
                        currentOnDragStart()
                        scrollJob = scope.launch {
                            while (true)
                            {
                                val speed = edgeScrollSpeed(
                                    state.pointerRootY, state.viewportTop, state.viewportBottom, edgePx, maxScrollSpeed,
                                )
                                if (speed != 0f)
                                {
                                    // The list moves under the finger, so the row must move with it.
                                    val scrolled = scrollState.scrollBy(speed)
                                    state.dragOffset = clampedOffset(state.dragOffset + scrolled)
                                }
                                delay(16)
                            }
                        }
                    },
                    onDrag = { change, dragAmount ->
                        if (state.draggedId == id)
                        {
                            change.consume()
                            state.dragOffset = clampedOffset(state.dragOffset + dragAmount.y)
                            coordinates?.let { state.pointerRootY = it.localToRoot(change.position).y }
                        }
                    },
                    onDragEnd = { drop(cancelled = false) },
                    onDragCancel = { drop(cancelled = true) },
                )
            }
            .graphicsLayer {
                this.translationY = translationY
                if (isDragged)
                {
                    scaleX = 1.03f
                    scaleY = 1.03f
                }
            }
            // Lifted off the page while held, so it reads as "in the hand".
            .then(if (isDragged) Modifier.shadow(12.dp, RoundedCornerShape(18.dp)) else Modifier),
    ) {
        content()
    }
}
