package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.assertThatThrownBySuspending
import kotlinx.coroutines.test.runTest
import com.kyovo.cents.application.fakes.InMemoryAccountRepository
import com.kyovo.cents.application.fakes.InMemoryTransactionRepository
import com.kyovo.cents.application.fakes.aMoney
import com.kyovo.cents.application.fakes.anAccount
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.application.fakes.aTransaction
import com.kyovo.cents.application.fakes.aTransactionId
import com.kyovo.cents.domain.model.AccountBalance
import com.kyovo.cents.domain.model.TransactionCategory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GetAccountBalanceServiceTest
{
    @Test
    fun `returns the account's balance as the sum of its transactions`() = runTest()
    {
        // GIVEN
        val accountId = anAccountId()
        val accountRepository = InMemoryAccountRepository()
        accountRepository.save(anAccount(id = accountId))
        val transactionRepository = InMemoryTransactionRepository()
        transactionRepository.save(
            aTransaction(
                id = aTransactionId("11111111-1111-1111-1111-111111111111"),
                accountId = accountId,
                amount = aMoney(10_000),
                category = TransactionCategory.INITIAL_DEPOSIT
            )
        )
        transactionRepository.save(
            aTransaction(
                id = aTransactionId("22222222-2222-2222-2222-222222222222"),
                accountId = accountId,
                amount = aMoney(5_000),
                category = TransactionCategory.INCOME
            )
        )
        transactionRepository.save(
            aTransaction(
                id = aTransactionId("33333333-3333-3333-3333-333333333333"),
                accountId = accountId,
                amount = aMoney(3_000),
                category = TransactionCategory.EXPENSE
            )
        )
        val service = GetAccountBalanceService(accountRepository, transactionRepository)

        // WHEN
        val result = service.getBalance(accountId)

        // THEN
        assertThat(result).isEqualTo(AccountBalance(12_000))
    }

    @Test
    fun `returns a zero balance when the account has no transactions`() = runTest()
    {
        // GIVEN
        val accountId = anAccountId()
        val accountRepository = InMemoryAccountRepository()
        accountRepository.save(anAccount(id = accountId))
        val transactionRepository = InMemoryTransactionRepository()
        val service = GetAccountBalanceService(accountRepository, transactionRepository)

        // WHEN
        val result = service.getBalance(accountId)

        // THEN
        assertThat(result).isEqualTo(AccountBalance(0))
    }

    @Test
    fun `does not include transactions belonging to other accounts`() = runTest()
    {
        // GIVEN
        val accountId = anAccountId("11111111-1111-1111-1111-111111111111")
        val otherAccountId = anAccountId("22222222-2222-2222-2222-222222222222")
        val accountRepository = InMemoryAccountRepository()
        accountRepository.save(anAccount(id = accountId))
        val transactionRepository = InMemoryTransactionRepository()
        transactionRepository.save(
            aTransaction(
                id = aTransactionId("33333333-3333-3333-3333-333333333333"),
                accountId = accountId,
                amount = aMoney(1_000),
                category = TransactionCategory.INITIAL_DEPOSIT
            )
        )
        transactionRepository.save(
            aTransaction(
                id = aTransactionId("44444444-4444-4444-4444-444444444444"),
                accountId = otherAccountId,
                amount = aMoney(99_000),
                category = TransactionCategory.INITIAL_DEPOSIT
            )
        )
        val service = GetAccountBalanceService(accountRepository, transactionRepository)

        // WHEN
        val result = service.getBalance(accountId)

        // THEN
        assertThat(result).isEqualTo(AccountBalance(1_000))
    }

    @Test
    fun `returns null when the account does not exist`() = runTest()
    {
        // GIVEN
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val service = GetAccountBalanceService(accountRepository, transactionRepository)

        // WHEN
        val result = service.getBalance(anAccountId())

        // THEN
        assertThat(result).isNull()
    }
}
