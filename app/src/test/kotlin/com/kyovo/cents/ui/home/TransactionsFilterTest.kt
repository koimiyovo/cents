package com.kyovo.cents.ui.home

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.model.TransactionTitle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.uuid.Uuid

private fun aTransactionAt(date: Instant): Transaction
{
    return Transaction.recorded(
        TransactionId(Uuid.random()),
        AccountId(Uuid.random()),
        Money(1_000),
        TransactionTitle("Test transaction"),
        RecordableTransactionCategory.EXPENSE,
        subcategory = null,
        description = null,
        date = date,
    )
}

class PeriodRangeTest
{
    private val now = Instant.parse("2026-09-23T12:00:00Z")

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
    fun `all-time period has no lower or upper bound`()
    {
        // WHEN
        val (from, to) = periodRange(TransactionsPeriod.ALL_TIME, customFrom = null, customTo = null, now = now)

        // THEN
        assertThat(from).isNull()
        assertThat(to).isNull()
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
