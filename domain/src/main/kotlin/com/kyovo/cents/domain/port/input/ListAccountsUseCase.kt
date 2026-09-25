package com.kyovo.cents.domain.port.input

import kotlinx.coroutines.flow.Flow
import com.kyovo.cents.domain.model.Account

interface ListAccountsUseCase
{
    fun observe(nameFilter: String = ""): Flow<List<Account>>
}