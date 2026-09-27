package com.kyovo.cents.infrastructure.id

import com.kyovo.cents.domain.model.RecurringExpenseId
import com.kyovo.cents.domain.port.output.RecurringExpenseIdGenerator
import java.util.UUID

class UuidRecurringExpenseIdGenerator : RecurringExpenseIdGenerator
{
    override fun generate(): RecurringExpenseId
    {
        return RecurringExpenseId(UUID.randomUUID())
    }
}
