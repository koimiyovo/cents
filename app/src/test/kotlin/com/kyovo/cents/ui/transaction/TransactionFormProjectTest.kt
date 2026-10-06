package com.kyovo.cents.ui.transaction

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.model.TransactionTitle
import com.kyovo.cents.domain.port.input.RecordTransactionCommand
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Instant
import java.util.UUID

private val NOW = Instant.parse("2026-09-23T12:00:00Z")
private val CHECKING = AccountId(UUID.randomUUID())
private val OTHER_ACCOUNT = AccountId(UUID.randomUUID())

/**
 * The "Projet" field of the transaction form: an income or an expense can be filed under a project, next
 * to its subcategory. A transfer cannot, and neither can a transaction the form turns into a recurring
 * rule - a rule is not tied to a project - so those two forget the project instead of carrying it.
 */
class TransactionFormProjectTest
{
    private val expense = TransactionFormState(
        type = TransactionFormType.EXPENSE,
        accountId = CHECKING,
        amountText = "850",
        title = "Billets d'avion",
        date = NOW,
    )

    // ------------------------------------------------------------------ choosing

    @Test
    fun `a new form has no project`()
    {
        assertThat(expense.project).isNull()
    }

    @Test
    fun `a project can be chosen, changed and taken away`()
    {
        val chosen = expense.withProject(JAPAN_PROJECT)

        assertThat(chosen.project).isEqualTo(JAPAN_PROJECT)
        assertThat(chosen.withProject(KITCHEN_PROJECT).project).isEqualTo(KITCHEN_PROJECT)
        assertThat(chosen.withProject(null).project).isNull()
    }

    @Test
    fun `an income can be filed under a project too, a refund for instance`()
    {
        val refund = expense.withType(TransactionFormType.INCOME).withProject(JAPAN_PROJECT)

        assertThat(refund.project).isEqualTo(JAPAN_PROJECT)
    }

    @Test
    fun `switching between expense and income keeps the project`()
    {
        val form = expense.withProject(JAPAN_PROJECT).withType(TransactionFormType.INCOME)

        assertThat(form.project).isEqualTo(JAPAN_PROJECT)
    }

    @Test
    fun `the project is independent of the subcategory`()
    {
        val form = expense.copy(subcategory = GROCERIES_SUBCATEGORY).withProject(JAPAN_PROJECT)
            .withType(TransactionFormType.INCOME)

        // The subcategory of the wrong kind goes, the project stays.
        assertThat(form.subcategory).isNull()
        assertThat(form.project).isEqualTo(JAPAN_PROJECT)
    }

    // ------------------------------------------------------------------ where it is offered

    @Test
    fun `an income or an expense can have a project, a transfer cannot`()
    {
        assertThat(expense.canHaveProject).isTrue()
        assertThat(expense.withType(TransactionFormType.INCOME).canHaveProject).isTrue()
        assertThat(expense.withType(TransactionFormType.TRANSFER).canHaveProject).isFalse()
    }

    @Test
    fun `a transfer forgets the project, and does not get it back`()
    {
        val form = expense.withProject(JAPAN_PROJECT).withType(TransactionFormType.TRANSFER)

        assertThat(form.project).isNull()
        assertThat(form.withType(TransactionFormType.EXPENSE).project).isNull()
    }

    @Test
    fun `a project cannot be chosen on a transfer`()
    {
        val transfer = expense.withType(TransactionFormType.TRANSFER)

        assertThat(transfer.withProject(JAPAN_PROJECT).project).isNull()
    }

    @Test
    fun `a transaction that repeats cannot have a project`()
    {
        val repeating = expense.withRepeat(true)

        assertThat(repeating.canHaveProject).isFalse()
        assertThat(repeating.withProject(JAPAN_PROJECT).project).isNull()
    }

    @Test
    fun `turning repeat on forgets the project, turning it off does not bring it back`()
    {
        val form = expense.withProject(JAPAN_PROJECT).withRepeat(true)

        assertThat(form.project).isNull()
        assertThat(form.withRepeat(false).project).isNull()
        assertThat(form.withRepeat(false).canHaveProject).isTrue()
    }

