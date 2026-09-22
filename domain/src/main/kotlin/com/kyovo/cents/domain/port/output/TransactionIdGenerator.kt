package com.kyovo.cents.domain.port.output

import com.kyovo.cents.domain.model.TransactionId

interface TransactionIdGenerator
{
    fun generate(): TransactionId
}