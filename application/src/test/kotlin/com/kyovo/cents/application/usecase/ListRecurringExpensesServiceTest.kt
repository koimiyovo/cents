package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryRecurringExpenseRepository
import com.kyovo.cents.application.fakes.aRecurringExpense
import com.kyovo.cents.application.fakes.aRecurringExpenseId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ListRecurringExpensesServiceTest
{
    private val recurringExpenseRepository = InMemoryRecurringExpenseRepository()
    private val service = ListRecurringExpensesService(recurringExpenseRepository)

    @Test
    fun `observes every stored rule`() = runTest()
    {
        // GIVEN
        val first = aRecurringExpense(id = aRecurringExpenseId("11111111-1111-1111-1111-111111111111"))
        val second = aRecurringExpense(id = aRecurringExpenseId("22222222-2222-2222-2222-222222222222"))
        recurringExpenseRepository.save(first)
        recurringExpenseRepository.save(second)

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
