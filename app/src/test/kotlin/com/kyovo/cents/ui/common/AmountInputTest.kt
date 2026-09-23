package com.kyovo.cents.ui.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class AmountInputFilterTest
{
    @ParameterizedTest
    @ValueSource(strings = ["", "1", "12", "12,", "12.", "12,5", "12,50", "12.50", "0,5", "123456789"])
    fun `accepts every state of a well-formed amount while it is being typed`(text: String)
    {
        assertThat(acceptsAmountInput(text)).isTrue()
    }

    @ParameterizedTest
    @ValueSource(strings = ["a", "12a", "-5", "12,505", "1,2,3", "12 €", " 12", "12 ", ",5", "1234567890"])
    fun `rejects letters, signs, spaces, a second separator, a third decimal or too many digits`(text: String)
    {
        assertThat(acceptsAmountInput(text)).isFalse()
    }

    @ParameterizedTest
    @ValueSource(strings = ["12,", "12.", "0,5", "7"])
    fun `every non-zero state the filter accepts is also an amount that can be saved`(text: String)
    {
        // The field must never let the user type something the form then refuses as malformed:
        // "12," (a trailing separator) counts as 12.00.
        assertThat(acceptsAmountInput(text)).isTrue()
        assertThat(parseAmountToCents(text)).isNotNull()
    }
}

class ParseAmountTest
{
    @Test
    fun `zero is refused by default`()
    {
        assertThat(parseAmountToCents("0")).isNull()
        assertThat(parseAmountToCents("0,00")).isNull()
    }

    @Test
    fun `zero is accepted when explicitly allowed, for an account that starts empty`()
    {
        assertThat(parseAmountToCents("0", allowZero = true)).isEqualTo(0L)
        assertThat(parseAmountToCents("0,00", allowZero = true)).isEqualTo(0L)
    }

    @Test
    fun `allowing zero does not loosen anything else`()
    {
        assertThat(parseAmountToCents("-1", allowZero = true)).isNull()
        assertThat(parseAmountToCents("1,234", allowZero = true)).isNull()
        assertThat(parseAmountToCents("abc", allowZero = true)).isNull()
        assertThat(parseAmountToCents("", allowZero = true)).isNull()
    }
}
