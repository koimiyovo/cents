package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryRecurringExpenseRepository
import com.kyovo.cents.application.fakes.aRecurringExpense
import com.kyovo.cents.application.fakes.aRecurringExpenseId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteRecurringExpenseServiceTest
{
    private val recurringExpenseRepository = InMemoryRecurringExpenseRepository()
    private val service = DeleteRecurringExpenseService(recurringExpenseRepository)

    @Test
    fun `deletes the rule, only it`() = runTest()
    {
        // GIVEN
        val id = aRecurringExpenseId()
        val other = aRecurringExpense(id = aRecurringExpenseId("77777777-7777-7777-7777-777777777777"))
        recurringExpenseRepository.save(aRecurringExpense(id = id))
        recurringExpenseRepository.save(other)

        // WHEN
        service.delete(id)

        // THEN
        assertThat(recurringExpenseRepository.saved).containsExactly(other)
    }

    @Test
    fun `an unknown id is a silent no-op`() = runTest()
    {
        // WHEN / THEN no exception
        service.delete(aRecurringExpenseId())
    }
}
