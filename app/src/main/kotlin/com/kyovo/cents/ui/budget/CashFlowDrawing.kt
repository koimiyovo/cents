package com.kyovo.cents.ui.budget

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.ColorUtils
import kotlin.math.ceil

/** One box of the diagram, already worded and coloured: drawn as "label: amount", then the [note] if it has one. */
data class DiagramNode(val label: String, val cents: Long, val color: Int, val note: String? = null)

/** Everything [drawCashFlow] paints: a column of [sources], the [potLabel] box they all flow into, a column of [uses]. */
data class Diagram(
    val title: String?,
    /** A small line at the bottom left — where the diagram comes from — or null for none. */
    val footer: String?,
    val sources: List<DiagramNode>,
    val uses: List<DiagramNode>,
    val potLabel: String,
    val total: Long,
    val format: (Long) -> String,
)

/** Colours as ARGB ints and the size of the text in pixels, which everything else is proportioned to. */
data class DiagramStyle(
    val background: Int,
    val text: Int,
    val labelBackground: Int,
    val pot: Int,
    val textSizePx: Float,
)

/**
 * Draws the cash-flow (Sankey) diagram of [diagram] on a plain [Canvas] — the platform's, not Compose's — so
 * that the very same code paints the screen (through `drawIntoCanvas`), a PNG's bitmap, and a PDF page: three
 * outputs, one drawing, and in the PDF the shapes and the text stay vectors. Three columns: the sources on the
 * left, one box in the middle, the uses on the right, joined by bands as thick as the amounts.
 */
