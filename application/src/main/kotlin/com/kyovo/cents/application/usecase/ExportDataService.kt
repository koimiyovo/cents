package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.model.BackupSnapshot
import com.kyovo.cents.domain.port.input.ExportDataUseCase
import com.kyovo.cents.domain.port.output.AccountRepository
import com.kyovo.cents.domain.port.output.BackupSerializer
import com.kyovo.cents.domain.port.output.BudgetCalendarRepository
import com.kyovo.cents.domain.port.output.BudgetRepository
import com.kyovo.cents.domain.port.output.ProjectRepository
import com.kyovo.cents.domain.port.output.RecurringTransactionRepository
import com.kyovo.cents.domain.port.output.SubcategoryRepository
import com.kyovo.cents.domain.port.output.TransactionRepository
import com.kyovo.cents.domain.port.output.UnitOfWork
import kotlinx.coroutines.flow.first

class ExportDataService(
    private val accountRepository: AccountRepository,
    private val subcategoryRepository: SubcategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val budgetRepository: BudgetRepository,
    private val budgetCalendarRepository: BudgetCalendarRepository,
    private val recurringTransactionRepository: RecurringTransactionRepository,
    private val projectRepository: ProjectRepository,
    private val unitOfWork: UnitOfWork,
    private val backupSerializer: BackupSerializer
) : ExportDataUseCase
{
    override suspend fun export(): String
    {
        return unitOfWork.execute {
            backupSerializer.serialize(
                BackupSnapshot(
                    accounts = accountRepository.findAll(),
                    subcategories = subcategoryRepository.findAll(),
                    transactions = transactionRepository.findAll(),
                    budgets = budgetRepository.observeAll().first(),
                    budgetCalendar = budgetCalendarRepository.observe().first(),
                    recurringTransactions = recurringTransactionRepository.findAll(),
                    projects = projectRepository.findAll()
                )
            )
        }
    }
}