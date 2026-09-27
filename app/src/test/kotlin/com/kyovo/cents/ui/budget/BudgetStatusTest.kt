package com.kyovo.cents.ui.budget

import com.kyovo.cents.domain.model.AlertThreshold
import com.kyovo.cents.domain.model.BudgetProgress
import com.kyovo.cents.domain.model.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * What a budget row says about itself: fine, close to its limit, or over it. "Close" starts at 80 % of
 * the limit and lasts up to the limit itself — spending exactly the limit is *close*, not over, like
 * `BudgetProgress.isOverspent`, which only turns true a cent above it.
 *
 * The percentages are worked out on whole cents (no floating point), so a boundary such as exactly 80 %
 * cannot land on the wrong side because of a rounding error.
 */
class BudgetStatusTest
{
    @ParameterizedTest(name = "limit {0}, spent {1} -> {2}")
    @CsvSource(
        // the everyday case: a limit of 300 euros
        "30000,      0, ON_TRACK",
        "30000,  23999, ON_TRACK",       // 79.99 %: one cent short of 80 %
        "30000,  24000, CLOSE_TO_LIMIT", // exactly 80 %
        "30000,  29999, CLOSE_TO_LIMIT",
        "30000,  30000, CLOSE_TO_LIMIT", // exactly the limit: not over yet
        "30000,  30001, OVER",           // one cent over
        "30000,  45000, OVER",
        // small limits, where a percentage is not a whole number of cents
        "5,     3, ON_TRACK",            // 60 %
        "5,     4, CLOSE_TO_LIMIT",      // exactly 80 %
        "7,     5, ON_TRACK",            // 71.4 %
        "7,     6, CLOSE_TO_LIMIT",      // 85.7 %
        "999, 799, ON_TRACK",            // 79.98 %
        "1,     1, CLOSE_TO_LIMIT",
        "1,     2, OVER",
    )
    fun `a budget is on track below 80 percent, close to its limit from 80 percent up to the limit, and over above it`(
        limit: Long,
        spent: Long,
        expected: BudgetStatus
    )
    {
        // WHEN
        val status = budgetStatus(BudgetProgress(Money(limit), Money(spent)))

        // THEN
        assertThat(status).isEqualTo(expected)
    }

    // The 80 % above is only the default: each budget has its own alert threshold, and "close" starts there.
    @ParameterizedTest(name = "limit {0}, spent {1}, threshold {2} % -> {3}")
    @CsvSource(
        "30000, 14999, 50, ON_TRACK",
        "30000, 15000, 50, CLOSE_TO_LIMIT",       // exactly 50 %
        "30000, 3000,  10, CLOSE_TO_LIMIT",       // a low threshold warns early
        "30000, 29999, 100, ON_TRACK",            // 100 %: close only once the limit is reached
        "30000, 30000, 100, CLOSE_TO_LIMIT",
        "30000, 30001, 100, OVER",                // the threshold never postpones "over"
        "30000, 30001, 50, OVER",
        "30000, 26999, 90, ON_TRACK",
        "30000, 27000, 90, CLOSE_TO_LIMIT",
    )
    fun `close starts at the alert threshold of the budget, and over is always above the limit`(
        limit: Long,
        spent: Long,
        threshold: Int,
        expected: BudgetStatus
    )
    {
        // WHEN
        val status = budgetStatus(BudgetProgress(Money(limit), Money(spent), AlertThreshold(threshold)))

        // THEN
        assertThat(status).isEqualTo(expected)
    }
}
