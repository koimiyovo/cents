package com.kyovo.cents.ui.budget

import com.kyovo.cents.domain.model.BudgetProgress
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.ui.transaction.FUEL_SUBCATEGORY
import com.kyovo.cents.ui.transaction.GROCERIES_SUBCATEGORY
import com.kyovo.cents.ui.transaction.SALARY_SUBCATEGORY
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * The budgets screen lists the **expense** subcategories — an income has nothing to be capped — one row
 * each, in the order given (already by name). A row carries the progress of the month shown when a
 * budget is in force, and nothing when there is none, so the screen can offer to set one.
 */
class BudgetRowsTest
{
    private val all = listOf(GROCERIES_SUBCATEGORY, SALARY_SUBCATEGORY, FUEL_SUBCATEGORY)

    private fun progress(limit: Long, spent: Long) = BudgetProgress(Money(limit), Money(spent))

    @Test
    fun `there is one row per expense subcategory, in the order given, and none for an income`()
    {
        // WHEN
        val rows = budgetRows(all, emptyMap())

        // THEN
        assertThat(rows.map { it.subcategory }).containsExactly(GROCERIES_SUBCATEGORY, FUEL_SUBCATEGORY)
    }

    @Test
    fun `a subcategory with a budget in force carries its progress, the others carry none`()
    {
        // GIVEN only groceries has a budget this month
        val groceries = progress(limit = 30_000, spent = 12_000)

        // WHEN
        val rows = budgetRows(all, mapOf(GROCERIES_SUBCATEGORY.id to groceries))

        // THEN
        assertThat(rows.map { it.progress }).containsExactly(groceries, null)
    }

    @Test
    fun `a progress for an income subcategory or an unknown one does not make a row`()
    {
        // GIVEN progress keyed by ids the screen does not list
        val stray = mapOf(SALARY_SUBCATEGORY.id to progress(1_000, 0))

        // WHEN
        val rows = budgetRows(listOf(GROCERIES_SUBCATEGORY), stray)

        // THEN
        assertThat(rows).hasSize(1)
        assertThat(rows.single().progress).isNull()
    }

    @Test
    fun `the status of a row is the one of its progress, and a row without a budget has none`()
    {
        // GIVEN groceries close to its limit
        val rows = budgetRows(all, mapOf(GROCERIES_SUBCATEGORY.id to progress(limit = 30_000, spent = 25_000)))

        // WHEN / THEN
        assertThat(rows.map { it.status }).containsExactly(BudgetStatus.CLOSE_TO_LIMIT, null)
    }

    // The bar shows how much of the limit is used: empty at nothing, full at the limit, and it stays
    // full above it — a bar cannot go past its end, the "over" state and the numbers say the rest.
    @ParameterizedTest(name = "limit {0}, spent {1} -> bar at {2}")
    @CsvSource(
        "30000,      0, 0.0",
        "30000,  15000, 0.5",
        "30000,  24000, 0.8",
        "30000,  30000, 1.0",
        "30000,  30001, 1.0",
        "30000, 900000, 1.0",
        "3,          1, 0.3333",
    )
    fun `the bar is the share of the limit spent, kept between empty and full`(limit: Long, spent: Long, expected: Float)
    {
        // WHEN
        val rows = budgetRows(listOf(GROCERIES_SUBCATEGORY), mapOf(GROCERIES_SUBCATEGORY.id to progress(limit, spent)))

        // THEN
        assertThat(rows.single().barFraction).isCloseTo(expected, within(0.0001f))
    }

    @Test
    fun `a row without a budget has no bar`()
    {
        // WHEN
        val rows = budgetRows(listOf(GROCERIES_SUBCATEGORY), emptyMap())

        // THEN
        assertThat(rows.single().barFraction).isNull()
    }
}
