package com.kyovo.cents.domain.port.input

interface ExportTransactionsCsvUseCase
{
    /** Every transaction as CSV text, for a spreadsheet: one line each, oldest first. */
    suspend fun export(): String
}
