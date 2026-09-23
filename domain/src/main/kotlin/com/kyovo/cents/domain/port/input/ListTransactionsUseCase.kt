package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionCategory
import java.time.Instant

interface ListTransactionsUseCase
{
    fun list(
        accountId: AccountId? = null,
        category: TransactionCategory? = null,
        from: Instant? = null,
        to: Instant? = null,
        titleFilter: String = ""
    ): List<Transaction>
}