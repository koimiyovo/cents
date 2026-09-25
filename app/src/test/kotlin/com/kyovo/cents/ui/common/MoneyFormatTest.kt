package com.kyovo.cents.ui.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Every amount on screen goes through these two functions. The French format puts a (no-break) space
 * between thousands and before the euro sign — which kind of space depends on the Java version, so the
 * tests compare with plain spaces (see [plain]) and pin what matters: the digits, the comma, the
 * grouping and the sign.
 */
class MoneyFormatTest
{
    /** Turns the no-break spaces of the French format into plain ones. */
    private fun plain(text: String) = text.replace(' ', ' ').replace(' ', ' ')

    @ParameterizedTest
    @CsvSource(
        "0, '0,00 €'",
        "5, '0,05 €'",
        "50, '0,50 €'",
        "100, '1,00 €'",
        "1250, '12,50 €'",
        "12345, '123,45 €'",
    )
    fun `writes cents as euros with a comma and two decimals`(cents: Long, expected: String)
    {
        assertThat(plain(formatEuroCents(cents))).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "99999, '999,99 €'",
        "100000, '1 000,00 €'",
        "214025, '2 140,25 €'",
        "100000000, '1 000 000,00 €'",
        "99999999999, '999 999 999,99 €'",
    )
    fun `groups the thousands`(cents: Long, expected: String)
    {
        assertThat(plain(formatEuroCents(cents))).isEqualTo(expected)
    }

    // The amount fields take nine digits before the comma at most: that is the largest amount that can
    // ever be shown, and it must come out exact (no floating-point noise in the cents).
    @Test
    fun `the largest amount the app accepts is written exactly`()
    {
        assertThat(plain(formatEuroCents(99_999_999_999L))).isEqualTo("999 999 999,99 €")
    }

    @Test
    fun `puts the euro sign after the amount`()
    {
        val text = plain(formatEuroCents(1_250))

        assertThat(text).endsWith(" €")
        assertThat(text).doesNotContain("$")
    }

    @Test
    fun `a negative amount is written with a minus sign`()
    {
        assertThat(plain(formatEuroCents(-1_250))).isEqualTo("-12,50 €")
    }

    @ParameterizedTest
    @CsvSource(
        "1250, '+12,50 €'",
        "5, '+0,05 €'",
        "245000, '+2 450,00 €'",
        "-1250, '-12,50 €'",
        "-5, '-0,05 €'",
        "-72000, '-720,00 €'",
    )
    fun `a signed amount always shows its sign`(cents: Long, expected: String)
    {
        assertThat(plain(formatSignedEuroCents(cents))).isEqualTo(expected)
    }

    // Zero is neither a gain nor a loss: what a total shows when nothing happened is "0,00 €".
    @Test
    fun `zero is written without a sign`()
    {
        assertThat(plain(formatSignedEuroCents(0))).isEqualTo("0,00 €")
    }

    @Test
    fun `zero is written like the plain amount`()
    {
        assertThat(formatSignedEuroCents(0)).isEqualTo(formatEuroCents(0))
    }

    @Test
    fun `a signed amount is its sign followed by the plain amount`()
    {
        assertThat(formatSignedEuroCents(-214_025)).isEqualTo("-" + formatEuroCents(214_025))
        assertThat(formatSignedEuroCents(214_025)).isEqualTo("+" + formatEuroCents(214_025))
    }

    @Test
    fun `an amount and its opposite differ only by the sign`()
    {
        assertThat(formatSignedEuroCents(-99_999_999_999L).substring(1))
            .isEqualTo(formatSignedEuroCents(99_999_999_999L).substring(1))
    }
}
