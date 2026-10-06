package com.kyovo.cents.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Where a project stands. It is the *net* cost: a refund or any income attached to the project (an
 * insurance paying back a part of the trip) lowers what it cost, so the target is compared with
 * expenses minus incomes.
 */
class ProjectProgressTest
{
    private fun progress(
        expenses: Long,
        incomes: Long = 0,
        target: Long? = null,
        count: Int = 1,
        threshold: Int = AlertThreshold.DEFAULT.percent,
    ) = ProjectProgress(target?.let { Money(it) }, Money(expenses), Money(incomes), count, AlertThreshold(threshold))

    @Test
    fun `the net cost is the expenses minus the incomes`()
    {
        assertThat(progress(expenses = 100_000, incomes = 15_000).net).isEqualTo(85_000)
    }

    @Test
    fun `the net cost is negative when the incomes exceed the expenses`()
    {
        assertThat(progress(expenses = 10_000, incomes = 12_000).net).isEqualTo(-2_000)
    }

    @Test
    fun `what remains is the target minus the net cost`()
    {
        assertThat(progress(expenses = 100_000, incomes = 10_000, target = 300_000).remaining).isEqualTo(210_000)
    }

    @Test
    fun `what remains is negative once over the target`()
    {
        assertThat(progress(expenses = 320_000, target = 300_000).remaining).isEqualTo(-20_000)
    }

    @Test
    fun `there is nothing remaining to say without a target`()
    {
        assertThat(progress(expenses = 100_000).remaining).isNull()
    }

    @Test
    fun `is over the target only above it, not at it`()
    {
        assertThat(progress(expenses = 300_001, target = 300_000).isOverTarget).isTrue()
        assertThat(progress(expenses = 300_000, target = 300_000).isOverTarget).isFalse()
        assertThat(progress(expenses = 299_999, target = 300_000).isOverTarget).isFalse()
    }

    @Test
    fun `a refund can bring a project back under its target`()
    {
        assertThat(progress(expenses = 320_000, incomes = 30_000, target = 300_000).isOverTarget).isFalse()
    }

    @Test
    fun `is never over without a target`()
    {
        assertThat(progress(expenses = 999_999_999).isOverTarget).isFalse()
    }

    // ------------------------------------------------------------------ the alert level

    // Same bands as a budget's, with its default threshold: close to the limit from 80 %, over only above it.
    @Test
    fun `there is no alert below 80 percent of the target`()
    {
        assertThat(progress(expenses = 79_999, target = 100_000).alertLevel()).isNull()
        assertThat(progress(expenses = 0, target = 100_000).alertLevel()).isNull()
    }

    @Test
    fun `it is close to the target from 80 percent up to and including the target`()
    {
        assertThat(progress(expenses = 80_000, target = 100_000).alertLevel()).isEqualTo(BudgetAlertLevel.CLOSE_TO_LIMIT)
        assertThat(progress(expenses = 100_000, target = 100_000).alertLevel()).isEqualTo(BudgetAlertLevel.CLOSE_TO_LIMIT)
    }

    @Test
    fun `it is over the target only a cent above it`()
    {
        assertThat(progress(expenses = 100_001, target = 100_000).alertLevel()).isEqualTo(BudgetAlertLevel.OVER)
    }

    @Test
    fun `a refund lowers the level, since it lowers what the project cost`()
    {
        // 120 000 spent, 30 000 paid back: 90 000 net, close to the 100 000 target but no longer over it.
        assertThat(progress(expenses = 120_000, incomes = 30_000, target = 100_000).alertLevel())
            .isEqualTo(BudgetAlertLevel.CLOSE_TO_LIMIT)
        assertThat(progress(expenses = 120_000, incomes = 60_000, target = 100_000).alertLevel()).isNull()
    }

    // Each project has its own threshold, as each budget does: the default is only where it starts.
    @Test
    fun `it is close to the target from the project's own threshold`()
    {
        assertThat(progress(expenses = 59_999, target = 100_000, threshold = 60).alertLevel()).isNull()
        assertThat(progress(expenses = 60_000, target = 100_000, threshold = 60).alertLevel())
            .isEqualTo(BudgetAlertLevel.CLOSE_TO_LIMIT)
    }

    @Test
    fun `a threshold of 100 means close only once the target is reached`()
    {
        assertThat(progress(expenses = 99_999, target = 100_000, threshold = 100).alertLevel()).isNull()
        assertThat(progress(expenses = 100_000, target = 100_000, threshold = 100).alertLevel())
            .isEqualTo(BudgetAlertLevel.CLOSE_TO_LIMIT)
    }

    @Test
    fun `over the target is over whatever the threshold`()
    {
        assertThat(progress(expenses = 100_001, target = 100_000, threshold = 50).alertLevel()).isEqualTo(BudgetAlertLevel.OVER)
        assertThat(progress(expenses = 100_001, target = 100_000, threshold = 100).alertLevel()).isEqualTo(BudgetAlertLevel.OVER)
    }

    @Test
    fun `the threshold defaults to 80 percent`()
    {
        assertThat(ProjectProgress(Money(100_000), Money(80_000), Money(0), 1).alertThreshold)
            .isEqualTo(AlertThreshold.DEFAULT)
    }

    @Test
    fun `a project without a target never alerts`()
    {
        assertThat(progress(expenses = 999_999_999).alertLevel()).isNull()
    }

    @Test
    fun `a negative net cost never alerts`()
    {
        assertThat(progress(expenses = 1_000, incomes = 9_000, target = 100_000).alertLevel()).isNull()
    }

    @Test
    fun `an empty project has spent nothing`()
    {
        val empty = progress(expenses = 0, count = 0, target = 100_000)

        assertThat(empty.net).isZero()
        assertThat(empty.remaining).isEqualTo(100_000)
        assertThat(empty.transactionCount).isZero()
    }
}