    @Test
    fun `an edited transaction can change project, it never repeats`()
    {
        val editing = expense.copy(editingId = TransactionId(UUID.randomUUID()))

        assertThat(editing.canHaveProject).isTrue()
        assertThat(editing.withProject(KITCHEN_PROJECT).project).isEqualTo(KITCHEN_PROJECT)
    }

    @Test
    fun `the form offers the projects it is given, none when it cannot have one`()
    {
        assertThat(expense.projectChoices(ALL_TEST_PROJECTS)).containsExactlyElementsOf(ALL_TEST_PROJECTS)
        assertThat(expense.withType(TransactionFormType.TRANSFER).projectChoices(ALL_TEST_PROJECTS)).isEmpty()
        assertThat(expense.withRepeat(true).projectChoices(ALL_TEST_PROJECTS)).isEmpty()
    }

    // ------------------------------------------------------------------ saving

    @Test
    fun `an expense is recorded with its project`()
    {
        val submission = expense.withProject(JAPAN_PROJECT).submit() as FormSubmission.Record

        assertThat(submission.command.projectId).isEqualTo(JAPAN_PROJECT.id)
    }

    @ParameterizedTest
    @EnumSource(RecordableTransactionCategory::class)
    fun `an income or an expense is recorded with its project`(category: RecordableTransactionCategory)
    {
        val type = if (category == RecordableTransactionCategory.INCOME) TransactionFormType.INCOME else TransactionFormType.EXPENSE

        val command = (expense.withType(type).withProject(KITCHEN_PROJECT).submit() as FormSubmission.Record).command

        assertThat(command.category).isEqualTo(category)
        assertThat(command.projectId).isEqualTo(KITCHEN_PROJECT.id)
    }

    @Test
    fun `without a project, the command has none`()
    {
        val command: RecordTransactionCommand = (expense.submit() as FormSubmission.Record).command

        assertThat(command.projectId).isNull()
    }

    @Test
    fun `a transfer is recorded without any project`()
    {
        val transfer = expense.withProject(JAPAN_PROJECT).withType(TransactionFormType.TRANSFER)
            .copy(toAccountId = OTHER_ACCOUNT)

        assertThat(transfer.submit()).isInstanceOf(FormSubmission.Transfer::class.java)
    }

    @Test
    fun `a rule is created without any project`()
    {
        val submission = expense.withProject(JAPAN_PROJECT).withRepeat(true).submit()

        assertThat(submission).isInstanceOf(FormSubmission.Repeat::class.java)
    }

    // ------------------------------------------------------------------ editing

    private fun anExpenseInAProject(project: com.kyovo.cents.domain.model.ProjectId?) = Transaction.recorded(
        TransactionId(UUID.randomUUID()), CHECKING, Money(85_000), TransactionTitle("Billets"),
        RecordableTransactionCategory.EXPENSE, null, null, NOW, project,
    )

    @Test
    fun `editing a transaction opens on its project`()
    {
        val form = TransactionFormState.editing(anExpenseInAProject(JAPAN_PROJECT.id), null, JAPAN_PROJECT)

        assertThat(form.project).isEqualTo(JAPAN_PROJECT)
    }

    @Test
    fun `editing a transaction without a project opens on none`()
    {
        val form = TransactionFormState.editing(anExpenseInAProject(null), null, null)

        assertThat(form.project).isNull()
    }

    @Test
    fun `saving an edit sends the project back, so it is not lost`()
    {
        val form = TransactionFormState.editing(anExpenseInAProject(JAPAN_PROJECT.id), null, JAPAN_PROJECT)

        val command = (form.submit() as FormSubmission.Update).command

        assertThat(command.projectId).isEqualTo(JAPAN_PROJECT.id)
    }

    @Test
    fun `an edit can move the transaction to another project, or out of its project`()
    {
        val form = TransactionFormState.editing(anExpenseInAProject(JAPAN_PROJECT.id), null, JAPAN_PROJECT)

        assertThat((form.withProject(KITCHEN_PROJECT).submit() as FormSubmission.Update).command.projectId)
            .isEqualTo(KITCHEN_PROJECT.id)
        assertThat((form.withProject(null).submit() as FormSubmission.Update).command.projectId).isNull()
    }
}
