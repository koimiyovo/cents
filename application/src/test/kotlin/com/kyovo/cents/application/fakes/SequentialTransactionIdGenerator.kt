package com.kyovo.cents.application.fakes

import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.port.output.TransactionIdGenerator

class SequentialTransactionIdGenerator(private val ids: List<TransactionId>) : TransactionIdGenerator
{
    private var index = 0

    override fun generate(): TransactionId
    {
        return ids[index++]
    }
}
