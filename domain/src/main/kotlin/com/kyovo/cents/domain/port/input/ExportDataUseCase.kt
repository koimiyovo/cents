package com.kyovo.cents.domain.port.input

interface ExportDataUseCase
{
    suspend fun export(): String
}