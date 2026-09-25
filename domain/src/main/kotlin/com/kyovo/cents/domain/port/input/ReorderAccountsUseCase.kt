package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.AccountId

interface ReorderAccountsUseCase
{
    fun reorder(orderedIds: List<AccountId>)
}
