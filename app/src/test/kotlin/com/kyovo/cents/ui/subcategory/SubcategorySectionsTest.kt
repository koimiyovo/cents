package com.kyovo.cents.ui.subcategory

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.SubcategoryName
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.model.TransactionTitle
import com.kyovo.cents.ui.transaction.FUEL_SUBCATEGORY
import com.kyovo.cents.ui.transaction.GROCERIES_SUBCATEGORY
import com.kyovo.cents.ui.transaction.SALARY_SUBCATEGORY
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.uuid.Uuid

private fun aTransaction(subcategory: Subcategory?, kind: RecordableTransactionCategory? = subcategory?.kind) =
    Transaction.recorded(
        id = TransactionId(Uuid.random()),
        accountId = AccountId(Uuid.parse("11111111-1111-1111-1111-111111111111")),
        amount = Money(1_000),
        title = TransactionTitle("Achat"),
        category = kind ?: RecordableTransactionCategory.EXPENSE,
        subcategory = subcategory,
        description = null,
        date = Instant.parse("2026-09-20T08:30:00Z"),
    )

/**
 * The management screen lists the subcategories in two sections, expenses then incomes, each row
 * with the number of transactions that use it (what the user is told before deleting or renaming).
 */
class SubcategorySectionsTest
{
    private val all = listOf(GROCERIES_SUBCATEGORY, FUEL_SUBCATEGORY, SALARY_SUBCATEGORY)

    @Test
    fun `there is one section per kind, expenses first`()
    {
        // WHEN
        val sections = subcategorySections(all, emptyList())

        // THEN
        assertThat(sections.map { it.kind })
            .containsExactly(RecordableTransactionCategory.EXPENSE, RecordableTransactionCategory.INCOME)
    }

    @Test
    fun `each subcategory is in the section of its kind, in the order given`()
    {
        // WHEN
        val (expenses, incomes) = subcategorySections(all, emptyList())

        // THEN
        assertThat(expenses.rows.map { it.subcategory }).containsExactly(GROCERIES_SUBCATEGORY, FUEL_SUBCATEGORY)
        assertThat(incomes.rows.map { it.subcategory }).containsExactly(SALARY_SUBCATEGORY)
    }

    // Each section ends with its own "new subcategory" action: an empty kind must still be there.
    @Test
    fun `both sections are there even when there is nothing to list`()
    {
        // WHEN
        val sections = subcategorySections(emptyList(), emptyList())

        // THEN
        assertThat(sections).hasSize(2)
        assertThat(sections.flatMap { it.rows }).isEmpty()
    }

    @Test
    fun `a kind without any subcategory has an empty section`()
    {
        // WHEN
        val (expenses, incomes) = subcategorySections(listOf(GROCERIES_SUBCATEGORY), emptyList())

        // THEN
        assertThat(expenses.rows).hasSize(1)
        assertThat(incomes.rows).isEmpty()
    }

    @Test
    fun `a row counts the transactions that use its subcategory`()
    {
        // GIVEN
        val transactions = listOf(
            aTransaction(GROCERIES_SUBCATEGORY),
            aTransaction(GROCERIES_SUBCATEGORY),
            aTransaction(GROCERIES_SUBCATEGORY),
            aTransaction(SALARY_SUBCATEGORY),
        )

        // WHEN
        val (expenses, incomes) = subcategorySections(all, transactions)

        // THEN
        assertThat(expenses.rows.map { it.transactionCount }).containsExactly(3, 0)
        assertThat(incomes.rows.map { it.transactionCount }).containsExactly(1)
    }

    @Test
    fun `transactions without a subcategory count for none`()
    {
        // GIVEN
        val transactions = listOf(aTransaction(null), aTransaction(null), aTransaction(FUEL_SUBCATEGORY))

        // WHEN
        val (expenses, _) = subcategorySections(all, transactions)

        // THEN
        assertThat(expenses.rows.map { it.transactionCount }).containsExactly(0, 1)
    }

    @Test
    fun `a subcategory nothing uses has a count of zero`()
    {
        // WHEN
        val (expenses, _) = subcategorySections(listOf(GROCERIES_SUBCATEGORY), listOf(aTransaction(null)))

        // THEN
        assertThat(expenses.rows.single().transactionCount).isZero()
    }

    // A transaction can't point to a subcategory that no longer exists (deleting one clears it), but the
    // screen must not crash or invent a row if the two lists are ever out of step.
    @Test
    fun `a transaction pointing to an unknown subcategory adds no row`()
    {
        // GIVEN
        val unknown = Subcategory(
            SubcategoryId(Uuid.random()),
            RecordableTransactionCategory.EXPENSE,
            SubcategoryName("Inconnue"),
            null,
        )

        // WHEN
        val (expenses, _) = subcategorySections(listOf(GROCERIES_SUBCATEGORY), listOf(aTransaction(unknown)))

        // THEN
        assertThat(expenses.rows.map { it.subcategory }).containsExactly(GROCERIES_SUBCATEGORY)
        assertThat(expenses.rows.single().transactionCount).isZero()
    }
}
