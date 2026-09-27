package com.kyovo.cents.ui.recurring

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.RecurrenceFrequency
import com.kyovo.cents.domain.model.RecurringExpense
import com.kyovo.cents.domain.model.Subcategory

/** A rule plus what a row needs to say about it: its account's and subcategory's names, resolved once. */
data class RecurringExpenseRow(
    val recurringExpense: RecurringExpense,
    val accountName: String,
    val subcategoryName: String?,
)

/**
 * The rows the management screen lists: every rule, resolved against the accounts and subcategories
 * it points to. [accounts] should include archived ones too — a rule on a closed account is still
 * listed here (only generation skips it), so its name must still resolve.
 */
fun recurringExpenseRows(
    recurringExpenses: List<RecurringExpense>,
    accounts: List<Account>,
    subcategories: List<Subcategory>,
): List<RecurringExpenseRow>
{
    val accountNameById = accounts.associate { it.id to it.name.value }
    val subcategoryNameById = subcategories.associate { it.id to it.name.value }
    return recurringExpenses.map { rule ->
        RecurringExpenseRow(
            recurringExpense = rule,
            accountName = accountNameById[rule.accountId].orEmpty(),
            subcategoryName = rule.subcategoryId?.let { subcategoryNameById[it] },
        )
    }
}

/** Natural French wording for a rule's pace: "Toutes les semaines", "Tous les 3 mois", "Tous les ans"... */
fun recurrenceSummary(frequency: RecurrenceFrequency, interval: Int): String
{
    val (prefix, unit) = when (frequency)
    {
        RecurrenceFrequency.WEEKLY  -> "Toutes les" to "semaines"
        RecurrenceFrequency.MONTHLY -> "Tous les" to "mois"
        RecurrenceFrequency.YEARLY  -> "Tous les" to "ans"
    }
    return if (interval == 1) "$prefix $unit" else "$prefix $interval $unit"
}
