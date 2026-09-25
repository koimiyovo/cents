package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryAccountRepository
import com.kyovo.cents.application.fakes.InMemoryTransactionRepository
import com.kyovo.cents.application.fakes.aMoney
import com.kyovo.cents.application.fakes.anAccount
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.application.fakes.anInstant
import com.kyovo.cents.application.fakes.aTransaction
import com.kyovo.cents.application.fakes.aTransactionId
import com.kyovo.cents.domain.model.AccountBalance
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.TransactionCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * A balance is never stored: it is the sum of an account's transactions, so it is *observed* — it is
 * emitted now and again whenever the accounts or the transactions change it. [observe] follows one
 * account (null while there is no such account); [observeAll] follows every account at once, which
 * is what a list of accounts needs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GetAccountBalanceServiceTest
{
    private val accountRepository = InMemoryAccountRepository()
    private val transactionRepository = InMemoryTransactionRepository()
    private val service = GetAccountBalanceService(accountRepository, transactionRepository)

    private val accountId = anAccountId("11111111-1111-1111-1111-111111111111")
    private val otherId = anAccountId("22222222-2222-2222-2222-222222222222")

    private suspend fun record(suffix: Int, on: AccountId, cents: Long, category: TransactionCategory) =
        transactionRepository.save(
            aTransaction(
                id = aTransactionId("33333333-3333-3333-3333-33333333333$suffix"),
                accountId = on,
                amount = aMoney(cents),
                category = category,
            ),
        )

    // ------------------------------------------------------------------ one account

    @Test
    fun `emits the account's balance as the sum of its transactions`() = runTest()
    {
        // GIVEN
        accountRepository.save(anAccount(id = accountId))
        record(1, accountId, 10_000, TransactionCategory.INITIAL_DEPOSIT)
        record(2, accountId, 5_000, TransactionCategory.INCOME)
        record(3, accountId, 3_000, TransactionCategory.EXPENSE)

        // WHEN / THEN
        assertThat(service.observe(accountId).first()).isEqualTo(AccountBalance(12_000))
    }

    @Test
    fun `emits a zero balance when the account has no transactions`() = runTest()
    {
        // GIVEN
        accountRepository.save(anAccount(id = accountId))

        // WHEN / THEN
        assertThat(service.observe(accountId).first()).isEqualTo(AccountBalance(0))
    }

    @Test
    fun `does not include transactions belonging to other accounts`() = runTest()
    {
        // GIVEN
        accountRepository.save(anAccount(id = accountId))
        record(1, accountId, 1_000, TransactionCategory.INITIAL_DEPOSIT)
        record(2, otherId, 99_000, TransactionCategory.INITIAL_DEPOSIT)

        // WHEN / THEN
        assertThat(service.observe(accountId).first()).isEqualTo(AccountBalance(1_000))
    }

    @Test
    fun `emits null when the account does not exist`() = runTest()
    {
        assertThat(service.observe(accountId).first()).isNull()
    }

    @Test
    fun `follows the balance as transactions are recorded, changed and deleted`() = runTest()
    {
        // GIVEN a page showing the balance
        accountRepository.save(anAccount(id = accountId))
        val emissions = mutableListOf<AccountBalance?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler))
        {
            service.observe(accountId).collect { emissions += it }
        }

        // WHEN
        record(1, accountId, 10_000, TransactionCategory.INITIAL_DEPOSIT)
        record(2, accountId, 3_000, TransactionCategory.EXPENSE)
        record(2, accountId, 4_000, TransactionCategory.EXPENSE)
        transactionRepository.deleteById(aTransactionId("33333333-3333-3333-3333-333333333332"))

        // THEN
        assertThat(emissions).containsExactly(
            AccountBalance(0),
            AccountBalance(10_000),
            AccountBalance(7_000),
            AccountBalance(6_000),
            AccountBalance(10_000),
        )
    }

    // A page showing one balance has nothing to redraw when another account moves.
    @Test
    fun `does not emit again for a transaction of another account`() = runTest()
    {
        // GIVEN
        accountRepository.save(anAccount(id = accountId))
        accountRepository.save(anAccount(id = otherId))
        val emissions = mutableListOf<AccountBalance?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler))
        {
            service.observe(accountId).collect { emissions += it }
        }

        // WHEN
        record(1, otherId, 5_000, TransactionCategory.INCOME)

        // THEN
        assertThat(emissions).containsExactly(AccountBalance(0))
    }

    @Test
    fun `does not emit again for a transaction that leaves the balance as it was`() = runTest()
    {
        // GIVEN a transaction edited without changing its amount
        accountRepository.save(anAccount(id = accountId))
        record(1, accountId, 5_000, TransactionCategory.INCOME)
        val emissions = mutableListOf<AccountBalance?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler))
        {
            service.observe(accountId).collect { emissions += it }
        }

        // WHEN
        record(1, accountId, 5_000, TransactionCategory.INCOME)

        // THEN
        assertThat(emissions).containsExactly(AccountBalance(5_000))
    }

    @Test
    fun `emits null once the account is deleted, and its balance once it is created`() = runTest()
    {
        // GIVEN a page opened on an id nothing has yet
        val emissions = mutableListOf<AccountBalance?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler))
        {
            service.observe(accountId).collect { emissions += it }
        }

        // WHEN
        accountRepository.save(anAccount(id = accountId))
        record(1, accountId, 2_000, TransactionCategory.INITIAL_DEPOSIT)
        accountRepository.deleteById(accountId)

        // THEN
        assertThat(emissions).containsExactly(null, AccountBalance(0), AccountBalance(2_000), null)
    }

    // ------------------------------------------------------------------ every account

    @Test
    fun `emits a balance for every account, archived ones and empty ones included`() = runTest()
    {
        // GIVEN
        accountRepository.save(anAccount(id = accountId))
        accountRepository.save(anAccount(id = otherId, archivedAt = anInstant()))
        record(1, accountId, 10_000, TransactionCategory.INITIAL_DEPOSIT)
        record(2, accountId, 2_500, TransactionCategory.EXPENSE)

        // WHEN / THEN
        assertThat(service.observeAll().first()).containsExactlyInAnyOrderEntriesOf(
            mapOf(accountId to AccountBalance(7_500), otherId to AccountBalance(0)),
        )
    }

    @Test
    fun `emits an empty map when there is no account`() = runTest()
    {
        assertThat(service.observeAll().first()).isEmpty()
    }

    @Test
    fun `ignores the transactions of an account that does not exist`() = runTest()
    {
        // GIVEN
        accountRepository.save(anAccount(id = accountId))
        record(1, otherId, 99_000, TransactionCategory.INITIAL_DEPOSIT)

        // WHEN / THEN
        assertThat(service.observeAll().first()).containsOnlyKeys(accountId)
    }

    @Test
    fun `follows every balance as accounts and transactions change`() = runTest()
    {
        // GIVEN a list of accounts showing their balances
        val emissions = mutableListOf<Map<AccountId, AccountBalance>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler))
        {
            service.observeAll().collect { emissions += it }
        }

        // WHEN
        accountRepository.save(anAccount(id = accountId))
        record(1, accountId, 4_000, TransactionCategory.INITIAL_DEPOSIT)
        accountRepository.save(anAccount(id = otherId))
        accountRepository.deleteById(accountId)

        // THEN
        assertThat(emissions).containsExactly(
            emptyMap(),
            mapOf(accountId to AccountBalance(0)),
            mapOf(accountId to AccountBalance(4_000)),
            mapOf(accountId to AccountBalance(4_000), otherId to AccountBalance(0)),
            mapOf(otherId to AccountBalance(0)),
        )
    }
}
