package com.kyovo.cents.infrastructure.exchange

import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.ExchangeRates
import com.kyovo.cents.domain.model.Money
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class FrankfurterRatesParserTest
{
    private val parser = FrankfurterRatesParser()

    @Test
    fun `reads the day and the rates of a Frankfurter answer`()
    {
        // GIVEN what GET /v1/latest?base=EUR answers
        val json =
            """{"amount":1.0,"base":"EUR","date":"2026-10-12","rates":{"CAD":1.5,"GBP":0.85,"USD":1.1}}"""

        // WHEN
        val rates = parser.parse(json)

        // THEN the rates are those of that day, against the euro
        assertThat(rates).isEqualTo(
            ExchangeRates(
                date = LocalDate.of(2026, 10, 12),
                perEuro = mapOf(
                    AccountCurrency.CAD to BigDecimal("1.5"),
                    AccountCurrency.GBP to BigDecimal("0.85"),
                    AccountCurrency.USD to BigDecimal("1.1")
                )
            )
        )
    }

    // Triangulation
    @Test
    fun `reads whatever day, currencies and digits the answer has`()
    {
        // GIVEN an answer of another day, without the Canadian dollar
        val json =
            """{"amount":1.0,"base":"EUR","date":"2026-10-13","rates":{"GBP":0.86712,"USD":1.16345}}"""

        // WHEN
        val rates = parser.parse(json)

        // THEN
        assertThat(rates).isEqualTo(
            ExchangeRates(
                date = LocalDate.of(2026, 10, 13),
                perEuro = mapOf(
                    AccountCurrency.GBP to BigDecimal("0.86712"),
                    AccountCurrency.USD to BigDecimal("1.16345")
                )
            )
        )
    }

    // Test 3: Frankfurter publishes about thirty currencies, the app handles five.
    @Test
    fun `ignores the currencies the app does not handle`()
    {
        // GIVEN an answer with the yen, the Swiss franc and a code that is not even a currency
        val json =
            """{"amount":1.0,"base":"EUR","date":"2026-10-12","rates":{"CHF":0.93,"JPY":162.5,"USD":1.1,"ZZZ":3.0}}"""

        // WHEN
        val rates = parser.parse(json)

        // THEN only the dollar is kept
        assertThat(rates.perEuro).containsOnlyKeys(AccountCurrency.USD)
    }

    // The euro is the base, and the CFA franc is tied to it at a fixed rate: neither is taken from the answer.
    @Test
    fun `does not take the euro or the CFA franc from the answer`()
    {
        // GIVEN an answer that lists them anyway
        val json =
            """{"amount":1.0,"base":"EUR","date":"2026-10-12","rates":{"EUR":1.0,"XOF":700.0,"USD":1.1}}"""

        // WHEN
        val rates = parser.parse(json)

        // THEN the franc keeps its fixed rate, whatever the answer says
        assertThat(rates.perEuro).containsOnlyKeys(AccountCurrency.USD)
        assertThat(rates.convert(Money(10_000), AccountCurrency.EUR, AccountCurrency.XOF)).isEqualTo(Money(65_596))
    }

    @Test
    fun `gives rates without any currency when the answer has none of the app's`()
    {
        // GIVEN
        val json = """{"amount":1.0,"base":"EUR","date":"2026-10-12","rates":{"JPY":162.5}}"""

        // WHEN
        val rates = parser.parse(json)

        // THEN
        assertThat(rates.perEuro).isEmpty()
        assertThat(rates.date).isEqualTo(LocalDate.of(2026, 10, 12))
    }

    // A server may add fields one day: what is not read does not matter.
    @Test
    fun `ignores the fields it does not read`()
    {
        // GIVEN
        val json =
            """{"amount":1.0,"base":"EUR","date":"2026-10-12","provider":"ECB","rates":{"USD":1.1},"extra":[1,2]}"""

        // WHEN
        val rates = parser.parse(json)

        // THEN
        assertThat(rates.perEuro).containsOnlyKeys(AccountCurrency.USD)
    }

    // What follows is an answer that cannot be trusted: it must be refused with one clear exception, not
    // with whatever the JSON library or the date parser happens to throw.
    @Test
    fun `refuses an answer that is not JSON`()
    {
        assertThatThrownBy { parser.parse("<html>502 Bad Gateway</html>") }
            .isInstanceOf(MalformedExchangeRatesException::class.java)
    }

    @Test
    fun `refuses an empty answer`()
    {
        assertThatThrownBy { parser.parse("") }
            .isInstanceOf(MalformedExchangeRatesException::class.java)
    }

    @Test
    fun `refuses an answer that is not an object`()
    {
        assertThatThrownBy { parser.parse("[1,2,3]") }
            .isInstanceOf(MalformedExchangeRatesException::class.java)
    }

    @Test
    fun `refuses an answer whose base is not the euro`()
    {
        // GIVEN rates against the dollar: reading them as against the euro would be wrong
        val json = """{"amount":1.0,"base":"USD","date":"2026-10-12","rates":{"EUR":0.9}}"""

        // WHEN / THEN
        assertThatThrownBy { parser.parse(json) }
            .isInstanceOf(MalformedExchangeRatesException::class.java)
    }

    @Test
    fun `refuses an answer without a base`()
    {
        assertThatThrownBy { parser.parse("""{"date":"2026-10-12","rates":{"USD":1.1}}""") }
            .isInstanceOf(MalformedExchangeRatesException::class.java)
    }

    @Test
    fun `refuses an answer without a date`()
    {
        assertThatThrownBy { parser.parse("""{"base":"EUR","rates":{"USD":1.1}}""") }
            .isInstanceOf(MalformedExchangeRatesException::class.java)
    }

    @Test
    fun `refuses an answer whose date is not a date`()
    {
        assertThatThrownBy { parser.parse("""{"base":"EUR","date":"yesterday","rates":{"USD":1.1}}""") }
            .isInstanceOf(MalformedExchangeRatesException::class.java)
    }

    @Test
    fun `refuses an answer without rates`()
    {
        assertThatThrownBy { parser.parse("""{"base":"EUR","date":"2026-10-12"}""") }
            .isInstanceOf(MalformedExchangeRatesException::class.java)
    }

    @Test
    fun `refuses an answer whose rates are not an object`()
    {
        assertThatThrownBy { parser.parse("""{"base":"EUR","date":"2026-10-12","rates":[1.1]}""") }
            .isInstanceOf(MalformedExchangeRatesException::class.java)
    }

    @Test
    fun `refuses a rate that is not a number`()
    {
        assertThatThrownBy { parser.parse("""{"base":"EUR","date":"2026-10-12","rates":{"USD":"a lot"}}""") }
            .isInstanceOf(MalformedExchangeRatesException::class.java)
    }

    @Test
    fun `refuses a rate of zero or less`()
    {
        assertThatThrownBy { parser.parse("""{"base":"EUR","date":"2026-10-12","rates":{"USD":0}}""") }
            .isInstanceOf(MalformedExchangeRatesException::class.java)
        assertThatThrownBy { parser.parse("""{"base":"EUR","date":"2026-10-12","rates":{"USD":-1.1}}""") }
            .isInstanceOf(MalformedExchangeRatesException::class.java)
    }

    @Test
    fun `a rate that is ignored is not checked`()
    {
        // GIVEN a yen rate that is garbage: the yen is not handled, so it is not looked at
        val json = """{"base":"EUR","date":"2026-10-12","rates":{"JPY":"n/a","USD":1.1}}"""

        // THEN
        assertThat(parser.parse(json).perEuro).containsOnlyKeys(AccountCurrency.USD)
    }

    @Test
    fun `says what is wrong in the message and keeps the cause`()
    {
        // WHEN / THEN
        assertThatThrownBy { parser.parse("""{"base":"EUR","date":"yesterday","rates":{}}""") }
            .isInstanceOf(MalformedExchangeRatesException::class.java)
            .hasMessageContaining("Frankfurter")
            .hasCauseInstanceOf(java.time.DateTimeException::class.java)
    }
}
