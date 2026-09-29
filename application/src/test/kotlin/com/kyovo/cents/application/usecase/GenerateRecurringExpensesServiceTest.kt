package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryAccountRepository
import com.kyovo.cents.application.fakes.InMemoryRecurringExpenseRepository
import com.kyovo.cents.application.fakes.InMemorySubcategoryRepository
import com.kyovo.cents.application.fakes.InMemoryTransactionRepository
import com.kyovo.cents.application.fakes.InMemoryUnitOfWork
import com.kyovo.cents.application.fakes.SequentialTransactionIdGenerator
import com.kyovo.cents.application.fakes.aMoney
import com.kyovo.cents.application.fakes.aRecurringExpense
import com.kyovo.cents.application.fakes.aRecurringExpenseId
import com.kyovo.cents.application.fakes.aSubcategory
import com.kyovo.cents.application.fakes.aSubcategoryId
import com.kyovo.cents.application.fakes.anAccount
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.RecurrenceFrequency
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.port.input.NotifyBudgetAlertUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.UUID

/**
 * Records the months it is asked to check the budget alerts of, and how many transactions were saved at
 * that moment (to tell whether the check came after the transactions were written).
 */
private class RecordingNotifyBudgetAlerts(private val transactions: InMemoryTransactionRepository) : NotifyBudgetAlertUseCase
{
    val months = mutableListOf<YearMonth>()
    val savedWhenCalled = mutableListOf<Int>()

    override suspend fun notify(month: YearMonth)
    {
        months += month
        savedWhenCalled += transactions.saved.size
    }
}

