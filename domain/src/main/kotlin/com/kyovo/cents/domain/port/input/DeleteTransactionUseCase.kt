package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.TransactionId

interface DeleteTransactionUseCase
{
    fun delete(id: TransactionId)
}