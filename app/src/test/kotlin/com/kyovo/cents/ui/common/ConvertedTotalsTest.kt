package com.kyovo.cents.ui.common

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.model.TransactionTitle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * The income and expense totals of a list of transactions, in the main currency: each transaction of the list
 * is counted at the amount it has in the converted list (same id). One with no converted version (its currency
 * has no rate) is left out of the sums, and counted, so that the screen can say the total is incomplete.
 */
class ConvertedTotalsTest
{
    private val account = AccountId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
    private var number = 0

    private fun aTransaction(cents: Long, category: RecordableTransactionCategory): Transaction
    {
        return Transaction.recorded(
            id = TransactionId(UUID(0, (++number).toLong())),
            accountId = account,
            amount = Money(cents),
            title = TransactionTitle("Test"),
            category = category,
            subcategory = null,
            description = null,
            date = Instant.parse("2026-09-22T10:00:00Z")
        )
    }

    private val income = RecordableTransactionCategory.INCOME
    private val expense = RecordableTransactionCategory.EXPENSE

    @Test
    fun `adds up the incomes and the expenses at their converted amounts`()
    {
        // GIVEN a 100,00 income and a 20,00 expense in dollars, worth 90,00 and 18,00 once converted
        val pay = aTransaction(10_000, income)
        val lunch = aTransaction(2_000, expense)
        val converted = listOf(pay.withAmount(Money(9_000)), lunch.withAmount(Money(1_800)))

        // WHEN
        val totals = convertedTotals(listOf(pay, lunch), converted)

        // THEN the original amounts do not count, the converted ones do
        assertThat(totals).isEqualTo(ConvertedTotals(incomeCents = 9_000, expenseCents = 1_800, unconverted = 0))
    }

    @Test
    fun `gives zero for an empty list`()
    {
        assertThat(convertedTotals(emptyList(), emptyList()))
            .isEqualTo(ConvertedTotals(incomeCents = 0, expenseCents = 0, unconverted = 0))
    }

    @Test
    fun `leaves out and counts a transaction that has no converted version`()
    {
        // GIVEN two expenses, one of which could not be converted
        val converted = aTransaction(2_000, expense)
        val unconvertible = aTransaction(5_000, expense)

        // WHEN
        val totals = convertedTotals(listOf(converted, unconvertible), listOf(converted))

        // THEN
        assertThat(totals).isEqualTo(ConvertedTotals(incomeCents = 0, expenseCents = 2_000, unconverted = 1))
    }

    @Test
    fun `counts only the transactions of the list, not the whole converted list`()
    {
        // GIVEN a period with one expense, while the converted list holds every transaction ever
        val inPeriod = aTransaction(2_000, expense)
        val outOfPeriod = aTransaction(9_000, expense)

        // WHEN
        val totals = convertedTotals(listOf(inPeriod), listOf(inPeriod, outOfPeriod))

        // THEN
        assertThat(totals.expenseCents).isEqualTo(2_000)
    }

    @Test
    fun `ignores the transfers and the opening deposits, which are neither income nor expense`()
    {
        // GIVEN
        val deposit = Transaction.openingDeposit(
            TransactionId(UUID(0, 99)), account, Money(10_000), Instant.parse("2026-09-01T10:00:00Z")
        )
        val out = Transaction.transferOut(
            TransactionId(UUID(0, 98)), account, Money(500), TransactionTitle("Virement"), Instant.parse("2026-09-02T10:00:00Z")
        )

        // WHEN
        val totals = convertedTotals(listOf(deposit, out), listOf(deposit, out))

        // THEN they are not in the totals, and are not "unconverted" either
        assertThat(totals).isEqualTo(ConvertedTotals(incomeCents = 0, expenseCents = 0, unconverted = 0))
    }

    @Test
    fun `net is income minus expense`()
    {
        assertThat(ConvertedTotals(incomeCents = 9_000, expenseCents = 2_500, unconverted = 0).netCents)
            .isEqualTo(6_500)
        assertThat(ConvertedTotals(incomeCents = 1_000, expenseCents = 2_500, unconverted = 0).netCents)
            .isEqualTo(-1_500)
    }
}
