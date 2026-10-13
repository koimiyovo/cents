package com.kyovo.cents.domain.port.output

import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.RecurringTransaction

interface RecurringTransactionNotifier
{
    /** [currency] is the one of the rule's account: what its amount is written in. */
    suspend fun notify(recurringTransaction: RecurringTransaction, currency: AccountCurrency)
}
