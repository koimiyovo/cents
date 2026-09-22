package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryTransactionRepository
import com.kyovo.cents.application.fakes.aMoney
import com.kyovo.cents.application.fakes.aTransaction
import com.kyovo.cents.application.fakes.aTransactionId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GetTransactionServiceTest
{
    @Test
    fun `returns the transaction matching the given id`()
    {
        // GIVEN
        val id = aTransactionId()
        val repository = InMemoryTransactionRepository()
        repository.save(aTransaction(id = id))
        val service = GetTransactionService(repository)

        // WHEN
        val result = service.get(id)

        // THEN
        assertThat(result).isEqualTo(aTransaction(id = id))
    }

    @Test
    fun `returns the transaction matching the given id among several saved transactions`()
    {
        // GIVEN
        val id = aTransactionId("44444444-4444-4444-4444-444444444444")
        val repository = InMemoryTransactionRepository()
        repository.save(aTransaction())
        repository.save(aTransaction(id = id, amount = aMoney(2_000)))
        val service = GetTransactionService(repository)

        // WHEN
        val result = service.get(id)

        // THEN
        assertThat(result).isEqualTo(aTransaction(id = id, amount = aMoney(2_000)))
    }

    @Test
    fun `returns null when no transaction matches the given id`()
    {
        // GIVEN
        val repository = InMemoryTransactionRepository()
        val service = GetTransactionService(repository)

        // WHEN
        val result = service.get(aTransactionId())

        // THEN
        assertThat(result).isNull()
    }
}
