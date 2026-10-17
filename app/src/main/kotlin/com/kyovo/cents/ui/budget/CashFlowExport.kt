package com.kyovo.cents.ui.budget

import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import java.io.OutputStream

/** The two formats the diagram can be saved in. */
enum class CashFlowFormat(val mimeType: String, val extension: String)
{
    PNG("image/png", "png"),
    PDF("application/pdf", "pdf"),
}

private const val PNG_WIDTH = 1800
private const val PDF_WIDTH = 1200

/** Text is 2 % of the width, whatever the format: a PNG and a PDF page look alike, only their resolution differs. */
private fun textSizeFor(width: Int) = width * 0.02f

/** Tall enough for [rows] boxes with a label each, and never flatter than a landscape sheet. */
private fun heightFor(width: Int, rows: Int): Int = maxOf(width * 0.5f, width * (0.12f + 0.06f * rows)).toInt()

/**
 * Writes a chart to [out] as a PNG or a one-page PDF, the chart being whatever [draw] paints on the canvas it is
 * given (with its width, its height and the text size to use, all in pixels). [rows] is how many boxes or lines
 * the chart has: it sets the page's height. Always on a white page, so that an export made in dark mode is still
 * fit to print. The PDF is built with the platform's `PdfDocument` (no library): its page is the canvas [draw]
 * paints on, so shapes and text stay vectors there. The caller closes [out].
 */
fun writeChart(out: OutputStream, format: CashFlowFormat, rows: Int, draw: (android.graphics.Canvas, Float, Float, Float) -> Unit)
{
    val width = if (format == CashFlowFormat.PNG) PNG_WIDTH else PDF_WIDTH
    val height = heightFor(width, rows)
    val textSize = textSizeFor(width)

    when (format)
    {
        CashFlowFormat.PNG ->
        {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try
            {
                draw(android.graphics.Canvas(bitmap), width.toFloat(), height.toFloat(), textSize)
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "The PNG could not be written" }
            } finally
            {
                bitmap.recycle()
            }
        }

        CashFlowFormat.PDF ->
        {
            val document = PdfDocument()
            try
            {
                val page = document.startPage(PdfDocument.PageInfo.Builder(width, height, 1).create())
                draw(page.canvas, width.toFloat(), height.toFloat(), textSize)
                document.finishPage(page)
                document.writeTo(out)
            } finally
            {
                document.close()
            }
        }
    }
}

/** The cash-flow [diagram] saved with [writeChart], in [style]'s colours. */
fun writeCashFlow(out: OutputStream, format: CashFlowFormat, diagram: Diagram, style: DiagramStyle)
{
    writeChart(out, format, maxOf(diagram.sources.size, diagram.uses.size)) { canvas, width, height, textSize ->
        drawCashFlow(canvas, width, height, diagram, style.copy(textSizePx = textSize))
    }
}

/** The spending ring [diagram] saved with [writeChart], in [style]'s colours. */
fun writeSpendingPie(out: OutputStream, format: CashFlowFormat, diagram: PieDiagram, style: DiagramStyle)
{
    writeChart(out, format, diagram.slices.size) { canvas, width, height, textSize ->
        drawSpendingPie(canvas, width, height, diagram, style.copy(textSizePx = textSize))
    }
}
