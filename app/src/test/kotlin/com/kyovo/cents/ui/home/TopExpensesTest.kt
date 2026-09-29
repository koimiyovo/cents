package com.kyovo.cents.ui.home

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionCategory
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.model.TransactionTitle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * The biggest expenses of a period, most expensive first — often what explains a period's total at a
 * glance. Only an expense counts: an income, a transfer or the opening deposit isn't one that could be
 * overspent on.
 */
class TopExpensesTest
{
    private fun aTransaction(
        cents: Long,
        category: TransactionCategory = TransactionCategory.EXPENSE,
        date: Instant = Instant.parse("2026-09-15T10:00:00Z"),
    ): Transaction =
        Transaction.restored(
            id = TransactionId(UUID.randomUUID()),
            accountId = AccountId(UUID.randomUUID()),
            amount = Money(cents),
            title = TransactionTitle("Test"),
            category = category,
            subcategoryId = null,
            description = null,
            date = date,
        )

    @Test
    fun `orders the expenses from most to least expensive`()
    {
        // GIVEN
        val small = aTransaction(1_000)
        val big = aTransaction(9_000)
        val medium = aTransaction(4_500)

        // WHEN
        val top = topExpenses(listOf(small, big, medium))

        // THEN
        assertThat(top).containsExactly(big, medium, small)
    }

    @Test
    fun `ignores incomes, transfers and the opening deposit`()
    {
        // GIVEN
        val expense = aTransaction(1_000, TransactionCategory.EXPENSE)
        val income = aTransaction(50_000, TransactionCategory.INCOME)
        val transferOut = aTransaction(20_000, TransactionCategory.TRANSFER_OUT)
        val transferIn = aTransaction(20_000, TransactionCategory.TRANSFER_IN)
        val deposit = aTransaction(100_000, TransactionCategory.INITIAL_DEPOSIT)

        // WHEN
        val top = topExpenses(listOf(expense, income, transferOut, transferIn, deposit))

        // THEN
        assertThat(top).containsExactly(expense)
    }

    @Test
    fun `is capped at the given limit, the smaller ones dropped`()
    {
        // GIVEN six expenses
        val expenses = (1..6).map { aTransaction(it * 1_000L) }

        // WHEN
        val top = topExpenses(expenses, limit = 5)

        // THEN the five biggest, the smallest (1_000) dropped
        assertThat(top).hasSize(5)
        assertThat(top.map { it.amount }).containsExactly(
            Money(6_000), Money(5_000), Money(4_000), Money(3_000), Money(2_000),
        )
    }

    @Test
    fun `defaults to five`()
    {
        // GIVEN eight expenses
        val expenses = (1..8).map { aTransaction(it * 1_000L) }

        // WHEN
        val top = topExpenses(expenses)

        // THEN
        assertThat(top).hasSize(5)
    }

    @Test
    fun `fewer expenses than the limit are all returned`()
    {
        // GIVEN
        val expenses = listOf(aTransaction(1_000), aTransaction(2_000))

        // WHEN
        val top = topExpenses(expenses, limit = 5)

        // THEN
        assertThat(top).hasSize(2)
    }

    @Test
    fun `no expenses at all gives an empty list`()
    {
        // WHEN
        val top = topExpenses(listOf(aTransaction(1_000, TransactionCategory.INCOME)))

        // THEN
        assertThat(top).isEmpty()
    }

    // The list shows what is coming as well as what happened, and the insights answer "of what I'm looking
    // at": an expense generated ahead of its due date (see the lookahead horizon) counts like any other.
    @Test
    fun `an expense dated in the future counts like any other`()
    {
        // GIVEN a recurring expense generated well ahead of today
        val today = Instant.parse("2026-09-27T12:00:00Z")
        val past = aTransaction(1_000, date = today.minus(1, ChronoUnit.DAYS))
        val ahead = aTransaction(999_999, date = today.plus(60, ChronoUnit.DAYS))

        // WHEN
        val top = topExpenses(listOf(past, ahead))

        // THEN
        assertThat(top).containsExactly(ahead, past)
    }
}
