package com.kyovo.cents.ui.budget

import com.kyovo.cents.domain.model.BudgetProgress
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.SubcategoryName
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * What the budget tab shows "at a glance": the budgets that are set, the most urgent first, then — apart —
 * the subcategories that have none this month, and a summary of the month on top.
 */
class BudgetOverviewTest
{
    private fun aSubcategory(suffix: Int, name: String) = Subcategory(
        SubcategoryId(UUID.fromString("bbbbbbbb-0000-0000-0000-00000000000$suffix")),
        RecordableTransactionCategory.EXPENSE,
        SubcategoryName(name),
        null,
    )

    private val groceries = aSubcategory(1, "Alimentation")
    private val fuel = aSubcategory(2, "Carburant")
    private val leisure = aSubcategory(3, "Loisirs")
    private val health = aSubcategory(4, "Santé")
    private val gifts = aSubcategory(5, "Cadeaux")

    // Spent against a limit of 100 euros: below, near and above the default threshold of 80 %.
    private fun onTrack(subcategory: Subcategory) = BudgetRow(subcategory, BudgetProgress(Money(10_000), Money(3_000)))
    private fun close(subcategory: Subcategory) = BudgetRow(subcategory, BudgetProgress(Money(10_000), Money(9_000)))
    private fun over(subcategory: Subcategory) = BudgetRow(subcategory, BudgetProgress(Money(10_000), Money(12_000)))
    private fun none(subcategory: Subcategory) = BudgetRow(subcategory, null)

    @Test
    fun `budgets come most urgent first, over then close then on track`()
    {
        // GIVEN
        val rows = listOf(onTrack(groceries), close(fuel), over(leisure))

        // WHEN
        val overview = budgetOverview(rows)

        // THEN
        assertThat(overview.budgeted.map { it.subcategory }).containsExactly(leisure, fuel, groceries)
    }

    @Test
    fun `rows of the same status keep the order they were given in`()
    {
        // GIVEN
        val rows = listOf(over(health), close(gifts), over(groceries), close(fuel), over(leisure))

        // WHEN
        val overview = budgetOverview(rows)

        // THEN
        assertThat(overview.budgeted.map { it.subcategory }).containsExactly(health, groceries, leisure, gifts, fuel)
    }

    @Test
    fun `the subcategories without a budget are apart, in the order given`()
    {
        // GIVEN
        val rows = listOf(none(health), onTrack(groceries), none(gifts))

        // WHEN
        val overview = budgetOverview(rows)

        // THEN
        assertThat(overview.unbudgeted.map { it.subcategory }).containsExactly(health, gifts)
        assertThat(overview.budgeted.map { it.subcategory }).containsExactly(groceries)
    }

    @Test
    fun `there is no summary when no budget is set, and the tab has nothing but the subcategories`()
    {
        // GIVEN
        val overview = budgetOverview(listOf(none(groceries), none(fuel)))

        // THEN
        assertThat(overview.summary).isNull()
        assertThat(overview.budgeted).isEmpty()
        assertThat(overview.unbudgeted).hasSize(2)
    }

    @Test
    fun `an empty list gives an empty overview`()
    {
        assertThat(budgetOverview(emptyList())).isEqualTo(BudgetOverview(emptyList(), emptyList(), null))
    }

    @Test
    fun `the summary adds up the limits and what was spent of the budgets that are set only`()
    {
        // GIVEN 100 + 100 + 100 euros of limits, 30 + 90 + 120 euros spent, and a subcategory without a budget
        val overview = budgetOverview(listOf(onTrack(groceries), close(fuel), over(leisure), none(health)))

        // THEN
        val summary = overview.summary!!
        assertThat(summary.totalLimit).isEqualTo(Money(30_000))
        assertThat(summary.totalSpent).isEqualTo(Money(24_000))
        assertThat(summary.remaining).isEqualTo(6_000)
        assertThat(summary.isOverspent).isFalse()
    }

    @Test
    fun `the summary counts how many budgets are over and how many are close`()
    {
        // GIVEN
        val overview = budgetOverview(listOf(onTrack(groceries), close(fuel), close(gifts), over(leisure)))

        // THEN
        assertThat(overview.summary!!.overCount).isEqualTo(1)
        assertThat(overview.summary!!.closeCount).isEqualTo(2)
    }

    // Everything can be fine as a whole with one budget over, and the month as a whole can be over: the total
    // is judged on its own, apart from the count of budgets that are over.
    @Test
    fun `the month as a whole is over only when the total spent passes the total limit`()
    {
        // GIVEN 130 euros spent on a limit of 100, and another limit of 100 left untouched
        val overview = budgetOverview(
            listOf(
                BudgetRow(groceries, BudgetProgress(Money(10_000), Money(13_000))),
                BudgetRow(fuel, BudgetProgress(Money(10_000), Money(0))),
            )
        )

        // THEN 130 spent of 200: not over as a whole, though one budget is
        assertThat(overview.summary!!.isOverspent).isFalse()
        assertThat(overview.summary!!.overCount).isEqualTo(1)

        // AND when the total itself passes the total limit
        val worse = budgetOverview(listOf(BudgetRow(groceries, BudgetProgress(Money(10_000), Money(13_000)))))
        assertThat(worse.summary!!.isOverspent).isTrue()
        assertThat(worse.summary!!.remaining).isEqualTo(-3_000)
    }

    @Test
    fun `the summary bar is the share of the total limit spent, kept between empty and full`()
    {
        assertThat(budgetOverview(listOf(onTrack(groceries))).summary!!.barFraction).isCloseTo(0.3f, within(0.0001f))
        assertThat(budgetOverview(listOf(over(groceries))).summary!!.barFraction).isEqualTo(1f)
        assertThat(
            budgetOverview(listOf(BudgetRow(groceries, BudgetProgress(Money(10_000), Money(0))))).summary!!.barFraction
        ).isEqualTo(0f)
    }
}
