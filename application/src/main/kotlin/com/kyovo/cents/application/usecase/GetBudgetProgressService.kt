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
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import java.time.ZoneId

class GetBudgetProgressService(
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository,
    private val zone: ZoneId
) : GetBudgetProgressUseCase
{
    // One subcategory's progress is read out of the month's: the rules (which budget is in force, what a
    // month is, what counts as spent) are written once, in `observeAll`. `distinctUntilChanged` keeps a
    // page from redrawing for a change that does not concern its subcategory.
    override fun observe(subcategoryId: SubcategoryId, month: YearMonth): Flow<BudgetProgress?>
    {
        return observeAll(month)
            .map { progress -> progress[subcategoryId] }
            .distinctUntilChanged()
    }

    // Follows both the budgets and the transactions: the progress moves when a limit is set or changed,
    // and when an expense is added, edited or deleted.
    override fun observeAll(month: YearMonth): Flow<Map<SubcategoryId, BudgetProgress>>
    {
        // A month runs from midnight on its 1st to midnight on the next month's 1st, where the user lives.
        val start = month.atDay(1).atStartOfDay(zone).toInstant()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant()

        return combine(budgetRepository.observeAll(), transactionRepository.observeAll())
        { budgets, transactions ->
            val spentBySubcategory = transactions
                .filter {
                    it.category == TransactionCategory.EXPENSE &&
                            it.subcategoryId != null &&
                            !it.date.isBefore(start) &&
                            it.date.isBefore(end)
                }
                .groupBy { it.subcategoryId!! }
                .mapValues { (_, expenses) -> expenses.sumOf { it.amount.value } }

            // The budget in force of each subcategory: the month's own, else the most recent earlier one.
            // Never a later one, so a subcategory whose first budget comes after the month is not listed.
            budgets
                .filter { it.month <= month }
                .groupBy { it.subcategoryId }
                .mapValues { (_, ofSubcategory) -> ofSubcategory.maxBy { it.month } }
                .mapValues { (subcategoryId, budget) ->
                    BudgetProgress(budget.limit, Money(spentBySubcategory[subcategoryId] ?: 0))
                }
        }.distinctUntilChanged()
    }
}
