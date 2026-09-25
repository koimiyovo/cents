package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.Account

interface OpenAccountUseCase
{
    suspend fun open(command: OpenAccountCommand): Account
}