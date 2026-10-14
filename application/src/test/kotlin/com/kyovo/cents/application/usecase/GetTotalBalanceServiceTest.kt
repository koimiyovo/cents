package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryAccountRepository
import com.kyovo.cents.application.fakes.InMemoryExchangeRateRepository
import com.kyovo.cents.application.fakes.InMemoryMainCurrencyRepository
import com.kyovo.cents.application.fakes.InMemoryTransactionRepository
import com.kyovo.cents.application.fakes.aCurrency
import com.kyovo.cents.application.fakes.aMoney
import com.kyovo.cents.application.fakes.aTransaction
import com.kyovo.cents.application.fakes.aTransactionId
import com.kyovo.cents.application.fakes.anAccount
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.application.fakes.anInstant
import com.kyovo.cents.domain.model.AccountBalance
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.ExchangeRates
import com.kyovo.cents.domain.model.TotalBalance
import com.kyovo.cents.domain.model.TransactionCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * The consolidated balance at the top of the Accounts tab: the active accounts added up in the main currency,
 * the others converted with the rates kept on the device.
 */
class GetTotalBalanceServiceTest
{
    private val accounts = InMemoryAccountRepository()
    private val transactions = InMemoryTransactionRepository()
    private val mainCurrency = InMemoryMainCurrencyRepository(AccountCurrency.EUR)
    private val rates = InMemoryExchangeRateRepository()
    private val service = GetTotalBalanceService(accounts, transactions, mainCurrency, rates)

    // 1 EUR = 1.10 USD; the Canadian dollar is not covered.
    private val monday = ExchangeRates(LocalDate.of(2026, 10, 12), mapOf(AccountCurrency.USD to BigDecimal("1.10")))

    private var transactionNumber = 0

    private fun nextTransactionId() = aTransactionId(UUID(0, (++transactionNumber).toLong()).toString())

    private suspend fun givenAnAccount(id: String, currency: String, balance: Long, archived: Boolean = false): AccountId
    {
        val accountId = anAccountId(id)
        accounts.save(
            anAccount(
                accountId,
                AccountName("Compte $currency $id"),
                currency = aCurrency(currency),
                archivedAt = if (archived) anInstant() else null
            )
        )
        if (balance > 0)
        {
            transactions.save(aTransaction(nextTransactionId(), accountId, aMoney(balance)))
        } else if (balance < 0)
        {
            transactions.save(
                aTransaction(nextTransactionId(), accountId, aMoney(-balance), category = TransactionCategory.EXPENSE)
            )
        }
        return accountId
    }

    private suspend fun total(): TotalBalance = service.observe().first()

    @Test
    fun `adds up the accounts in the main currency`() = runTest()
    {
        givenAnAccount("11111111-1111-1111-1111-111111111111", "EUR", 10_000)
        givenAnAccount("22222222-2222-2222-2222-222222222222", "EUR", 2_500)

        assertThat(total()).isEqualTo(TotalBalance(AccountBalance(12_500), 0))
    }

    @Test
    fun `converts the balance of an account in another currency before adding it`() = runTest()
    {
        givenAnAccount("11111111-1111-1111-1111-111111111111", "EUR", 10_000)
        givenAnAccount("22222222-2222-2222-2222-222222222222", "USD", 11_000)
        rates.save(monday)

        // 100,00 EUR + 110,00 USD (100,00 EUR)
        assertThat(total()).isEqualTo(TotalBalance(AccountBalance(20_000), 0))
    }

    @Test
    fun `converts a negative balance and keeps its sign`() = runTest()
    {
        givenAnAccount("22222222-2222-2222-2222-222222222222", "USD", -11_000)
        rates.save(monday)

        assertThat(total().amount).isEqualTo(AccountBalance(-10_000))
    }

    @Test
    fun `leaves out the archived accounts`() = runTest()
    {
        givenAnAccount("11111111-1111-1111-1111-111111111111", "EUR", 10_000)
        givenAnAccount("22222222-2222-2222-2222-222222222222", "EUR", 4_000, archived = true)

        assertThat(total()).isEqualTo(TotalBalance(AccountBalance(10_000), 0))
    }

    @Test
    fun `counts the accounts it cannot convert yet instead of guessing`() = runTest()
    {
        givenAnAccount("11111111-1111-1111-1111-111111111111", "EUR", 10_000)
        givenAnAccount("22222222-2222-2222-2222-222222222222", "USD", 11_000)

        // no rates fetched: the dollars are not added, and the figure says it is incomplete
        assertThat(total()).isEqualTo(TotalBalance(AccountBalance(10_000), 1))
    }

    @Test
    fun `counts an account in a currency the rates do not cover`() = runTest()
    {
        givenAnAccount("11111111-1111-1111-1111-111111111111", "EUR", 10_000)
        givenAnAccount("33333333-3333-3333-3333-333333333333", "CAD", 7_000)
        rates.save(monday)

        assertThat(total()).isEqualTo(TotalBalance(AccountBalance(10_000), 1))
    }

    @Test
    fun `is zero without any account`() = runTest()
    {
        assertThat(total()).isEqualTo(TotalBalance(AccountBalance(0), 0))
    }

    @Test
    fun `follows a change of the main currency`() = runTest()
    {
        givenAnAccount("11111111-1111-1111-1111-111111111111", "EUR", 10_000)
        givenAnAccount("22222222-2222-2222-2222-222222222222", "USD", 11_000)
        rates.save(monday)

        mainCurrency.save(AccountCurrency.USD)

        // 100,00 EUR is 110,00 USD, plus the 110,00 USD
        assertThat(total().amount).isEqualTo(AccountBalance(22_000))
    }

    @Test
    fun `follows new rates`() = runTest()
    {
        givenAnAccount("22222222-2222-2222-2222-222222222222", "USD", 11_000)
        rates.save(monday)

        rates.save(ExchangeRates(LocalDate.of(2026, 10, 13), mapOf(AccountCurrency.USD to BigDecimal("1.25"))))

        assertThat(total().amount).isEqualTo(AccountBalance(8_800))
    }

    @Test
    fun `follows the transactions`() = runTest()
    {
        val accountId = givenAnAccount("11111111-1111-1111-1111-111111111111", "EUR", 10_000)

        transactions.save(
            aTransaction(nextTransactionId(), accountId, aMoney(3_000), category = TransactionCategory.EXPENSE)
        )

        assertThat(total().amount).isEqualTo(AccountBalance(7_000))
    }

    @Test
    fun `the euro is the main currency until one is chosen`() = runTest()
    {
        val unchosen = GetTotalBalanceService(accounts, transactions, InMemoryMainCurrencyRepository(null), rates)
        givenAnAccount("22222222-2222-2222-2222-222222222222", "USD", 11_000)
        rates.save(monday)

        assertThat(unchosen.observe().first().amount).isEqualTo(AccountBalance(10_000))
    }
}
