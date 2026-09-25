package com.kyovo.cents.ui.home

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

private val ACCOUNT = AccountId(Uuid.parse("11111111-1111-1111-1111-111111111111"))
private val WHEN_SPENT = Instant.parse("2026-09-20T08:30:00Z")

private fun anExpense(subcategory: Subcategory?) = Transaction.recorded(
    id = TransactionId(Uuid.random()),
    accountId = ACCOUNT,
    amount = Money(1_000),
    title = TransactionTitle("Achat"),
    category = subcategory?.kind ?: RecordableTransactionCategory.EXPENSE,
    subcategory = subcategory,
    description = null,
    date = WHEN_SPENT,
)

/**
 * The subcategory filter of the Transactions tab and of an account's page only offers the
 * subcategories used by the transactions of the period on screen — so choosing one never leaves an
 * empty list. Both screens ask this same question of the same data.
 *
 * Expected: `availableSubcategories(transactions, subcategories)` in `ui.home`, returning the
 * subcategories among [subcategories] that a transaction points to, in the order of [subcategories].
 */
class AvailableSubcategoriesTest
{
    private val all = listOf(GROCERIES_SUBCATEGORY, FUEL_SUBCATEGORY, SALARY_SUBCATEGORY)

    @Test
    fun `offers only the subcategories the transactions use`()
    {
        // GIVEN
        val transactions = listOf(anExpense(GROCERIES_SUBCATEGORY), anExpense(SALARY_SUBCATEGORY))

        // WHEN / THEN
        assertThat(availableSubcategories(transactions, all))
            .containsExactly(GROCERIES_SUBCATEGORY, SALARY_SUBCATEGORY)
    }

    @Test
    fun `offers each one once, however many transactions use it`()
    {
        // GIVEN
        val transactions = List(3) { anExpense(FUEL_SUBCATEGORY) }

        // WHEN / THEN
        assertThat(availableSubcategories(transactions, all)).containsExactly(FUEL_SUBCATEGORY)
    }

    @Test
    fun `keeps the order of the subcategories, not the order of the transactions`()
    {
        // GIVEN the transactions come in the opposite order
        val transactions = listOf(anExpense(FUEL_SUBCATEGORY), anExpense(GROCERIES_SUBCATEGORY))

        // WHEN / THEN
        assertThat(availableSubcategories(transactions, all))
            .containsExactly(GROCERIES_SUBCATEGORY, FUEL_SUBCATEGORY)
    }

    @Test
    fun `ignores the transactions that have no subcategory`()
    {
        // GIVEN
        val transactions = listOf(anExpense(null), anExpense(GROCERIES_SUBCATEGORY), anExpense(null))

        // WHEN / THEN
        assertThat(availableSubcategories(transactions, all)).containsExactly(GROCERIES_SUBCATEGORY)
    }

    @Test
    fun `offers nothing when no transaction has a subcategory, or there is no transaction`()
    {
        assertThat(availableSubcategories(listOf(anExpense(null)), all)).isEmpty()
        assertThat(availableSubcategories(emptyList(), all)).isEmpty()
    }

    // A transaction can't point to a subcategory that no longer exists (deleting one clears it), but
    // a screen must not crash or invent an entry if the two lists are ever out of step.
    @Test
    fun `ignores a subcategory that is not in the list`()
    {
        // GIVEN a transaction pointing to a subcategory the list doesn't know
        val unknown = Subcategory(
            SubcategoryId(Uuid.random()),
            RecordableTransactionCategory.EXPENSE,
            SubcategoryName("Inconnue"),
            null,
        )
        val transactions = listOf(anExpense(unknown), anExpense(GROCERIES_SUBCATEGORY))

        // WHEN / THEN
        assertThat(availableSubcategories(transactions, all)).containsExactly(GROCERIES_SUBCATEGORY)
    }
}
