package com.kyovo.cents.application.fakes

import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.port.output.TransactionIdGenerator

class FixedTransactionIdGenerator(private val id: TransactionId) : TransactionIdGenerator
{
    override fun generate(): TransactionId = id
}
