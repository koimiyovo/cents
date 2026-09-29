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
    private val now = Instant.parse("2026-09-23T12:00:00Z")

    // A preset period says how far back to look and nothing about the future: a recurring expense is
    // generated weeks or months ahead (see GenerateRecurringExpensesService's lookahead) and the list shows
    // what is coming as well as what happened, so there is no upper bound.
    @Test
    fun `7-day period starts 7 days before now and has no upper bound`()
    {
        // WHEN
        val (from, to) = periodRange(TransactionsPeriod.LAST_7_DAYS, customFrom = null, customTo = null, now = now)

        // THEN
        assertThat(from).isEqualTo(now.minus(7, ChronoUnit.DAYS))
        assertThat(to).isNull()
    }

    @Test
    fun `30-day period starts 30 days before now and has no upper bound`()
    {
        // WHEN
        val (from, to) = periodRange(TransactionsPeriod.LAST_30_DAYS, customFrom = null, customTo = null, now = now)

        // THEN
        assertThat(from).isEqualTo(now.minus(30, ChronoUnit.DAYS))
        assertThat(to).isNull()
    }

    @Test
    fun `all-time period has no bound at all`()
    {
        // WHEN
        val (from, to) = periodRange(TransactionsPeriod.ALL_TIME, customFrom = null, customTo = null, now = now)

        // THEN
        assertThat(from).isNull()
        assertThat(to).isNull()
    }

    @Test
    fun `a recurring expense generated months ahead is within a preset period`()
    {
        // GIVEN one dated three months from now, and one from yesterday
        val (from, to) = periodRange(TransactionsPeriod.LAST_30_DAYS, customFrom = null, customTo = null, now = now)
        val ahead = aTransactionAt(now.plus(90, ChronoUnit.DAYS))
        val yesterday = aTransactionAt(now.minus(1, ChronoUnit.DAYS))

        // WHEN
        val inPeriod = transactionsWithinRange(listOf(ahead, yesterday), from, to)

        // THEN
        assertThat(inPeriod).containsExactlyInAnyOrder(ahead, yesterday)
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
 * The Analyse tab's insights (Top 5, weekday pattern) share these two filters with Mouvements — same
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
