package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.TransactionCategory
import com.kyovo.cents.domain.port.input.GetSpendingBreakdownUseCase
import com.kyovo.cents.domain.port.output.BudgetCalendarRepository
import com.kyovo.cents.domain.port.output.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.YearMonth
import java.time.ZoneId

class GetSpendingBreakdownService(
    private val transactionRepository: TransactionRepository,
    private val calendarRepository: BudgetCalendarRepository,
    private val zone: ZoneId
) : GetSpendingBreakdownUseCase
{
    // Same month-boundary rule as a budget's progress: a month is a cycle of the calendar, from midnight on its
    // start day to midnight on the next cycle's start day, where the user lives.
    override fun observe(month: YearMonth, accountId: AccountId?): Flow<Map<SubcategoryId?, Money>>
    {
        return combine(transactionRepository.observeAll(), calendarRepository.observe())
        { transactions, calendar ->
            val start = calendar.startOf(month).atStartOfDay(zone).toInstant()
            val end = calendar.endOf(month).atStartOfDay(zone).toInstant()

            transactions
                .filter {
                    it.category == TransactionCategory.EXPENSE &&
                            (accountId == null || it.accountId == accountId) &&
                            !it.date.isBefore(start) &&
                            it.date.isBefore(end)
                }
                .groupBy { it.subcategoryId }
                .mapValues { (_, expenses) -> Money(expenses.sumOf { it.amount.value }) }
        }.distinctUntilChanged()
    }
}
