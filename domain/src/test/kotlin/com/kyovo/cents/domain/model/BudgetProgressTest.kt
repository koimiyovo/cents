package com.kyovo.cents.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * How much of a budget is used. `remaining` is a plain signed number, not a `Money`: it goes below zero
 * once the limit is passed, and `Money` never can. Reaching the limit exactly is not overspending.
 */
class BudgetProgressTest
{
    @Test
    fun `holds the limit and what has been spent`()
    {
        // WHEN
        val progress = BudgetProgress(limit = Money(30_000), spent = Money(12_000))

        // THEN
        assertThat(progress.limit).isEqualTo(Money(30_000))
        assertThat(progress.spent).isEqualTo(Money(12_000))
    }

    @ParameterizedTest(name = "limit {0}, spent {1} -> remaining {2}")
    @CsvSource(
        "30000, 0,      30000",
        "30000, 12000,  18000",
        "30000, 30000,  0",
        "30000, 30001,  -1",
        "30000, 45000,  -15000",
    )
    fun `the remaining is the limit minus what was spent, and goes negative once the limit is passed`(
        limit: Long,
        spent: Long,
        remaining: Long
    )
    {
        // WHEN
        val progress = BudgetProgress(Money(limit), Money(spent))

        // THEN
        assertThat(progress.remaining).isEqualTo(remaining)
    }

    @ParameterizedTest(name = "limit {0}, spent {1} -> overspent {2}")
    @CsvSource(
        "30000, 0,      false",
        "30000, 29999,  false",
        "30000, 30000,  false",
        "30000, 30001,  true",
        "30000, 45000,  true",
    )
    fun `is overspent only when more than the limit was spent`(limit: Long, spent: Long, overspent: Boolean)
    {
        // WHEN
        val progress = BudgetProgress(Money(limit), Money(spent))

        // THEN
        assertThat(progress.isOverspent).isEqualTo(overspent)
    }
}
