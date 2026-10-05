package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.MonthlySpending
import com.kyovo.cents.domain.model.TransactionCategory
import com.kyovo.cents.domain.port.input.GetSpendingTrendUseCase
import com.kyovo.cents.domain.port.output.BudgetCalendarRepository
import com.kyovo.cents.domain.port.output.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.YearMonth
import java.time.ZoneId

class GetSpendingTrendService(
    private val transactionRepository: TransactionRepository,
    private val calendarRepository: BudgetCalendarRepository,
    private val zone: ZoneId
) : GetSpendingTrendUseCase
{
    override fun observe(month: YearMonth, months: Int, accountId: AccountId?): Flow<List<MonthlySpending>>
    {
        // Oldest to newest, [month] itself last: the window the chart plots.
        val window = (months - 1 downTo 0).map { month.minusMonths(it.toLong()) }

        return combine(transactionRepository.observeAll(), calendarRepository.observe())
        { transactions, calendar ->
            // Each point is a cycle of the calendar: the window runs from the start of its first cycle to the
            // end of [month]'s, and an expense counts in the cycle open on the day it was made.
            val start = calendar.startOf(window.first()).atStartOfDay(zone).toInstant()
            val end = calendar.endOf(month).atStartOfDay(zone).toInstant()

            val spentByMonth = transactions
                .filter {
                    it.category == TransactionCategory.EXPENSE &&
                            (accountId == null || it.accountId == accountId) &&
                            !it.date.isBefore(start) &&
                            it.date.isBefore(end)
                }
                .groupBy { calendar.cycleOf(it.date.atZone(zone).toLocalDate()) }
                .mapValues { (_, expenses) -> expenses.sumOf { it.amount.value } }

            window.map { MonthlySpending(it, Money(spentByMonth[it] ?: 0)) }
        }.distinctUntilChanged()
    }
}
