package com.kyovo.cents.domain.model

import com.kyovo.cents.domain.exception.InvalidBudgetLimitException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.YearMonth
import java.util.UUID

class BudgetTest
{
    private val subcategoryId = SubcategoryId(UUID.fromString("22222222-2222-2222-2222-222222222222"))

    @Test
    fun `holds the limit set on a subcategory for a given month`()
    {
        // WHEN
        val budget = Budget(subcategoryId, YearMonth.of(2026, 9), Money(30_000))

        // THEN
        assertThat(budget.subcategoryId).isEqualTo(subcategoryId)
        assertThat(budget.month).isEqualTo(YearMonth.of(2026, 9))
        assertThat(budget.limit).isEqualTo(Money(30_000))
    }

    // Money itself accepts 0, but a budget of 0 would be "overspent" from the first cent: it says nothing
    // a user could not say by having no budget at all.
    @Test
    fun `refuses a zero limit`()
    {
        // WHEN / THEN
        assertThatThrownBy { Budget(subcategoryId, YearMonth.of(2026, 9), Money(0)) }
            .isInstanceOf(InvalidBudgetLimitException::class.java)
    }
}
