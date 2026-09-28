package com.kyovo.cents.ui.home

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionCategory
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.model.TransactionTitle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

private fun aTransactionAt(date: Instant): Transaction
{
    return Transaction.recorded(
        TransactionId(UUID.randomUUID()),
        AccountId(UUID.randomUUID()),
        Money(1_000),
        TransactionTitle("Test transaction"),
        RecordableTransactionCategory.EXPENSE,
        subcategory = null,
        description = null,
        date = date,
    )
}

private fun aTransactionOn(accountId: AccountId, subcategoryId: SubcategoryId?): Transaction
{
    return Transaction.restored(
        id = TransactionId(UUID.randomUUID()),
        accountId = accountId,
        amount = Money(1_000),
        title = TransactionTitle("Test transaction"),
        category = TransactionCategory.EXPENSE,
        subcategoryId = subcategoryId,
        description = null,
        date = Instant.parse("2026-09-15T12:00:00Z"),
    )
}

class PeriodRangeTest
{
    private val zone = ZoneId.systemDefault()

    // Well before midnight, so end-of-day is a different, later instant than `now` itself: a
    // transaction recorded a moment after the screen computed this range (still today) must not
    // be treated as "in the future" just because it is after the captured `now`.
    private val now = Instant.parse("2026-09-23T12:00:00Z")
    private val endOfToday = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1)

    @Test
    fun `7-day period starts 7 days before now and stops at the end of today`()
    {
        // WHEN
        val (from, to) = periodRange(TransactionsPeriod.LAST_7_DAYS, customFrom = null, customTo = null, now = now)

        // THEN
        assertThat(from).isEqualTo(now.minus(7, ChronoUnit.DAYS))
        assertThat(to).isEqualTo(endOfToday)
    }

    @Test
    fun `30-day period starts 30 days before now and stops at the end of today`()
    {
        // WHEN
        val (from, to) = periodRange(TransactionsPeriod.LAST_30_DAYS, customFrom = null, customTo = null, now = now)

        // THEN
        assertThat(from).isEqualTo(now.minus(30, ChronoUnit.DAYS))
        assertThat(to).isEqualTo(endOfToday)
    }

    @Test
    fun `all-time period has no lower bound but still stops at the end of today`()
    {
        // WHEN a recurring expense generated well ahead of today (see the lookahead horizon) must not show
        val (from, to) = periodRange(TransactionsPeriod.ALL_TIME, customFrom = null, customTo = null, now = now)

        // THEN
        assertThat(from).isNull()
        assertThat(to).isEqualTo(endOfToday)
    }

    @Test
    fun `a transaction recorded moments after now was captured still counts as today`()
    {
        // WHEN a transaction is recorded a second after the screen last computed its period range
        val (_, to) = periodRange(TransactionsPeriod.LAST_30_DAYS, customFrom = null, customTo = null, now = now)
        val recordedJustAfter = now.plusSeconds(1)

        // THEN it is still within the period, because the cap is the end of the day, not the exact
        // captured instant (the ViewModel-less screen state only recomputes `now` when the filters
        // themselves change, not on every recomposition)
        assertThat(recordedJustAfter.isAfter(to)).isFalse()
    }

    @Test
    fun `custom period bounds from the start of the first day to the end of the last day`()
    {
        // GIVEN
        val zone = ZoneId.systemDefault()
        val customFrom = LocalDate.of(2026, 9, 1)
        val customTo = LocalDate.of(2026, 9, 10)

        // WHEN
        val (from, to) = periodRange(TransactionsPeriod.CUSTOM, customFrom, customTo, now)

        // THEN
        assertThat(from).isEqualTo(customFrom.atStartOfDay(zone).toInstant())
        assertThat(to).isEqualTo(customTo.plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1))
    }

    @Test
    fun `custom period without picked dates yet has no bounds`()
    {
        // WHEN
        val (from, to) = periodRange(TransactionsPeriod.CUSTOM, customFrom = null, customTo = null, now = now)

        // THEN
        assertThat(from).isNull()
        assertThat(to).isNull()
    }
}

