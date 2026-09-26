package com.kyovo.cents.application.fakes

import com.kyovo.cents.domain.model.Budget
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.port.input.SetBudgetCommand
import com.kyovo.cents.domain.port.output.BudgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.YearMonth

fun aBudget(
    subcategoryId: SubcategoryId = aSubcategoryId(),
    month: YearMonth = YearMonth.of(2026, 9),
    limit: Money = aMoney(30_000)
): Budget
{
    return Budget(subcategoryId, month, limit)
}

fun aSetBudgetCommand(
    subcategoryId: SubcategoryId = aSubcategoryId(),
    month: YearMonth = YearMonth.of(2026, 9),
    limit: Money = aMoney(30_000)
): SetBudgetCommand
{
    return SetBudgetCommand(subcategoryId, month, limit)
}

class InMemoryBudgetRepository : BudgetRepository
{
    private val state = MutableStateFlow<List<Budget>>(emptyList())

    /** What is stored, in order (for the tests to look at). */
    val saved: List<Budget> get() = state.value

    override suspend fun save(budget: Budget)
    {
        val current = state.value
        val index =
            current.indexOfFirst { it.subcategoryId == budget.subcategoryId && it.month == budget.month }
        state.value = if (index >= 0) current.toMutableList().also { it[index] = budget } else current + budget
    }

    override suspend fun deleteBySubcategoryId(subcategoryId: SubcategoryId)
    {
        state.value = state.value.filterNot { it.subcategoryId == subcategoryId }
    }

    override fun observeAll(): Flow<List<Budget>>
    {
        return state
    }
}
