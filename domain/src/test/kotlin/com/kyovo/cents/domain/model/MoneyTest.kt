package com.kyovo.cents.domain.model

import com.kyovo.cents.domain.exception.InvalidMoneyAmountException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class MoneyTest
{
    @Test
    fun `accepts a zero amount`()
    {
        // WHEN
        val money = Money(0)

        // THEN
        assertThat(money.value).isEqualTo(0)
    }

    @Test
    fun `accepts a positive amount`()
    {
        // WHEN
        val money = Money(1_500)

        // THEN
        assertThat(money.value).isEqualTo(1_500)
    }

    @Test
    fun `refuses a negative amount`()
    {
        // WHEN / THEN
        assertThatThrownBy { Money(-1) }
            .isInstanceOf(InvalidMoneyAmountException::class.java)
    }
}
