package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.model.ProjectId
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.port.input.ListProjectTransactionsUseCase
import com.kyovo.cents.domain.port.output.AccountRepository
import com.kyovo.cents.domain.port.output.ExchangeRateRepository
import com.kyovo.cents.domain.port.output.ProjectRepository
import com.kyovo.cents.domain.port.output.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class ListProjectTransactionsService(
    projectRepository: ProjectRepository,
    transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    exchangeRateRepository: ExchangeRateRepository
) : ListProjectTransactionsUseCase
{
    private val projectTransactions =
        ProjectTransactions(projectRepository, transactionRepository, accountRepository, exchangeRateRepository)

    override fun observe(id: ProjectId): Flow<List<Transaction>>
    {
        return projectTransactions.observe()
            .map { byProject -> byProject.entries.firstOrNull { it.key.id == id }?.value.orEmpty() }
            .distinctUntilChanged()
    }
}
