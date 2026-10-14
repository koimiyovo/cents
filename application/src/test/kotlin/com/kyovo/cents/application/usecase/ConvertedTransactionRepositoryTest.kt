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
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.ExchangeRates
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * The transactions as the totals see them: every amount in the main currency. Those of an account in another
 * currency are converted with the rates kept on the device; when there is no rate for them (none fetched yet,
 * or a currency the rates do not cover), they are left out rather than added at a wrong value.
 */
class ConvertedTransactionRepositoryTest
{
    private val transactions = InMemoryTransactionRepository()
    private val accounts = InMemoryAccountRepository()
    private val mainCurrency = InMemoryMainCurrencyRepository(AccountCurrency.EUR)
    private val rates = InMemoryExchangeRateRepository()
    private val repository = ConvertedTransactionRepository(transactions, accounts, mainCurrency, rates)

    private val euroAccount = anAccountId("11111111-1111-1111-1111-111111111111")
    private val dollarAccount = anAccountId("22222222-2222-2222-2222-222222222222")
    private val canadianAccount = anAccountId("33333333-3333-3333-3333-333333333333")

    private val euroExpense = aTransactionId("a0000000-0000-0000-0000-000000000001")
    private val dollarExpense = aTransactionId("a0000000-0000-0000-0000-000000000002")
    private val canadianExpense = aTransactionId("a0000000-0000-0000-0000-000000000003")

    // 1 EUR = 1.10 USD; the Canadian dollar is not covered.
    private val monday = ExchangeRates(LocalDate.of(2026, 10, 12), mapOf(AccountCurrency.USD to BigDecimal("1.10")))

    private suspend fun givenAnAccountPerCurrency()
    {
        accounts.save(anAccount(euroAccount, AccountName("Euros"), currency = aCurrency("EUR")))
        accounts.save(anAccount(dollarAccount, AccountName("Dollars"), currency = aCurrency("USD")))
        accounts.save(anAccount(canadianAccount, AccountName("Canadiens"), currency = aCurrency("CAD")))
        transactions.save(expense(euroExpense, euroAccount, 5_000))
        transactions.save(expense(dollarExpense, dollarAccount, 11_000))
        transactions.save(expense(canadianExpense, canadianAccount, 7_000))
    }

    private fun expense(id: com.kyovo.cents.domain.model.TransactionId, accountId: com.kyovo.cents.domain.model.AccountId, cents: Long) =
        aTransaction(id, accountId, aMoney(cents), category = TransactionCategory.EXPENSE)

    private suspend fun observed(): List<Transaction> = repository.observeAll().first()

    private suspend fun amountOf(id: com.kyovo.cents.domain.model.TransactionId): Money? =
        observed().firstOrNull { it.id == id }?.amount

    @Test
    fun `leaves the transactions in the main currency as they are`() = runTest()
    {
        givenAnAccountPerCurrency()
        rates.save(monday)

        assertThat(amountOf(euroExpense)).isEqualTo(Money(5_000))
    }

    @Test
    fun `converts a transaction of another currency into the main currency`() = runTest()
    {
        givenAnAccountPerCurrency()
        rates.save(monday)

        // 110,00 USD is 100,00 EUR
        assertThat(amountOf(dollarExpense)).isEqualTo(Money(10_000))
    }

    @Test
    fun `keeps everything but the amount of a converted transaction`() = runTest()
    {
        givenAnAccountPerCurrency()
        rates.save(monday)

        val converted = observed().first { it.id == dollarExpense }
        val original = transactions.findById(dollarExpense)!!

        assertThat(converted).isEqualTo(original.withAmount(Money(10_000)))
        assertThat(converted.signedAmount).isEqualTo(-10_000L)
    }

    @Test
    fun `converts into the CFA franc when it is the main currency`() = runTest()
    {
        givenAnAccountPerCurrency()
        rates.save(monday)
        mainCurrency.save(AccountCurrency.XOF)

        // 50,00 EUR is 32 798 XOF (32 797.85), and 110,00 USD is 100,00 EUR, that is 65 596 XOF
        assertThat(amountOf(euroExpense)).isEqualTo(Money(32_798))
        assertThat(amountOf(dollarExpense)).isEqualTo(Money(65_596))
    }

    @Test
    fun `leaves out the other currencies while no rates have been fetched`() = runTest()
    {
        givenAnAccountPerCurrency()

        assertThat(observed().map { it.id }).containsExactly(euroExpense)
    }

    @Test
    fun `leaves out a currency the rates do not cover, and converts the others`() = runTest()
    {
        givenAnAccountPerCurrency()
        rates.save(monday)

        assertThat(observed().map { it.id }).containsExactly(euroExpense, dollarExpense)
    }

    @Test
    fun `follows a change of the main currency`() = runTest()
    {
        givenAnAccountPerCurrency()
        rates.save(monday)

        mainCurrency.save(AccountCurrency.USD)

        // 50,00 EUR is 55,00 USD; the dollars stay as they are
        assertThat(amountOf(euroExpense)).isEqualTo(Money(5_500))
        assertThat(amountOf(dollarExpense)).isEqualTo(Money(11_000))
    }

    @Test
    fun `follows new rates`() = runTest()
    {
        givenAnAccountPerCurrency()
        rates.save(monday)

        rates.save(ExchangeRates(LocalDate.of(2026, 10, 13), mapOf(AccountCurrency.USD to BigDecimal("1.25"))))

        // 110,00 USD is now 88,00 EUR
        assertThat(amountOf(dollarExpense)).isEqualTo(Money(8_800))
    }

    @Test
    fun `the euro is the main currency until one is chosen`() = runTest()
    {
        val unchosen = ConvertedTransactionRepository(transactions, accounts, InMemoryMainCurrencyRepository(null), rates)
        givenAnAccountPerCurrency()
        rates.save(monday)

        assertThat(unchosen.observeAll().first().first { it.id == dollarExpense }.amount).isEqualTo(Money(10_000))
    }

    @Test
    fun `leaves out a transaction whose account is unknown`() = runTest()
    {
        givenAnAccountPerCurrency()
        rates.save(monday)
        val orphan = aTransactionId("a0000000-0000-0000-0000-000000000009")
        transactions.save(expense(orphan, anAccountId("99999999-9999-9999-9999-999999999999"), 100))

        assertThat(observed().map { it.id }).doesNotContain(orphan)
    }

    @Test
    fun `everything else is the real repository's, in the original amounts`() = runTest()
    {
        givenAnAccountPerCurrency()
        rates.save(monday)

        assertThat(repository.findById(dollarExpense)!!.amount).isEqualTo(Money(11_000))
        assertThat(repository.findAll().map { it.amount }).containsExactly(Money(5_000), Money(11_000), Money(7_000))
    }
}
