package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.Transaction
import java.time.Instant

interface ListTransactionsUseCase
{
    fun list(from: Instant? = null, to: Instant? = null): List<Transaction>
}