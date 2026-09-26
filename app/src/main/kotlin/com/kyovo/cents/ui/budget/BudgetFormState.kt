package com.kyovo.cents.ui.budget

import com.kyovo.cents.domain.model.AlertThreshold
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.port.input.SetBudgetCommand
import com.kyovo.cents.ui.common.acceptsAmountInput
import com.kyovo.cents.ui.common.formatCentsForInput
import com.kyovo.cents.ui.common.parseAmountToCents
import java.time.YearMonth

/**
 * What the alert threshold slider offers, in percent: from half the limit to the limit itself, in steps of
 * five. The domain accepts any whole percentage from 1 to 100; a slider with a hundred positions would be
 * fiddly, and no one needs to tell 61 % from 60 %.
 */
const val THRESHOLD_SLIDER_MIN_PERCENT = 50
const val THRESHOLD_SLIDER_MAX_PERCENT = 100
const val THRESHOLD_SLIDER_STEP_PERCENT = 5

/** The stops *between* the two ends of the slider — what a Compose slider is told: 55, 60, ... 95 is nine. */
const val THRESHOLD_SLIDER_STEPS =
    (THRESHOLD_SLIDER_MAX_PERCENT - THRESHOLD_SLIDER_MIN_PERCENT) / THRESHOLD_SLIDER_STEP_PERCENT - 1

/**
 * The form that sets one expense [subcategory]'s budget for one [month]: the limit, a single amount kept as
 * raw text (parsed in [submit], like the other amount forms, so a half-typed "12," is never an error while
 * typing), and the alert threshold, in percent, moved with a slider. Stricter than the domain about zero: a
 * budget of nothing is refused.
 */
data class BudgetFormState(
    val subcategory: Subcategory,
    val month: YearMonth,
    val limitText: String,
    val thresholdPercent: Int = AlertThreshold.DEFAULT.percent,
)
{
    companion object
    {
        /**
         * The form for a row of the month shown. When a budget is in force — the month's own or one carried
         * over from an earlier month — its limit and its alert threshold are pre-filled, since that is what is
         * being changed; otherwise the field starts empty and the threshold is the default, 80 %.
         */
        fun setting(row: BudgetRow, month: YearMonth): BudgetFormState
        {
            return BudgetFormState(
                subcategory = row.subcategory,
                month = month,
                limitText = row.progress?.let { formatCentsForInput(it.limit.value) } ?: "",
                thresholdPercent = row.progress?.alertThreshold?.percent ?: AlertThreshold.DEFAULT.percent,
            )
        }
    }

    /** What was typed, unless it would leave the shape of an amount (see [acceptsAmountInput]): then nothing changes. */
    fun withLimit(text: String): BudgetFormState
    {
        return if (acceptsAmountInput(text)) copy(limitText = text) else this
    }

    /**
     * The slider moved to [percent]: brought to the nearest step of the slider and kept within its range, so
     * the form only ever holds a position the slider can show.
     */
    fun withThreshold(percent: Int): BudgetFormState
    {
        val step = THRESHOLD_SLIDER_STEP_PERCENT
        val snapped = Math.round(percent.toDouble() / step).toInt() * step
        return copy(thresholdPercent = snapped.coerceIn(THRESHOLD_SLIDER_MIN_PERCENT, THRESHOLD_SLIDER_MAX_PERCENT))
    }

    /** Zero is refused (see [parseAmountToCents]): the limit has to be an amount above nothing. */
    fun submit(): BudgetSubmission
    {
        val cents = parseAmountToCents(limitText) ?: return BudgetSubmission.Invalid
        return BudgetSubmission.Set(
            SetBudgetCommand(subcategory.id, month, Money(cents), AlertThreshold(thresholdPercent))
        )
    }
}

sealed interface BudgetSubmission
{
    data class Set(val command: SetBudgetCommand) : BudgetSubmission

    /** The limit is the only field, so it is the only thing that can be wrong. */
    data object Invalid : BudgetSubmission
}
