package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.BudgetProgress
import com.kyovo.cents.domain.model.SubcategoryId
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

interface GetBudgetProgressUseCase
{
    /** [accountId], when given, counts only that account's expenses — null (the default) counts every account. */
    fun observe(subcategoryId: SubcategoryId, month: YearMonth, accountId: AccountId? = null): Flow<BudgetProgress?>
    fun observeAll(month: YearMonth, accountId: AccountId? = null): Flow<Map<SubcategoryId, BudgetProgress>>
}