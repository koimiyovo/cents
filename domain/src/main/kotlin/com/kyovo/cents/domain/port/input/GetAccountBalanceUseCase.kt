package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.AccountBalance
import com.kyovo.cents.domain.model.AccountId

interface GetAccountBalanceUseCase
{
    suspend fun getBalance(accountId: AccountId): AccountBalance?
}