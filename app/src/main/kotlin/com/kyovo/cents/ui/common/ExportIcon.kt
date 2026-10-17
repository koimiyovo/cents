package com.kyovo.cents.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.Composable

/**
 * The export icon: an arrow leaving an open square, hand-drawn like the app's other glyphs (chevron, eye). Not
 * the 📤 emoji, whose look changes from one phone to the next — on some it is an archive tray. Decorative: the
 * button that holds it carries the description.
 */
@Composable
internal fun ExportIcon(tint: Color, modifier: Modifier = Modifier)
{
    Canvas(modifier = modifier.decorative()) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // The square, open at the top where the arrow comes out.
        val square = Path().apply {
            moveTo(w * 0.35f, h * 0.46f)
            lineTo(w * 0.20f, h * 0.46f)
            lineTo(w * 0.20f, h * 0.92f)
            lineTo(w * 0.80f, h * 0.92f)
            lineTo(w * 0.80f, h * 0.46f)
            lineTo(w * 0.65f, h * 0.46f)
        }
        drawPath(square, color = tint, style = stroke)

        // The arrow: a shaft and its head.
        drawLine(tint, Offset(w * 0.5f, h * 0.68f), Offset(w * 0.5f, h * 0.10f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        val head = Path().apply {
            moveTo(w * 0.32f, h * 0.28f)
            lineTo(w * 0.5f, h * 0.10f)
            lineTo(w * 0.68f, h * 0.28f)
        }
        drawPath(head, color = tint, style = stroke)
    }
}
