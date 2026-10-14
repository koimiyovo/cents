package com.kyovo.cents.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kyovo.cents.domain.port.input.RefreshExchangeRatesUseCase
import com.kyovo.cents.infrastructure.exchange.MalformedExchangeRatesException
import java.io.IOException

/** How a refresh of the exchange rates went, in the terms of WorkManager. */
enum class ExchangeRatesOutcome
{
    SUCCESS,
    RETRY,
    FAILURE,
}

private const val MAX_ATTEMPTS = 3

/**
 * What to do after a refresh: [failure] is what it threw (null when it worked), [runAttemptCount] how many times
 * this run was already tried. A failing network is tried again a little later, a few times; an answer that cannot
 * be read is not (asking again gives the same answer). Either way the next daily run starts afresh.
 */
fun exchangeRatesOutcome(failure: Throwable?, runAttemptCount: Int): ExchangeRatesOutcome
{
    return when
    {
        failure == null                                  -> ExchangeRatesOutcome.SUCCESS
        failure is MalformedExchangeRatesException      -> ExchangeRatesOutcome.FAILURE
        failure is IOException && runAttemptCount < MAX_ATTEMPTS -> ExchangeRatesOutcome.RETRY
        else                                             -> ExchangeRatesOutcome.FAILURE
    }
}

/**
 * The daily refresh of the exchange rates, as thin as the other workers: the rule is in [exchangeRatesOutcome], the
 * work in the use case. Scheduled with the network constraint, so it only runs when there is a connection.
 * Built by [CentsWorkerFactory].
 */
class ExchangeRatesWorker(
    context: Context,
    params: WorkerParameters,
    private val refreshExchangeRates: RefreshExchangeRatesUseCase,
) : CoroutineWorker(context, params)
{
    override suspend fun doWork(): Result
    {
        val failure = try
        {
            refreshExchangeRates.refresh()
            null
        } catch (failure: IOException)
        {
            failure
        } catch (failure: MalformedExchangeRatesException)
        {
            failure
        }

        return when (exchangeRatesOutcome(failure, runAttemptCount))
        {
            ExchangeRatesOutcome.SUCCESS -> Result.success()
            ExchangeRatesOutcome.RETRY   -> Result.retry()
            ExchangeRatesOutcome.FAILURE -> Result.failure()
        }
    }
}