fun drawCashFlow(canvas: Canvas, width: Float, height: Float, diagram: Diagram, style: DiagramStyle)
{
    canvas.drawColor(style.background)
    if (diagram.total <= 0L) return

    val text = style.textSizePx
    val pad = text * 0.9f
    val barWidth = text * 0.5f
    val gap = text * 0.45f
    val minNode = text * 0.15f

    val titleHeight = if (diagram.title != null) text * 2.6f else 0f
    val top = pad + titleHeight
    val footerHeight = if (diagram.footer != null) text * 2.2f else 0f
    val bottom = height - pad - footerHeight
    val available = bottom - top

    val rows = maxOf(diagram.sources.size, diagram.uses.size)
    val scale = (available - (rows - 1) * gap).coerceAtLeast(1f) / diagram.total * 0.98f

    val leftX = pad
    val potX = width * 0.47f
    val rightX = width - pad - barWidth

    val sourceTops = columnTops(diagram.sources, scale, gap, minNode, top, available)
    val useTops = columnTops(diagram.uses, scale, gap, minNode, top, available)
    val potHeight = diagram.total * scale
    val potTop = top + (available - potHeight) / 2f

    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val bar = Paint(Paint.ANTI_ALIAS_FLAG)

    // The bands first, so the boxes and the labels sit on top of them.
    var potCursor = potTop
    diagram.sources.forEachIndexed { i, node ->
        val from = sourceTops[i]
        val fromHeight = nodeHeight(node, scale, minNode)
        val bandHeight = node.cents * scale
        fill.color = ColorUtils.setAlphaComponent(node.color, 0x66)
        canvas.drawPath(band(leftX + barWidth, from, fromHeight, potX, potCursor, bandHeight), fill)
        potCursor += bandHeight
    }
    potCursor = potTop
    diagram.uses.forEachIndexed { i, node ->
        val to = useTops[i]
        val toHeight = nodeHeight(node, scale, minNode)
        val bandHeight = node.cents * scale
        fill.color = ColorUtils.setAlphaComponent(node.color, 0x66)
        canvas.drawPath(band(potX + barWidth, potCursor, bandHeight, rightX, to, toHeight), fill)
        potCursor += bandHeight
    }

    diagram.sources.forEachIndexed { i, node ->
        bar.color = node.color
        canvas.drawRect(leftX, sourceTops[i], leftX + barWidth, sourceTops[i] + nodeHeight(node, scale, minNode), bar)
    }
    diagram.uses.forEachIndexed { i, node ->
        bar.color = node.color
        canvas.drawRect(rightX, useTops[i], rightX + barWidth, useTops[i] + nodeHeight(node, scale, minNode), bar)
    }
    bar.color = style.pot
    canvas.drawRect(potX, potTop, potX + barWidth, potTop + potHeight, bar)

    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = style.text
        textSize = text
        typeface = Typeface.DEFAULT
    }
    val labelFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = style.labelBackground }
    val labelGap = text * 0.25f

    // Labels sit on the bands, next to their boxes; a long one wraps onto a second line, and two small boxes
    // in a row would put theirs on top of each other, so each column's labels are pushed apart.
    val sourceLabels = diagram.sources.map { layoutLabel(textOf(it, diagram), paint, potX - leftX - barWidth - text) }
    val sourceCentres = spreadLabels(
        diagram.sources.mapIndexed { i, n -> sourceTops[i] + nodeHeight(n, scale, minNode) / 2f },
        sourceLabels.map { it.height }, labelGap, top, bottom,
    )
    sourceLabels.forEachIndexed { i, label ->
        drawLabel(canvas, paint, labelFill, label, leftX + barWidth + text * 0.4f, sourceCentres[i], alignRight = false)
    }
    val useLabels = diagram.uses.map { layoutLabel(textOf(it, diagram), paint, rightX - potX - barWidth - text) }
    val useCentres = spreadLabels(
        diagram.uses.mapIndexed { i, n -> useTops[i] + nodeHeight(n, scale, minNode) / 2f },
        useLabels.map { it.height }, labelGap, top, bottom,
    )
    useLabels.forEachIndexed { i, label ->
        drawLabel(canvas, paint, labelFill, label, rightX - text * 0.4f, useCentres[i], alignRight = true)
    }
    paint.isFakeBoldText = true
    val potLabel = layoutLabel("${diagram.potLabel}: ${diagram.format(diagram.total)}", paint, width * 0.36f)
    drawLabel(canvas, paint, labelFill, potLabel, potX + barWidth / 2f, potTop - potLabel.height / 2f - text * 0.4f, alignRight = false, centred = true)

    diagram.footer?.let {
        paint.isFakeBoldText = false
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

private fun textOf(node: DiagramNode, diagram: Diagram): String =
    "${node.label}: ${diagram.format(node.cents)}" + (node.note?.let { " $it" } ?: "")

private fun nodeHeight(node: DiagramNode, scale: Float, minNode: Float) = maxOf(node.cents * scale, minNode)

/** The top of each box of a column, stacked with [gap] between them and centred in the [available] height. */
private fun columnTops(nodes: List<DiagramNode>, scale: Float, gap: Float, minNode: Float, top: Float, available: Float): List<Float>
{
    val heights = nodes.map { nodeHeight(it, scale, minNode) }
    val used = heights.sum() + gap * (nodes.size - 1).coerceAtLeast(0)
    var y = top + ((available - used) / 2f).coerceAtLeast(0f)
    return heights.map { h -> y.also { y += h + gap } }
}

/** A curved band from the right edge of one box ([fromX], [fromTop], [fromHeight]) to the left edge of another. */
private fun band(fromX: Float, fromTop: Float, fromHeight: Float, toX: Float, toTop: Float, toHeight: Float): Path
{
    val middle = (fromX + toX) / 2f
    return Path().apply {
        moveTo(fromX, fromTop)
        cubicTo(middle, fromTop, middle, toTop, toX, toTop)
        lineTo(toX, toTop + toHeight)
        cubicTo(middle, toTop + toHeight, middle, fromTop + fromHeight, fromX, fromTop + fromHeight)
        close()
    }
}

/** A label laid out in lines: [layout] is as narrow as its widest line, [width] and [height] are its size. */
private class Label(val layout: StaticLayout, val width: Float)
{
    val height: Float get() = layout.height.toFloat()
}

/** [text] wrapped to fit [maxWidth] (padding included) on at most three lines, the last one ellipsized if need be. */
private fun layoutLabel(text: String, paint: TextPaint, maxWidth: Float): Label
{
    val padding = paint.textSize * 0.3f
    val available = (maxWidth - 2 * padding).toInt().coerceAtLeast(1)
    fun build(width: Int) = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
        .setMaxLines(3)
        .setEllipsize(TextUtils.TruncateAt.END)
        .build()

    val first = build(available)
    val widest = (0 until first.lineCount).maxOf { first.getLineWidth(it) }
    // Laid out again at the width it really takes, so the box around it has no empty margin.
    val tight = ceil(widest).toInt().coerceIn(1, available)
    return Label(build(tight), tight.toFloat())
}

/**
 * Keeps the labels centred on [centres] (top to bottom, each [heights] tall) from touching one another — at least
 * [gap] between two — inside [lo]..[hi], moving each as little as it can.
 */
internal fun spreadLabels(centres: List<Float>, heights: List<Float>, gap: Float, lo: Float, hi: Float): List<Float>
{
    if (centres.isEmpty()) return centres
    fun between(i: Int, j: Int) = (heights[i] + heights[j]) / 2f + gap

    val result = centres.toMutableList()
    result[0] = result[0].coerceAtLeast(lo + heights[0] / 2f)
    for (i in 1 until result.size) result[i] = maxOf(result[i], result[i - 1] + between(i - 1, i))
    val limit = hi - heights.last() / 2f
    if (result.last() > limit)
    {
        result[result.lastIndex] = limit
        for (i in result.lastIndex - 1 downTo 0) result[i] = minOf(result[i], result[i + 1] - between(i, i + 1))
    }
    return result
}

private fun drawLabel(
    canvas: Canvas,
    paint: TextPaint,
    labelFill: Paint,
    label: Label,
    anchorX: Float,
    centreY: Float,
    alignRight: Boolean,
    centred: Boolean = false,
)
{
    val padding = paint.textSize * 0.3f
    val left = when
    {
        centred    -> anchorX - label.width / 2f
        alignRight -> anchorX - label.width
        else       -> anchorX
    }
    val top = centreY - label.height / 2f
    val box = RectF(left - padding, top - padding * 0.4f, left + label.width + padding, top + label.height + padding * 0.4f)
    canvas.drawRoundRect(box, padding, padding, labelFill)
    canvas.save()
    canvas.translate(left, top)
    label.layout.draw(canvas)
    canvas.restore()
}
