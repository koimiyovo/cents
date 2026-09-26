package com.kyovo.cents.ui.budget

import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.port.input.SetBudgetCommand
import com.kyovo.cents.ui.common.acceptsAmountInput
import com.kyovo.cents.ui.common.formatCentsForInput
import com.kyovo.cents.ui.common.parseAmountToCents
import java.time.YearMonth

/**
 * The form that sets the limit of one expense [subcategory] for one [month]: a single amount, kept as raw
 * text (parsed in [submit], like the other amount forms, so a half-typed "12," is never an error while
 * typing). Stricter than the domain about zero: a budget of nothing is refused.
 */
data class BudgetFormState(
    val subcategory: Subcategory,
    val month: YearMonth,
    val limitText: String,
)
{
    companion object
    {
        /**
         * The form for a row of the month shown. When a budget is in force — the month's own or one carried
         * over from an earlier month — its limit is pre-filled, since that is what is being changed;
         * otherwise the field starts empty.
         */
        fun setting(row: BudgetRow, month: YearMonth): BudgetFormState
        {
            return BudgetFormState(
                subcategory = row.subcategory,
                month = month,
                limitText = row.progress?.let { formatCentsForInput(it.limit.value) } ?: "",
            )
        }
    }

    /** What was typed, unless it would leave the shape of an amount (see [acceptsAmountInput]): then nothing changes. */
    fun withLimit(text: String): BudgetFormState
    {
        return if (acceptsAmountInput(text)) copy(limitText = text) else this
    }

    /** Zero is refused (see [parseAmountToCents]): the limit has to be an amount above nothing. */
    fun submit(): BudgetSubmission
    {
        val cents = parseAmountToCents(limitText) ?: return BudgetSubmission.Invalid
        return BudgetSubmission.Set(SetBudgetCommand(subcategory.id, month, Money(cents)))
    }
}

sealed interface BudgetSubmission
{
    data class Set(val command: SetBudgetCommand) : BudgetSubmission

    /** The limit is the only field, so it is the only thing that can be wrong. */
    data object Invalid : BudgetSubmission
}
