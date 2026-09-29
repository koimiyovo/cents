package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.RecurringTransaction

interface CreateRecurringTransactionUseCase
{
    suspend fun create(command: CreateRecurringTransactionCommand): RecurringTransaction
}
