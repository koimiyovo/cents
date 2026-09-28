package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.TransactionCategory
import com.kyovo.cents.domain.port.input.GetSpendingBreakdownUseCase
import com.kyovo.cents.domain.port.output.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import java.time.ZoneId

class GetSpendingBreakdownService(
    private val transactionRepository: TransactionRepository,
    private val zone: ZoneId
) : GetSpendingBreakdownUseCase
{
    // Same month-boundary rule as a budget's progress: midnight to midnight where the user lives.
    override fun observe(month: YearMonth, accountId: AccountId?): Flow<Map<SubcategoryId?, Money>>
    {
        val start = month.atDay(1).atStartOfDay(zone).toInstant()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant()

        return transactionRepository.observeAll()
            .map { transactions ->
                transactions
                    .filter {
                        it.category == TransactionCategory.EXPENSE &&
                                (accountId == null || it.accountId == accountId) &&
                                !it.date.isBefore(start) &&
                                it.date.isBefore(end)
                    }
                    .groupBy { it.subcategoryId }
                    .mapValues { (_, expenses) -> Money(expenses.sumOf { it.amount.value }) }
            }
            .distinctUntilChanged()
    }
}
