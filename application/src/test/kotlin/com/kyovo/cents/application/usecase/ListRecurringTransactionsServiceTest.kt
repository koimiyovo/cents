package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryRecurringTransactionRepository
import com.kyovo.cents.application.fakes.aRecurringTransaction
import com.kyovo.cents.application.fakes.aRecurringTransactionId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ListRecurringTransactionsServiceTest
{
    private val recurringTransactionRepository = InMemoryRecurringTransactionRepository()
    private val service = ListRecurringTransactionsService(recurringTransactionRepository)

    @Test
    fun `observes every stored rule`() = runTest()
    {
        // GIVEN
        val first = aRecurringTransaction(id = aRecurringTransactionId("11111111-1111-1111-1111-111111111111"))
        val second = aRecurringTransaction(id = aRecurringTransactionId("22222222-2222-2222-2222-222222222222"))
        recurringTransactionRepository.save(first)
        recurringTransactionRepository.save(second)

        // WHEN / THEN
        assertThat(service.observe().first()).containsExactly(first, second)
    }

    @Test
    fun `observes nothing when no rule is stored`() = runTest()
    {
        // WHEN / THEN
        assertThat(service.observe().first()).isEmpty()
    }
}
