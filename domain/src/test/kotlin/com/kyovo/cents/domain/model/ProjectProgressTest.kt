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
    private fun progress(expenses: Long, incomes: Long = 0, target: Long? = null, count: Int = 1) =
        ProjectProgress(target?.let { Money(it) }, Money(expenses), Money(incomes), count)

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

    @Test
    fun `an empty project has spent nothing`()
    {
        val empty = progress(expenses = 0, count = 0, target = 100_000)

        assertThat(empty.net).isZero()
        assertThat(empty.remaining).isEqualTo(100_000)
        assertThat(empty.transactionCount).isZero()
    }
}
