package com.kyovo.cents.infrastructure.exchange

import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.infrastructure.persistence.realTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The real Frankfurter API, with the real client: it tells when the service changes its format or drops a
 * currency, which the tests against a fake server cannot see. It needs the network and the day's answer, so it
 * runs only where `FRANKFURTER_LIVE=1` (the scheduled check of `.github/workflows/frankfurter-check.yml`) and is
 * skipped everywhere else, `./gradlew test` included.
 */
@EnabledIfEnvironmentVariable(named = "FRANKFURTER_LIVE", matches = "1")
class FrankfurterLiveTest
{
    // The European Central Bank publishes on working days: a long weekend, or Christmas, must not raise the alarm.
    private val oldestAcceptableAnswer = 7L

    @Test
    fun `the real API still answers what the app reads`() = realTime()
    {
        // WHEN the rates are asked from the real service
        val rates = FrankfurterExchangeRateProvider("https://api.frankfurter.dev").fetchLatest()

        // THEN every currency fetched is there, with a usable rate
        assertThat(rates.perEuro).containsOnlyKeys(AccountCurrency.CAD, AccountCurrency.GBP, AccountCurrency.USD)
        assertThat(rates.perEuro.values).allSatisfy { assertThat(it).isGreaterThan(BigDecimal.ZERO) }

        // AND they are recent
        val age = ChronoUnit.DAYS.between(rates.date, LocalDate.now())
        assertThat(age).isBetween(-1L, oldestAcceptableAnswer)
    }
}
