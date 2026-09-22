package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.FixedTransactionIdGenerator
import com.kyovo.cents.application.fakes.InMemoryAccountRepository
import com.kyovo.cents.application.fakes.InMemoryTransactionRepository
import com.kyovo.cents.application.fakes.aMoney
import com.kyovo.cents.application.fakes.aRecordTransactionCommand
import com.kyovo.cents.application.fakes.aTransaction
import com.kyovo.cents.application.fakes.aTransactionId
import com.kyovo.cents.application.fakes.anAccount
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.application.fakes.anInstant
import com.kyovo.cents.domain.exception.AccountNotFoundException
import com.kyovo.cents.domain.exception.InvalidTransactionSubcategoryException
import com.kyovo.cents.domain.model.ExpenseSubcategory
import com.kyovo.cents.domain.model.IncomeSubcategory
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.TransactionCategory
import com.kyovo.cents.domain.model.TransactionDescription
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
            category = RecordableTransactionCategory.EXPENSE,
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
                category = TransactionCategory.EXPENSE
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
            category = RecordableTransactionCategory.INCOME,
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
                category = TransactionCategory.INCOME
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

    @Test
    fun `records an expense transaction with a subcategory and a description`()
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
            category = RecordableTransactionCategory.EXPENSE,
            date = date,
            subcategory = ExpenseSubcategory.GROCERIES,
            description = TransactionDescription.of("Courses de la semaine")
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
                category = TransactionCategory.EXPENSE,
                subcategory = ExpenseSubcategory.GROCERIES,
                description = TransactionDescription.of("Courses de la semaine")
            )
        )
    }

    @Test
    fun `throws when the subcategory does not belong to the given category`()
    {
        // GIVEN
        val accountId = anAccountId()
        val accountRepository = InMemoryAccountRepository()
        accountRepository.save(anAccount(id = accountId))
        val transactionRepository = InMemoryTransactionRepository()
        val service = RecordTransactionService(
            accountRepository,
            transactionRepository,
            FixedTransactionIdGenerator(aTransactionId())
        )
        val command = aRecordTransactionCommand(
            accountId = accountId,
            category = RecordableTransactionCategory.EXPENSE,
            subcategory = IncomeSubcategory.SALARY
        )

        // WHEN / THEN
        assertThatThrownBy { service.record(command) }
            .isInstanceOf(InvalidTransactionSubcategoryException::class.java)
    }

    @Test
    fun `records a transaction with no description when given a blank description`()
    {
        // GIVEN
        val accountId = anAccountId()
        val accountRepository = InMemoryAccountRepository()
        accountRepository.save(anAccount(id = accountId))
        val transactionRepository = InMemoryTransactionRepository()
        val service = RecordTransactionService(
            accountRepository,
            transactionRepository,
            FixedTransactionIdGenerator(aTransactionId())
        )
        val command = aRecordTransactionCommand(
            accountId = accountId,
            description = TransactionDescription.of("   ")
        )

        // WHEN
        val result = service.record(command)

        // THEN
        assertThat(result.description).isNull()
    }
}
