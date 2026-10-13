package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryAccountRepository
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
import com.kyovo.cents.domain.model.TransactionCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The totals only see the accounts held in the main currency: without exchange rates, dollars cannot be added
 * to euros. Everything but the observed list is the real repository's.
 */
class MainCurrencyTransactionRepositoryTest
{
    private val transactions = InMemoryTransactionRepository()
    private val accounts = InMemoryAccountRepository()
    private val mainCurrency = InMemoryMainCurrencyRepository(AccountCurrency.EUR)
    private val repository = MainCurrencyTransactionRepository(transactions, accounts, mainCurrency)

    private val euroAccount = anAccountId("11111111-1111-1111-1111-111111111111")
    private val dollarAccount = anAccountId("22222222-2222-2222-2222-222222222222")
    private val francAccount = anAccountId("33333333-3333-3333-3333-333333333333")

    private val euroDeposit = aTransactionId("a0000000-0000-0000-0000-000000000001")
    private val dollarDeposit = aTransactionId("a0000000-0000-0000-0000-000000000002")
    private val francDeposit = aTransactionId("a0000000-0000-0000-0000-000000000003")

    private suspend fun givenOneAccountPerCurrency()
    {
        accounts.save(anAccount(euroAccount, AccountName("Euros"), currency = aCurrency("EUR")))
        accounts.save(anAccount(dollarAccount, AccountName("Dollars"), currency = aCurrency("USD")))
        accounts.save(anAccount(francAccount, AccountName("Francs"), currency = aCurrency("XOF")))
        transactions.save(aTransaction(euroDeposit, euroAccount, aMoney(100)))
        transactions.save(aTransaction(dollarDeposit, dollarAccount, aMoney(200)))
        transactions.save(aTransaction(francDeposit, francAccount, aMoney(300)))
    }

    private suspend fun observedIds() = repository.observeAll().first().map { it.id }

    @Test
    fun `only the transactions of accounts in the main currency are observed`() = runTest()
    {
        givenOneAccountPerCurrency()

        assertThat(observedIds()).containsExactly(euroDeposit)
    }

    @Test
    fun `changing the main currency changes what is observed`() = runTest()
    {
        givenOneAccountPerCurrency()

        mainCurrency.save(AccountCurrency.XOF)
        assertThat(observedIds()).containsExactly(francDeposit)

        mainCurrency.save(AccountCurrency.USD)
        assertThat(observedIds()).containsExactly(dollarDeposit)
    }

    @Test
    fun `the euro is the main currency until one is chosen`() = runTest()
    {
        val unchosen = MainCurrencyTransactionRepository(transactions, accounts, InMemoryMainCurrencyRepository(null))
        givenOneAccountPerCurrency()

        assertThat(unchosen.observeAll().first().map { it.id }).containsExactly(euroDeposit)
    }

    @Test
    fun `a transaction whose account is unknown is left out`() = runTest()
    {
        givenOneAccountPerCurrency()
        transactions.save(
            aTransaction(aTransactionId("a0000000-0000-0000-0000-000000000009"), anAccountId("99999999-9999-9999-9999-999999999999")),
        )

        assertThat(observedIds()).containsExactly(euroDeposit)
    }

    @Test
    fun `a transaction added later shows up`() = runTest()
    {
        givenOneAccountPerCurrency()
        val expense = aTransactionId("a0000000-0000-0000-0000-000000000004")

        transactions.save(aTransaction(expense, euroAccount, aMoney(50), category = TransactionCategory.EXPENSE))

        assertThat(observedIds()).containsExactly(euroDeposit, expense)
    }

    @Test
    fun `everything else is the real repository's, whatever the currency`() = runTest()
    {
        givenOneAccountPerCurrency()

        assertThat(repository.findAll().map { it.id }).containsExactly(euroDeposit, dollarDeposit, francDeposit)
        assertThat(repository.findById(dollarDeposit)).isNotNull()

        repository.deleteById(dollarDeposit)
        assertThat(transactions.findAll().map { it.id }).containsExactly(euroDeposit, francDeposit)
    }
}
