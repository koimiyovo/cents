package com.kyovo.cents.domain.port.output

import com.kyovo.cents.domain.model.RecurringTransaction

interface RecurringTransactionNotifier
{
    suspend fun notify(recurringTransaction: RecurringTransaction)
}
