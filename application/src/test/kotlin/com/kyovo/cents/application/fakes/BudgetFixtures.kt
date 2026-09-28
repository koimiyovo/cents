package com.kyovo.cents.application.fakes

import com.kyovo.cents.domain.model.AlertThreshold
import com.kyovo.cents.domain.model.Budget
import com.kyovo.cents.domain.model.BudgetAlert
import com.kyovo.cents.domain.model.BudgetAlertLevel
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.port.input.SetBudgetCommand
import com.kyovo.cents.domain.port.output.BudgetAlertRepository
import com.kyovo.cents.domain.port.output.BudgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.YearMonth

fun aBudget(
    subcategoryId: SubcategoryId = aSubcategoryId(),
    month: YearMonth = YearMonth.of(2026, 9),
    limit: Money = aMoney(30_000),
    alertThreshold: AlertThreshold = AlertThreshold.DEFAULT
): Budget
{
    return Budget(subcategoryId, month, limit, alertThreshold)
}

fun aSetBudgetCommand(
    subcategoryId: SubcategoryId = aSubcategoryId(),
    month: YearMonth = YearMonth.of(2026, 9),
    limit: Money = aMoney(30_000),
    alertThreshold: AlertThreshold = AlertThreshold.DEFAULT
): SetBudgetCommand
{
    return SetBudgetCommand(subcategoryId, month, limit, alertThreshold)
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

fun aBudgetAlert(
    subcategoryId: SubcategoryId = aSubcategoryId(),
    month: YearMonth = YearMonth.of(2026, 9),
    level: BudgetAlertLevel = BudgetAlertLevel.CLOSE_TO_LIMIT,
): BudgetAlert
{
    return BudgetAlert(subcategoryId, month, level)
}

class InMemoryBudgetAlertRepository : BudgetAlertRepository
{
    private val alerts = mutableListOf<BudgetAlert>()

    /** What has been recorded, in order (for the tests to look at). */
    val saved: List<BudgetAlert> get() = alerts

    override suspend fun findByMonth(month: YearMonth): Set<BudgetAlert>
    {
        return alerts.filter { it.month == month }.toSet()
    }

    override suspend fun record(alert: BudgetAlert)
    {
        alerts += alert
    }

    override suspend fun deleteBySubcategoryId(subcategoryId: SubcategoryId)
    {
        alerts.removeAll { it.subcategoryId == subcategoryId }
    }
}
