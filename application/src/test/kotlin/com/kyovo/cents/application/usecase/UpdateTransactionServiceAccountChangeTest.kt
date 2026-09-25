package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryAccountRepository
import com.kyovo.cents.application.fakes.InMemorySubcategoryRepository
import com.kyovo.cents.application.fakes.InMemoryTransactionRepository
import com.kyovo.cents.application.fakes.aMoney
import com.kyovo.cents.application.fakes.aTransaction
import com.kyovo.cents.application.fakes.aTransactionId
import com.kyovo.cents.application.fakes.anAccount
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.application.fakes.anInstant
import com.kyovo.cents.application.fakes.anUpdateTransactionCommand
import com.kyovo.cents.domain.exception.AccountNotFoundException
import com.kyovo.cents.domain.exception.CannotRecordTransactionOnArchivedAccountException
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.TransactionCategory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Editing a transaction may also move it to another account. The rules follow those of recording
 * one: the destination must exist and must not be archived (an archived account takes no new
 * transaction). Leaving an archived account is fine, and so is staying on one — updating a
 * transaction that already sits on an archived account was always allowed.
 */
class UpdateTransactionServiceAccountChangeTest
{
    private val id = aTransactionId()
    private val fromId = anAccountId()
    private val toId = anAccountId("22222222-2222-2222-2222-222222222222")

    private val accountRepository = InMemoryAccountRepository()
    private val transactionRepository = InMemoryTransactionRepository()
    private val service = UpdateTransactionService(accountRepository, transactionRepository, InMemorySubcategoryRepository())

    private val from = anAccount(id = fromId, name = AccountName("Compte courant"))
    private val to = anAccount(id = toId, name = AccountName("Livret A"))

    private fun anExpenseOn(accountId: AccountId) =
        aTransaction(id = id, accountId = accountId, amount = aMoney(1_000), category = TransactionCategory.EXPENSE)

    @Test
    fun `moves the transaction to another account`()
    {
        // GIVEN
        accountRepository.save(from)
        accountRepository.save(to)
        transactionRepository.save(anExpenseOn(fromId))

        // WHEN
        val result = service.update(anUpdateTransactionCommand(id = id, accountId = toId, amount = aMoney(1_500)))

        // THEN the same transaction, now on the other account and with the other edits applied
        assertThat(result.id).isEqualTo(id)
        assertThat(result.accountId).isEqualTo(toId)
        assertThat(result.amount).isEqualTo(aMoney(1_500))
        assertThat(transactionRepository.saved).containsExactly(result)
    }

    @Test
    fun `the balances of both accounts follow the move`()
    {
        // GIVEN each account has an opening deposit, and the expense is on the first one
        accountRepository.save(from)
        accountRepository.save(to)
        transactionRepository.save(
            aTransaction(
                id = aTransactionId("44444444-4444-4444-4444-444444444444"),
                accountId = fromId,
                amount = aMoney(10_000),
                category = TransactionCategory.INITIAL_DEPOSIT,
            ),
        )
        transactionRepository.save(
            aTransaction(
                id = aTransactionId("55555555-5555-5555-5555-555555555555"),
                accountId = toId,
                amount = aMoney(5_000),
                category = TransactionCategory.INITIAL_DEPOSIT,
            ),
        )
        transactionRepository.save(anExpenseOn(fromId))
        val balances = GetAccountBalanceService(accountRepository, transactionRepository)
        assertThat(balances.getBalance(fromId)!!.value).isEqualTo(9_000)
        assertThat(balances.getBalance(toId)!!.value).isEqualTo(5_000)

        // WHEN
        service.update(anUpdateTransactionCommand(id = id, accountId = toId, amount = aMoney(1_000)))

        // THEN the 10,00 € left the first account and weighs on the second
        assertThat(balances.getBalance(fromId)!!.value).isEqualTo(10_000)
        assertThat(balances.getBalance(toId)!!.value).isEqualTo(4_000)
    }

    @Test
    fun `throws when the destination account does not exist`()
    {
        // GIVEN
        accountRepository.save(from)
        transactionRepository.save(anExpenseOn(fromId))
        val unknown = anAccountId("99999999-9999-9999-9999-999999999999")

        // WHEN / THEN
        assertThatThrownBy { service.update(anUpdateTransactionCommand(id = id, accountId = unknown)) }
            .isInstanceOf(AccountNotFoundException::class.java)
    }

    @Test
    fun `throws when the destination account is archived`()
    {
        // GIVEN
        accountRepository.save(from)
        accountRepository.save(to.copy(archivedAt = anInstant("2026-01-01T00:00:00Z")))
        transactionRepository.save(anExpenseOn(fromId))

        // WHEN / THEN
        assertThatThrownBy { service.update(anUpdateTransactionCommand(id = id, accountId = toId)) }
            .isInstanceOf(CannotRecordTransactionOnArchivedAccountException::class.java)
    }

    @Test
    fun `changes nothing when the move is refused`()
    {
        // GIVEN
        accountRepository.save(from)
        accountRepository.save(to.copy(archivedAt = anInstant("2026-01-01T00:00:00Z")))
        val original = anExpenseOn(fromId)
        transactionRepository.save(original)

        // WHEN
        assertThatThrownBy {
            service.update(anUpdateTransactionCommand(id = id, accountId = toId, amount = aMoney(9_999)))
        }.isInstanceOf(CannotRecordTransactionOnArchivedAccountException::class.java)

        // THEN
        assertThat(transactionRepository.saved).containsExactly(original)
    }

    @Test
    fun `allows moving a transaction out of an archived account`()
    {
        // GIVEN
        accountRepository.save(from.copy(archivedAt = anInstant("2026-01-01T00:00:00Z")))
        accountRepository.save(to)
        transactionRepository.save(anExpenseOn(fromId))

        // WHEN
        val result = service.update(anUpdateTransactionCommand(id = id, accountId = toId))

        // THEN
        assertThat(result.accountId).isEqualTo(toId)
    }

    @Test
    fun `does not look the account up when the transaction stays on it`()
    {
        // GIVEN the account is not even in the repository: only a move needs it to exist
        transactionRepository.save(anExpenseOn(fromId))

        // WHEN
        val result = service.update(anUpdateTransactionCommand(id = id, accountId = fromId, amount = aMoney(2_500)))

        // THEN
        assertThat(result.amount).isEqualTo(aMoney(2_500))
    }
}
