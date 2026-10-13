package com.kyovo.cents.ui.common

import com.kyovo.cents.domain.model.AccountCurrency
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The kind of space between the digits depends on the JDK, so amounts are compared with plain spaces. */
private fun String.plain() = replace(Regex("[\\u00A0\\u202F]"), " ")

class MoneyFormatCurrencyTest
{
    @Test
    fun `dollars keep their two decimals and carry their own symbol`()
    {
        val text = formatMoney(123_456, AccountCurrency.USD).plain()

        assertThat(text).startsWith("1 234,56")
        assertThat(text).doesNotContain("€")
        assertThat(text).contains("$")
    }

    @Test
    fun `the CFA franc has no decimals - the amount is a count of francs`()
    {
        val text = formatMoney(1_250, AccountCurrency.XOF).plain()

        assertThat(text).startsWith("1 250")
        assertThat(text).doesNotContain(",")
        assertThat(text).contains("CFA")
    }

    @Test
    fun `a CFA franc amount is not divided by a hundred`()
    {
        assertThat(formatMoney(500_000, AccountCurrency.XOF).plain()).startsWith("500 000")
    }

    @Test
    fun `the euro is unchanged`()
    {
        assertThat(formatMoney(214_025, AccountCurrency.EUR).plain()).isEqualTo("2 140,25 €")
    }

    @Test
    fun `signed amounts carry the currency too, and zero has no sign`()
    {
        assertThat(formatSignedMoney(-1_250, AccountCurrency.XOF).plain()).startsWith("-1 250")
        assertThat(formatSignedMoney(1_250, AccountCurrency.XOF).plain()).startsWith("+1 250")
        assertThat(formatSignedMoney(0, AccountCurrency.XOF).plain()).doesNotStartWith("+").doesNotStartWith("-")
        assertThat(formatSignedMoney(-1_250, AccountCurrency.USD).plain()).startsWith("-12,50")
    }

    @Test
    fun `a huge amount is formatted exactly, not through a double`()
    {
        // 9 007 199 254 740 993 is the first whole number a Double cannot hold.
        assertThat(formatMoney(9_007_199_254_740_993, AccountCurrency.XOF).plain()).startsWith("9 007 199 254 740 993")
    }

    @Test
    fun `Canadian dollars and pounds keep their two decimals and their own symbol`()
    {
        val canadian = formatMoney(123_456, AccountCurrency.CAD).plain()
        val pounds = formatMoney(123_456, AccountCurrency.GBP).plain()

        assertThat(canadian).startsWith("1 234,56").contains("$").doesNotContain("€")
        assertThat(pounds).startsWith("1 234,56").contains("£").doesNotContain("€")
        // The two dollars are told apart: "$" alone would not say which one.
        assertThat(canadian).isNotEqualTo(formatMoney(123_456, AccountCurrency.USD).plain())
    }
}
