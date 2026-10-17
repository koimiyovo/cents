package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryBudgetCalendarRepository
import com.kyovo.cents.application.fakes.InMemoryTransactionRepository
import com.kyovo.cents.application.fakes.aMoney
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.application.fakes.aSubcategoryId
import com.kyovo.cents.application.fakes.aTransaction
import com.kyovo.cents.application.fakes.aTransactionId
import com.kyovo.cents.application.fakes.anInstant
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.YearMonth
import java.time.ZoneId

/** Where the money came from this month, by subcategory: the mirror of [GetSpendingBreakdownService]. */
class GetIncomeBreakdownServiceTest
{
    private val salaryId = aSubcategoryId("11111111-1111-1111-1111-111111111111")
    private val giftId = aSubcategoryId("22222222-2222-2222-2222-222222222222")
    private val september = YearMonth.of(2026, 9)

    private val transactionRepository = InMemoryTransactionRepository()
    private val service = GetIncomeBreakdownService(transactionRepository, InMemoryBudgetCalendarRepository(), ZoneId.of("UTC"))

    private fun aTransactionOf(
        suffix: Int,
        category: TransactionCategory,
        cents: Long,
        date: String,
        subcategoryId: SubcategoryId? = salaryId,
        accountId: AccountId = anAccountId(),
    ): Transaction =
        aTransaction(
            id = aTransactionId("33333333-3333-3333-3333-33333333333$suffix"),
            accountId = accountId,
            amount = aMoney(cents),
            date = anInstant(date),
            category = category,
            subcategoryId = subcategoryId,
        )

    @Test
    fun `sums the income of the month by subcategory, income with none kept apart, expenses ignored`() = runTest()
    {
        // GIVEN
        transactionRepository.save(aTransactionOf(1, TransactionCategory.INCOME, 200_000, "2026-09-01T08:00:00Z"))
        transactionRepository.save(aTransactionOf(2, TransactionCategory.INCOME, 5_000, "2026-09-10T08:00:00Z", subcategoryId = giftId))
        transactionRepository.save(aTransactionOf(3, TransactionCategory.INCOME, 1_500, "2026-09-12T08:00:00Z", subcategoryId = null))
        transactionRepository.save(aTransactionOf(4, TransactionCategory.EXPENSE, 9_000, "2026-09-12T08:00:00Z"))

        // WHEN
        val breakdown = service.observe(september).first()

        // THEN
        assertThat(breakdown).isEqualTo(
            mapOf(
                salaryId to aMoney(200_000),
                giftId to aMoney(5_000),
                null to aMoney(1_500),
            )
        )
    }

    @Test
    fun `leaves out the income of other months`() = runTest()
    {
        // GIVEN
        transactionRepository.save(aTransactionOf(1, TransactionCategory.INCOME, 200_000, "2026-08-31T22:00:00Z"))
        transactionRepository.save(aTransactionOf(2, TransactionCategory.INCOME, 1_000, "2026-09-30T22:00:00Z"))
        transactionRepository.save(aTransactionOf(3, TransactionCategory.INCOME, 300_000, "2026-10-01T00:00:00Z"))

        // WHEN
        val breakdown = service.observe(september).first()

        // THEN
        assertThat(breakdown).isEqualTo(mapOf(salaryId to aMoney(1_000)))
    }

    @Test
    fun `an account given counts only that account's income`() = runTest()
    {
        // GIVEN
        val checking = anAccountId("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
        val savings = anAccountId("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
        transactionRepository.save(aTransactionOf(1, TransactionCategory.INCOME, 200_000, "2026-09-01T08:00:00Z", accountId = checking))
        transactionRepository.save(aTransactionOf(2, TransactionCategory.INCOME, 1_000, "2026-09-02T08:00:00Z", accountId = savings))

        // WHEN
        val breakdown = service.observe(september, savings).first()

        // THEN
        assertThat(breakdown).isEqualTo(mapOf(salaryId to aMoney(1_000)))
    }
}
