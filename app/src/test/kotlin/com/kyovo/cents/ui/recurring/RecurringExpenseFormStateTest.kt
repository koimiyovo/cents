package com.kyovo.cents.ui.recurring

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.RecurrenceFrequency
import com.kyovo.cents.domain.model.RecurringExpense
import com.kyovo.cents.domain.model.RecurringExpenseId
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryEmoji
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.SubcategoryName
import com.kyovo.cents.domain.model.TransactionTitle
import com.kyovo.cents.domain.port.input.CreateRecurringExpenseCommand
import com.kyovo.cents.domain.port.input.UpdateRecurringExpenseCommand
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

private val ACCOUNT_ID = AccountId(UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001"))
private val TODAY: LocalDate = LocalDate.of(2026, 9, 27)

private fun aSubcategory(kind: RecordableTransactionCategory = RecordableTransactionCategory.EXPENSE) = Subcategory(
    SubcategoryId(UUID.randomUUID()),
    kind,
    SubcategoryName("Abonnements"),
    SubcategoryEmoji("📱"),
)

private fun aRecurringExpense(
    id: RecurringExpenseId = RecurringExpenseId(UUID.randomUUID()),
    accountId: AccountId = ACCOUNT_ID,
    frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    interval: Int = 1,
    startDate: LocalDate = TODAY,
    endDate: LocalDate? = null,
    subcategoryId: SubcategoryId? = null,
) = RecurringExpense(
    id = id,
    accountId = accountId,
    amount = Money(1_500),
    title = TransactionTitle("Loyer"),
    subcategoryId = subcategoryId,
    description = null,
    frequency = frequency,
    interval = interval,
    startDate = startDate,
    endDate = endDate,
)

class RecurringExpenseFormCreatingTest
{
    @Test
    fun `a new form defaults to a monthly rule starting today, with no end date`()
    {
        // WHEN
        val form = RecurringExpenseFormState.creating(ACCOUNT_ID, TODAY)

        // THEN
        assertThat(form.accountId).isEqualTo(ACCOUNT_ID)
        assertThat(form.amountText).isEmpty()
        assertThat(form.title).isEmpty()
        assertThat(form.subcategory).isNull()
        assertThat(form.frequency).isEqualTo(RecurrenceFrequency.MONTHLY)
        assertThat(form.intervalText).isEqualTo("1")
        assertThat(form.startDate).isEqualTo(TODAY)
        assertThat(form.hasEndDate).isFalse()
        assertThat(form.isEditing).isFalse()
    }

    @Test
    fun `a complete form creates the rule`()
    {
        // GIVEN
        val subcategory = aSubcategory()
        val form = RecurringExpenseFormState.creating(ACCOUNT_ID, TODAY)
            .withAmount("14,50")
            .withTitle("Abonnement")
            .withSubcategory(subcategory)
            .withFrequency(RecurrenceFrequency.YEARLY)
            .withInterval("2")

        // WHEN
        val submission = form.submit()

        // THEN
        assertThat(submission).isEqualTo(
            RecurringExpenseSubmission.Create(
                CreateRecurringExpenseCommand(
                    accountId = ACCOUNT_ID,
                    amount = Money(1_450),
                    title = TransactionTitle("Abonnement"),
                    subcategoryId = subcategory.id,
                    description = null,
                    frequency = RecurrenceFrequency.YEARLY,
                    interval = 2,
                    startDate = TODAY,
                    endDate = null,
                ),
            ),
        )
    }

    @Test
    fun `the subcategory is optional`()
    {
        // GIVEN
        val form = RecurringExpenseFormState.creating(ACCOUNT_ID, TODAY).withAmount("10").withTitle("Loyer")

        // WHEN
        val submission = form.submit() as RecurringExpenseSubmission.Create

        // THEN
        assertThat(submission.command.subcategoryId).isNull()
    }

    @Test
    fun `an end date on or after the start date is accepted`()
    {
        // GIVEN
        val form = RecurringExpenseFormState.creating(ACCOUNT_ID, TODAY)
            .withAmount("10")
            .withTitle("Loyer")
            .withEndDateEnabled(true)
            .withEndDate(TODAY.plusMonths(6))

        // WHEN
        val submission = form.submit() as RecurringExpenseSubmission.Create

        // THEN
        assertThat(submission.command.endDate).isEqualTo(TODAY.plusMonths(6))
    }

    @Test
    fun `no account is refused`()
    {
        // GIVEN
        val form = RecurringExpenseFormState.creating(null, TODAY).withAmount("10").withTitle("Loyer")

        // WHEN / THEN
        assertThat(form.submit()).isEqualTo(
            RecurringExpenseSubmission.Invalid(setOf(RecurringExpenseFormError.ACCOUNT_REQUIRED)),
        )
    }

    @Test
    fun `an invalid amount is refused`()
    {
        // GIVEN
        val form = RecurringExpenseFormState.creating(ACCOUNT_ID, TODAY).withTitle("Loyer").withAmount("abc")

        // WHEN / THEN
        assertThat(form.submit()).isEqualTo(
            RecurringExpenseSubmission.Invalid(setOf(RecurringExpenseFormError.AMOUNT_INVALID)),
        )
    }

    @Test
    fun `a blank title is refused`()
    {
        // GIVEN
        val form = RecurringExpenseFormState.creating(ACCOUNT_ID, TODAY).withAmount("10")

        // WHEN / THEN
        assertThat(form.submit()).isEqualTo(
            RecurringExpenseSubmission.Invalid(setOf(RecurringExpenseFormError.TITLE_REQUIRED)),
        )
    }

    @Test
    fun `an interval that got in blank is refused, not defaulted`()
    {
        // GIVEN — withInterval("") would be accepted by the pattern, so this is reachable while typing
        val form = RecurringExpenseFormState.creating(ACCOUNT_ID, TODAY)
            .withAmount("10")
            .withTitle("Loyer")
            .withInterval("")

        // WHEN / THEN
        assertThat(form.submit()).isEqualTo(
            RecurringExpenseSubmission.Invalid(setOf(RecurringExpenseFormError.INTERVAL_INVALID)),
        )
    }

    @Test
    fun `an interval of zero is refused`()
    {
        // GIVEN
        val form = RecurringExpenseFormState.creating(ACCOUNT_ID, TODAY)
            .withAmount("10")
            .withTitle("Loyer")
            .withInterval("0")

        // WHEN / THEN
        assertThat(form.submit()).isEqualTo(
            RecurringExpenseSubmission.Invalid(setOf(RecurringExpenseFormError.INTERVAL_INVALID)),
        )
    }

    @Test
    fun `an end date before the start date is refused`()
    {
        // GIVEN
        val form = RecurringExpenseFormState.creating(ACCOUNT_ID, TODAY)
            .withAmount("10")
            .withTitle("Loyer")
            .withEndDateEnabled(true)
            .withEndDate(TODAY.minusDays(1))

        // WHEN / THEN
        assertThat(form.submit()).isEqualTo(
            RecurringExpenseSubmission.Invalid(setOf(RecurringExpenseFormError.END_BEFORE_START)),
        )
    }

    @Test
    fun `every problem is reported at once`()
    {
        // GIVEN
        val form = RecurringExpenseFormState.creating(null, TODAY).withInterval("")

        // WHEN / THEN
        assertThat(form.submit()).isEqualTo(
            RecurringExpenseSubmission.Invalid(
                setOf(
                    RecurringExpenseFormError.ACCOUNT_REQUIRED,
                    RecurringExpenseFormError.AMOUNT_INVALID,
                    RecurringExpenseFormError.TITLE_REQUIRED,
                    RecurringExpenseFormError.INTERVAL_INVALID,
                ),
            ),
        )
    }
}

class RecurringExpenseFormMutatorsTest
{
    @Test
    fun `the interval field only accepts up to three digits`()
    {
        // GIVEN
        val form = RecurringExpenseFormState.creating(ACCOUNT_ID, TODAY)

        // WHEN / THEN
        assertThat(form.withInterval("12a").intervalText).isEqualTo("1")
        assertThat(form.withInterval("999").intervalText).isEqualTo("999")
        assertThat(form.withInterval("9999").intervalText).isEqualTo("1")
    }

    @Test
    fun `moving the start date after an existing end date pulls the end date with it`()
    {
        // GIVEN
        val form = RecurringExpenseFormState.creating(ACCOUNT_ID, TODAY)
            .withEndDateEnabled(true)
            .withEndDate(TODAY.plusDays(5))

        // WHEN
        val moved = form.withStartDate(TODAY.plusDays(10))

        // THEN
        assertThat(moved.startDate).isEqualTo(TODAY.plusDays(10))
        assertThat(moved.endDate).isEqualTo(TODAY.plusDays(10))
    }

    @Test
    fun `moving the start date earlier than the end date leaves the end date alone`()
    {
        // GIVEN
        val form = RecurringExpenseFormState.creating(ACCOUNT_ID, TODAY)
            .withEndDateEnabled(true)
            .withEndDate(TODAY.plusDays(5))

        // WHEN
        val moved = form.withStartDate(TODAY.minusDays(1))

        // THEN
        assertThat(moved.endDate).isEqualTo(TODAY.plusDays(5))
    }

    @Test
    fun `enabling an end date before the start date snaps it to the start date`()
    {
        // GIVEN a form whose stale end date (from creating()) equals the start date already, moved back
        val form = RecurringExpenseFormState.creating(ACCOUNT_ID, TODAY).copy(endDate = TODAY.minusDays(3))

        // WHEN
        val enabled = form.withEndDateEnabled(true)

        // THEN
        assertThat(enabled.endDate).isEqualTo(TODAY)
    }

    @Test
    fun `only expense subcategories are offered`()
    {
        // GIVEN
        val expense = aSubcategory(RecordableTransactionCategory.EXPENSE)
        val income = aSubcategory(RecordableTransactionCategory.INCOME)
        val form = RecurringExpenseFormState.creating(ACCOUNT_ID, TODAY)

        // WHEN / THEN
        assertThat(form.subcategoryChoices(listOf(expense, income))).containsExactly(expense)
    }
}

class RecurringExpenseFormEditingTest
{
    @Test
    fun `editing pre-fills every field, keeping the account and start date for display`()
    {
        // GIVEN
        val subcategory = aSubcategory()
        val rule = aRecurringExpense(
            frequency = RecurrenceFrequency.WEEKLY,
            interval = 2,
            startDate = TODAY.minusMonths(3),
            endDate = TODAY.plusMonths(3),
            subcategoryId = subcategory.id,
        )

        // WHEN
        val form = RecurringExpenseFormState.editing(rule, subcategory, "Compte courant")

        // THEN
        assertThat(form.editingId).isEqualTo(rule.id)
        assertThat(form.accountId).isEqualTo(rule.accountId)
        assertThat(form.accountName).isEqualTo("Compte courant")
        assertThat(form.amountText).isEqualTo("15,00")
        assertThat(form.title).isEqualTo("Loyer")
        assertThat(form.subcategory).isEqualTo(subcategory)
        assertThat(form.frequency).isEqualTo(RecurrenceFrequency.WEEKLY)
        assertThat(form.intervalText).isEqualTo("2")
        assertThat(form.startDate).isEqualTo(rule.startDate)
        assertThat(form.hasEndDate).isTrue()
        assertThat(form.endDate).isEqualTo(rule.endDate)
        assertThat(form.isEditing).isTrue()
    }

    @Test
    fun `a rule without an end date is edited with none`()
    {
        // WHEN
        val form = RecurringExpenseFormState.editing(aRecurringExpense(endDate = null), null, "Compte courant")

        // THEN
        assertThat(form.hasEndDate).isFalse()
        assertThat(form.endDate).isEqualTo(form.startDate)
    }

    @Test
    fun `saving an edit never carries the account or the start date`()
    {
        // GIVEN
        val rule = aRecurringExpense()
        val form = RecurringExpenseFormState.editing(rule, null, "Compte courant")
            .withTitle("Loyer révisé")
            .withAmount("16,00")

        // WHEN
        val submission = form.submit()

        // THEN
        assertThat(submission).isEqualTo(
            RecurringExpenseSubmission.Update(
                UpdateRecurringExpenseCommand(
                    id = rule.id,
                    amount = Money(1_600),
                    title = TransactionTitle("Loyer révisé"),
                    subcategoryId = null,
                    description = null,
                    frequency = rule.frequency,
                    interval = rule.interval,
                    endDate = null,
                ),
            ),
        )
    }

    @Test
    fun `an untouched edit saves what it was opened with`()
    {
        // GIVEN
        val rule = aRecurringExpense()

        // WHEN
        val submission =
            RecurringExpenseFormState.editing(rule, null, "Compte courant").submit() as RecurringExpenseSubmission.Update

        // THEN
        assertThat(submission.command.amount).isEqualTo(rule.amount)
        assertThat(submission.command.title).isEqualTo(rule.title)
        assertThat(submission.command.frequency).isEqualTo(rule.frequency)
        assertThat(submission.command.interval).isEqualTo(rule.interval)
    }

    @Test
    fun `clearing the subcategory of an edit saves it with none`()
    {
        // GIVEN
        val subcategory = aSubcategory()
        val form = RecurringExpenseFormState.editing(aRecurringExpense(subcategoryId = subcategory.id), subcategory, "Compte courant")
            .withSubcategory(null)

        // WHEN
        val submission = form.submit() as RecurringExpenseSubmission.Update

        // THEN
        assertThat(submission.command.subcategoryId).isNull()
    }

    @Test
    fun `a blank title on an edit is refused`()
    {
        // GIVEN
        val form = RecurringExpenseFormState.editing(aRecurringExpense(), null, "Compte courant").withTitle("")

        // WHEN / THEN
        assertThat(form.submit()).isEqualTo(
            RecurringExpenseSubmission.Invalid(setOf(RecurringExpenseFormError.TITLE_REQUIRED)),
        )
    }
}
