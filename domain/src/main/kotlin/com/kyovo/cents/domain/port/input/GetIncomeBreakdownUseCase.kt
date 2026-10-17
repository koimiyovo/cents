package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.SubcategoryId
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

/**
 * Where the money came from in a month: the total received per income subcategory, a null key for income
 * with none. The mirror of [GetSpendingBreakdownUseCase], over the same budget month. A subcategory with
 * nothing received is simply absent.
 */
interface GetIncomeBreakdownUseCase
{
    /** [accountId], when given, counts only that account's income — null (the default) counts every account. */
    fun observe(month: YearMonth, accountId: AccountId? = null): Flow<Map<SubcategoryId?, Money>>
}
