package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.Transaction

interface RecordTransactionUseCase
{
    fun record(command: RecordTransactionCommand): Transaction
}