package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecurrenceFrequency
import com.kyovo.cents.domain.model.RecurringTransactionId
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.TransactionDescription
import com.kyovo.cents.domain.model.TransactionTitle
import java.time.LocalDate

/**
 * The account, the category (income or expense) and the start date are not here: changing which account a
 * rule pays out of, whether it is an income or an expense, or when it began, would rewrite already-generated
 * history in a confusing way. Only what applies to occurrences not
 * yet generated is editable — including the frequency and interval, which only change the pace of what
 * comes next.
 */
data class UpdateRecurringTransactionCommand(
    val id: RecurringTransactionId,
    val amount: Money,
    val title: TransactionTitle,
    val subcategoryId: SubcategoryId?,
    val description: TransactionDescription?,
    val frequency: RecurrenceFrequency,
    val interval: Int,
    val endDate: LocalDate?,
)
