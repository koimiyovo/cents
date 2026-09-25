package com.kyovo.cents.infrastructure.id

import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.port.output.TransactionIdGenerator
import kotlin.uuid.Uuid

class UuidTransactionIdGenerator : TransactionIdGenerator
{
    override fun generate(): TransactionId
    {
        return TransactionId(Uuid.random())
    }
}
