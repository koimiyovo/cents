package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.RecurringTransaction

interface UpdateRecurringTransactionUseCase
{
    suspend fun update(command: UpdateRecurringTransactionCommand): RecurringTransaction
}
