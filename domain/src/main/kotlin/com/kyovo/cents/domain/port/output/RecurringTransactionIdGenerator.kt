package com.kyovo.cents.domain.port.output

import com.kyovo.cents.domain.model.RecurringTransactionId

interface RecurringTransactionIdGenerator
{
    fun generate(): RecurringTransactionId
}
