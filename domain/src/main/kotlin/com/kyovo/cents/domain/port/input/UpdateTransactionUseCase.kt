package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.Transaction

interface UpdateTransactionUseCase
{
    suspend fun update(command: UpdateTransactionCommand): Transaction
}