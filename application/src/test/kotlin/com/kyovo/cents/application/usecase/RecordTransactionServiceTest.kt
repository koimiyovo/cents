package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.FixedTransactionIdGenerator
import com.kyovo.cents.application.fakes.InMemoryAccountRepository
import com.kyovo.cents.application.fakes.InMemoryTransactionRepository
import com.kyovo.cents.application.fakes.aMoney
import com.kyovo.cents.application.fakes.anAccount
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.application.fakes.anInstant
import com.kyovo.cents.application.fakes.aRecordTransactionCommand
import com.kyovo.cents.application.fakes.aTransaction
import com.kyovo.cents.application.fakes.aTransactionId
import com.kyovo.cents.domain.exception.AccountNotFoundException
import com.kyovo.cents.domain.model.RecordableTransactionType
import com.kyovo.cents.domain.model.TransactionType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class RecordTransactionServiceTest
{
    @Test
    fun `records an expense transaction for an existing account`()
    {
        // GIVEN
        val accountId = anAccountId()
        val generatedTransactionId = aTransactionId()
        val date = anInstant()
        val accountRepository = InMemoryAccountRepository()
        accountRepository.save(anAccount(id = accountId))
        val transactionRepository = InMemoryTransactionRepository()
        val service = RecordTransactionService(
            accountRepository,
            transactionRepository,
            FixedTransactionIdGenerator(generatedTransactionId)
        )
        val command = aRecordTransactionCommand(
            accountId = accountId,
            amount = aMoney(2_000),
            type = RecordableTransactionType.EXPENSE,
            date = date
        )

        // WHEN
        val result = service.record(command)

        // THEN
        assertThat(result).isEqualTo(
            aTransaction(
                id = generatedTransactionId,
                accountId = accountId,
                amount = aMoney(2_000),
                date = date,
                type = TransactionType.EXPENSE
            )
        )
        assertThat(transactionRepository.saved).containsExactly(result)
    }

    @Test
    fun `records an income transaction for an existing account`()
    {
        // GIVEN
        val accountId = anAccountId()
        val generatedTransactionId = aTransactionId()
        val date = anInstant()
        val accountRepository = InMemoryAccountRepository()
        accountRepository.save(anAccount(id = accountId))
        val transactionRepository = InMemoryTransactionRepository()
        val service = RecordTransactionService(
            accountRepository,
            transactionRepository,
            FixedTransactionIdGenerator(generatedTransactionId)
        )
        val command = aRecordTransactionCommand(
            accountId = accountId,
            amount = aMoney(50_000),
            type = RecordableTransactionType.INCOME,
            date = date
        )

        // WHEN
        val result = service.record(command)

        // THEN
        assertThat(result).isEqualTo(
            aTransaction(
                id = generatedTransactionId,
                accountId = accountId,
                amount = aMoney(50_000),
                date = date,
                type = TransactionType.INCOME
            )
        )
    }

    @Test
    fun `throws when no account matches the given id`()
    {
        // GIVEN
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val service = RecordTransactionService(
            accountRepository,
            transactionRepository,
            FixedTransactionIdGenerator(aTransactionId())
        )
        val command = aRecordTransactionCommand(accountId = anAccountId())

        // WHEN / THEN
        assertThatThrownBy { service.record(command) }
            .isInstanceOf(AccountNotFoundException::class.java)
    }

    @Test
    fun `does not save a transaction when no account matches the given id`()
    {
        // GIVEN
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val service = RecordTransactionService(
            accountRepository,
            transactionRepository,
            FixedTransactionIdGenerator(aTransactionId())
        )
        val command = aRecordTransactionCommand(accountId = anAccountId())

        // WHEN
        assertThatThrownBy { service.record(command) }
            .isInstanceOf(AccountNotFoundException::class.java)

        // THEN
        assertThat(transactionRepository.saved).isEmpty()
    }
}
