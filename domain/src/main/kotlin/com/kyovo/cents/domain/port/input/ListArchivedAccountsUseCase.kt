package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.Account

interface ListArchivedAccountsUseCase
{
    fun list(): List<Account>
}