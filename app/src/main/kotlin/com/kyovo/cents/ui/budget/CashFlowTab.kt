package com.kyovo.cents.ui.budget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyovo.cents.R
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.ui.common.ExpandableChart
import com.kyovo.cents.ui.common.formatMoney
import com.kyovo.cents.ui.home.AccountsPalette
import com.kyovo.cents.ui.home.LightAccountsPalette
import com.kyovo.cents.ui.settings.LocalMainCurrency

/**
 * The "Flux" tab: the month's income flowing into one pot and out again into the month's spending, as a
 * Sankey diagram, with two buttons to save it as an image or a PDF. All the amounts are in the main
 * currency, like the pie's, and it follows the same month and account filter as the other tabs.
 * [onMessage] is how the outcome of a save reaches the snackbar of the screen above.
 */
@Composable
internal fun BudgetCashFlowTab(
    palette: AccountsPalette,
    state: BudgetsUiState,
    onMessage: (Int) -> Unit,
)
{
    val flow = remember(state.incomeBreakdown, state.breakdown) { cashFlow(state.incomeBreakdown, state.breakdown) }
    if (flow.isEmpty)
    {
        Text(text = stringResource(R.string.cashflow_empty), color = palette.textMuted, fontSize = 14.sp)
        return
    }

    val currency = LocalMainCurrency.current
    val sourceLabels = flow.sources.map { cashFlowLabelText(it.label) }
    val useLabels = flow.uses.map { cashFlowLabelText(it.label) }
    val pot = stringResource(R.string.cashflow_pot)
    val title = stringResource(R.string.cashflow_title, state.selector.label)
    val savingsRate = flow.savingsRatePercent?.let { stringResource(R.string.cashflow_savings_rate, it) }

    val onScreen = remember(flow, sourceLabels, useLabels, pot, savingsRate, palette, currency) {
        cashFlowDiagram(flow, sourceLabels, useLabels, pot, savingsRate, null, null, palette, currency)
    }
    // Saved on white with the light palette whatever the theme is: a diagram to print or to send.
    val footer = stringResource(R.string.cashflow_footer)
    val toSave = remember(flow, sourceLabels, useLabels, pot, savingsRate, title, footer, currency) {
        cashFlowDiagram(flow, sourceLabels, useLabels, pot, savingsRate, title, footer, LightAccountsPalette, currency)
    }

    val rows = maxOf(flow.sources.size, flow.uses.size)
    val textPx = with(LocalDensity.current) { 12.sp.toPx() }
    val style = DiagramStyle(
        background = palette.surface.toArgb(),
        text = palette.textPrimary.toArgb(),
        labelBackground = palette.surface.copy(alpha = 0.8f).toArgb(),
        pot = palette.iconToneGold.toArgb(),
        textSizePx = textPx,
    )
    val saveStyle = DiagramStyle(
        background = Color.White.toArgb(),
        text = LightAccountsPalette.textPrimary.toArgb(),
        labelBackground = Color.White.copy(alpha = 0.8f).toArgb(),
        pot = LightAccountsPalette.iconToneGold.toArgb(),
        textSizePx = textPx,
    )

    // A drawing says nothing to a screen reader: the same figures, as a sentence.
    val incomeWord = stringResource(R.string.cashflow_income)
    val expensesWord = stringResource(R.string.cashflow_expenses)
    val summary = remember(onScreen, incomeWord, expensesWord, currency) {
        fun spoken(nodes: List<DiagramNode>) = nodes.joinToString(", ") { node ->
            "${node.label} ${formatMoney(node.cents, currency)}" + (node.note?.let { " $it" } ?: "")
        }
        "$incomeWord : ${spoken(onScreen.sources)}. $expensesWord : ${spoken(onScreen.uses)}."
    }

    val fileName = "centimes-flux-${state.selector.month}"

    Column(verticalArrangement = Arrangement.spacedBy(16.dp))
    {
        // The same drawing twice: in the screen, and — with more height, so the labels have room — in the
        // popup a tap opens, where it can be zoomed.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End)
        {
            ChartExportMenu(palette, fileName, onMessage) { out, format -> writeCashFlow(out, format, toSave, saveStyle) }
        }
        @Composable
        fun flowCanvas(heightDp: Int)
        {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heightDp.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clearAndSetSemantics { contentDescription = summary },
            ) {
                drawIntoCanvas { drawCashFlow(it.nativeCanvas, size.width, size.height, onScreen, style) }
            }
        }
        ExpandableChart(palette, zoomedContent = { flowCanvas(maxOf(520, rows * 80)) }) {
            flowCanvas(maxOf(300, rows * 46))
        }
    }
}

@Composable
private fun cashFlowLabelText(label: CashFlowLabel): String = when (label)
{
    is CashFlowLabel.Slice -> sliceLabelText(label.label)
    CashFlowLabel.Savings -> stringResource(R.string.cashflow_savings)
    CashFlowLabel.FromSavings -> stringResource(R.string.cashflow_from_savings)
}

/** The flow worded and coloured for [palette]: sources in the income green, uses in the pie's colours. */
private fun cashFlowDiagram(
    flow: CashFlow,
    sourceLabels: List<String>,
    useLabels: List<String>,
    pot: String,
    savingsRate: String?,
    title: String?,
    footer: String?,
    palette: AccountsPalette,
    currency: AccountCurrency,
): Diagram
{
    val sources = flow.sources.mapIndexed { i, node ->
        val color = if (node.label == CashFlowLabel.FromSavings) palette.iconToneGold else palette.heroIncomeAccent
        DiagramNode(sourceLabels[i], node.cents, color.toArgb())
    }
    val uses = flow.uses.mapIndexed { i, node ->
        val color = when (val label = node.label)
        {
            CashFlowLabel.Savings -> palette.iconToneGreen
            CashFlowLabel.FromSavings -> palette.iconToneGold
            is CashFlowLabel.Slice ->
                if (label.label == SpendingSliceLabel.Other) palette.textMuted else palette.chartColors[i % palette.chartColors.size]
        }
        DiagramNode(useLabels[i], node.cents, color.toArgb(), note = savingsRate.takeIf { node.label == CashFlowLabel.Savings })
    }
    return Diagram(title, footer, sources, uses, pot, flow.total) { formatMoney(it, currency) }
}
