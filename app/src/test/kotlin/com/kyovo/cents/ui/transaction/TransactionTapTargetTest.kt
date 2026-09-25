package com.kyovo.cents.ui.transaction

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.model.TransactionTitle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.uuid.Uuid

private val OWNER = AccountId(Uuid.parse("11111111-1111-1111-1111-111111111111"))
private val THEN = Instant.parse("2026-09-20T08:30:00Z")
private val TITLE = TransactionTitle("Achat")

private fun aRecorded(category: RecordableTransactionCategory) = Transaction.recorded(
    TransactionId(Uuid.random()), OWNER, Money(1_000), TITLE, category, null, null, THEN,
)

/**
 * A tap on a row of the lists goes to the form that fits it — or nowhere. An income or an expense
 * has the full form, an opening deposit only has its amount to correct (its own one-field form), and
 * a transfer leg reacts to nothing: its two legs are not linked, so editing one would unbalance both.
 */
class TransactionTapTargetTest
{
    @Test
    fun `an expense opens the transaction form`()
    {
        assertThat(transactionTapTarget(aRecorded(RecordableTransactionCategory.EXPENSE)))
            .isEqualTo(TransactionTapTarget.TRANSACTION_FORM)
    }

    @Test
    fun `an income opens the transaction form`()
    {
        assertThat(transactionTapTarget(aRecorded(RecordableTransactionCategory.INCOME)))
            .isEqualTo(TransactionTapTarget.TRANSACTION_FORM)
    }

    @Test
    fun `an opening deposit opens its own form`()
    {
        val deposit = Transaction.openingDeposit(TransactionId(Uuid.random()), OWNER, Money(10_000), THEN)

        assertThat(transactionTapTarget(deposit)).isEqualTo(TransactionTapTarget.INITIAL_DEPOSIT_FORM)
    }

    @Test
    fun `neither leg of a transfer opens anything`()
    {
        val out = Transaction.transferOut(TransactionId(Uuid.random()), OWNER, Money(500), TITLE, THEN)
        val into = Transaction.transferIn(TransactionId(Uuid.random()), OWNER, Money(500), TITLE, THEN)

        assertThat(transactionTapTarget(out)).isEqualTo(TransactionTapTarget.NONE)
        assertThat(transactionTapTarget(into)).isEqualTo(TransactionTapTarget.NONE)
    }

    @Test
    fun `a row reacts to a tap exactly when it leads somewhere`()
    {
        // GIVEN one transaction of every category
        val all = listOf(
            aRecorded(RecordableTransactionCategory.EXPENSE),
            aRecorded(RecordableTransactionCategory.INCOME),
            Transaction.openingDeposit(TransactionId(Uuid.random()), OWNER, Money(1), THEN),
            Transaction.transferOut(TransactionId(Uuid.random()), OWNER, Money(1), TITLE, THEN),
            Transaction.transferIn(TransactionId(Uuid.random()), OWNER, Money(1), TITLE, THEN),
        )

        // WHEN / THEN only the transfer legs are inert
        assertThat(all.map { it.reactsToTap() }).containsExactly(true, true, true, false, false)
    }

    @Test
    fun `no transaction opens both forms`()
    {
        val all = listOf(
            aRecorded(RecordableTransactionCategory.EXPENSE),
            Transaction.openingDeposit(TransactionId(Uuid.random()), OWNER, Money(1), THEN),
        )

        all.forEach {
            assertThat(canEditTransaction(it) && canEditInitialDeposit(it)).isFalse()
        }
    }
}
