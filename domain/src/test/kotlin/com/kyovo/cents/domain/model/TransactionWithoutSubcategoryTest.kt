package com.kyovo.cents.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * When a subcategory is deleted, the transactions that used it are kept and simply lose it. A
 * `Transaction` can only be built through its factories (its constructor and `copy` are private), so
 * the domain itself has to offer this one change.
 */
class TransactionWithoutSubcategoryTest
{
    private val groceries = Subcategory(
        SubcategoryId(Uuid.parse("55555555-5555-5555-5555-555555555555")),
        RecordableTransactionCategory.EXPENSE,
        SubcategoryName("Alimentation"),
        null
    )

    private fun aRecorded(category: RecordableTransactionCategory, subcategory: Subcategory?) =
        Transaction.recorded(
            id = TransactionId(Uuid.parse("33333333-3333-3333-3333-333333333333")),
            accountId = AccountId(Uuid.parse("11111111-1111-1111-1111-111111111111")),
            amount = Money(1_250),
            title = TransactionTitle("Courses de la semaine"),
            category = category,
            subcategory = subcategory,
            description = TransactionDescription.of("Marché du samedi"),
            date = Instant.parse("2026-09-22T10:00:00Z")
        )

    @Test
    fun `drops the subcategory and keeps everything else`()
    {
        // GIVEN
        val transaction = aRecorded(RecordableTransactionCategory.EXPENSE, groceries)

        // WHEN
        val result = transaction.withoutSubcategory()

        // THEN
        assertThat(result).isEqualTo(aRecorded(RecordableTransactionCategory.EXPENSE, subcategory = null))
        assertThat(result.subcategoryId).isNull()
        assertThat(result.signedAmount).isEqualTo(transaction.signedAmount)
    }

    @Test
    fun `leaves a transaction that has no subcategory as it is`()
    {
        // GIVEN
        val transaction = aRecorded(RecordableTransactionCategory.INCOME, subcategory = null)

        // WHEN / THEN
        assertThat(transaction.withoutSubcategory()).isEqualTo(transaction)
    }

    @Test
    fun `does not change the transaction it is called on`()
    {
        // GIVEN
        val transaction = aRecorded(RecordableTransactionCategory.EXPENSE, groceries)

        // WHEN
        transaction.withoutSubcategory()

        // THEN
        assertThat(transaction.subcategoryId).isEqualTo(groceries.id)
    }
}
