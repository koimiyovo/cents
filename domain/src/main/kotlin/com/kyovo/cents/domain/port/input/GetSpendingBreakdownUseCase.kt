package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.SubcategoryId
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

/**
 * Where the money went in a month: the total spent per expense subcategory, a null key for expenses with
 * none. No budget is involved — unlike [GetBudgetProgressUseCase], every expense counts, whether or not its
 * subcategory has a limit. A subcategory with nothing spent is simply absent.
 */
interface GetSpendingBreakdownUseCase
{
    /** [accountId], when given, counts only that account's expenses — null (the default) counts every account. */
    fun observe(month: YearMonth, accountId: AccountId? = null): Flow<Map<SubcategoryId?, Money>>
}
