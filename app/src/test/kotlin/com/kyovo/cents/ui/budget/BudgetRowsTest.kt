package com.kyovo.cents.ui.budget

import com.kyovo.cents.domain.model.BudgetProgress
import com.kyovo.cents.domain.model.BudgetProjection
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.ui.transaction.FUEL_SUBCATEGORY
import com.kyovo.cents.ui.transaction.GROCERIES_SUBCATEGORY
import com.kyovo.cents.ui.transaction.SALARY_SUBCATEGORY
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate
import java.time.YearMonth

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

    // What is left of the limit, or by how much it was passed: the two things a row says in words.
    @ParameterizedTest(name = "limit {0}, spent {1} -> {2} {3}")
    @CsvSource(
        "30000,      0, LEFT,  30000",
        "30000,  12000, LEFT,  18000",
        "30000,  29999, LEFT,      1",
        "30000,  30000, LEFT,      0",   // exactly the limit: nothing left, but not over
        "30000,  30001, OVER,      1",
        "30000,  45050, OVER,  15050",
    )
    fun `a row says what is left of its limit, or by how much it was passed`(
        limit: Long,
        spent: Long,
        kind: String,
        cents: Long
    )
    {
        // WHEN
        val row = budgetRows(listOf(GROCERIES_SUBCATEGORY), mapOf(GROCERIES_SUBCATEGORY.id to progress(limit, spent))).single()

        // THEN
        val expected = if (kind == "LEFT") BudgetRemaining.Left(cents) else BudgetRemaining.Over(cents)
        assertThat(row.remaining).isEqualTo(expected)
    }

    @Test
    fun `a row without a budget has nothing left to say`()
    {
        assertThat(budgetRows(listOf(GROCERIES_SUBCATEGORY), emptyMap()).single().remaining).isNull()
    }

    @Test
    fun `a row without a budget has no bar`()
    {
        // WHEN
        val rows = budgetRows(listOf(GROCERIES_SUBCATEGORY), emptyMap())

        // THEN
        assertThat(rows.single().barFraction).isNull()
    }

    // ------------------------------------------------------------------ projection

    private val september = YearMonth.of(2026, 9)
    private val august = YearMonth.of(2026, 8)
    private val midSeptember = LocalDate.of(2026, 9, 15)

    @Test
    fun `without a month and today, no row projects, even with a budget in force`()
    {
        // WHEN
        val rows = budgetRows(listOf(GROCERIES_SUBCATEGORY), mapOf(GROCERIES_SUBCATEGORY.id to progress(30_000, 12_000)))

        // THEN
        assertThat(rows.single().projection).isNull()
    }

    @Test
    fun `given the month shown and today, a row with a budget in force carries its projection`()
    {
        // WHEN
        val rows = budgetRows(
            listOf(GROCERIES_SUBCATEGORY),
            mapOf(GROCERIES_SUBCATEGORY.id to progress(30_000, 12_000)),
            month = september,
            today = midSeptember,
        )

        // THEN 12_000 spent by day 15 of 30 projects to 24_000
        assertThat(rows.single().projection).isEqualTo(BudgetProjection(Money(30_000), Money(12_000), Money(24_000)))
    }

    @Test
    fun `a row without a budget has no projection either`()
    {
        // WHEN
        val rows = budgetRows(listOf(GROCERIES_SUBCATEGORY), emptyMap(), month = september, today = midSeptember)

        // THEN
        assertThat(rows.single().projection).isNull()
    }

    @Test
    fun `a row of a month other than the one today falls in has no projection`()
    {
        // WHEN looking at August's rows while today is in September
        val rows = budgetRows(
            listOf(GROCERIES_SUBCATEGORY),
            mapOf(GROCERIES_SUBCATEGORY.id to progress(25_000, 5_000)),
            month = august,
            today = midSeptember,
        )

        // THEN
        assertThat(rows.single().projection).isNull()
    }
}
