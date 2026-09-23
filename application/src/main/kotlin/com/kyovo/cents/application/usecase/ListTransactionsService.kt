package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionCategory
import com.kyovo.cents.domain.port.input.ListTransactionsUseCase
import com.kyovo.cents.domain.port.output.TransactionRepository
import java.time.Instant

class ListTransactionsService(private val transactionRepository: TransactionRepository) :
    ListTransactionsUseCase
{
    override fun list(
        accountId: AccountId?,
        category: TransactionCategory?,
        from: Instant?,
        to: Instant?,
        titleFilter: String
    ): List<Transaction>
    {
        return transactionRepository.findAll()
            .filter { transaction ->
                (accountId == null || transaction.accountId == accountId) &&
                        (category == null || transaction.category == category) &&
                        (from == null || !transaction.date.isBefore(from)) &&
                        (to == null || !transaction.date.isAfter(to)) &&
                        transaction.title.contains(titleFilter)
            }
    }
}