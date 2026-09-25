package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.AccountId

interface DeleteAccountUseCase
{
    suspend fun delete(id: AccountId, deleteTransactions: Boolean = false)
}