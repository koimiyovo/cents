package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionId

interface UpdateInitialDepositUseCase
{
    fun update(id: TransactionId, amount: Money): Transaction
}