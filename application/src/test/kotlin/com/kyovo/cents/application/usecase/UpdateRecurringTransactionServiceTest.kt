package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryRecurringTransactionRepository
import com.kyovo.cents.application.fakes.InMemorySubcategoryRepository
import com.kyovo.cents.application.fakes.aRecurringTransaction
import com.kyovo.cents.application.fakes.aRecurringTransactionId
import com.kyovo.cents.application.fakes.aSubcategory
import com.kyovo.cents.application.fakes.aSubcategoryId
import com.kyovo.cents.application.fakes.anUpdateRecurringTransactionCommand
import com.kyovo.cents.application.fakes.assertThatThrownBySuspending
import com.kyovo.cents.domain.exception.InvalidTransactionSubcategoryException
import com.kyovo.cents.domain.exception.RecurringTransactionNotFoundException
import com.kyovo.cents.domain.exception.SubcategoryNotFoundException
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.RecurrenceFrequency
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateRecurringTransactionServiceTest
{
    private val subcategoryRepository = InMemorySubcategoryRepository()
    private val recurringTransactionRepository = InMemoryRecurringTransactionRepository()
    private val service = UpdateRecurringTransactionService(recurringTransactionRepository, subcategoryRepository)

    @Test
    fun `updates the amount, title, subcategory, description, frequency, interval and end date`() = runTest()
    {
        // GIVEN
        val id = aRecurringTransactionId()
        recurringTransactionRepository.save(aRecurringTransaction(id = id, startDate = LocalDate.of(2026, 9, 5)))

        // WHEN
        val updated = service.update(
            anUpdateRecurringTransactionCommand(
                id = id,
                amount = Money(50_000),
                frequency = RecurrenceFrequency.YEARLY,
                interval = 2,
                endDate = LocalDate.of(2027, 9, 5),
            )
        )

        // THEN the start date never moves
        assertThat(updated.amount).isEqualTo(Money(50_000))
        assertThat(updated.frequency).isEqualTo(RecurrenceFrequency.YEARLY)
        assertThat(updated.interval).isEqualTo(2)
        assertThat(updated.endDate).isEqualTo(LocalDate.of(2027, 9, 5))
        assertThat(updated.startDate).isEqualTo(LocalDate.of(2026, 9, 5))
    }

    @Test
    fun `refuses an unknown rule`() = runTest()
    {
        // WHEN / THEN
        assertThatThrownBySuspending {
            service.update(anUpdateRecurringTransactionCommand(id = aRecurringTransactionId()))
        }.isInstanceOf(RecurringTransactionNotFoundException::class.java)
    }

    @Test
    fun `refuses an unknown subcategory, the rule left untouched`() = runTest()
    {
        // GIVEN
        val id = aRecurringTransactionId()
        val original = aRecurringTransaction(id = id)
        recurringTransactionRepository.save(original)

        // WHEN / THEN
        assertThatThrownBySuspending {
            service.update(anUpdateRecurringTransactionCommand(id = id, subcategoryId = aSubcategoryId()))
        }.isInstanceOf(SubcategoryNotFoundException::class.java)
        assertThat(recurringTransactionRepository.saved).containsExactly(original)
    }

    @Test
    fun `refuses an income subcategory`() = runTest()
    {
        // GIVEN
        val id = aRecurringTransactionId()
        recurringTransactionRepository.save(aRecurringTransaction(id = id))
        val incomeSubcategoryId = aSubcategoryId()
        subcategoryRepository.save(aSubcategory(id = incomeSubcategoryId, kind = RecordableTransactionCategory.INCOME))

        // WHEN / THEN
        assertThatThrownBySuspending {
            service.update(anUpdateRecurringTransactionCommand(id = id, subcategoryId = incomeSubcategoryId))
        }.isInstanceOf(InvalidTransactionSubcategoryException::class.java)
    }
}
