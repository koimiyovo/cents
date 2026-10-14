package com.kyovo.cents.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AccountCurrencyTest
{
    @Test
    fun `the app offers its five currencies in alphabetical order of their codes`()
    {
        assertThat(AccountCurrency.SUPPORTED.map { it.value.currencyCode })
            .containsExactly("CAD", "EUR", "GBP", "USD", "XOF")
    }

    @Test
    fun `the euro, the dollars and the pound have two decimals, the CFA franc none`()
    {
        assertThat(AccountCurrency.EUR.fractionDigits).isEqualTo(2)
        assertThat(AccountCurrency.USD.fractionDigits).isEqualTo(2)
        assertThat(AccountCurrency.CAD.fractionDigits).isEqualTo(2)
        assertThat(AccountCurrency.GBP.fractionDigits).isEqualTo(2)
        assertThat(AccountCurrency.XOF.fractionDigits).isEqualTo(0)
    }

    @Test
    fun `two currencies with the same code are equal`()
    {
        assertThat(AccountCurrency(java.util.Currency.getInstance("USD"))).isEqualTo(AccountCurrency.USD)
        assertThat(AccountCurrency.USD).isNotEqualTo(AccountCurrency.EUR)
    }
}
