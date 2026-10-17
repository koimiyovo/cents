package com.kyovo.cents.ui.budget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import com.kyovo.cents.R
import com.kyovo.cents.ui.common.formatMoney
import com.kyovo.cents.ui.home.AccountsPalette
import com.kyovo.cents.ui.home.LightAccountsPalette
import com.kyovo.cents.ui.settings.LocalMainCurrency
import kotlin.math.roundToInt

/**
 * The export icon of a spending ring: the same ring and legend as on the screen, saved with [title] on
 * top, on white with the light palette whatever the theme is — a diagram to print or to send. [onMessage] is how
 * the outcome reaches the snackbar of the screen above.
 */
@Composable
internal fun SpendingPieExportMenu(
    palette: AccountsPalette,
    breakdown: SpendingBreakdown,
    title: String,
    fileName: String,
    onMessage: (Int) -> Unit,
)
{
    val currency = LocalMainCurrency.current
    val footer = stringResource(R.string.cashflow_footer)
    val centre = stringResource(R.string.budget_overview_total_spent, formatMoney(breakdown.total.value, currency))
    val labels = breakdown.slices.map { sliceLabelText(it.label) }
    val percents = breakdown.slices.map { stringResource(R.string.budget_overview_slice_percent, (it.fraction * 100).roundToInt()) }

    val diagram = remember(breakdown, labels, percents, title, footer, centre, currency) {
        PieDiagram(
            title = title,
            footer = footer,
            centre = centre,
            slices = breakdown.slices.mapIndexed { i, slice ->
                PieSlice(labels[i], slice.amount.value, slice.fraction, percents[i], sliceColor(LightAccountsPalette, i, slice.label).toArgb())
            },
            format = { formatMoney(it, currency) },
        )
    }
    val style = DiagramStyle(
        background = Color.White.toArgb(),
        text = LightAccountsPalette.textPrimary.toArgb(),
        labelBackground = Color.White.toArgb(),
        pot = LightAccountsPalette.iconToneGold.toArgb(),
        textSizePx = 0f,
    )
    ChartExportMenu(palette, fileName, onMessage) { out, format -> writeSpendingPie(out, format, diagram, style) }
}
