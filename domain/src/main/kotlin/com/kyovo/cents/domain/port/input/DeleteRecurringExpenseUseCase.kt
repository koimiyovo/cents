package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.RecurringExpenseId

/**
 * Deletes only the rule: transactions it already generated stay, as ordinary transactions — there is
 * nothing linking them back to it, the same way a deleted subcategory leaves its past transactions be.
 */
interface DeleteRecurringExpenseUseCase
{
    suspend fun delete(id: RecurringExpenseId)
}
