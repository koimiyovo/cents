package com.kyovo.cents.infrastructure.persistence

import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Currency
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class ListUnitOfWorkTest
{
    @Test
    fun `commits every write performed inside the block when it completes successfully`() = runTest()
    {
        // GIVEN
        val accountRepository = ListAccountRepository()
        val transactionRepository = ListTransactionRepository()
        val unitOfWork = ListUnitOfWork(accountRepository, transactionRepository, ListSubcategoryRepository())
        val account = anAccount(name = AccountName("Livret A"))
        val transaction = anOpeningDeposit(accountId = account.id)

        // WHEN
        val result = unitOfWork.execute {
            accountRepository.save(account)
            transactionRepository.save(transaction)
            "done"
        }

        // THEN
        assertThat(result).isEqualTo("done")
        assertThat(accountRepository.findAll()).containsExactly(account)
    }

    @Test
    fun `rolls back writes performed inside the block when it throws`() = runTest()
    {
        // GIVEN
        val accountRepository = ListAccountRepository()
        val transactionRepository = ListTransactionRepository()
        val unitOfWork = ListUnitOfWork(accountRepository, transactionRepository, ListSubcategoryRepository())
        val account = anAccount(name = AccountName("Livret A"))

        // WHEN / THEN
        assertThatThrownBySuspending {
            unitOfWork.execute {
                accountRepository.save(account)
                throw RuntimeException("boom")
            }
        }.isInstanceOf(RuntimeException::class.java)

        assertThat(accountRepository.findAll()).isEmpty()
    }

    @Test
    fun `does not roll back writes committed before the failing unit of work started`() = runTest()
    {
        // GIVEN
        val accountRepository = ListAccountRepository()
        val transactionRepository = ListTransactionRepository()
        val unitOfWork = ListUnitOfWork(accountRepository, transactionRepository, ListSubcategoryRepository())
        val existingAccount = anAccount(
            id = AccountId(Uuid.parse("11111111-1111-1111-1111-111111111111")),
            name = AccountName("Livret A")
        )
        accountRepository.save(existingAccount)
        val newAccount = anAccount(
            id = AccountId(Uuid.parse("22222222-2222-2222-2222-222222222222")),
            name = AccountName("Compte courant")
        )

        // WHEN
        assertThatThrownBySuspending {
            unitOfWork.execute {
                accountRepository.save(newAccount)
                throw RuntimeException("boom")
            }
        }.isInstanceOf(RuntimeException::class.java)

        // THEN
        assertThat(accountRepository.findAll()).containsExactly(existingAccount)
    }

    // A screen collecting the accounts must not be left showing what a failed unit of work had
    // written: the rollback restores the repository *and* tells its observers.
    @Test
    fun `a rollback is seen by the observers of the accounts`() = runTest()
    {
        // GIVEN a screen collecting the accounts
        val accountRepository = ListAccountRepository()
        val unitOfWork = ListUnitOfWork(accountRepository, ListTransactionRepository(), ListSubcategoryRepository())
        val existing = anAccount(name = AccountName("Livret A"))
        accountRepository.save(existing)
        val seen = mutableListOf<List<String>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler))
        {
            accountRepository.observeAll().collect { list -> seen += list.map { it.name.value } }
        }

        // WHEN a unit of work fails after having added an account
        assertThatThrownBySuspending {
            unitOfWork.execute {
                accountRepository.save(
                    anAccount(id = AccountId(Uuid.parse("22222222-2222-2222-2222-222222222222")), name = AccountName("Compte courant")),
                )
                throw RuntimeException("boom")
            }
        }.isInstanceOf(RuntimeException::class.java)

        // THEN the observer saw the account come, then go
        assertThat(seen).containsExactly(listOf("Livret A"), listOf("Livret A", "Compte courant"), listOf("Livret A"))
    }

    @Test
    fun `a rollback puts a reordered list back for the observers`() = runTest()
    {
        // GIVEN
        val accountRepository = ListAccountRepository()
        val unitOfWork = ListUnitOfWork(accountRepository, ListTransactionRepository(), ListSubcategoryRepository())
        val first = anAccount(id = AccountId(Uuid.parse("11111111-1111-1111-1111-111111111111")), name = AccountName("A"))
        val second = anAccount(id = AccountId(Uuid.parse("22222222-2222-2222-2222-222222222222")), name = AccountName("B"))
        accountRepository.save(first)
        accountRepository.save(second)

        // WHEN a reorder is followed by a failure
        assertThatThrownBySuspending {
            unitOfWork.execute {
                accountRepository.reorder(listOf(second.id, first.id))
                throw RuntimeException("boom")
            }
        }.isInstanceOf(RuntimeException::class.java)

        // THEN
        assertThat(accountRepository.observeAll().first()).containsExactly(first, second)
    }

    @Test
    fun `a rollback is seen by the observers of the transactions`() = runTest()
    {
        // GIVEN a screen collecting the transactions
        val accountRepository = ListAccountRepository()
        val transactionRepository = ListTransactionRepository()
        val unitOfWork = ListUnitOfWork(accountRepository, transactionRepository, ListSubcategoryRepository())
        val account = anAccount(name = AccountName("Livret A"))
        val existing = anOpeningDeposit(account.id)
        transactionRepository.save(existing)
        val seen = mutableListOf<Int>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler))
        {
            transactionRepository.observeAll().collect { list -> seen += list.size }
        }

        // WHEN a unit of work fails after having recorded another transaction
        assertThatThrownBySuspending {
            unitOfWork.execute {
                transactionRepository.save(
                    Transaction.openingDeposit(
                        id = TransactionId(Uuid.parse("44444444-4444-4444-4444-444444444444")),
                        accountId = account.id,
                        amount = Money(1_000),
                        date = Instant.parse("2026-09-22T10:00:00Z"),
                    ),
                )
                throw RuntimeException("boom")
            }
        }.isInstanceOf(RuntimeException::class.java)

        // THEN the observer saw it come, then go
        assertThat(seen).containsExactly(1, 2, 1)
        assertThat(transactionRepository.observeAll().first()).containsExactly(existing)
    }

    private fun anAccount(
        id: AccountId = AccountId(Uuid.parse("11111111-1111-1111-1111-111111111111")),
        name: AccountName
    ): Account
    {
        return Account(
            id = id,
            name = name,
            type = AccountType.CHECKING,
            currency = AccountCurrency(Currency.getInstance("EUR")),
            createdAt = Instant.parse("2026-09-22T10:00:00Z")
        )
    }

    private fun anOpeningDeposit(accountId: AccountId): Transaction
    {
        return Transaction.openingDeposit(
            id = TransactionId(Uuid.parse("33333333-3333-3333-3333-333333333333")),
            accountId = accountId,
            amount = Money(15_000),
            date = Instant.parse("2026-09-22T10:00:00Z")
        )
    }
}
