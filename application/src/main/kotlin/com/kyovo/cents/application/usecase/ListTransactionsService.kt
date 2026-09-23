package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.port.input.ListTransactionsUseCase
import com.kyovo.cents.domain.port.output.TransactionRepository
import java.time.Instant

class ListTransactionsService(private val transactionRepository: TransactionRepository) :
    ListTransactionsUseCase
{
    override fun list(from: Instant?, to: Instant?): List<Transaction>
    {
        return transactionRepository.findAll()
            .filter { transaction ->
                (from == null || !transaction.date.isBefore(from)) &&
                        (to == null || !transaction.date.isAfter(to))
            }
    }
}