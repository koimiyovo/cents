package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.AccountBalance
import com.kyovo.cents.domain.model.AccountId

interface GetAccountBalanceUseCase
{
    fun getBalance(accountId: AccountId): AccountBalance?
}