package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.BudgetProgress
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.TransactionCategory
import com.kyovo.cents.domain.port.input.GetBudgetProgressUseCase
import com.kyovo.cents.domain.port.output.BudgetCalendarRepository
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
    private val calendarRepository: BudgetCalendarRepository,
    private val zone: ZoneId
) : GetBudgetProgressUseCase
{
    // One subcategory's progress is read out of the month's: the rules (which budget is in force, what a
    // month is, what counts as spent) are written once, in `observeAll`. `distinctUntilChanged` keeps a
    // page from redrawing for a change that does not concern its subcategory.
    override fun observe(subcategoryId: SubcategoryId, month: YearMonth, accountId: AccountId?): Flow<BudgetProgress?>
    {
        return observeAll(month, accountId)
            .map { progress -> progress[subcategoryId] }
            .distinctUntilChanged()
    }

    // Follows both the budgets and the transactions: the progress moves when a limit is set or changed,
    // and when an expense is added, edited or deleted.
    override fun observeAll(month: YearMonth, accountId: AccountId?): Flow<Map<SubcategoryId, BudgetProgress>>
    {
        return combine(
            budgetRepository.observeAll(),
            transactionRepository.observeAll(),
            calendarRepository.observe()
        )
        { budgets, transactions, calendar ->
            // A month is a cycle of the calendar: from midnight on its start day to midnight on the next
            // cycle's start day, where the user lives.
            val start = calendar.startOf(month).atStartOfDay(zone).toInstant()
            val end = calendar.endOf(month).atStartOfDay(zone).toInstant()

            val spentBySubcategory = transactions
                .filter {
                    it.category == TransactionCategory.EXPENSE &&
                            it.subcategoryId != null &&
                            (accountId == null || it.accountId == accountId) &&
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
                    BudgetProgress(budget.limit, Money(spentBySubcategory[subcategoryId] ?: 0), budget.alertThreshold)
                }
        }.distinctUntilChanged()
    }
}
