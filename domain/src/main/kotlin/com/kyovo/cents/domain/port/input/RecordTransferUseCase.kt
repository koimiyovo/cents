package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.TransferResult

interface RecordTransferUseCase
{
    suspend fun record(command: RecordTransferCommand): TransferResult
}