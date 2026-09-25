package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryAccountRepository
import com.kyovo.cents.application.fakes.InMemoryTransactionRepository
import com.kyovo.cents.application.fakes.aMoney
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.application.fakes.anInstant
import com.kyovo.cents.application.fakes.anUpdateTransactionCommand
import com.kyovo.cents.application.fakes.aTransaction
import com.kyovo.cents.application.fakes.aTransactionId
import com.kyovo.cents.domain.exception.CannotUpdateInitialDepositException
import com.kyovo.cents.domain.exception.InvalidTransactionSubcategoryException
import com.kyovo.cents.domain.exception.TransactionNotFoundException
import com.kyovo.cents.domain.model.IncomeSubcategory
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.TransactionCategory
import com.kyovo.cents.domain.model.TransactionDescription
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class UpdateTransactionServiceTest
{
    @Test
    fun `updates the amount, category, subcategory, description and date of an existing transaction`()
    {
        // GIVEN
        val id = aTransactionId()
        val accountId = anAccountId()
        val repository = InMemoryTransactionRepository()
        repository.save(
            aTransaction(id = id, accountId = accountId, amount = aMoney(1_000), category = TransactionCategory.EXPENSE)
        )
        val service = UpdateTransactionService(InMemoryAccountRepository(), repository)
        val command = anUpdateTransactionCommand(
            id = id,
            amount = aMoney(5_000),
            category = RecordableTransactionCategory.INCOME,
            subcategory = IncomeSubcategory.SALARY,
            description = TransactionDescription.of("Paie de septembre"),
            date = anInstant("2026-09-05T08:00:00Z")
        )

        // WHEN
        val result = service.update(command)

        // THEN
        assertThat(result).isEqualTo(
            aTransaction(
                id = id,
                accountId = accountId,
                amount = aMoney(5_000),
                category = TransactionCategory.INCOME,
                subcategory = IncomeSubcategory.SALARY,
                description = TransactionDescription.of("Paie de septembre"),
                date = anInstant("2026-09-05T08:00:00Z")
            )
        )
        assertThat(repository.saved).containsExactly(result)
    }

    @Test
    fun `keeps the transaction on its account when the command carries the same one`()
    {
        // GIVEN
        val id = aTransactionId()
        val accountId = anAccountId("22222222-2222-2222-2222-222222222222")
        val repository = InMemoryTransactionRepository()
        repository.save(aTransaction(id = id, accountId = accountId, category = TransactionCategory.EXPENSE))
        val service = UpdateTransactionService(InMemoryAccountRepository(), repository)
        val command = anUpdateTransactionCommand(
            id = id,
            accountId = accountId,
            category = RecordableTransactionCategory.INCOME
        )

        // WHEN
        val result = service.update(command)

        // THEN
        assertThat(result.accountId).isEqualTo(accountId)
    }

    @Test
    fun `throws when no transaction matches the given id`()
    {
        // GIVEN
        val repository = InMemoryTransactionRepository()
        val service = UpdateTransactionService(InMemoryAccountRepository(), repository)
        val command = anUpdateTransactionCommand(id = aTransactionId())

        // WHEN / THEN
        assertThatThrownBy { service.update(command) }
            .isInstanceOf(TransactionNotFoundException::class.java)
    }

    @Test
    fun `throws when trying to update an initial deposit transaction`()
    {
        // GIVEN
        val id = aTransactionId()
        val repository = InMemoryTransactionRepository()
        repository.save(aTransaction(id = id, category = TransactionCategory.INITIAL_DEPOSIT))
        val service = UpdateTransactionService(InMemoryAccountRepository(), repository)
        val command = anUpdateTransactionCommand(id = id)

        // WHEN / THEN
        assertThatThrownBy { service.update(command) }
            .isInstanceOf(CannotUpdateInitialDepositException::class.java)
    }

    @Test
    fun `does not modify the transaction when trying to update an initial deposit transaction`()
    {
        // GIVEN
        val id = aTransactionId()
        val original = aTransaction(id = id, category = TransactionCategory.INITIAL_DEPOSIT)
        val repository = InMemoryTransactionRepository()
        repository.save(original)
        val service = UpdateTransactionService(InMemoryAccountRepository(), repository)
        val command = anUpdateTransactionCommand(id = id)

        // WHEN
        assertThatThrownBy { service.update(command) }
            .isInstanceOf(CannotUpdateInitialDepositException::class.java)

        // THEN
        assertThat(repository.saved).containsExactly(original)
    }

    @Test
    fun `throws when the new subcategory does not belong to the new category`()
    {
        // GIVEN
        val id = aTransactionId()
        val repository = InMemoryTransactionRepository()
        repository.save(aTransaction(id = id, category = TransactionCategory.EXPENSE))
        val service = UpdateTransactionService(InMemoryAccountRepository(), repository)
        val command = anUpdateTransactionCommand(
            id = id,
            category = RecordableTransactionCategory.EXPENSE,
            subcategory = IncomeSubcategory.SALARY
        )

        // WHEN / THEN
        assertThatThrownBy { service.update(command) }
            .isInstanceOf(InvalidTransactionSubcategoryException::class.java)
    }
}
