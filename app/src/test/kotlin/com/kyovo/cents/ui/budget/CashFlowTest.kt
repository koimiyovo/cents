package com.kyovo.cents.ui.budget

import com.kyovo.cents.domain.model.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The two sides of the cash-flow diagram: income in, spending out, and whatever closes the gap between them. */
class CashFlowTest
{
    private fun named(name: String) = SpendingSliceLabel.Named(name, "💰")

    private fun breakdown(vararg slices: Pair<SpendingSliceLabel, Long>): SpendingBreakdown
    {
        val total = slices.sumOf { it.second }
        return SpendingBreakdown(
            Money(total),
            slices.map { (label, cents) -> SpendingSlice(label, Money(cents), cents.toFloat() / total) },
        )
    }

    private fun slice(label: SpendingSliceLabel, cents: Long) = CashFlowNode(CashFlowLabel.Slice(label), cents)

    @Test
    fun `income left after spending is shown as savings on the spending side`()
    {
        // GIVEN
        val salary = named("Salaire")
        val rent = named("Loyer")
        val income = breakdown(salary to 250_000)
        val spending = breakdown(rent to 150_000)

        // WHEN
        val flow = cashFlow(income, spending)

        // THEN
        assertThat(flow.sources).containsExactly(slice(salary, 250_000))
        assertThat(flow.uses).containsExactly(slice(rent, 150_000), CashFlowNode(CashFlowLabel.Savings, 100_000))
        assertThat(flow.total).isEqualTo(250_000)
    }

    @Test
    fun `spending beyond the income is shown as coming from savings on the income side`()
    {
        // GIVEN
        val salary = named("Salaire")
        val rent = named("Loyer")
        val income = breakdown(salary to 100_000)
        val spending = breakdown(rent to 150_000)

        // WHEN
        val flow = cashFlow(income, spending)

        // THEN
        assertThat(flow.sources).containsExactly(slice(salary, 100_000), CashFlowNode(CashFlowLabel.FromSavings, 50_000))
        assertThat(flow.uses).containsExactly(slice(rent, 150_000))
        assertThat(flow.total).isEqualTo(150_000)
    }

    @Test
    fun `a balanced month needs no node to close it`()
    {
        // GIVEN
        val income = breakdown(named("Salaire") to 100_000)
        val spending = breakdown(named("Loyer") to 60_000, named("Courses") to 40_000)

        // WHEN
        val flow = cashFlow(income, spending)

        // THEN
        assertThat(flow.sources.map { it.cents }).containsExactly(100_000L)
        assertThat(flow.uses.map { it.cents }).containsExactly(60_000L, 40_000L)
        assertThat(flow.total).isEqualTo(100_000)
    }

    @Test
    fun `both sides of the diagram always add up to the total`()
    {
        // GIVEN
        val income = breakdown(named("Salaire") to 80_000, SpendingSliceLabel.Uncategorized to 5_000)
        val spending = breakdown(named("Loyer") to 90_000, SpendingSliceLabel.Other to 1_000)

        // WHEN
        val flow = cashFlow(income, spending)

        // THEN
        assertThat(flow.sources.sumOf { it.cents }).isEqualTo(flow.total)
        assertThat(flow.uses.sumOf { it.cents }).isEqualTo(flow.total)
    }

    @Test
    fun `a month with nothing received or spent is empty`()
    {
        // GIVEN
        val nothing = SpendingBreakdown(Money(0), emptyList())

        // WHEN
        val flow = cashFlow(nothing, nothing)

        // THEN
        assertThat(flow.isEmpty).isTrue()
        assertThat(flow.sources).isEmpty()
        assertThat(flow.uses).isEmpty()
    }

    @Test
    fun `the savings rate is the share of the income that was not spent, in whole percent`()
    {
        // GIVEN
        val income = breakdown(named("Salaire") to 250_000)
        val spending = breakdown(named("Loyer") to 150_000)

        // WHEN
        val flow = cashFlow(income, spending)

        // THEN
        assertThat(flow.savingsRatePercent).isEqualTo(40)
    }

    @Test
    fun `the savings rate is rounded to the nearest percent`()
    {
        // GIVEN
        val income = breakdown(named("Salaire") to 300)
        val spending = breakdown(named("Loyer") to 200)

        // WHEN
        val flow = cashFlow(income, spending)

        // THEN
        assertThat(flow.savingsRatePercent).isEqualTo(33)
    }

    @Test
    fun `there is no savings rate in a deficit or a balanced month`()
    {
        // GIVEN
        val salary = named("Salaire")
        val rent = named("Loyer")

        // WHEN
        val deficit = cashFlow(breakdown(salary to 100_000), breakdown(rent to 150_000))
        val balanced = cashFlow(breakdown(salary to 100_000), breakdown(rent to 100_000))

        // THEN
        assertThat(deficit.savingsRatePercent).isNull()
        assertThat(balanced.savingsRatePercent).isNull()
    }
}
