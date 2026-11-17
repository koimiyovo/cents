package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.Project
import com.kyovo.cents.domain.model.ProjectId
import com.kyovo.cents.domain.model.ProjectProgress
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionCategory
import com.kyovo.cents.domain.port.input.GetProjectProgressUseCase
import com.kyovo.cents.domain.port.output.AccountRepository
import com.kyovo.cents.domain.port.output.ExchangeRateRepository
import com.kyovo.cents.domain.port.output.ProjectRepository
import com.kyovo.cents.domain.port.output.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * What each project has cost against its target, both in the project's currency: the transactions of an account in
 * another currency are converted (see [ProjectTransactions]), so a project can gather spending from any account.
 */
class GetProjectProgressService(
    projectRepository: ProjectRepository,
    transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    exchangeRateRepository: ExchangeRateRepository
) : GetProjectProgressUseCase
{
    private val projectTransactions =
        ProjectTransactions(projectRepository, transactionRepository, accountRepository, exchangeRateRepository)

    override fun observe(id: ProjectId): Flow<ProjectProgress?>
    {
        return observeAll().map { it[id] }.distinctUntilChanged()
    }

    override fun observeAll(): Flow<Map<ProjectId, ProjectProgress>>
    {
        return projectTransactions.observe()
            .map { byProject -> byProject.entries.associate { (project, transactions) -> project.id to progressOf(project, transactions) } }
            .distinctUntilChanged()
    }

    private fun progressOf(project: Project, transactions: List<Transaction>): ProjectProgress
    {
        fun total(category: TransactionCategory): Money =
            Money(transactions.filter { it.category == category }.sumOf { it.amount.value })

        return ProjectProgress(
            target = project.target,
            expenses = total(TransactionCategory.EXPENSE),
            incomes = total(TransactionCategory.INCOME),
            transactionCount = transactions.size,
            alertThreshold = project.alertThreshold,
        )
    }
}
