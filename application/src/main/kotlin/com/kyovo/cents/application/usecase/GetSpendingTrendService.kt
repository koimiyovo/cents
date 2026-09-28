package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.MonthlySpending
import com.kyovo.cents.domain.model.TransactionCategory
import com.kyovo.cents.domain.port.input.GetSpendingTrendUseCase
import com.kyovo.cents.domain.port.output.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import java.time.ZoneId

class GetSpendingTrendService(
    private val transactionRepository: TransactionRepository,
    private val zone: ZoneId
) : GetSpendingTrendUseCase
{
    override fun observe(month: YearMonth, months: Int, accountId: AccountId?): Flow<List<MonthlySpending>>
    {
        // Oldest to newest, [month] itself last: the window the chart plots.
        val window = (months - 1 downTo 0).map { month.minusMonths(it.toLong()) }
        val start = window.first().atDay(1).atStartOfDay(zone).toInstant()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant()

        return transactionRepository.observeAll()
            .map { transactions ->
                val spentByMonth = transactions
                    .filter {
                        it.category == TransactionCategory.EXPENSE &&
                                (accountId == null || it.accountId == accountId) &&
                                !it.date.isBefore(start) &&
                                it.date.isBefore(end)
                    }
                    .groupBy { YearMonth.from(it.date.atZone(zone)) }
                    .mapValues { (_, expenses) -> expenses.sumOf { it.amount.value } }

                window.map { MonthlySpending(it, Money(spentByMonth[it] ?: 0)) }
            }
            .distinctUntilChanged()
    }
}
