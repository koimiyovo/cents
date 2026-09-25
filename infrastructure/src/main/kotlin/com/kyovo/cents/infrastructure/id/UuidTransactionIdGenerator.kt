package com.kyovo.cents.infrastructure.id

import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.port.output.TransactionIdGenerator
import java.util.UUID

class UuidTransactionIdGenerator : TransactionIdGenerator
{
    override fun generate(): TransactionId
    {
        return TransactionId(UUID.randomUUID())
    }
}
