package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryAccountRepository
import com.kyovo.cents.application.fakes.InMemoryBudgetCalendarRepository
import com.kyovo.cents.application.fakes.InMemoryRecurringTransactionRepository
import com.kyovo.cents.application.fakes.InMemorySubcategoryRepository
import com.kyovo.cents.application.fakes.InMemoryTransactionRepository
import com.kyovo.cents.application.fakes.InMemoryUnitOfWork
import com.kyovo.cents.application.fakes.SequentialTransactionIdGenerator
import com.kyovo.cents.application.fakes.aMoney
import com.kyovo.cents.application.fakes.aRecurringTransaction
import com.kyovo.cents.application.fakes.aRecurringTransactionId
import com.kyovo.cents.application.fakes.aSubcategory
import com.kyovo.cents.application.fakes.aSubcategoryId
import com.kyovo.cents.application.fakes.anAccount
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.domain.model.BudgetStartDay
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.RecurrenceFrequency
import com.kyovo.cents.domain.model.RecurringTransaction
import com.kyovo.cents.domain.model.TransactionCategory
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
 * Catches every recurring transaction up: generates the real transactions of the occurrences that have
 * already come, and at most one more, the next one, when it falls later in the current month — never the
 * same occurrence twice, and never months in advance.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GenerateRecurringTransactionsServiceTest
{
    // "Today" is September 15th, 2026, UTC.
    private val clock = Clock.fixed(Instant.parse("2026-09-15T10:00:00Z"), ZoneOffset.UTC)

    private val accountRepository = InMemoryAccountRepository()
    private val subcategoryRepository = InMemorySubcategoryRepository()
    private val recurringTransactionRepository = InMemoryRecurringTransactionRepository()
    private val transactionRepository = InMemoryTransactionRepository()
    private val unitOfWork = InMemoryUnitOfWork()
    private val calendarRepository = InMemoryBudgetCalendarRepository()
    private val budgetAlerts = RecordingNotifyBudgetAlerts(transactionRepository)

    private val accountId = anAccountId()

    private fun aService(ids: List<TransactionId>, at: Clock = clock) = GenerateRecurringTransactionsService(
        recurringTransactionRepository,
        transactionRepository,
        accountRepository,
        subcategoryRepository,
        SequentialTransactionIdGenerator(ids),
        unitOfWork,
        calendarRepository,
        at,
        budgetAlerts,
    )

    private fun ids(count: Int) = List(count) { TransactionId(UUID.randomUUID()) }

    @Test
    fun `generates every occurrence already due, catching up on the past months`() = runTest()
    {
        // GIVEN a monthly rent starting in July, nothing generated yet — "today" is Sep 15th
        accountRepository.save(anAccount(id = accountId))
        recurringTransactionRepository.save(
            aRecurringTransaction(
                accountId = accountId,
                amount = aMoney(80_000),
                frequency = RecurrenceFrequency.MONTHLY,
                startDate = LocalDate.of(2026, 7, 5),
            )
        )

        // WHEN
        aService(ids(6)).generate()

        // THEN July, August and September — the ones that have come — and not October's, next month
        assertThat(transactionRepository.saved.map { it.date }).containsExactly(
            LocalDate.of(2026, 7, 5).atTime(12, 0).toInstant(ZoneOffset.UTC),
            LocalDate.of(2026, 8, 5).atTime(12, 0).toInstant(ZoneOffset.UTC),
            LocalDate.of(2026, 9, 5).atTime(12, 0).toInstant(ZoneOffset.UTC),
        )
        assertThat(transactionRepository.saved).allSatisfy { assertThat(it.amount).isEqualTo(aMoney(80_000)) }
    }

    @Test
    fun `advances lastGeneratedDate to the last occurrence generated`() = runTest()
    {
        // GIVEN
        accountRepository.save(anAccount(id = accountId))
        val rule = aRecurringTransaction(accountId = accountId, startDate = LocalDate.of(2026, 9, 5))
        recurringTransactionRepository.save(rule)

        // WHEN
        aService(ids(4)).generate()

        // THEN only September's, the one that has come (October's is next month)
        assertThat(recurringTransactionRepository.saved.single().lastGeneratedDate).isEqualTo(LocalDate.of(2026, 9, 5))
    }

    @Test
    fun `generates nothing more, and leaves lastGeneratedDate as it was, once caught up`() = runTest()
    {
        // GIVEN already generated well ahead (as an older version of the app did)
        accountRepository.save(anAccount(id = accountId))
        recurringTransactionRepository.save(
            aRecurringTransaction(
                accountId = accountId,
                startDate = LocalDate.of(2026, 9, 5),
                lastGeneratedDate = LocalDate.of(2026, 12, 5),
            )
        )

        // WHEN
        aService(ids(0)).generate()

        // THEN
        assertThat(transactionRepository.saved).isEmpty()
        assertThat(recurringTransactionRepository.saved.single().lastGeneratedDate).isEqualTo(LocalDate.of(2026, 12, 5))
    }

    @Test
    fun `does not generate past the current month`() = runTest()
    {
        // GIVEN a yearly rule started years ago
        accountRepository.save(anAccount(id = accountId))
        recurringTransactionRepository.save(
            aRecurringTransaction(
                accountId = accountId,
                frequency = RecurrenceFrequency.YEARLY,
                startDate = LocalDate.of(2020, 9, 5),
            )
        )

        // WHEN
        aService(ids(10)).generate()

        // THEN the last one generated is this year's (Sep 5th 2026), not next year's
        assertThat(recurringTransactionRepository.saved.single().lastGeneratedDate).isEqualTo(LocalDate.of(2026, 9, 5))
    }

    @Test
    fun `skips a rule whose account is archived, retried next time rather than lost`() = runTest()
    {
        // GIVEN
        accountRepository.save(anAccount(id = accountId, archivedAt = Instant.parse("2026-09-01T00:00:00Z")))
        recurringTransactionRepository.save(aRecurringTransaction(accountId = accountId, startDate = LocalDate.of(2026, 9, 5)))

        // WHEN
        aService(ids(0)).generate()

        // THEN nothing generated, and lastGeneratedDate untouched so it stays pending
        assertThat(transactionRepository.saved).isEmpty()
        assertThat(recurringTransactionRepository.saved.single().lastGeneratedDate).isNull()
    }

    @Test
    fun `generates without a subcategory when the rule's subcategory has since been deleted`() = runTest()
    {
        // GIVEN a rule pointing at a subcategory id that no longer resolves, a single occurrence due
        accountRepository.save(anAccount(id = accountId))
        val goneSubcategoryId = aSubcategoryId()
        recurringTransactionRepository.save(
            aRecurringTransaction(
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
        recurringTransactionRepository.save(
            aRecurringTransaction(
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
        recurringTransactionRepository.save(
            aRecurringTransaction(
                id = aRecurringTransactionId("11111111-1111-1111-1111-111111111111"),
                accountId = accountId,
                startDate = LocalDate.of(2026, 9, 5),
                endDate = LocalDate.of(2026, 9, 5),
            )
        )
        recurringTransactionRepository.save(
            aRecurringTransaction(
                id = aRecurringTransactionId("22222222-2222-2222-2222-222222222222"),
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
        // GIVEN a monthly rule from the 5th: September's occurrence is the one that has come (today is Sep 15th)
        accountRepository.save(anAccount(id = accountId))
        recurringTransactionRepository.save(aRecurringTransaction(accountId = accountId, startDate = LocalDate.of(2026, 9, 5)))

        // WHEN
        aService(ids(4)).generate()

        // THEN September, the current month, is checked
        assertThat(budgetAlerts.months).containsExactly(YearMonth.of(2026, 9))
    }

    @Test
    fun `checks the current month once, however many transactions it received`() = runTest()
    {
        // GIVEN two rules that both generate a September transaction
        accountRepository.save(anAccount(id = accountId))
        recurringTransactionRepository.save(aRecurringTransaction(accountId = accountId, startDate = LocalDate.of(2026, 9, 5)))
        recurringTransactionRepository.save(
            aRecurringTransaction(
                id = aRecurringTransactionId("77777777-7777-7777-7777-777777777777"),
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
        recurringTransactionRepository.save(aRecurringTransaction(accountId = accountId, startDate = LocalDate.of(2026, 9, 5)))

        // WHEN
        aService(ids(4)).generate()

        // THEN September's transaction was there when the check ran
        assertThat(budgetAlerts.savedWhenCalled).containsExactly(1)
    }

    @Test
    fun `checks nothing when nothing was generated`() = runTest()
    {
        // GIVEN already caught up to the horizon
        accountRepository.save(anAccount(id = accountId))
        recurringTransactionRepository.save(
            aRecurringTransaction(
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
    fun `a rule that starts next month generates nothing yet, so nothing is checked`() = runTest()
    {
        // GIVEN a rule whose first occurrence is next month (today is Sep 15th)
        accountRepository.save(anAccount(id = accountId))
        recurringTransactionRepository.save(aRecurringTransaction(accountId = accountId, startDate = LocalDate.of(2026, 10, 5)))

        // WHEN
        aService(ids(3)).generate()

        // THEN
        assertThat(transactionRepository.saved).isEmpty()
        assertThat(recurringTransactionRepository.saved.single().lastGeneratedDate).isNull()
        assertThat(budgetAlerts.months).isEmpty()
    }

    @Test
    fun `does not check a month already past, a catch-up of old occurrences is not news`() = runTest()
    {
        // GIVEN a rule that ended in August, never generated (the phone was off)
        accountRepository.save(anAccount(id = accountId))
        recurringTransactionRepository.save(
            aRecurringTransaction(
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
        recurringTransactionRepository.save(aRecurringTransaction(accountId = accountId, startDate = LocalDate.of(2026, 9, 5)))

        // WHEN
        aService(ids(4)).generate()

        // THEN
        assertThat(transactionRepository.saved).isEmpty()
        assertThat(budgetAlerts.months).isEmpty()
    }

    // ------------------------------------------------------------------ incomes
    // A recurring income (a salary) is generated like a recurring expense, but as income transactions, and it
    // is no news to a budget: only spending can cross one.

    @Test
    fun `an income rule generates income transactions, filed under its income subcategory`() = runTest()
    {
        // GIVEN a monthly salary from the 5th (today is Sep 15th): September's is the one that has come
        accountRepository.save(anAccount(id = accountId))
        val salaryId = aSubcategoryId()
        subcategoryRepository.save(aSubcategory(id = salaryId, kind = RecordableTransactionCategory.INCOME))
        givenRule(
            aRecurringTransaction(
                accountId = accountId,
                category = RecordableTransactionCategory.INCOME,
                amount = aMoney(200_000),
                subcategoryId = salaryId,
                startDate = LocalDate.of(2026, 9, 5),
            )
        )

        // WHEN
        aService(ids(4)).generate()

        // THEN
        assertThat(transactionRepository.saved).hasSize(1)
        assertThat(transactionRepository.saved.map { it.category }).containsOnly(TransactionCategory.INCOME)
        assertThat(transactionRepository.saved.map { it.subcategoryId }).containsOnly(salaryId)
        assertThat(transactionRepository.saved.map { it.amount }).containsOnly(aMoney(200_000))
    }

    @Test
    fun `an income does not trigger the budget check, even in the current month`() = runTest()
    {
        // GIVEN
        accountRepository.save(anAccount(id = accountId))
        givenRule(
            aRecurringTransaction(
                accountId = accountId,
                category = RecordableTransactionCategory.INCOME,
                startDate = LocalDate.of(2026, 9, 5),
            )
        )

        // WHEN
        aService(ids(4)).generate()

        // THEN September is recorded, and no alert is checked
        assertThat(transactionRepository.saved).hasSize(1)
        assertThat(budgetAlerts.months).isEmpty()
    }

    @Test
    fun `an income next to an expense in the same month checks the budget once, for the expense`() = runTest()
    {
        // GIVEN
        accountRepository.save(anAccount(id = accountId))
        givenRule(
            aRecurringTransaction(
                accountId = accountId,
                category = RecordableTransactionCategory.INCOME,
                startDate = LocalDate.of(2026, 9, 5),
            )
        )
        givenRule(
            aRecurringTransaction(
                id = aRecurringTransactionId("77777777-7777-7777-7777-777777777777"),
                accountId = accountId,
                startDate = LocalDate.of(2026, 9, 20),
            )
        )

        // WHEN
        aService(ids(8)).generate()

        // THEN
        assertThat(budgetAlerts.months).containsExactly(YearMonth.of(2026, 9))
    }

    private suspend fun givenRule(rule: RecurringTransaction)
    {
        recurringTransactionRepository.save(rule)
    }

    // ------------------------------------------------------------------ what is generated, and when
    // Generating months ahead crowded the transactions list with what is not due yet. So a rule generates
    // what has already come, plus at most one more: the next one, when it falls later this month.

    @Test
    fun `generates the first occurrence when it falls later in the current month`() = runTest()
    {
        // GIVEN a monthly rule whose first occurrence is Sep 20th (today is Sep 15th)
        accountRepository.save(anAccount(id = accountId))
        recurringTransactionRepository.save(aRecurringTransaction(accountId = accountId, startDate = LocalDate.of(2026, 9, 20)))

        // WHEN
        aService(ids(4)).generate()

        // THEN only that one, and not October's, November's or December's
        assertThat(transactionRepository.saved.map { it.date })
            .containsExactly(LocalDate.of(2026, 9, 20).atTime(12, 0).toInstant(ZoneOffset.UTC))
        assertThat(recurringTransactionRepository.saved.single().lastGeneratedDate).isEqualTo(LocalDate.of(2026, 9, 20))
    }

    @Test
    fun `generates nothing for a rule whose first occurrence falls in a later month`() = runTest()
    {
        // GIVEN a first occurrence on Oct 5th (today is Sep 15th)
        accountRepository.save(anAccount(id = accountId))
        recurringTransactionRepository.save(aRecurringTransaction(accountId = accountId, startDate = LocalDate.of(2026, 10, 5)))

        // WHEN
        aService(ids(4)).generate()

        // THEN nothing yet, and it stays pending
        assertThat(transactionRepository.saved).isEmpty()
        assertThat(recurringTransactionRepository.saved.single().lastGeneratedDate).isNull()
    }

    @Test
    fun `generates the next occurrence once its month has come`() = runTest()
    {
        // GIVEN a monthly rule generated up to September, and "today" is now Oct 1st
        accountRepository.save(anAccount(id = accountId))
        recurringTransactionRepository.save(
            aRecurringTransaction(
                accountId = accountId,
                startDate = LocalDate.of(2026, 7, 5),
                lastGeneratedDate = LocalDate.of(2026, 9, 5),
            )
        )
        val firstOfOctober = Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneOffset.UTC)

        // WHEN
        aService(ids(4), firstOfOctober).generate()

        // THEN October's, which falls later this month, and not November's
        assertThat(transactionRepository.saved.map { it.date })
            .containsExactly(LocalDate.of(2026, 10, 5).atTime(12, 0).toInstant(ZoneOffset.UTC))
    }

    @Test
    fun `generates at most one occurrence ahead of today, after the ones that have come`() = runTest()
    {
        // GIVEN a weekly rule from Thursday Sep 10th (today is Tuesday Sep 15th): 10th has come, 17th and 24th are ahead
        accountRepository.save(anAccount(id = accountId))
        recurringTransactionRepository.save(
            aRecurringTransaction(
                accountId = accountId,
                frequency = RecurrenceFrequency.WEEKLY,
                startDate = LocalDate.of(2026, 9, 10),
            )
        )

        // WHEN
        aService(ids(6)).generate()

        // THEN the 10th, and the next one (the 17th), but not the 24th
        assertThat(transactionRepository.saved.map { it.date }).containsExactly(
            LocalDate.of(2026, 9, 10).atTime(12, 0).toInstant(ZoneOffset.UTC),
            LocalDate.of(2026, 9, 17).atTime(12, 0).toInstant(ZoneOffset.UTC),
        )
        assertThat(recurringTransactionRepository.saved.single().lastGeneratedDate).isEqualTo(LocalDate.of(2026, 9, 17))
    }

    // The service runs at every launch and every day: running it again must not pull one more occurrence ahead.
    @Test
    fun `running it again generates nothing more while one occurrence is already ahead`() = runTest()
    {
        // GIVEN the weekly rule above, already generated once
        accountRepository.save(anAccount(id = accountId))
        recurringTransactionRepository.save(
            aRecurringTransaction(
                accountId = accountId,
                frequency = RecurrenceFrequency.WEEKLY,
                startDate = LocalDate.of(2026, 9, 10),
            )
        )
        aService(ids(6)).generate()

        // WHEN it runs a second time the same day
        aService(ids(6)).generate()

        // THEN still the two of the first run
        assertThat(transactionRepository.saved).hasSize(2)
        assertThat(recurringTransactionRepository.saved.single().lastGeneratedDate).isEqualTo(LocalDate.of(2026, 9, 17))
    }

    @Test
    fun `generates the next one ahead once the previous one has come`() = runTest()
    {
        // GIVEN the weekly rule generated up to the 17th, and "today" is now the 17th
        accountRepository.save(anAccount(id = accountId))
        recurringTransactionRepository.save(
            aRecurringTransaction(
                accountId = accountId,
                frequency = RecurrenceFrequency.WEEKLY,
                startDate = LocalDate.of(2026, 9, 10),
                lastGeneratedDate = LocalDate.of(2026, 9, 17),
            )
        )
        val theSeventeenth = Clock.fixed(Instant.parse("2026-09-17T10:00:00Z"), ZoneOffset.UTC)

        // WHEN
        aService(ids(4), theSeventeenth).generate()

        // THEN the 24th, but not October's 1st, which is next month
        assertThat(transactionRepository.saved.map { it.date })
            .containsExactly(LocalDate.of(2026, 9, 24).atTime(12, 0).toInstant(ZoneOffset.UTC))
    }

    // ------------------------------------------------------------------ budget cycles
    // "The current month" is the budget cycle open today, and "later this month" runs to the end of that
    // cycle: with pay on the 25th, the 26th of September is already October's budget month.

    private fun clockAt(instant: String) = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC)

    @Test
    fun `checks the budget alerts of the cycle open today, not of the calendar month`() = runTest()
    {
        // GIVEN cycles opening on the 25th, and today is September 26th: October's cycle, begun yesterday
        calendarRepository.saveDefaultStartDay(BudgetStartDay(25))
        accountRepository.save(anAccount(id = accountId))
        recurringTransactionRepository.save(aRecurringTransaction(accountId = accountId, startDate = LocalDate.of(2026, 9, 26)))

        // WHEN
        aService(ids(4), clockAt("2026-09-26T10:00:00Z")).generate()

        // THEN
        assertThat(transactionRepository.saved).hasSize(1)
        assertThat(budgetAlerts.months).containsExactly(YearMonth.of(2026, 10))
    }

    @Test
    fun `does not check a cycle that received nothing`() = runTest()
    {
        // GIVEN cycles opening on the 25th, today is September 26th, and the rule's only occurrence due is
        // September 20th: September's cycle, which is over
        calendarRepository.saveDefaultStartDay(BudgetStartDay(25))
        accountRepository.save(anAccount(id = accountId))
        recurringTransactionRepository.save(
            aRecurringTransaction(accountId = accountId, startDate = LocalDate.of(2026, 9, 20), endDate = LocalDate.of(2026, 9, 20))
        )

        // WHEN
        aService(ids(4), clockAt("2026-09-26T10:00:00Z")).generate()

        // THEN it is recorded (a catch-up), but nothing is news for the cycle open today
        assertThat(transactionRepository.saved).hasSize(1)
        assertThat(budgetAlerts.months).isEmpty()
    }

    @Test
    fun `the next occurrence is generated while it falls before the end of the cycle, even in the next calendar month`() = runTest()
    {
        // GIVEN cycles opening on the 5th: today (September 15th) is in the cycle that ends on October 4th
        calendarRepository.saveDefaultStartDay(BudgetStartDay(5))
        accountRepository.save(anAccount(id = accountId))
        recurringTransactionRepository.save(aRecurringTransaction(accountId = accountId, startDate = LocalDate.of(2026, 8, 3)))

        // WHEN
        aService(ids(4)).generate()

        // THEN August 3rd and September 3rd have come, and October 3rd is still this cycle's
        assertThat(transactionRepository.saved.map { it.date.atZone(ZoneOffset.UTC).toLocalDate() })
            .containsExactlyInAnyOrder(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 9, 3), LocalDate.of(2026, 10, 3))
    }

    @Test
    fun `an occurrence after the end of the cycle waits for the next one`() = runTest()
    {
        // GIVEN cycles opening on the 5th: today (September 15th) is in the cycle that ends on October 4th
        calendarRepository.saveDefaultStartDay(BudgetStartDay(5))
        accountRepository.save(anAccount(id = accountId))
        recurringTransactionRepository.save(aRecurringTransaction(accountId = accountId, startDate = LocalDate.of(2026, 8, 10)))

        // WHEN
        aService(ids(4)).generate()

        // THEN October 10th is next cycle's
        assertThat(transactionRepository.saved.map { it.date.atZone(ZoneOffset.UTC).toLocalDate() })
            .containsExactlyInAnyOrder(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 10))
    }
}
