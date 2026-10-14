package com.kyovo.cents.infrastructure.persistence.room

import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.ExchangeRates
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class ExchangeRatesEntityMapperTest
{
    private val rates = ExchangeRates(
        LocalDate.of(2026, 10, 12),
        mapOf(AccountCurrency.USD to BigDecimal("1.10"), AccountCurrency.GBP to BigDecimal("0.85"))
    )

    @Test
    fun `goes to the database and comes back as the same rates`()
    {
        assertThat(rates.toEntity().toDomain()).isEqualTo(rates)
    }

    @Test
    fun `is the one row of its table`()
    {
        assertThat(rates.toEntity().id).isEqualTo(ExchangeRatesEntity.ROW_ID)
    }

    @Test
    fun `stores the day as its epoch day, since it is a day and not an instant`()
    {
        assertThat(rates.toEntity().epochDay).isEqualTo(LocalDate.of(2026, 10, 12).toEpochDay())
    }

    @Test
    fun `writes each rate as its currency code and the digits it has, in a stable order`()
    {
        // The order of the map must not decide what is stored: two equal rates give the same text.
        val reversed = ExchangeRates(
            rates.date,
            mapOf(AccountCurrency.GBP to BigDecimal("0.85"), AccountCurrency.USD to BigDecimal("1.10"))
        )

        assertThat(rates.toEntity().rates).isEqualTo("GBP=0.85;USD=1.10")
        assertThat(reversed.toEntity().rates).isEqualTo(rates.toEntity().rates)
    }

    @Test
    fun `writes rates that cover nothing as an empty text`()
    {
        val entity = ExchangeRates(rates.date, emptyMap()).toEntity()

        assertThat(entity.rates).isEmpty()
        assertThat(entity.toDomain().perEuro).isEmpty()
    }

    @Test
    fun `refuses a row whose rates cannot be read, instead of guessing`()
    {
        val broken = ExchangeRatesEntity(epochDay = 20_000, rates = "USD=a lot")

        assertThatThrownBy { broken.toDomain() }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `refuses a row with a currency that does not exist`()
    {
        val broken = ExchangeRatesEntity(epochDay = 20_000, rates = "ZZZ=1.5")

        assertThatThrownBy { broken.toDomain() }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `refuses a row with a rate that is not positive`()
    {
        val broken = ExchangeRatesEntity(epochDay = 20_000, rates = "USD=0")

        assertThatThrownBy { broken.toDomain() }.isInstanceOf(IllegalStateException::class.java)
    }
}
