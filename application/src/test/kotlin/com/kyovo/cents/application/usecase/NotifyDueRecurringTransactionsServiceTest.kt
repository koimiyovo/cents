package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryAccountRepository
import com.kyovo.cents.application.fakes.InMemoryRecurringTransactionRepository
import com.kyovo.cents.application.fakes.aRecurringTransaction
import com.kyovo.cents.application.fakes.aRecurringTransactionId
import com.kyovo.cents.application.fakes.anAccount
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.application.fakes.anInstant
import com.kyovo.cents.domain.model.RecurrenceFrequency
import com.kyovo.cents.domain.model.RecurringTransaction
import com.kyovo.cents.domain.port.output.RecurringTransactionNotifier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** Records what it is asked to notify. */
private class RecordingRecurringTransactionNotifier : RecurringTransactionNotifier
{
    val notified = mutableListOf<RecurringTransaction>()

    override suspend fun notify(recurringTransaction: RecurringTransaction)
    {
        notified += recurringTransaction
    }
}

/**
 * What the daily WorkManager check does once the day's transactions have been generated: tell the user
 * about each recurring expense that has an occurrence on the very day asked (today). It goes by the rule's
 * calendar, not by what was generated — generation can run ahead of today, so "already generated" says nothing
 * about whether today is the day — and it skips a rule generation would skip (an archived or unknown
 * account), since nothing was recorded for it and "recorded today" would be a lie.
 *
 * Not deduplicated here: asking twice on the same day notifies twice. Telling the same day's notification
 * apart from a new one is the notifier's job (a stable id per rule and day).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotifyDueRecurringTransactionsServiceTest
{
    private val today = LocalDate.of(2026, 9, 15)

    private val accountRepository = InMemoryAccountRepository()
    private val recurringTransactionRepository = InMemoryRecurringTransactionRepository()
    private val notifier = RecordingRecurringTransactionNotifier()

    private val service = NotifyDueRecurringTransactionsService(recurringTransactionRepository, accountRepository, notifier)

    private val accountId = anAccountId()

    private suspend fun givenAnActiveAccount()
    {
        accountRepository.save(anAccount(id = accountId))
    }

    private suspend fun givenRuleStartingOn(
        startDate: LocalDate,
        id: String = "66666666-6666-6666-6666-666666666666",
        frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
        endDate: LocalDate? = null,
        lastGeneratedDate: LocalDate? = null,
    ): RecurringTransaction
    {
        val rule = aRecurringTransaction(
            id = aRecurringTransactionId(id),
            accountId = accountId,
            frequency = frequency,
            startDate = startDate,
            endDate = endDate,
            lastGeneratedDate = lastGeneratedDate,
        )
        recurringTransactionRepository.save(rule)
        return rule
    }

    @Test
    fun `notifies a rule that has an occurrence today`() = runTest()
    {
        // GIVEN a monthly rule whose day of the month is today's
        givenAnActiveAccount()
        val rent = givenRuleStartingOn(LocalDate.of(2026, 7, 15))

        // WHEN
        service.notify(today)

        // THEN
        assertThat(notifier.notified).containsExactly(rent)
    }

    @Test
    fun `notifies nothing when no rule has an occurrence today`() = runTest()
    {
        // GIVEN a monthly rule due on the 5th, and a weekly one falling on the 2nd, 9th and 16th (never the 15th)
        givenAnActiveAccount()
        givenRuleStartingOn(LocalDate.of(2026, 7, 5))
        givenRuleStartingOn(LocalDate.of(2026, 9, 2), id = "77777777-7777-7777-7777-777777777777", frequency = RecurrenceFrequency.WEEKLY)

        // WHEN
        service.notify(today)

        // THEN
        assertThat(notifier.notified).isEmpty()
    }

    @Test
    fun `notifies every rule due today, and only those`() = runTest()
    {
        // GIVEN two rules due today (a monthly one and a weekly one) and one due tomorrow
        givenAnActiveAccount()
        val rent = givenRuleStartingOn(LocalDate.of(2026, 7, 15))
        val gym = givenRuleStartingOn(
            LocalDate.of(2026, 9, 1), id = "77777777-7777-7777-7777-777777777777", frequency = RecurrenceFrequency.WEEKLY,
        )
        givenRuleStartingOn(LocalDate.of(2026, 7, 16), id = "88888888-8888-8888-8888-888888888888")

        // WHEN
        service.notify(today)

        // THEN
        assertThat(notifier.notified).containsExactlyInAnyOrder(rent, gym)
    }

    @Test
    fun `an occurrence that was generated ahead of time is still notified on its day`() = runTest()
    {
        // GIVEN generation already went months past today
        givenAnActiveAccount()
        val rent = givenRuleStartingOn(LocalDate.of(2026, 7, 15), lastGeneratedDate = LocalDate.of(2026, 12, 15))

        // WHEN
        service.notify(today)

        // THEN
        assertThat(notifier.notified).containsExactly(rent)
    }

    @Test
    fun `a rule that has not started yet is not notified`() = runTest()
    {
        // GIVEN a rule starting next month, on the same day of the month
        givenAnActiveAccount()
        givenRuleStartingOn(LocalDate.of(2026, 10, 15))

        // WHEN
        service.notify(today)

        // THEN
        assertThat(notifier.notified).isEmpty()
    }

    @Test
    fun `a rule that has ended is not notified`() = runTest()
    {
        // GIVEN a rule that ended last month
        givenAnActiveAccount()
        givenRuleStartingOn(LocalDate.of(2026, 5, 15), endDate = LocalDate.of(2026, 8, 15))

        // WHEN
        service.notify(today)

        // THEN
        assertThat(notifier.notified).isEmpty()
    }

    @Test
    fun `a rule on an archived account is not notified, nothing was recorded for it`() = runTest()
    {
        // GIVEN
        accountRepository.save(anAccount(id = accountId, archivedAt = anInstant()))
        givenRuleStartingOn(LocalDate.of(2026, 7, 15))

        // WHEN
        service.notify(today)

        // THEN
        assertThat(notifier.notified).isEmpty()
    }

    @Test
    fun `a rule whose account no longer exists is not notified`() = runTest()
    {
        // GIVEN no account saved at all
        givenRuleStartingOn(LocalDate.of(2026, 7, 15))

        // WHEN
        service.notify(today)

        // THEN
        assertThat(notifier.notified).isEmpty()
    }

    @Test
    fun `a rule on an archived account does not stop the others from being notified`() = runTest()
    {
        // GIVEN one rule on an archived account and one on an active account, both due today
        val archivedId = anAccountId("99999999-9999-9999-9999-999999999999")
        accountRepository.save(anAccount(id = archivedId, archivedAt = anInstant()))
        givenAnActiveAccount()
        recurringTransactionRepository.save(
            aRecurringTransaction(
                id = aRecurringTransactionId("77777777-7777-7777-7777-777777777777"),
                accountId = archivedId,
                startDate = LocalDate.of(2026, 7, 15),
            )
        )
        val rent = givenRuleStartingOn(LocalDate.of(2026, 7, 15))

        // WHEN
        service.notify(today)

        // THEN
        assertThat(notifier.notified).containsExactly(rent)
    }

    @Test
    fun `the day asked is the day used, not the clock`() = runTest()
    {
        // GIVEN a rule due on the 5th
        givenAnActiveAccount()
        val rent = givenRuleStartingOn(LocalDate.of(2026, 7, 5))

        // WHEN asked about another day
        service.notify(LocalDate.of(2026, 10, 5))

        // THEN
        assertThat(notifier.notified).containsExactly(rent)
    }
}
