package com.kyovo.cents.domain.port.output

import com.kyovo.cents.domain.model.RecurringExpense

interface RecurringExpenseNotifier
{
    suspend fun notify(recurringExpense: RecurringExpense)
}
