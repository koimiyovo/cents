package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.Account

interface ListAccountsUseCase
{
    fun list(nameFilter: String = ""): List<Account>
}