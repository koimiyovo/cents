package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.assertThatThrownBySuspending
import kotlinx.coroutines.test.runTest
import com.kyovo.cents.application.fakes.InMemoryAccountRepository
import com.kyovo.cents.application.fakes.InMemoryTransactionRepository
import com.kyovo.cents.application.fakes.InMemoryUnitOfWork
import com.kyovo.cents.application.fakes.anAccount
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.application.fakes.aTransaction
import com.kyovo.cents.domain.exception.CannotDeleteAccountWithTransactionsException
import com.kyovo.cents.domain.model.AccountName
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class DeleteAccountServiceTest
{
    @Test
    fun `deletes an existing account that has no transactions`() = runTest()
    {
        // GIVEN
        val id = anAccountId()
        val accountRepository = InMemoryAccountRepository()
        accountRepository.save(anAccount(id = id))
        val service = DeleteAccountService(accountRepository, InMemoryTransactionRepository(), InMemoryUnitOfWork())

        // WHEN
        service.delete(id)

        // THEN
        assertThat(accountRepository.saved).isEmpty()
    }

    @Test
    fun `does not affect other accounts when deleting one of them`() = runTest()
    {
        // GIVEN
        val idToDelete = anAccountId()
        val otherAccount = anAccount(
            id = anAccountId("22222222-2222-2222-2222-222222222222"),
            name = AccountName("Compte courant")
        )
        val accountRepository = InMemoryAccountRepository()
        accountRepository.save(anAccount(id = idToDelete))
        accountRepository.save(otherAccount)
        val service = DeleteAccountService(accountRepository, InMemoryTransactionRepository(), InMemoryUnitOfWork())

        // WHEN
        service.delete(idToDelete)

        // THEN
        assertThat(accountRepository.saved).containsExactly(otherAccount)
    }

    @Test
    fun `does not throw when no account matches the given id`() = runTest()
    {
        // GIVEN
        val accountRepository = InMemoryAccountRepository()
        val service = DeleteAccountService(accountRepository, InMemoryTransactionRepository(), InMemoryUnitOfWork())

        // WHEN / THEN
        service.delete(anAccountId())
    }

    @Test
    fun `throws when the account has transactions`() = runTest()
    {
        // GIVEN
        val id = anAccountId()
        val accountRepository = InMemoryAccountRepository()
        accountRepository.save(anAccount(id = id))
        val transactionRepository = InMemoryTransactionRepository()
        transactionRepository.save(aTransaction(accountId = id))
        val service = DeleteAccountService(accountRepository, transactionRepository, InMemoryUnitOfWork())

        // WHEN / THEN
        assertThatThrownBySuspending { service.delete(id) }
            .isInstanceOf(CannotDeleteAccountWithTransactionsException::class.java)
    }

    @Test
    fun `does not delete the account when it has transactions`() = runTest()
    {
        // GIVEN
        val id = anAccountId()
        val account = anAccount(id = id)
        val accountRepository = InMemoryAccountRepository()
        accountRepository.save(account)
        val transactionRepository = InMemoryTransactionRepository()
        transactionRepository.save(aTransaction(accountId = id))
        val service = DeleteAccountService(accountRepository, transactionRepository, InMemoryUnitOfWork())

        // WHEN
        assertThatThrownBySuspending { service.delete(id) }
            .isInstanceOf(CannotDeleteAccountWithTransactionsException::class.java)

        // THEN
        assertThat(accountRepository.saved).containsExactly(account)
    }
}
