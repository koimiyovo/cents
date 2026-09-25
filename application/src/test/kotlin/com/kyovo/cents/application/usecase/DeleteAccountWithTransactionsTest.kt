package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.assertThatThrownBySuspending
import kotlinx.coroutines.test.runTest
import com.kyovo.cents.application.fakes.InMemoryAccountRepository
import com.kyovo.cents.application.fakes.InMemoryTransactionRepository
import com.kyovo.cents.application.fakes.InMemoryUnitOfWork
import com.kyovo.cents.application.fakes.aTransaction
import com.kyovo.cents.application.fakes.aTransactionId
import com.kyovo.cents.application.fakes.anAccount
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.application.fakes.anInstant
import com.kyovo.cents.domain.exception.CannotDeleteAccountWithTransactionsException
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.TransactionCategory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Deleting an account that has transactions is refused by default: it would erase its history.
 * The user may still do it on purpose, by saying so — `delete(id, deleteTransactions = true)` — and
 * then the account goes together with all of its own transactions, in one all-or-nothing step
 * (a unit of work). What belongs to other accounts is never touched, including the other side of a
 * transfer: that leg is part of the other account's history and balance.
 */
class DeleteAccountWithTransactionsTest
{
    private val id = anAccountId()
    private val otherId = anAccountId("22222222-2222-2222-2222-222222222222")

    private val accountRepository = InMemoryAccountRepository()
    private val transactionRepository = InMemoryTransactionRepository()
    private val unitOfWork = InMemoryUnitOfWork()
    private val service = DeleteAccountService(accountRepository, transactionRepository, unitOfWork)

    private fun transactionOf(accountId: AccountId, suffix: Int) =
        aTransaction(
            id = aTransactionId("33333333-3333-3333-3333-33333333333$suffix"),
            accountId = accountId,
            category = TransactionCategory.INITIAL_DEPOSIT,
        )

    @Test
    fun `still refuses an account with transactions when not asked to delete them`() = runTest()
    {
        // GIVEN
        accountRepository.save(anAccount(id = id))
        transactionRepository.save(transactionOf(id, 1))

        // WHEN / THEN
        assertThatThrownBySuspending { service.delete(id, deleteTransactions = false) }
            .isInstanceOf(CannotDeleteAccountWithTransactionsException::class.java)
    }

    @Test
    fun `changes nothing when it refuses`() = runTest()
    {
        // GIVEN
        val account = anAccount(id = id)
        val transaction = transactionOf(id, 1)
        accountRepository.save(account)
        transactionRepository.save(transaction)

        // WHEN
        assertThatThrownBySuspending { service.delete(id) }
            .isInstanceOf(CannotDeleteAccountWithTransactionsException::class.java)

        // THEN
        assertThat(accountRepository.saved).containsExactly(account)
        assertThat(transactionRepository.saved).containsExactly(transaction)
    }

    @Test
    fun `deletes the account together with all its transactions when asked to`() = runTest()
    {
        // GIVEN
        accountRepository.save(anAccount(id = id))
        transactionRepository.save(transactionOf(id, 1))
        transactionRepository.save(transactionOf(id, 2))
        transactionRepository.save(transactionOf(id, 3))

        // WHEN
        service.delete(id, deleteTransactions = true)

        // THEN
        assertThat(accountRepository.saved).isEmpty()
        assertThat(transactionRepository.saved).isEmpty()
    }

    @Test
    fun `only deletes what belongs to that account`() = runTest()
    {
        // GIVEN
        val other = anAccount(id = otherId, name = AccountName("Compte courant"))
        val othersTransaction = transactionOf(otherId, 4)
        accountRepository.save(anAccount(id = id))
        accountRepository.save(other)
        transactionRepository.save(transactionOf(id, 1))
        transactionRepository.save(othersTransaction)

        // WHEN
        service.delete(id, deleteTransactions = true)

        // THEN
        assertThat(accountRepository.saved).containsExactly(other)
        assertThat(transactionRepository.saved).containsExactly(othersTransaction)
    }

    @Test
    fun `keeps the other side of a transfer`() = runTest()
    {
        // GIVEN a transfer from the account to delete to another one: two legs, one on each account
        val other = anAccount(id = otherId, name = AccountName("Compte courant"))
        val legIn = aTransaction(
            id = aTransactionId("33333333-3333-3333-3333-333333333335"),
            accountId = otherId,
            category = TransactionCategory.TRANSFER_IN,
        )
        accountRepository.save(anAccount(id = id))
        accountRepository.save(other)
        transactionRepository.save(
            aTransaction(
                id = aTransactionId("33333333-3333-3333-3333-333333333334"),
                accountId = id,
                category = TransactionCategory.TRANSFER_OUT,
            ),
        )
        transactionRepository.save(legIn)

        // WHEN
        service.delete(id, deleteTransactions = true)

        // THEN the leg on the other account stays, so that account's history and balance don't change
        assertThat(transactionRepository.saved).containsExactly(legIn)
        assertThat(accountRepository.saved).containsExactly(other)
    }

    @Test
    fun `deletes an account that has no transactions the same way`() = runTest()
    {
        // GIVEN
        accountRepository.save(anAccount(id = id))

        // WHEN
        service.delete(id, deleteTransactions = true)

        // THEN
        assertThat(accountRepository.saved).isEmpty()
    }

    @Test
    fun `deletes an archived account and its transactions too`() = runTest()
    {
        // GIVEN
        accountRepository.save(anAccount(id = id, archivedAt = anInstant("2026-01-01T00:00:00Z")))
        transactionRepository.save(transactionOf(id, 1))

        // WHEN
        service.delete(id, deleteTransactions = true)

        // THEN
        assertThat(accountRepository.saved).isEmpty()
        assertThat(transactionRepository.saved).isEmpty()
    }

    @Test
    fun `does nothing when no account matches the given id`() = runTest()
    {
        // GIVEN
        val other = anAccount(id = otherId, name = AccountName("Compte courant"))
        val othersTransaction = transactionOf(otherId, 4)
        accountRepository.save(other)
        transactionRepository.save(othersTransaction)

        // WHEN / THEN
        service.delete(id, deleteTransactions = true)
        assertThat(accountRepository.saved).containsExactly(other)
        assertThat(transactionRepository.saved).containsExactly(othersTransaction)
    }

    @Test
    fun `deletes the account and its transactions in a single unit of work`() = runTest()
    {
        // GIVEN so that either both go or neither does
        accountRepository.save(anAccount(id = id))
        transactionRepository.save(transactionOf(id, 1))

        // WHEN
        service.delete(id, deleteTransactions = true)

        // THEN
        assertThat(unitOfWork.executionCount).isEqualTo(1)
    }
}
