package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.Project
import com.kyovo.cents.domain.model.ProjectId
import com.kyovo.cents.domain.model.ProjectProgress
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionCategory
import com.kyovo.cents.domain.port.input.GetProjectProgressUseCase
import com.kyovo.cents.domain.port.output.ProjectRepository
import com.kyovo.cents.domain.port.output.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class GetProjectProgressService(
    private val projectRepository: ProjectRepository,
    private val transactionRepository: TransactionRepository
) : GetProjectProgressUseCase
{
    override fun observe(id: ProjectId): Flow<ProjectProgress?>
    {
        return observeAll().map { it[id] }.distinctUntilChanged()
    }

    override fun observeAll(): Flow<Map<ProjectId, ProjectProgress>>
    {
        return combine(projectRepository.observeAll(), transactionRepository.observeAll())
        { projects, transactions ->
            val byProject = transactions.filter { it.projectId != null }.groupBy { it.projectId }
            projects.associate { project -> project.id to progressOf(project, byProject[project.id].orEmpty()) }
        }.distinctUntilChanged()
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
