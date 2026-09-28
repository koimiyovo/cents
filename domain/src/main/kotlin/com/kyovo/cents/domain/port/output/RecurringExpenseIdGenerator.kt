package com.kyovo.cents.domain.port.output

import com.kyovo.cents.domain.model.RecurringExpenseId

interface RecurringExpenseIdGenerator
{
    fun generate(): RecurringExpenseId
}
