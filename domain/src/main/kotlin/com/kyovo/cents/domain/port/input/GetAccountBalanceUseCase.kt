package com.kyovo.cents.domain.port.input

import kotlinx.coroutines.flow.Flow
import com.kyovo.cents.domain.model.AccountBalance
import com.kyovo.cents.domain.model.AccountId

interface GetAccountBalanceUseCase
{
    /** The balance of one account, null while there is no such account. */
    fun observe(accountId: AccountId): Flow<AccountBalance?>

    /** The balance of every existing account (archived ones and empty ones included). */
    fun observeAll(): Flow<Map<AccountId, AccountBalance>>
}