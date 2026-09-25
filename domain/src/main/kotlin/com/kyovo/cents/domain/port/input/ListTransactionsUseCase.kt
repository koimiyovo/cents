package com.kyovo.cents.domain.port.input

import kotlinx.coroutines.flow.Flow
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.Transaction
import java.time.Instant

interface ListTransactionsUseCase
{
    fun observe(
        accountId: AccountId? = null,
        subcategoryId: SubcategoryId? = null,
        from: Instant? = null,
        to: Instant? = null,
        titleFilter: String = ""
    ): Flow<List<Transaction>>
}
