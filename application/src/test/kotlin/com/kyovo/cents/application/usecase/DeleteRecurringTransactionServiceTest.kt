package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryRecurringTransactionRepository
import com.kyovo.cents.application.fakes.aRecurringTransaction
import com.kyovo.cents.application.fakes.aRecurringTransactionId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteRecurringTransactionServiceTest
{
    private val recurringTransactionRepository = InMemoryRecurringTransactionRepository()
    private val service = DeleteRecurringTransactionService(recurringTransactionRepository)

    @Test
    fun `deletes the rule, only it`() = runTest()
    {
        // GIVEN
        val id = aRecurringTransactionId()
        val other = aRecurringTransaction(id = aRecurringTransactionId("77777777-7777-7777-7777-777777777777"))
        recurringTransactionRepository.save(aRecurringTransaction(id = id))
        recurringTransactionRepository.save(other)

        // WHEN
        service.delete(id)

        // THEN
        assertThat(recurringTransactionRepository.saved).containsExactly(other)
    }

    @Test
    fun `an unknown id is a silent no-op`() = runTest()
    {
        // WHEN / THEN no exception
        service.delete(aRecurringTransactionId())
    }
}
