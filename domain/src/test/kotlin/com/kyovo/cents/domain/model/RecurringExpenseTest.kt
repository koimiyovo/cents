package com.kyovo.cents.domain.model

import com.kyovo.cents.domain.exception.InvalidRecurringExpenseEndException
import com.kyovo.cents.domain.exception.InvalidRecurringExpenseIntervalException
import com.kyovo.cents.domain.exception.InvalidTransactionAmountException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

/**
 * A recurring expense's occurrences fall on its start date and every [RecurringExpense.interval] of its
 * [RecurringExpense.frequency] after it — weekly, monthly, yearly, or "every N" of any of those.
 */
class RecurringExpenseTest
{
    private val accountId = AccountId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
    private val id = RecurringExpenseId(UUID.fromString("22222222-2222-2222-2222-222222222222"))

    private fun aRule(
        frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
        interval: Int = 1,
        startDate: LocalDate = LocalDate.of(2026, 9, 5),
        endDate: LocalDate? = null,
        lastGeneratedDate: LocalDate? = null,
        amount: Money = Money(80_000),
    ) = RecurringExpense(
        id = id,
        accountId = accountId,
        amount = amount,
        title = TransactionTitle("Loyer"),
        subcategoryId = null,
        description = null,
        frequency = frequency,
        interval = interval,
        startDate = startDate,
        endDate = endDate,
        lastGeneratedDate = lastGeneratedDate,
    )

    @Test
    fun `holds what it was given, with no end and nothing generated yet unless given`()
    {
        // WHEN
        val rule = aRule()

        // THEN
        assertThat(rule.endDate).isNull()
        assertThat(rule.lastGeneratedDate).isNull()
        assertThat(rule.interval).isEqualTo(1)
    }

    @Test
    fun `refuses a zero amount, the same rule as a recorded transaction`()
    {
        // WHEN / THEN
        assertThatThrownBy { aRule(amount = Money(0)) }
            .isInstanceOf(InvalidTransactionAmountException::class.java)
    }

    @Test
    fun `refuses an interval below 1`()
    {
        // WHEN / THEN
        assertThatThrownBy { aRule(interval = 0) }
            .isInstanceOf(InvalidRecurringExpenseIntervalException::class.java)
        assertThatThrownBy { aRule(interval = -1) }
            .isInstanceOf(InvalidRecurringExpenseIntervalException::class.java)
    }

    @Test
    fun `refuses an end date before the start date`()
    {
        // WHEN / THEN
        assertThatThrownBy {
            aRule(startDate = LocalDate.of(2026, 9, 5), endDate = LocalDate.of(2026, 9, 4))
        }.isInstanceOf(InvalidRecurringExpenseEndException::class.java)
    }

    @Test
    fun `accepts an end date equal to the start date, a rule for a single occurrence`()
    {
        // WHEN
        val rule = aRule(startDate = LocalDate.of(2026, 9, 5), endDate = LocalDate.of(2026, 9, 5))

        // THEN
        assertThat(rule.pendingOccurrences(LocalDate.of(2027, 1, 1))).containsExactly(LocalDate.of(2026, 9, 5))
    }

    @Test
    fun `weekly occurrences are every interval weeks from the start date`()
    {
        // WHEN every 2 weeks from Sep 5th
        val rule = aRule(frequency = RecurrenceFrequency.WEEKLY, interval = 2, startDate = LocalDate.of(2026, 9, 5))

        // THEN
        assertThat(rule.pendingOccurrences(LocalDate.of(2026, 10, 10))).containsExactly(
            LocalDate.of(2026, 9, 5),
            LocalDate.of(2026, 9, 19),
            LocalDate.of(2026, 10, 3),
        )
    }

    @Test
    fun `monthly occurrences are every interval months from the start date`()
    {
        // WHEN every 3 months from Sep 5th
        val rule = aRule(frequency = RecurrenceFrequency.MONTHLY, interval = 3, startDate = LocalDate.of(2026, 9, 5))

        // THEN
        assertThat(rule.pendingOccurrences(LocalDate.of(2027, 6, 30))).containsExactly(
            LocalDate.of(2026, 9, 5),
            LocalDate.of(2026, 12, 5),
            LocalDate.of(2027, 3, 5),
            LocalDate.of(2027, 6, 5),
        )
    }

    @Test
    fun `yearly occurrences are every interval years from the start date`()
    {
        // WHEN
        val rule = aRule(frequency = RecurrenceFrequency.YEARLY, interval = 1, startDate = LocalDate.of(2026, 9, 5))

        // THEN
        assertThat(rule.pendingOccurrences(LocalDate.of(2029, 1, 1))).containsExactly(
            LocalDate.of(2026, 9, 5),
            LocalDate.of(2027, 9, 5),
            LocalDate.of(2028, 9, 5),
        )
    }

    // A monthly the 31st computed fresh from the start date each time, never chained from the previous
    // (already-clamped) occurrence — otherwise every month after February would drift to the 28th forever.
    @Test
    fun `a short month clamps that occurrence only, without drifting the ones after it`()
    {
        // GIVEN the 31st of January
        val rule = aRule(frequency = RecurrenceFrequency.MONTHLY, interval = 1, startDate = LocalDate.of(2026, 1, 31))

        // WHEN
        val occurrences = rule.pendingOccurrences(LocalDate.of(2026, 4, 1))

        // THEN February clamps to the 28th, but March is back to the 31st
        assertThat(occurrences).containsExactly(
            LocalDate.of(2026, 1, 31),
            LocalDate.of(2026, 2, 28),
            LocalDate.of(2026, 3, 31),
        )
    }

    @Test
    fun `only occurrences after the last one already generated are pending`()
    {
        // GIVEN August and September already generated
        val rule = aRule(startDate = LocalDate.of(2026, 7, 5), lastGeneratedDate = LocalDate.of(2026, 9, 5))

        // WHEN
        val pending = rule.pendingOccurrences(LocalDate.of(2026, 12, 1))

        // THEN
        assertThat(pending).containsExactly(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 11, 5))
    }

    @Test
    fun `nothing is pending once every occurrence up to the horizon is already generated`()
    {
        // GIVEN
        val rule = aRule(startDate = LocalDate.of(2026, 7, 5), lastGeneratedDate = LocalDate.of(2026, 9, 5))

        // WHEN / THEN
        assertThat(rule.pendingOccurrences(LocalDate.of(2026, 9, 5))).isEmpty()
    }

    @Test
    fun `nothing is pending before the rule has started`()
    {
        // GIVEN a rule starting next month
        val rule = aRule(startDate = LocalDate.of(2026, 10, 1))

        // WHEN / THEN
        assertThat(rule.pendingOccurrences(LocalDate.of(2026, 9, 30))).isEmpty()
    }

    @Test
    fun `nothing is pending past the end date`()
    {
        // GIVEN a rule that ended in September
        val rule = aRule(startDate = LocalDate.of(2026, 7, 5), endDate = LocalDate.of(2026, 9, 5))

        // WHEN asked far beyond the end
        val pending = rule.pendingOccurrences(LocalDate.of(2027, 1, 1))

        // THEN it stops at the end date, not the horizon
        assertThat(pending).containsExactly(
            LocalDate.of(2026, 7, 5),
            LocalDate.of(2026, 8, 5),
            LocalDate.of(2026, 9, 5),
        )
    }
}
