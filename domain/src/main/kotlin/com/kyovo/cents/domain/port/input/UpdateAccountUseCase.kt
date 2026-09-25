package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.Account

interface UpdateAccountUseCase
{
    suspend fun update(command: UpdateAccountCommand): Account
}