/**
 * Catches every recurring expense up: generates the real transactions for whichever of their due
 * occurrences have not been generated yet, up to a few months ahead of today — never forever, and never
 * the same occurrence twice.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GenerateRecurringExpensesServiceTest
{
    // "Today" is September 15th, 2026, UTC.
    private val clock = Clock.fixed(Instant.parse("2026-09-15T10:00:00Z"), ZoneOffset.UTC)

    private val accountRepository = InMemoryAccountRepository()
    private val subcategoryRepository = InMemorySubcategoryRepository()
    private val recurringExpenseRepository = InMemoryRecurringExpenseRepository()
    private val transactionRepository = InMemoryTransactionRepository()
    private val unitOfWork = InMemoryUnitOfWork()
    private val budgetAlerts = RecordingNotifyBudgetAlerts(transactionRepository)

    private val accountId = anAccountId()

    private fun aService(ids: List<TransactionId>) = GenerateRecurringExpensesService(
        recurringExpenseRepository,
        transactionRepository,
        accountRepository,
        subcategoryRepository,
        SequentialTransactionIdGenerator(ids),
        unitOfWork,
        clock,
        budgetAlerts,
    )

    private fun ids(count: Int) = List(count) { TransactionId(UUID.randomUUID()) }

    @Test
    fun `generates a transaction for every pending occurrence, up to the lookahead horizon`() = runTest()
    {
        // GIVEN a monthly rent starting in July, nothing generated yet — "today" is Sep 15th, the
        // horizon is 3 months ahead of it, Dec 15th
        accountRepository.save(anAccount(id = accountId))
        recurringExpenseRepository.save(
            aRecurringExpense(
                accountId = accountId,
                amount = aMoney(80_000),
                frequency = RecurrenceFrequency.MONTHLY,
                startDate = LocalDate.of(2026, 7, 5),
            )
        )

        // WHEN
        aService(ids(6)).generate()

        // THEN July through December — the next one, January, is past the horizon
        assertThat(transactionRepository.saved.map { it.date }).containsExactly(
            LocalDate.of(2026, 7, 5).atTime(12, 0).toInstant(ZoneOffset.UTC),
            LocalDate.of(2026, 8, 5).atTime(12, 0).toInstant(ZoneOffset.UTC),
            LocalDate.of(2026, 9, 5).atTime(12, 0).toInstant(ZoneOffset.UTC),
            LocalDate.of(2026, 10, 5).atTime(12, 0).toInstant(ZoneOffset.UTC),
            LocalDate.of(2026, 11, 5).atTime(12, 0).toInstant(ZoneOffset.UTC),
            LocalDate.of(2026, 12, 5).atTime(12, 0).toInstant(ZoneOffset.UTC),
        )
        assertThat(transactionRepository.saved).allSatisfy { assertThat(it.amount).isEqualTo(aMoney(80_000)) }
    }

    @Test
    fun `advances lastGeneratedDate to the last occurrence generated`() = runTest()
    {
        // GIVEN
        accountRepository.save(anAccount(id = accountId))
        val rule = aRecurringExpense(accountId = accountId, startDate = LocalDate.of(2026, 9, 5))
        recurringExpenseRepository.save(rule)

        // WHEN
        aService(ids(4)).generate()

        // THEN generated up to the lookahead horizon (today + 3 months)
        assertThat(recurringExpenseRepository.saved.single().lastGeneratedDate).isEqualTo(LocalDate.of(2026, 12, 5))
    }

    @Test
    fun `generates nothing more, and leaves lastGeneratedDate as it was, once caught up`() = runTest()
    {
        // GIVEN already generated up to the lookahead horizon
        accountRepository.save(anAccount(id = accountId))
        recurringExpenseRepository.save(
            aRecurringExpense(
                accountId = accountId,
                startDate = LocalDate.of(2026, 9, 5),
                lastGeneratedDate = LocalDate.of(2026, 12, 5),
            )
        )

        // WHEN
        aService(ids(0)).generate()

        // THEN
        assertThat(transactionRepository.saved).isEmpty()
        assertThat(recurringExpenseRepository.saved.single().lastGeneratedDate).isEqualTo(LocalDate.of(2026, 12, 5))
    }

    @Test
    fun `does not generate past the lookahead horizon`() = runTest()
    {
        // GIVEN a weekly rule that would otherwise have many more occurrences
        accountRepository.save(anAccount(id = accountId))
        recurringExpenseRepository.save(
            aRecurringExpense(
                accountId = accountId,
                frequency = RecurrenceFrequency.YEARLY,
                startDate = LocalDate.of(2020, 9, 5),
            )
        )

        // WHEN
        aService(ids(10)).generate()

        // THEN the last one generated is the one on or before today + 3 months (Dec 15th), i.e. Sep 5th 2026
        assertThat(recurringExpenseRepository.saved.single().lastGeneratedDate).isEqualTo(LocalDate.of(2026, 9, 5))
    }

    @Test
    fun `skips a rule whose account is archived, retried next time rather than lost`() = runTest()
    {
        // GIVEN
        accountRepository.save(anAccount(id = accountId, archivedAt = Instant.parse("2026-09-01T00:00:00Z")))
        recurringExpenseRepository.save(aRecurringExpense(accountId = accountId, startDate = LocalDate.of(2026, 9, 5)))

        // WHEN
        aService(ids(0)).generate()

        // THEN nothing generated, and lastGeneratedDate untouched so it stays pending
        assertThat(transactionRepository.saved).isEmpty()
        assertThat(recurringExpenseRepository.saved.single().lastGeneratedDate).isNull()
    }

    @Test
    fun `generates without a subcategory when the rule's subcategory has since been deleted`() = runTest()
    {
        // GIVEN a rule pointing at a subcategory id that no longer resolves, a single occurrence due
        accountRepository.save(anAccount(id = accountId))
        val goneSubcategoryId = aSubcategoryId()
        recurringExpenseRepository.save(
            aRecurringExpense(
                accountId = accountId,
                subcategoryId = goneSubcategoryId,
                startDate = LocalDate.of(2026, 9, 5),
                endDate = LocalDate.of(2026, 9, 5),
            )
        )

        // WHEN
        aService(ids(1)).generate()

        // THEN
        assertThat(transactionRepository.saved.single().subcategoryId).isNull()
    }

    @Test
    fun `resolves the subcategory when it still exists`() = runTest()
    {
        // GIVEN a single occurrence due
        accountRepository.save(anAccount(id = accountId))
        val subcategoryId = aSubcategoryId()
        subcategoryRepository.save(aSubcategory(id = subcategoryId))
        recurringExpenseRepository.save(
            aRecurringExpense(
                accountId = accountId,
                subcategoryId = subcategoryId,
                startDate = LocalDate.of(2026, 9, 5),
                endDate = LocalDate.of(2026, 9, 5),
            )
        )

        // WHEN
        aService(ids(1)).generate()

        // THEN
        assertThat(transactionRepository.saved.single().subcategoryId).isEqualTo(subcategoryId)
    }

    @Test
    fun `generates independently for several rules`() = runTest()
    {
        // GIVEN two accounts, two rules, each with a single occurrence due
        val otherAccountId = anAccountId("22222222-2222-2222-2222-222222222222")
        accountRepository.save(anAccount(id = accountId))
        accountRepository.save(anAccount(id = otherAccountId, name = AccountName("Autre")))
        recurringExpenseRepository.save(
            aRecurringExpense(
                id = aRecurringExpenseId("11111111-1111-1111-1111-111111111111"),
                accountId = accountId,
                startDate = LocalDate.of(2026, 9, 5),
                endDate = LocalDate.of(2026, 9, 5),
            )
        )
        recurringExpenseRepository.save(
            aRecurringExpense(
                id = aRecurringExpenseId("22222222-2222-2222-2222-222222222222"),
                accountId = otherAccountId,
                startDate = LocalDate.of(2026, 9, 5),
                endDate = LocalDate.of(2026, 9, 5),
            )
        )

        // WHEN
        aService(ids(2)).generate()

        // THEN one generated transaction per rule
        assertThat(transactionRepository.saved).hasSize(2)
        assertThat(transactionRepository.saved.map { it.accountId }).containsExactlyInAnyOrder(accountId, otherAccountId)
    }

    @Test
    fun `does nothing when there is no recurring expense`() = runTest()
    {
        // WHEN / THEN no exception, nothing generated
        aService(ids(0)).generate()
        assertThat(transactionRepository.saved).isEmpty()
    }

    // ------------------------------------------------------------------ budget alerts
    // A generated expense can push a budget over a threshold, and nobody is typing it in: the user is told as
    // soon as it is recorded, not at the next daily check. Only the current month is checked — a month ahead
    // is checked when it becomes the current one, and a month long past is not news any more.

    @Test
    fun `checks the budget alerts of the current month once its transactions are generated`() = runTest()
    {
        // GIVEN a monthly rule from the 5th: September (today is Sep 15th) up to December
        accountRepository.save(anAccount(id = accountId))
        recurringExpenseRepository.save(aRecurringExpense(accountId = accountId, startDate = LocalDate.of(2026, 9, 5)))

        // WHEN
        aService(ids(4)).generate()

        // THEN only September, the current month, is checked
        assertThat(budgetAlerts.months).containsExactly(YearMonth.of(2026, 9))
    }

    @Test
    fun `checks the current month once, however many transactions it received`() = runTest()
    {
        // GIVEN two rules that both generate a September transaction
        accountRepository.save(anAccount(id = accountId))
        recurringExpenseRepository.save(aRecurringExpense(accountId = accountId, startDate = LocalDate.of(2026, 9, 5)))
        recurringExpenseRepository.save(
            aRecurringExpense(
                id = aRecurringExpenseId("77777777-7777-7777-7777-777777777777"),
                accountId = accountId,
                startDate = LocalDate.of(2026, 9, 20),
            )
        )

        // WHEN
        aService(ids(8)).generate()

        // THEN
        assertThat(budgetAlerts.months).containsExactly(YearMonth.of(2026, 9))
    }

    @Test
    fun `checks after the transactions are saved, so the check sees them`() = runTest()
    {
        // GIVEN
        accountRepository.save(anAccount(id = accountId))
        recurringExpenseRepository.save(aRecurringExpense(accountId = accountId, startDate = LocalDate.of(2026, 9, 5)))

        // WHEN
        aService(ids(4)).generate()

        // THEN all four transactions (September to December) were there when the check ran
        assertThat(budgetAlerts.savedWhenCalled).containsExactly(4)
    }

    @Test
    fun `checks nothing when nothing was generated`() = runTest()
    {
        // GIVEN already caught up to the horizon
        accountRepository.save(anAccount(id = accountId))
        recurringExpenseRepository.save(
            aRecurringExpense(
                accountId = accountId,
                startDate = LocalDate.of(2026, 9, 5),
                lastGeneratedDate = LocalDate.of(2026, 12, 5),
            )
        )

        // WHEN
        aService(ids(0)).generate()

        // THEN
        assertThat(budgetAlerts.months).isEmpty()
    }

    @Test
    fun `does not check a month ahead, only generated occurrences of the current one count`() = runTest()
    {
        // GIVEN a rule that starts next month: October to December are generated, nothing in September
        accountRepository.save(anAccount(id = accountId))
        recurringExpenseRepository.save(aRecurringExpense(accountId = accountId, startDate = LocalDate.of(2026, 10, 5)))

        // WHEN
        aService(ids(3)).generate()

        // THEN
        assertThat(transactionRepository.saved).hasSize(3)
        assertThat(budgetAlerts.months).isEmpty()
    }

    @Test
    fun `does not check a month already past, a catch-up of old occurrences is not news`() = runTest()
    {
        // GIVEN a rule that ended in August, never generated (the phone was off)
        accountRepository.save(anAccount(id = accountId))
        recurringExpenseRepository.save(
            aRecurringExpense(
                accountId = accountId,
                startDate = LocalDate.of(2026, 6, 5),
                endDate = LocalDate.of(2026, 8, 5),
            )
        )

        // WHEN
        aService(ids(3)).generate()

        // THEN June, July and August are recorded, none of them is checked
        assertThat(transactionRepository.saved).hasSize(3)
        assertThat(budgetAlerts.months).isEmpty()
    }

    @Test
    fun `does not check for a rule that was skipped, nothing was recorded for it`() = runTest()
    {
        // GIVEN a rule on an archived account
        accountRepository.save(anAccount(id = accountId, archivedAt = Instant.parse("2026-09-01T00:00:00Z")))
        recurringExpenseRepository.save(aRecurringExpense(accountId = accountId, startDate = LocalDate.of(2026, 9, 5)))

        // WHEN
        aService(ids(4)).generate()

        // THEN
        assertThat(transactionRepository.saved).isEmpty()
        assertThat(budgetAlerts.months).isEmpty()
    }
}
