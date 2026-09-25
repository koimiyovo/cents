package com.kyovo.cents.infrastructure.persistence

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
