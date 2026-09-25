package com.kyovo.cents.domain.port.input

import kotlinx.coroutines.flow.Flow
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountId

interface GetAccountUseCase
{
    suspend fun get(id: AccountId): Account?

    fun observe(id: AccountId): Flow<Account?>
}