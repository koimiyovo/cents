package com.kyovo.cents.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * A transaction seen in another currency (the totals show everything in the main currency) is the same
 * transaction with another amount. As its constructor and `copy` are private, the domain offers this change.
 */
class TransactionWithAmountTest
{
    private val groceries = Subcategory(
        SubcategoryId(UUID.fromString("55555555-5555-5555-5555-555555555555")),
        RecordableTransactionCategory.EXPENSE,
        SubcategoryName("Alimentation"),
        null
    )

    private val expense = Transaction.recorded(
        id = TransactionId(UUID.fromString("33333333-3333-3333-3333-333333333333")),
        accountId = AccountId(UUID.fromString("11111111-1111-1111-1111-111111111111")),
        amount = Money(1_250),
        title = TransactionTitle("Courses de la semaine"),
        category = RecordableTransactionCategory.EXPENSE,
        subcategory = groceries,
        description = TransactionDescription.of("Marché du samedi"),
        date = Instant.parse("2026-09-22T10:00:00Z"),
        projectId = ProjectId(UUID.fromString("66666666-6666-6666-6666-666666666666"))
    )

    @Test
    fun `changes the amount and keeps everything else`()
    {
        // WHEN
        val converted = expense.withAmount(Money(2_000))

        // THEN
        assertThat(converted).isEqualTo(
            Transaction.restored(
                id = expense.id,
                accountId = expense.accountId,
                amount = Money(2_000),
                title = expense.title,
                category = expense.category,
                subcategoryId = expense.subcategoryId,
                description = expense.description,
                date = expense.date,
                projectId = expense.projectId
            )
        )
    }

    @Test
    fun `leaves the transaction it is called on unchanged`()
    {
        // WHEN
        expense.withAmount(Money(2_000))

        // THEN
        assertThat(expense.amount).isEqualTo(Money(1_250))
    }

    // A small amount converted into a currency that is worth more can round to nothing: it is still the
    // same transaction, and the view may show it as 0 rather than fail.
    @Test
    fun `accepts zero, which a conversion can round to`()
    {
        assertThat(expense.withAmount(Money(0)).amount).isEqualTo(Money(0))
    }

    @Test
    fun `keeps the sign of the movement, which comes from its category`()
    {
        assertThat(expense.withAmount(Money(2_000)).signedAmount).isEqualTo(-2_000L)
    }
}
