package com.kyovo.cents.domain.port.input

import kotlinx.coroutines.flow.Flow
import com.kyovo.cents.domain.model.Account

interface ListArchivedAccountsUseCase
{
    fun observe(): Flow<List<Account>>
}