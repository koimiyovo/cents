package com.kyovo.cents.ui.budget

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.ColorUtils

/** One slice of the ring and its line of the legend: [percent] is already worded ("45 %"). */
data class PieSlice(val label: String, val cents: Long, val fraction: Float, val percent: String, val color: Int)

/** Everything [drawSpendingPie] paints: the ring of [slices] with [centre] in its hole, and the legend beside it. */
data class PieDiagram(
    val title: String?,
    val footer: String?,
    val centre: String,
    val slices: List<PieSlice>,
    val format: (Long) -> String,
)

/**
 * Draws the spending ring of [diagram] on a plain [Canvas], for the same reason as [drawCashFlow]: one drawing for
 * a PNG's bitmap and a PDF page. The ring is on the left with its total in the middle, the legend on the right: a
 * colour dot, the name, and the amount with its share.
 */
fun drawSpendingPie(canvas: Canvas, width: Float, height: Float, diagram: PieDiagram, style: DiagramStyle)
{
    canvas.drawColor(style.background)
    if (diagram.slices.isEmpty()) return

    val text = style.textSizePx
    val pad = text * 0.9f
    val titleHeight = if (diagram.title != null) text * 2.6f else 0f
    val footerHeight = if (diagram.footer != null) text * 2.2f else 0f
    val top = pad + titleHeight
    val bottom = height - pad - footerHeight
    val available = bottom - top

    val diameter = minOf(width * 0.44f, available)
    val centreX = width * 0.27f
    val centreY = top + available / 2f
    val stroke = diameter * 0.16f
    val oval = RectF(
        centreX - diameter / 2f + stroke / 2f, centreY - diameter / 2f + stroke / 2f,
        centreX + diameter / 2f - stroke / 2f, centreY + diameter / 2f - stroke / 2f,
    )

    val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.BUTT
    }
    val gap = if (diagram.slices.size > 1) 3f else 0f
    var start = -90f
    diagram.slices.forEach { slice ->
        val sweep = slice.fraction * 360f
        arc.color = slice.color
        canvas.drawArc(oval, start + gap / 2f, (sweep - gap).coerceAtLeast(0f), false, arc)
        start += sweep
    }

    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = style.text
        textSize = text
        typeface = Typeface.DEFAULT
    }

    // The total, centred in the hole of the ring, wrapped to leave a margin all round.
    paint.isFakeBoldText = true
    val holeWidth = ((diameter - 2 * stroke) * 0.8f).toInt().coerceAtLeast(1)
    val centreText = StaticLayout.Builder.obtain(diagram.centre, 0, diagram.centre.length, paint, holeWidth)
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .setMaxLines(3)
        .setEllipsize(TextUtils.TruncateAt.END)
        .build()
    canvas.save()
    canvas.translate(centreX - holeWidth / 2f, centreY - centreText.height / 2f)
    centreText.draw(canvas)
    canvas.restore()

    // The legend, one line per slice, centred in the height like the ring.
    paint.isFakeBoldText = false
    val rowHeight = text * 2.3f
    val legendLeft = width * 0.52f
    val legendRight = width - pad
    val legendTop = top + ((available - rowHeight * diagram.slices.size) / 2f).coerceAtLeast(0f)
    val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    val metrics = paint.fontMetrics
    diagram.slices.forEachIndexed { i, slice ->
        val middle = legendTop + rowHeight * i + rowHeight / 2f
        val baseline = middle - (metrics.ascent + metrics.descent) / 2f

        dot.color = slice.color
        canvas.drawCircle(legendLeft + text * 0.4f, middle, text * 0.4f, dot)

        val amount = "${diagram.format(slice.cents)}  ${slice.percent}"
        val amountWidth = paint.measureText(amount)
        paint.color = style.text
        canvas.drawText(amount, legendRight - amountWidth, baseline, paint)

        val nameLeft = legendLeft + text * 1.3f
        val name = TextUtils.ellipsize(slice.label, paint, (legendRight - amountWidth - text - nameLeft).coerceAtLeast(1f), TextUtils.TruncateAt.END)
        canvas.drawText(name, 0, name.length, nameLeft, baseline, paint)
    }

    diagram.footer?.let {
        paint.textSize = text * 0.85f
        paint.color = ColorUtils.setAlphaComponent(style.text, 0x99)
        canvas.drawText(it, pad, height - pad, paint)
    }
    diagram.title?.let {
        paint.color = style.text
        paint.isFakeBoldText = true
        paint.textSize = text * 1.35f
        canvas.drawText(it, pad, pad + text * 1.3f, paint)
    }
}
