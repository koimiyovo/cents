package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.FixedTransactionIdGenerator
import com.kyovo.cents.application.fakes.InMemoryAccountRepository
import com.kyovo.cents.application.fakes.InMemorySubcategoryRepository
import com.kyovo.cents.application.fakes.InMemoryTransactionRepository
import com.kyovo.cents.application.fakes.aMoney
import com.kyovo.cents.application.fakes.aRecordTransactionCommand
import com.kyovo.cents.application.fakes.aSubcategory
import com.kyovo.cents.application.fakes.aSubcategoryId
import com.kyovo.cents.application.fakes.aTransaction
import com.kyovo.cents.application.fakes.aTransactionId
import com.kyovo.cents.application.fakes.anAccount
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.application.fakes.anInstant
import com.kyovo.cents.domain.exception.AccountNotFoundException
import com.kyovo.cents.domain.exception.CannotRecordTransactionOnArchivedAccountException
import com.kyovo.cents.domain.exception.InvalidTransactionSubcategoryException
import com.kyovo.cents.domain.exception.SubcategoryNotFoundException
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.TransactionCategory
import com.kyovo.cents.domain.model.TransactionDescription
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class RecordTransactionServiceTest
{
    private val subcategoryRepository = InMemorySubcategoryRepository()
    private val groceriesId = aSubcategoryId("11111111-1111-1111-1111-111111111111")
    private val salaryId = aSubcategoryId("22222222-2222-2222-2222-222222222222")

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
            FixedTransactionIdGenerator(generatedTransactionId),
            subcategoryRepository
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
            FixedTransactionIdGenerator(generatedTransactionId),
            subcategoryRepository
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
            FixedTransactionIdGenerator(aTransactionId()),
            subcategoryRepository
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
            FixedTransactionIdGenerator(aTransactionId()),
            subcategoryRepository
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
        subcategoryRepository.save(aSubcategory(id = groceriesId, kind = RecordableTransactionCategory.EXPENSE))
        val transactionRepository = InMemoryTransactionRepository()
        val service = RecordTransactionService(
            accountRepository,
            transactionRepository,
            FixedTransactionIdGenerator(generatedTransactionId),
            subcategoryRepository
        )
        val command = aRecordTransactionCommand(
            accountId = accountId,
            amount = aMoney(2_000),
            category = RecordableTransactionCategory.EXPENSE,
            date = date,
            subcategoryId = groceriesId,
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
                subcategoryId = groceriesId,
                description = TransactionDescription.of("Courses de la semaine")
            )
        )
    }

    @Test
    fun `throws when the subcategory does not exist, and saves nothing`()
    {
        // GIVEN a subcategory id that no subcategory has
        val accountId = anAccountId()
        val accountRepository = InMemoryAccountRepository()
        accountRepository.save(anAccount(id = accountId))
        val transactionRepository = InMemoryTransactionRepository()
        val service = RecordTransactionService(
            accountRepository,
            transactionRepository,
            FixedTransactionIdGenerator(aTransactionId()),
            subcategoryRepository
        )
        val command = aRecordTransactionCommand(accountId = accountId, subcategoryId = groceriesId)

        // WHEN
        assertThatThrownBy { service.record(command) }
            .isInstanceOf(SubcategoryNotFoundException::class.java)

        // THEN
        assertThat(transactionRepository.saved).isEmpty()
    }

    @Test
    fun `records a transaction with no subcategory when none is given`()
    {
        // GIVEN
        val accountId = anAccountId()
        val accountRepository = InMemoryAccountRepository()
        accountRepository.save(anAccount(id = accountId))
        val service = RecordTransactionService(
            accountRepository,
            InMemoryTransactionRepository(),
            FixedTransactionIdGenerator(aTransactionId()),
            subcategoryRepository
        )

        // WHEN
        val result = service.record(aRecordTransactionCommand(accountId = accountId, subcategoryId = null))

        // THEN
        assertThat(result.subcategoryId).isNull()
    }

    @Test
    fun `throws when the subcategory does not belong to the given category`()
    {
        // GIVEN
        val accountId = anAccountId()
        val accountRepository = InMemoryAccountRepository()
        accountRepository.save(anAccount(id = accountId))
        subcategoryRepository.save(aSubcategory(id = salaryId, kind = RecordableTransactionCategory.INCOME))
        val transactionRepository = InMemoryTransactionRepository()
        val service = RecordTransactionService(
            accountRepository,
            transactionRepository,
            FixedTransactionIdGenerator(aTransactionId()),
            subcategoryRepository
        )
        val command = aRecordTransactionCommand(
            accountId = accountId,
            category = RecordableTransactionCategory.EXPENSE,
            subcategoryId = salaryId
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
            FixedTransactionIdGenerator(aTransactionId()),
            subcategoryRepository
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

    @Test
    fun `throws when trying to record a transaction on an archived account`()
    {
        // GIVEN
        val accountId = anAccountId()
        val accountRepository = InMemoryAccountRepository()
        accountRepository.save(anAccount(id = accountId, archivedAt = anInstant()))
        val transactionRepository = InMemoryTransactionRepository()
        val service = RecordTransactionService(
            accountRepository,
            transactionRepository,
            FixedTransactionIdGenerator(aTransactionId()),
            subcategoryRepository
        )
        val command = aRecordTransactionCommand(accountId = accountId)

        // WHEN / THEN
        assertThatThrownBy { service.record(command) }
            .isInstanceOf(CannotRecordTransactionOnArchivedAccountException::class.java)
    }

    @Test
    fun `does not save a transaction when trying to record it on an archived account`()
    {
        // GIVEN
        val accountId = anAccountId()
        val accountRepository = InMemoryAccountRepository()
        accountRepository.save(anAccount(id = accountId, archivedAt = anInstant()))
        val transactionRepository = InMemoryTransactionRepository()
        val service = RecordTransactionService(
            accountRepository,
            transactionRepository,
            FixedTransactionIdGenerator(aTransactionId()),
            subcategoryRepository
        )
        val command = aRecordTransactionCommand(accountId = accountId)

        // WHEN
        assertThatThrownBy { service.record(command) }
            .isInstanceOf(CannotRecordTransactionOnArchivedAccountException::class.java)

        // THEN
        assertThat(transactionRepository.saved).isEmpty()
    }
}
