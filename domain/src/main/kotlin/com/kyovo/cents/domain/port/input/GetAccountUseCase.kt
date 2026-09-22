package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountId

interface GetAccountUseCase
{
    fun get(id: AccountId): Account?
}