package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.model.BudgetProgress
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.TransactionCategory
import com.kyovo.cents.domain.port.input.GetBudgetProgressUseCase
import com.kyovo.cents.domain.port.output.BudgetRepository
import com.kyovo.cents.domain.port.output.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.YearMonth
import java.time.ZoneId

class GetBudgetProgressService(
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository,
    private val zone: ZoneId
) : GetBudgetProgressUseCase
{
    // Follows both the budgets and the transactions: the progress moves when a limit is set or changed,
    // and when an expense is added, edited or deleted. `distinctUntilChanged` keeps a page from redrawing
    // for a change that does not concern its subcategory.
    override fun observe(subcategoryId: SubcategoryId, month: YearMonth): Flow<BudgetProgress?>
    {
        // A month runs from midnight on its 1st to midnight on the next month's 1st, where the user lives.
        val start = month.atDay(1).atStartOfDay(zone).toInstant()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant()

        return combine(budgetRepository.observeAll(), transactionRepository.observeAll())
        { budgets, transactions ->
            // The budget in force: the month's own, else the most recent earlier one. Never a later one.
            val budget = budgets
                .filter { it.subcategoryId == subcategoryId && it.month <= month }
                .maxByOrNull { it.month }
                ?: return@combine null

            val spent = transactions
                .filter {
                    it.category == TransactionCategory.EXPENSE &&
                            it.subcategoryId == subcategoryId &&
                            !it.date.isBefore(start) &&
                            it.date.isBefore(end)
                }
                .sumOf { it.amount.value }

            BudgetProgress(budget.limit, Money(spent))
        }.distinctUntilChanged()
    }
}
