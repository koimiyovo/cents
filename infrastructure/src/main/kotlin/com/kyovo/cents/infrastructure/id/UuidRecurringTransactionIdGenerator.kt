package com.kyovo.cents.infrastructure.id

import com.kyovo.cents.domain.model.RecurringTransactionId
import com.kyovo.cents.domain.port.output.RecurringTransactionIdGenerator
import java.util.UUID

class UuidRecurringTransactionIdGenerator : RecurringTransactionIdGenerator
{
    override fun generate(): RecurringTransactionId
    {
        return RecurringTransactionId(UUID.randomUUID())
    }
}
