package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.assertThatThrownBySuspending
import kotlinx.coroutines.test.runTest
import com.kyovo.cents.application.fakes.InMemoryTransactionRepository
import com.kyovo.cents.application.fakes.aTransaction
import com.kyovo.cents.application.fakes.aTransactionId
import com.kyovo.cents.domain.exception.CannotDeleteInitialDepositException
import com.kyovo.cents.domain.model.TransactionCategory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class DeleteTransactionServiceTest
{
    @Test
    fun `deletes an existing recorded transaction`() = runTest()
    {
        // GIVEN
        val id = aTransactionId()
        val repository = InMemoryTransactionRepository()
        repository.save(aTransaction(id = id, category = TransactionCategory.EXPENSE))
        val service = DeleteTransactionService(repository)

        // WHEN
        service.delete(id)

        // THEN
        assertThat(repository.saved).isEmpty()
    }

    @Test
    fun `does not throw when no transaction matches the given id`() = runTest()
    {
        // GIVEN
        val repository = InMemoryTransactionRepository()
        val service = DeleteTransactionService(repository)

        // WHEN / THEN
        service.delete(aTransactionId())
    }

    @Test
    fun `throws when trying to delete an initial deposit transaction`() = runTest()
    {
        // GIVEN
        val id = aTransactionId()
        val repository = InMemoryTransactionRepository()
        repository.save(aTransaction(id = id, category = TransactionCategory.INITIAL_DEPOSIT))
        val service = DeleteTransactionService(repository)

        // WHEN / THEN
        assertThatThrownBySuspending { service.delete(id) }
            .isInstanceOf(CannotDeleteInitialDepositException::class.java)
    }

    @Test
    fun `does not delete the transaction when trying to delete an initial deposit transaction`() = runTest()
    {
        // GIVEN
        val id = aTransactionId()
        val original = aTransaction(id = id, category = TransactionCategory.INITIAL_DEPOSIT)
        val repository = InMemoryTransactionRepository()
        repository.save(original)
        val service = DeleteTransactionService(repository)

        // WHEN
        assertThatThrownBySuspending { service.delete(id) }
            .isInstanceOf(CannotDeleteInitialDepositException::class.java)

        // THEN
        assertThat(repository.saved).containsExactly(original)
    }
}
