package com.kyovo.cents.domain.model

import com.kyovo.cents.domain.exception.InvalidBudgetStartDayException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/** The day a budget cycle starts on by default: 1 to 28, never 29 to 31, which February could not honour. */
class BudgetStartDayTest
{
    @Test
    fun `accepts every day from 1 to 28`()
    {
        (1..28).forEach { assertThat(BudgetStartDay(it).value).isEqualTo(it) }
    }

    @Test
    fun `refuses any other day, so an invalid one cannot exist`()
    {
        listOf(0, -1, 29, 31).forEach { day ->
            assertThatThrownBy { BudgetStartDay(day) }.isInstanceOf(InvalidBudgetStartDayException::class.java)
        }
    }

    @Test
    fun `the default is the 1st, which makes cycles calendar months`()
    {
        assertThat(BudgetStartDay.DEFAULT).isEqualTo(BudgetStartDay(1))
        assertThat(BudgetCalendar().defaultStartDay).isEqualTo(BudgetStartDay.DEFAULT)
    }
}
