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

    // The screen needs the threshold of the budget in force to say "close", and a progress is what it gets.
    @Test
    fun `carries the alert threshold of its budget, 80 percent unless given`()
    {
        assertThat(BudgetProgress(Money(30_000), Money(0)).alertThreshold).isEqualTo(AlertThreshold.DEFAULT)
        assertThat(BudgetProgress(Money(30_000), Money(0), AlertThreshold(60)).alertThreshold)
            .isEqualTo(AlertThreshold(60))
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

    // The domain's own version of what `:app`'s BudgetStatus already says for display (ON_TRACK/
    // CLOSE_TO_LIMIT/OVER): needed here too, not just to show a bar on screen — the WorkManager alert
    // check must classify a progress the same way, without depending on `:app`. Unlike BudgetStatus,
    // there is no ON_TRACK case: a progress that isn't close or over has nothing to alert about.
    @Test
    fun `spending below the alert threshold has no alert level`()
    {
        // WHEN
        val progress = BudgetProgress(Money(30_000), Money(10_000))

        // THEN
        assertThat(progress.alertLevel()).isNull()
    }

    @ParameterizedTest(name = "limit {0}, spent {1} -> {2}")
    @CsvSource(
        // the everyday case: a limit of 300 euros
        "30000,      0, ",
        "30000,  23999, ",               // 79.99 %: one cent short of 80 %
        "30000,  24000, CLOSE_TO_LIMIT", // exactly 80 %
        "30000,  29999, CLOSE_TO_LIMIT",
        "30000,  30000, CLOSE_TO_LIMIT", // exactly the limit: not over yet
        "30000,  30001, OVER",           // one cent over
        "30000,  45000, OVER",
        // small limits, where a percentage is not a whole number of cents
        "5,     3, ",                    // 60 %
        "5,     4, CLOSE_TO_LIMIT",      // exactly 80 %
        "7,     5, ",                    // 71.4 %
        "7,     6, CLOSE_TO_LIMIT",      // 85.7 %
        "999, 799, ",                    // 79.98 %
        "1,     1, CLOSE_TO_LIMIT",
        "1,     2, OVER",
    )
    fun `has no alert level below 80 percent, close to the limit from 80 percent up to the limit, and over above it`(
        limit: Long,
        spent: Long,
        expected: BudgetAlertLevel?
    )
    {
        // WHEN
        val level = BudgetProgress(Money(limit), Money(spent)).alertLevel()

        // THEN
        assertThat(level).isEqualTo(expected)
    }

    // The 80 % above is only the default: each budget has its own alert threshold, and "close" starts there.
    @ParameterizedTest(name = "limit {0}, spent {1}, threshold {2} % -> {3}")
    @CsvSource(
        "30000, 14999, 50, ",
        "30000, 15000, 50, CLOSE_TO_LIMIT",       // exactly 50 %
        "30000, 3000,  10, CLOSE_TO_LIMIT",       // a low threshold warns early
        "30000, 29999, 100, ",                    // 100 %: close only once the limit is reached
        "30000, 30000, 100, CLOSE_TO_LIMIT",
        "30000, 30001, 100, OVER",                // the threshold never postpones "over"
        "30000, 30001, 50, OVER",
        "30000, 26999, 90, ",
        "30000, 27000, 90, CLOSE_TO_LIMIT",
    )
    fun `close starts at the alert threshold of the budget, and over is always above the limit`(
        limit: Long,
        spent: Long,
        threshold: Int,
        expected: BudgetAlertLevel?
    )
    {
        // WHEN
        val level = BudgetProgress(Money(limit), Money(spent), AlertThreshold(threshold)).alertLevel()

        // THEN
        assertThat(level).isEqualTo(expected)
    }
}
