package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.AccountId

interface ReorderAccountsUseCase
{
    suspend fun reorder(orderedIds: List<AccountId>)
}
