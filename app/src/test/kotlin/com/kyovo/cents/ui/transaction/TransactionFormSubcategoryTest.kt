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
import java.util.UUID

private val WHEN_IT_HAPPENED = Instant.parse("2026-09-20T08:30:00Z")

private fun anExpenseWith(subcategoryId: Boolean) = Transaction.recorded(
    id = TransactionId(UUID.randomUUID()),
    accountId = AccountId(UUID.randomUUID()),
    amount = Money(1_250),
    title = TransactionTitle("Courses"),
    category = RecordableTransactionCategory.EXPENSE,
    subcategory = if (subcategoryId) GROCERIES_SUBCATEGORY else null,
    description = null,
    date = WHEN_IT_HAPPENED,
)

/** The subcategories a form offers, and how editing carries the one a transaction already has. */
class TransactionFormSubcategoryTest
{
    private val form = TransactionFormState(
        type = TransactionFormType.EXPENSE,
        accountId = null,
        date = WHEN_IT_HAPPENED,
    )

    @Test
    fun `an expense form offers the expense subcategories, in the order given`()
    {
        assertThat(form.subcategoryChoices(ALL_TEST_SUBCATEGORIES))
            .containsExactly(GROCERIES_SUBCATEGORY, FUEL_SUBCATEGORY)
    }

    @Test
    fun `an income form offers the income subcategories only`()
    {
        assertThat(form.withType(TransactionFormType.INCOME).subcategoryChoices(ALL_TEST_SUBCATEGORIES))
            .containsExactly(SALARY_SUBCATEGORY)
    }

    @Test
    fun `a transfer form offers none`()
    {
        assertThat(form.withType(TransactionFormType.TRANSFER).subcategoryChoices(ALL_TEST_SUBCATEGORIES)).isEmpty()
    }

    @Test
    fun `offers nothing when there is no subcategory yet`()
    {
        assertThat(form.subcategoryChoices(emptyList())).isEmpty()
    }

    @Test
    fun `editing keeps the subcategory the transaction points to, and saving sends its id`()
    {
        // WHEN
        val editing = TransactionFormState.editing(anExpenseWith(subcategoryId = true), GROCERIES_SUBCATEGORY)
        val command = (editing.submit() as FormSubmission.Update).command

        // THEN
        assertThat(editing.subcategory).isEqualTo(GROCERIES_SUBCATEGORY)
        assertThat(command.subcategoryId).isEqualTo(GROCERIES_SUBCATEGORY.id)
    }

    @Test
    fun `editing a transaction with no subcategory leaves the field empty`()
    {
        assertThat(TransactionFormState.editing(anExpenseWith(subcategoryId = false), null).subcategory).isNull()
    }

    @Test
    fun `changing the type drops a subcategory of the other kind`()
    {
        // GIVEN an expense form with an expense subcategory
        val withGroceries = form.copy(subcategory = GROCERIES_SUBCATEGORY)

        // WHEN / THEN it survives staying an expense, not becoming an income
        assertThat(withGroceries.withType(TransactionFormType.EXPENSE).subcategory).isEqualTo(GROCERIES_SUBCATEGORY)
        assertThat(withGroceries.withType(TransactionFormType.INCOME).subcategory).isNull()
    }

    @Test
    fun `a subcategory of the wrong kind is refused when saving`()
    {
        // GIVEN an expense form holding an income subcategory
        val mismatched = form.copy(
            amountText = "12,50",
            title = "Courses",
            accountId = AccountId(UUID.randomUUID()),
            subcategory = SALARY_SUBCATEGORY,
        )

        // WHEN
        val submission = mismatched.submit()

        // THEN
        assertThat(submission).isEqualTo(FormSubmission.Invalid(setOf(FormError.SUBCATEGORY_MISMATCH)))
    }
}
