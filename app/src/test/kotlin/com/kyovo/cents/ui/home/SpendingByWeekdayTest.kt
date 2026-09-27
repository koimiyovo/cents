package com.kyovo.cents.ui.home

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionCategory
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.model.TransactionTitle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Instant
import java.util.UUID

/**
 * Expenses grouped by day of the week, all seven always present — a day with nothing spent is a zero, not
 * an absence — with the busiest day marked out, so a habit (spending more on weekends, say) shows up at a
 * glance rather than being buried in a plain list.
 */
class SpendingByWeekdayTest
{
    // 2026-09-14 is a Monday.
    private fun anExpense(cents: Long, dayOffset: Long): Transaction = Transaction.restored(
        id = TransactionId(UUID.randomUUID()),
        accountId = AccountId(UUID.randomUUID()),
        amount = Money(cents),
        title = TransactionTitle("Test"),
        category = TransactionCategory.EXPENSE,
        subcategoryId = null,
        description = null,
        date = Instant.parse("2026-09-14T12:00:00Z").plus(dayOffset, java.time.temporal.ChronoUnit.DAYS),
    )

    @Test
    fun `always has all seven days, Monday first`()
    {
        // WHEN
        val weekdays = spendingByWeekday(emptyList())

        // THEN
        assertThat(weekdays.map { it.dayOfWeek }).containsExactly(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
        )
    }

    @Test
    fun `a day with nothing spent is a zero, not an absence`()
    {
        // WHEN only Monday has an expense
        val weekdays = spendingByWeekday(listOf(anExpense(1_000, dayOffset = 0)))

        // THEN Monday carries it, the other six are zero, not missing
        assertThat(weekdays).hasSize(7)
        assertThat(weekdays.single { it.dayOfWeek == DayOfWeek.MONDAY }.total).isEqualTo(Money(1_000))
        assertThat(weekdays.filter { it.dayOfWeek != DayOfWeek.MONDAY }.map { it.total }).containsOnly(Money(0))
    }

    @Test
    fun `sums the expenses of each day of the week`()
    {
        // GIVEN two expenses on the same Monday, one on the Tuesday after
        val weekdays = spendingByWeekday(
            listOf(anExpense(1_000, dayOffset = 0), anExpense(2_000, dayOffset = 0), anExpense(500, dayOffset = 1))
        )

        // THEN
        val monday = weekdays.single { it.dayOfWeek == DayOfWeek.MONDAY }
        val tuesday = weekdays.single { it.dayOfWeek == DayOfWeek.TUESDAY }
        assertThat(monday.total).isEqualTo(Money(3_000))
        assertThat(tuesday.total).isEqualTo(Money(500))
    }

    @Test
    fun `a bar's fraction is its share of the busiest day, and only the busiest day is marked highest`()
    {
        // GIVEN Monday is busiest
        val weekdays = spendingByWeekday(
            listOf(anExpense(4_000, dayOffset = 0), anExpense(1_000, dayOffset = 1))
        )

        // THEN
        val monday = weekdays.single { it.dayOfWeek == DayOfWeek.MONDAY }
        val tuesday = weekdays.single { it.dayOfWeek == DayOfWeek.TUESDAY }
        assertThat(monday.barFraction).isEqualTo(1f)
        assertThat(monday.isHighest).isTrue()
        assertThat(tuesday.barFraction).isEqualTo(0.25f)
        assertThat(tuesday.isHighest).isFalse()
        assertThat(weekdays.count { it.isHighest }).isEqualTo(1)
    }

    @Test
    fun `nothing spent anywhere marks no day as highest`()
    {
        // WHEN
        val weekdays = spendingByWeekday(emptyList())

        // THEN
        assertThat(weekdays).noneMatch { it.isHighest }
    }

    @Test
    fun `ignores incomes and transfers`()
    {
        // GIVEN
        val income = Transaction.restored(
            id = TransactionId(UUID.randomUUID()),
            accountId = AccountId(UUID.randomUUID()),
            amount = Money(50_000),
            title = TransactionTitle("Salaire"),
            category = TransactionCategory.INCOME,
            subcategoryId = null,
            description = null,
            date = Instant.parse("2026-09-14T12:00:00Z"),
        )

        // WHEN
        val weekdays = spendingByWeekday(listOf(income))

        // THEN
        assertThat(weekdays.map { it.total }).containsOnly(Money(0))
    }

    @Test
    fun `each day carries a short French label`()
    {
        // WHEN
        val weekdays = spendingByWeekday(emptyList())

        // THEN
        assertThat(weekdays.map { it.label }).containsExactly("lun.", "mar.", "mer.", "jeu.", "ven.", "sam.", "dim.")
    }
}
