package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.Transaction
import java.time.Instant

interface ListTransactionsUseCase
{
    fun list(
        accountId: AccountId? = null,
        subcategoryId: SubcategoryId? = null,
        from: Instant? = null,
        to: Instant? = null,
        titleFilter: String = ""
    ): List<Transaction>
}
