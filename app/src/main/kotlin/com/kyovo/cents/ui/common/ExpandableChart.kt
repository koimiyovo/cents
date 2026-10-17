package com.kyovo.cents.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kyovo.cents.R
import com.kyovo.cents.ui.home.AccountsPalette

private const val MAX_ZOOM = 6f
private const val DOUBLE_TAP_ZOOM = 2.5f

/**
 * A chart that opens, when tapped, in a full-screen popup where it can be zoomed (pinch, or a double tap) and
 * moved around. [content] is what sits in the screen; [zoomedContent] is what the popup shows — often the same
 * chart with its legend, or given more room.
 */
@Composable
internal fun ExpandableChart(
    palette: AccountsPalette,
    zoomedContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
)
{
    var open by rememberSaveable { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClickLabel = stringResource(R.string.chart_expand), role = Role.Button) { open = true },
    ) {
        content()
    }
    if (open) ZoomDialog(palette, onDismiss = { open = false }, zoomedContent)
}

@Composable
private fun ZoomDialog(palette: AccountsPalette, onDismiss: () -> Unit, content: @Composable () -> Unit)
{
    val closeLabel = stringResource(R.string.chart_close)
    // Full screen: the platform's default would leave margins around the chart.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(palette.background).systemBarsPadding())
        {
            ZoomableBox(modifier = Modifier.fillMaxSize().padding(top = 56.dp, bottom = 40.dp), content = content)
            Text(
                text = stringResource(R.string.chart_zoom_hint),
                color = palette.textMuted,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(palette.surface)
                    .clickable(role = Role.Button, onClick = onDismiss)
                    .semantics { contentDescription = closeLabel },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "✕", color = palette.textPrimary, fontSize = 18.sp, modifier = Modifier.decorative())
            }
        }
    }
}

/**
 * Lets [content] be pinched, dragged and double-tapped: scaled between 1× and [MAX_ZOOM]×, never moved far
 * enough to show anything beyond its edges. The zoom keeps the point under the fingers where it is. A
 * `graphicsLayer` scales the drawing itself, so a vector chart stays sharp at every zoom.
 */
@Composable
private fun ZoomableBox(modifier: Modifier, content: @Composable () -> Unit)
{
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    fun clamped(wanted: Offset, atScale: Float): Offset
    {
        val maxX = size.width * (atScale - 1f) / 2f
        val maxY = size.height * (atScale - 1f) / 2f
        return Offset(wanted.x.coerceIn(-maxX, maxX), wanted.y.coerceIn(-maxY, maxY))
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { tap ->
                    if (scale > 1f)
                    {
                        scale = 1f
                        offset = Offset.Zero
                    } else
                    {
                        val centre = Offset(size.width / 2f, size.height / 2f)
                        scale = DOUBLE_TAP_ZOOM
                        offset = clamped((centre - tap) * (DOUBLE_TAP_ZOOM - 1f), DOUBLE_TAP_ZOOM)
                    }
                })
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                    val applied = newScale / scale
                    val centre = Offset(size.width / 2f, size.height / 2f)
                    offset = clamped(offset * applied + (centroid - centre) * (1f - applied) + pan, newScale)
                    scale = newScale
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
        ) {
            content()
        }
    }
}
