package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryAccountRepository
import com.kyovo.cents.application.fakes.InMemoryExchangeRateRepository
import com.kyovo.cents.application.fakes.InMemoryTransactionRepository
import com.kyovo.cents.application.fakes.InMemoryUnitOfWork
import com.kyovo.cents.application.fakes.SequentialTransactionIdGenerator
import com.kyovo.cents.application.fakes.aCurrency
import com.kyovo.cents.application.fakes.aMoney
import com.kyovo.cents.application.fakes.aRecordTransferCommand
import com.kyovo.cents.application.fakes.aTransactionId
import com.kyovo.cents.application.fakes.anAccount
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.application.fakes.assertThatThrownBySuspending
import com.kyovo.cents.domain.exception.ExchangeRateUnavailableException
import com.kyovo.cents.domain.exception.InvalidTransactionAmountException
import com.kyovo.cents.domain.exception.InvalidTransferDestinationAmountException
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.ExchangeRates
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.TransactionCategory
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * A transfer between accounts of different currencies: what leaves the source is not what reaches the
 * destination. The user may type the amount received (the rate of their bank, which is not the ECB's); when they
 * do not, the destination gets the amount worked out with the exchange rates kept on the device. Without
 * either, there is no way to know it, and nothing is recorded.
 */
class RecordTransferServiceCrossCurrencyTest
{
    private val fromAccountId = anAccountId("11111111-1111-1111-1111-111111111111")
    private val toAccountId = anAccountId("22222222-2222-2222-2222-222222222222")

    // 1 EUR = 1.10 USD; the Canadian dollar is not covered.
    private val rates = ExchangeRates(LocalDate.of(2026, 10, 12), mapOf(AccountCurrency.USD to BigDecimal("1.10")))

    private val accounts = InMemoryAccountRepository()
    private val transactions = InMemoryTransactionRepository()
    private val exchangeRates = InMemoryExchangeRateRepository()
    private val service = RecordTransferService(
        accounts,
        transactions,
        SequentialTransactionIdGenerator(
            listOf(
                aTransactionId("33333333-3333-3333-3333-333333333333"),
                aTransactionId("44444444-4444-4444-4444-444444444444")
            )
        ),
        InMemoryUnitOfWork(),
        exchangeRates
    )

    private suspend fun givenAccountsIn(from: String, to: String)
    {
        accounts.save(anAccount(id = fromAccountId, currency = aCurrency(from)))
        accounts.save(anAccount(id = toAccountId, currency = aCurrency(to)))
    }

    private fun command(amount: Long, destinationAmount: Long? = null) = aRecordTransferCommand(
        fromAccountId = fromAccountId,
        toAccountId = toAccountId,
        amount = aMoney(amount),
        destinationAmount = destinationAmount?.let { aMoney(it) }
    )

    // --- The user types the amount received ----------------------------------------------------------------------

    @Test
    fun `credits the destination with the amount the user typed, not with a converted one`() = runTest()
    {
        // GIVEN 100,00 EUR sent, and the bank's own rate gave 109,50 USD (the ECB's would be 110,00)
        givenAccountsIn("EUR", "USD")
        exchangeRates.save(rates)

        // WHEN
        val result = service.record(command(amount = 10_000, destinationAmount = 10_950))

        // THEN
        assertThat(result.debit.signedAmount).isEqualTo(-10_000L)
        assertThat(result.debit.accountId).isEqualTo(fromAccountId)
        assertThat(result.credit.signedAmount).isEqualTo(10_950L)
        assertThat(result.credit.accountId).isEqualTo(toAccountId)
        assertThat(transactions.saved).containsExactly(result.debit, result.credit)
    }

    @Test
    fun `needs no exchange rates when the user typed the amount received`() = runTest()
    {
        // GIVEN nothing was ever fetched
        givenAccountsIn("EUR", "USD")

        // WHEN
        val result = service.record(command(amount = 10_000, destinationAmount = 10_950))

        // THEN
        assertThat(result.credit.signedAmount).isEqualTo(10_950L)
    }

    @Test
    fun `works between currencies that have no rate at all, when the user typed the amount`() = runTest()
    {
        // GIVEN the Canadian dollar is not in the rates
        givenAccountsIn("EUR", "CAD")
        exchangeRates.save(rates)

        // WHEN
        val result = service.record(command(amount = 10_000, destinationAmount = 15_000))

        // THEN
        assertThat(result.credit.signedAmount).isEqualTo(15_000L)
    }

    @Test
    fun `an amount typed in CFA francs is a count of francs`() = runTest()
    {
        // GIVEN
        givenAccountsIn("EUR", "XOF")

        // WHEN 100,00 EUR sent, 65 000 XOF received
        val result = service.record(command(amount = 10_000, destinationAmount = 65_000))

        // THEN
        assertThat(result.credit.signedAmount).isEqualTo(65_000L)
    }

    @Test
    fun `refuses an amount received of zero and saves neither leg`() = runTest()
    {
        // GIVEN
        givenAccountsIn("EUR", "USD")

        // WHEN / THEN
        assertThatThrownBySuspending { service.record(command(amount = 10_000, destinationAmount = 0)) }
            .isInstanceOf(InvalidTransactionAmountException::class.java)
        assertThat(transactions.saved).isEmpty()
    }

    // --- The user typed nothing: the ECB's rate -----------------------------------------------------------------

    @Test
    fun `converts with the exchange rates kept on the device when no amount was typed`() = runTest()
    {
        // GIVEN
        givenAccountsIn("EUR", "USD")
        exchangeRates.save(rates)

        // WHEN 100,00 EUR sent
        val result = service.record(command(amount = 10_000))

        // THEN 110,00 USD received
        assertThat(result.debit.signedAmount).isEqualTo(-10_000L)
        assertThat(result.credit.signedAmount).isEqualTo(11_000L)
    }

    @Test
    fun `converts the other way round too`() = runTest()
    {
        // GIVEN
        givenAccountsIn("USD", "EUR")
        exchangeRates.save(rates)

        // WHEN 110,00 USD sent
        val result = service.record(command(amount = 11_000))

        // THEN 100,00 EUR received
        assertThat(result.credit.signedAmount).isEqualTo(10_000L)
    }

    @Test
    fun `converts into the CFA franc at its fixed rate`() = runTest()
    {
        // GIVEN no rates fetched: the franc is tied to the euro, so euros into francs needs none
        givenAccountsIn("EUR", "XOF")

        // WHEN 100,00 EUR sent
        val result = service.record(command(amount = 10_000))

        // THEN 65 595.7 rounded to the franc
        assertThat(result.credit.signedAmount).isEqualTo(65_596L)
    }

    @Test
    fun `refuses, and saves nothing, when no amount was typed and no rates were ever fetched`() = runTest()
    {
        // GIVEN
        givenAccountsIn("EUR", "USD")

        // WHEN / THEN
        assertThatThrownBySuspending { service.record(command(amount = 10_000)) }
            .isInstanceOf(ExchangeRateUnavailableException::class.java)
        assertThat(transactions.saved).isEmpty()
    }

    @Test
    fun `refuses, and saves nothing, when no amount was typed and the rates do not cover a currency`() = runTest()
    {
        // GIVEN
        givenAccountsIn("EUR", "CAD")
        exchangeRates.save(rates)

        // WHEN / THEN
        assertThatThrownBySuspending { service.record(command(amount = 10_000)) }
            .isInstanceOf(ExchangeRateUnavailableException::class.java)
        assertThat(transactions.saved).isEmpty()
    }

    @Test
    fun `refuses, and saves nothing, when the conversion rounds to nothing`() = runTest()
    {
        // GIVEN 1 XOF is worth 0.15 euro cent: the euro account would receive nothing
        givenAccountsIn("XOF", "EUR")

        // WHEN / THEN
        assertThatThrownBySuspending { service.record(command(amount = 1)) }
            .isInstanceOf(InvalidTransactionAmountException::class.java)
        assertThat(transactions.saved).isEmpty()
    }

    // --- Same currency ----------------------------------------------------------------------------------------

    @Test
    fun `the destination gets the same amount within a currency, with no exchange rate needed`() = runTest()
    {
        // GIVEN
        givenAccountsIn("USD", "USD")

        // WHEN
        val result = service.record(command(amount = 5_000))

        // THEN
        assertThat(result.debit.signedAmount).isEqualTo(-5_000L)
        assertThat(result.credit.signedAmount).isEqualTo(5_000L)
    }

    @Test
    fun `accepts the amount received when it is the amount sent, within a currency`() = runTest()
    {
        // GIVEN
        givenAccountsIn("USD", "USD")

        // WHEN
        val result = service.record(command(amount = 5_000, destinationAmount = 5_000))

        // THEN
        assertThat(result.credit.signedAmount).isEqualTo(5_000L)
    }

    // Within one currency money cannot change value: a different amount received would make some disappear.
    @Test
    fun `refuses a different amount received within a currency, and saves nothing`() = runTest()
    {
        // GIVEN
        givenAccountsIn("EUR", "EUR")

        // WHEN / THEN
        assertThatThrownBySuspending { service.record(command(amount = 5_000, destinationAmount = 4_000)) }
            .isInstanceOf(InvalidTransferDestinationAmountException::class.java)
        assertThat(transactions.saved).isEmpty()
    }

    // --- What the legs look like ------------------------------------------------------------------------------

    @Test
    fun `records an outgoing leg and an incoming leg, with the same title and date`() = runTest()
    {
        // GIVEN
        givenAccountsIn("EUR", "USD")

        // WHEN
        val result = service.record(command(amount = 10_000, destinationAmount = 10_950))

        // THEN
        assertThat(result.debit.category).isEqualTo(TransactionCategory.TRANSFER_OUT)
        assertThat(result.credit.category).isEqualTo(TransactionCategory.TRANSFER_IN)
        assertThat(result.credit.title).isEqualTo(result.debit.title)
        assertThat(result.credit.date).isEqualTo(result.debit.date)
        assertThat(Money(10_950)).isEqualTo(result.credit.amount)
    }
}
