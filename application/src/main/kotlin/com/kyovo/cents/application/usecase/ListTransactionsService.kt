package com.kyovo.cents.application.usecase

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.port.input.ListTransactionsUseCase
import com.kyovo.cents.domain.port.output.TransactionRepository
import java.time.Instant

class ListTransactionsService(private val transactionRepository: TransactionRepository) :
    ListTransactionsUseCase
{
    override fun observe(
        accountId: AccountId?,
        subcategoryId: SubcategoryId?,
        from: Instant?,
        to: Instant?,
        titleFilter: String
    ): Flow<List<Transaction>>
    {
        return transactionRepository.observeAll()
            .map { transactions ->
                transactions.filter { transaction ->
                    (accountId == null || transaction.accountId == accountId) &&
                            (subcategoryId == null || transaction.subcategoryId == subcategoryId) &&
                            (from == null || !transaction.date.isBefore(from)) &&
                            (to == null || !transaction.date.isAfter(to)) &&
                            transaction.title.contains(titleFilter)
                }
            }
            .distinctUntilChanged()
    }
}
