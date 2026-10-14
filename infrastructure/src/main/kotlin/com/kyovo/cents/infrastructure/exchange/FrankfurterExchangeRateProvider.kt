package com.kyovo.cents.infrastructure.exchange

import com.kyovo.cents.domain.model.ExchangeRates
import com.kyovo.cents.domain.port.output.ExchangeRateProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import java.util.concurrent.TimeUnit

/**
 * Gets the latest exchange rates, against the euro, from the Frankfurter API ([baseUrl] is
 * `https://api.frankfurter.dev`; a test points it at a fake server). The answer is read by [parser].
 *
 * It fails with an [java.io.IOException] when the rates cannot be had (no connection, a timeout, an error status
 * as [ExchangeRatesServerException]) and with a [MalformedExchangeRatesException] when the answer is no use.
 * Nothing about the user is sent: one GET, with no cookie and no identifier.
 */
class FrankfurterExchangeRateProvider(
    private val baseUrl: String,
    private val client: OkHttpClient = defaultClient(),
    private val parser: FrankfurterRatesParser = FrankfurterRatesParser()
) : ExchangeRateProvider
{
    companion object
    {
        /** The whole call, from connecting to the last byte, may take ten seconds: it runs in the background. */
        fun defaultClient(): OkHttpClient
        {
            return OkHttpClient.Builder()
                .callTimeout(10, TimeUnit.SECONDS)
                .build()
        }
    }

    override suspend fun fetchLatest(): ExchangeRates
    {
        val request = Request.Builder()
            // providers=ecb: without it Frankfurter blends several sources, and the app's rates are the ECB's.
            .url(
                "${baseUrl.trimEnd('/')}/v2/rates" +
                    "?base=${FrankfurterRatesParser.BASE}&quotes=${FrankfurterRatesParser.QUOTES}&providers=ecb"
            )
            .build()

        // executeAsync suspends instead of blocking a thread, and cancels the call if the coroutine is cancelled.
        client.newCall(request).executeAsync().use { response ->
            if (!response.isSuccessful) throw ExchangeRatesServerException(response.code)

            // Reading the body is blocking input/output: off the caller's thread.
            val body = withContext(Dispatchers.IO) { response.body.string() }
            return parser.parse(body)
        }
    }
}
