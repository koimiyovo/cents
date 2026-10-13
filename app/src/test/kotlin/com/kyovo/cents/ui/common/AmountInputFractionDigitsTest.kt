package com.kyovo.cents.ui.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** A currency without decimals (the CFA franc): the amount is whole, and there is no separator to type. */
class AmountInputWithoutDecimalsTest
{
    @ParameterizedTest
    @ValueSource(strings = ["", "1", "1500", "500000", "123456789012"])
    fun `accepts whole numbers while typing`(text: String)
    {
        assertThat(acceptsAmountInput(text, fractionDigits = 0)).isTrue()
    }

    @ParameterizedTest
    @ValueSource(strings = ["12,", "12.", "12,5", "1,5", ",5", "a", "-5", "1234567890123"])
    fun `refuses a separator, letters, signs and too many digits`(text: String)
    {
        assertThat(acceptsAmountInput(text, fractionDigits = 0)).isFalse()
    }

    @Test
    fun `an amount is read as a count of francs, not of hundredths`()
    {
        assertThat(parseAmountToCents("1500", fractionDigits = 0)).isEqualTo(1_500L)
    }

    @ParameterizedTest
    @ValueSource(strings = ["12,50", "12,", "12.5", "", "abc"])
    fun `a decimal amount is refused rather than rounded`(text: String)
    {
        assertThat(parseAmountToCents(text, fractionDigits = 0)).isNull()
    }

    @Test
    fun `zero is refused unless allowed, like any currency`()
    {
        assertThat(parseAmountToCents("0", fractionDigits = 0)).isNull()
        assertThat(parseAmountToCents("0", allowZero = true, fractionDigits = 0)).isEqualTo(0L)
    }

    @Test
    fun `an amount too large for a long is refused`()
    {
        assertThat(parseAmountToCents("99999999999999999999", fractionDigits = 0)).isNull()
    }

    @Test
    fun `the field is filled with the whole number, without separator`()
    {
        assertThat(formatCentsForInput(1_500, fractionDigits = 0)).isEqualTo("1500")
        assertThat(formatCentsForInput(0, fractionDigits = 0)).isEqualTo("0")
    }

    @Test
    fun `what the field is filled with reads back as the same amount`()
    {
        val text = formatCentsForInput(123_456, fractionDigits = 0)

        assertThat(parseAmountToCents(text, fractionDigits = 0)).isEqualTo(123_456L)
    }
}

/** Two decimals stay what they were (the default), and the amount can say so explicitly. */
class AmountInputWithTwoDecimalsTest
{
    @Test
    fun `an explicit two decimals reads like the default`()
    {
        assertThat(parseAmountToCents("12,50", fractionDigits = 2)).isEqualTo(parseAmountToCents("12,50"))
        assertThat(formatCentsForInput(1_250, fractionDigits = 2)).isEqualTo("12,50")
        assertThat(acceptsAmountInput("12,505", fractionDigits = 2)).isFalse()
    }
}