class TransactionsWithinRangeTest
{
    private val reference = Instant.parse("2026-09-15T12:00:00Z")
    private val before = aTransactionAt(reference.minus(2, ChronoUnit.DAYS))
    private val onReference = aTransactionAt(reference)
    private val after = aTransactionAt(reference.plus(2, ChronoUnit.DAYS))

    @Test
    fun `returns every transaction when neither bound is set`()
    {
        // WHEN
        val result = transactionsWithinRange(listOf(before, onReference, after), from = null, to = null)

        // THEN
        assertThat(result).containsExactly(before, onReference, after)
    }

    @Test
    fun `excludes transactions strictly before the lower bound`()
    {
        // WHEN
        val result = transactionsWithinRange(listOf(before, onReference, after), from = reference, to = null)

        // THEN
        assertThat(result).containsExactly(onReference, after)
    }

    @Test
    fun `excludes transactions strictly after the upper bound`()
    {
        // WHEN
        val result = transactionsWithinRange(listOf(before, onReference, after), from = null, to = reference)

        // THEN
        assertThat(result).containsExactly(before, onReference)
    }

    @Test
    fun `combines both bounds`()
    {
        // WHEN
        val result = transactionsWithinRange(listOf(before, onReference, after), from = reference, to = reference)

        // THEN
        assertThat(result).containsExactly(onReference)
    }

    @Test
    fun `bounds are inclusive`()
    {
        // WHEN
        val result = transactionsWithinRange(
            listOf(before, onReference, after),
            from = reference.minus(2, ChronoUnit.DAYS),
            to = reference.plus(2, ChronoUnit.DAYS),
        )

        // THEN
        assertThat(result).containsExactly(before, onReference, after)
    }
}

/**
 * The Analyse tab's insights (Top 5, weekday pattern) share these two filters with Historique — same
 * as [transactionsWithinRange] does for the period, just on account and subcategory instead of dates.
 */
class FilterByAccountAndSubcategoryTest
{
    private val accountA = AccountId(UUID.randomUUID())
    private val accountB = AccountId(UUID.randomUUID())
    private val subcategoryX = SubcategoryId(UUID.randomUUID())
    private val subcategoryY = SubcategoryId(UUID.randomUUID())

    private val onAccountASubcategoryX = aTransactionOn(accountA, subcategoryX)
    private val onAccountBSubcategoryX = aTransactionOn(accountB, subcategoryX)
    private val onAccountASubcategoryY = aTransactionOn(accountA, subcategoryY)
    private val onAccountANoSubcategory = aTransactionOn(accountA, null)

    private val all =
        listOf(onAccountASubcategoryX, onAccountBSubcategoryX, onAccountASubcategoryY, onAccountANoSubcategory)

    @Test
    fun `neither filter set returns everything`()
    {
        // WHEN
        val result = filterByAccountAndSubcategory(all, accountId = null, subcategoryId = null)

        // THEN
        assertThat(result).containsExactlyElementsOf(all)
    }

    @Test
    fun `an account filter keeps only that account's transactions`()
    {
        // WHEN
        val result = filterByAccountAndSubcategory(all, accountId = accountA, subcategoryId = null)

        // THEN
        assertThat(result).containsExactly(onAccountASubcategoryX, onAccountASubcategoryY, onAccountANoSubcategory)
    }

    @Test
    fun `a subcategory filter keeps only that subcategory's transactions`()
    {
        // WHEN
        val result = filterByAccountAndSubcategory(all, accountId = null, subcategoryId = subcategoryX)

        // THEN
        assertThat(result).containsExactly(onAccountASubcategoryX, onAccountBSubcategoryX)
    }

    @Test
    fun `both filters combine`()
    {
        // WHEN
        val result = filterByAccountAndSubcategory(all, accountId = accountA, subcategoryId = subcategoryX)

        // THEN
        assertThat(result).containsExactly(onAccountASubcategoryX)
    }

    @Test
    fun `a subcategory filter never matches a transaction with none`()
    {
        // WHEN a subcategory that happens to be null on both sides is not treated as a match
        val result = filterByAccountAndSubcategory(listOf(onAccountANoSubcategory), accountId = null, subcategoryId = subcategoryX)

        // THEN
        assertThat(result).isEmpty()
    }
}
