package com.kyovo.cents.infrastructure.exchange

import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.ExchangeRates
import com.kyovo.cents.infrastructure.exchange.*
import com.kyovo.cents.infrastructure.persistence.realTime
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * The client of the Frankfurter API, tested against a fake HTTP server on this machine: a real socket and real
 * HTTP, so what is checked is what would go over the wire, without the network and without the real service.
 */
class FrankfurterExchangeRateProviderTest
{
    private val server = MockWebServer()

    @BeforeEach
    fun startServer()
    {
        server.start()
    }

    @AfterEach
    fun stopServer()
    {
        server.close()
    }

    private fun provider() = FrankfurterExchangeRateProvider(baseUrl = server.url("/").toString())

    @Test
    fun `fetches the latest rates the server answers`() = realTime()
    {
        // GIVEN a server that answers like Frankfurter
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/json")
                .body("""{"amount":1.0,"base":"EUR","date":"2026-10-12","rates":{"CAD":1.5,"GBP":0.85,"USD":1.1}}""")
                .build()
        )

        // WHEN
        val rates = provider().fetchLatest()

        // THEN they are the rates of that day, read by the parser
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
    fun `gives the rates of whatever the server answers`() = realTime()
    {
        // GIVEN a server that answers with another day, and without the Canadian dollar
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/json")
                .body("""{"amount":1.0,"base":"EUR","date":"2026-10-13","rates":{"GBP":0.86712,"USD":1.16345}}""")
                .build()
        )

        // WHEN
        val rates = provider().fetchLatest()

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

    // --- The request that goes over the wire ------------------------------------------------------------------

    private fun anAnswer() = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body("""{"amount":1.0,"base":"EUR","date":"2026-10-12","rates":{"USD":1.1}}""")
        .build()

    @Test
    fun `asks for the latest rates against the euro, with one GET and nothing else`() = realTime()
    {
        // GIVEN
        server.enqueue(anAnswer())

        // WHEN
        provider().fetchLatest()

        // THEN
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.url.encodedPath).isEqualTo("/v1/latest")
        assertThat(request.url.query).isEqualTo("base=EUR")
        assertThat(request.body).isNull()
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `works whether the base URL ends with a slash or not`() = realTime()
    {
        // GIVEN
        server.enqueue(anAnswer())
        server.enqueue(anAnswer())
        val withoutSlash = server.url("/").toString().trimEnd('/')

        // WHEN
        FrankfurterExchangeRateProvider(baseUrl = withoutSlash).fetchLatest()
        FrankfurterExchangeRateProvider(baseUrl = "$withoutSlash/").fetchLatest()

        // THEN both reach the same path
        assertThat(server.takeRequest().url.encodedPath).isEqualTo("/v1/latest")
        assertThat(server.takeRequest().url.encodedPath).isEqualTo("/v1/latest")
    }

    // --- What goes wrong -------------------------------------------------------------------------------------

    // A server error is not an unreadable answer: the body (often an HTML page) is not even looked at, and the
    // exception is an IOException like the other failures of the network, so a caller retries them all alike.
    @Test
    fun `fails with the status when the server answers with an error`() = realTime()
    {
        // GIVEN
        server.enqueue(MockResponse.Builder().code(500).body("<html>Internal Server Error</html>").build())

        // WHEN / THEN
        assertThatThrownBy { runBlocking { provider().fetchLatest() } }
            .isInstanceOf(ExchangeRatesServerException::class.java)
            .isInstanceOf(IOException::class.java)
            .hasFieldOrPropertyWithValue("statusCode", 500)
    }

    @Test
    fun `fails with the status when the answer is not found`() = realTime()
    {
        // GIVEN
        server.enqueue(MockResponse.Builder().code(404).body("""{"message":"not found"}""").build())

        // WHEN / THEN
        assertThatThrownBy { runBlocking { provider().fetchLatest() } }
            .isInstanceOf(ExchangeRatesServerException::class.java)
            .hasFieldOrPropertyWithValue("statusCode", 404)
    }

    @Test
    fun `fails with the status when there are too many requests`() = realTime()
    {
        // GIVEN
        server.enqueue(MockResponse.Builder().code(429).build())

        // WHEN / THEN
        assertThatThrownBy { runBlocking { provider().fetchLatest() } }
            .isInstanceOf(ExchangeRatesServerException::class.java)
            .hasFieldOrPropertyWithValue("statusCode", 429)
    }

    @Test
    fun `refuses an answer that is a success but cannot be read`() = realTime()
    {
        // GIVEN a captive portal or a proxy answering 200 with a page
        server.enqueue(MockResponse.Builder().code(200).body("<html>Please log in to the Wi-Fi</html>").build())

        // WHEN / THEN
        assertThatThrownBy { runBlocking { provider().fetchLatest() } }
            .isInstanceOf(MalformedExchangeRatesException::class.java)
    }

    @Test
    fun `refuses a success with no body`() = realTime()
    {
        // GIVEN
        server.enqueue(MockResponse.Builder().code(200).build())

        // WHEN / THEN
        assertThatThrownBy { runBlocking { provider().fetchLatest() } }
            .isInstanceOf(MalformedExchangeRatesException::class.java)
    }

    @Test
    fun `fails with an IOException when nobody answers at that address`() = realTime()
    {
        // GIVEN a server that is not there any more
        val provider = provider()
        server.close()

        // WHEN / THEN
        assertThatThrownBy { runBlocking { provider.fetchLatest() } }
            .isInstanceOf(IOException::class.java)
    }

    @Test
    fun `gives up on a server that is too slow`() = realTime()
    {
        // GIVEN a server that takes 3 seconds to answer, and a client that waits half a second
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"base":"EUR","date":"2026-10-12","rates":{}}""")
                .headersDelay(3, TimeUnit.SECONDS)
                .build()
        )
        val impatient = FrankfurterExchangeRateProvider(
            baseUrl = server.url("/").toString(),
            client = OkHttpClient.Builder().callTimeout(500, TimeUnit.MILLISECONDS).build()
        )

        // WHEN / THEN
        assertThatThrownBy { runBlocking { impatient.fetchLatest() } }
            .isInstanceOf(IOException::class.java)
    }

    @Test
    fun `waits ten seconds at most by default, so a refresh in the background cannot hang`()
    {
        // WHEN the default client of the provider is looked at
        val timeouts = FrankfurterExchangeRateProvider.defaultClient()

        // THEN
        assertThat(timeouts.callTimeoutMillis).isEqualTo(10_000)
    }
}
