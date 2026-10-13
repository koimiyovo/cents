package com.kyovo.cents.work

import com.kyovo.cents.infrastructure.exchange.ExchangeRatesServerException
import com.kyovo.cents.infrastructure.exchange.MalformedExchangeRatesException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * What the daily refresh of the exchange rates does with how it went: the rule is kept out of the worker, which
 * stays a thin shell, so that it can be tested without WorkManager. A network that fails is worth trying again a
 * little later; an answer that cannot be read will not get better by asking again at once.
 */
class ExchangeRatesOutcomeTest
{
    @Test
    fun `is a success when the rates were fetched`()
    {
        assertThat(exchangeRatesOutcome(failure = null, runAttemptCount = 0)).isEqualTo(ExchangeRatesOutcome.SUCCESS)
    }

    @Test
    fun `tries again when there is no connection`()
    {
        assertThat(exchangeRatesOutcome(IOException("no network"), runAttemptCount = 0))
            .isEqualTo(ExchangeRatesOutcome.RETRY)
    }

    @Test
    fun `tries again when the server is too slow`()
    {
        assertThat(exchangeRatesOutcome(SocketTimeoutException("timeout"), runAttemptCount = 1))
            .isEqualTo(ExchangeRatesOutcome.RETRY)
    }

    @Test
    fun `tries again when the server fails`()
    {
        assertThat(exchangeRatesOutcome(ExchangeRatesServerException(503), runAttemptCount = 2))
            .isEqualTo(ExchangeRatesOutcome.RETRY)
    }

    // Three tries in a row failed: the next daily run will do it again, so there is no point in insisting.
    @Test
    fun `gives up for today after three attempts`()
    {
        assertThat(exchangeRatesOutcome(IOException("no network"), runAttemptCount = 3))
            .isEqualTo(ExchangeRatesOutcome.FAILURE)
    }

    @Test
    fun `fails without trying again when the answer cannot be read`()
    {
        assertThat(exchangeRatesOutcome(MalformedExchangeRatesException("not JSON"), runAttemptCount = 0))
            .isEqualTo(ExchangeRatesOutcome.FAILURE)
    }

    @Test
    fun `is a success even after earlier attempts failed`()
    {
        assertThat(exchangeRatesOutcome(failure = null, runAttemptCount = 2)).isEqualTo(ExchangeRatesOutcome.SUCCESS)
    }
}
