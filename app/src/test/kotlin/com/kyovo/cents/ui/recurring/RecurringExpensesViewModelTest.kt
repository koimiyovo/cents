package com.kyovo.cents.ui.recurring

import com.kyovo.cents.MainDispatcherExtension
import com.kyovo.cents.domain.exception.AccountNotFoundException
import com.kyovo.cents.domain.exception.RecurringExpenseNotFoundException
import com.kyovo.cents.domain.exception.SubcategoryNotFoundException
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecurrenceFrequency
import com.kyovo.cents.domain.model.RecurringExpense
import com.kyovo.cents.domain.model.RecurringExpenseId
import com.kyovo.cents.domain.model.TransactionTitle
import com.kyovo.cents.domain.port.input.CreateRecurringExpenseCommand
import com.kyovo.cents.domain.port.input.CreateRecurringExpenseUseCase
import com.kyovo.cents.domain.port.input.DeleteRecurringExpenseUseCase
import com.kyovo.cents.domain.port.input.UpdateRecurringExpenseCommand
import com.kyovo.cents.domain.port.input.UpdateRecurringExpenseUseCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDate
import java.util.UUID

private val ACCOUNT_ID = AccountId(UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001"))
private val TODAY: LocalDate = LocalDate.of(2026, 9, 27)

private fun aRecurringExpense(id: RecurringExpenseId = RecurringExpenseId(UUID.randomUUID())) = RecurringExpense(
    id = id,
    accountId = ACCOUNT_ID,
    amount = Money(1_500),
    title = TransactionTitle("Loyer"),
    subcategoryId = null,
    description = null,
    frequency = RecurrenceFrequency.MONTHLY,
    interval = 1,
    startDate = TODAY,
)

private fun aRow(rule: RecurringExpense = aRecurringExpense()) = RecurringExpenseRow(rule, "Compte courant", null)

/** Records what it is asked to create; can be told to refuse it. */
private class RecordingCreate : CreateRecurringExpenseUseCase
{
    val commands = mutableListOf<CreateRecurringExpenseCommand>()
    var failWith: RuntimeException? = null

    override suspend fun create(command: CreateRecurringExpenseCommand): RecurringExpense
    {
        failWith?.let { throw it }
        commands += command
        return command.toRecurringExpense(RecurringExpenseId(UUID.randomUUID()))
    }
}

/** Records what it is asked to update; can be told to refuse it. */
private class RecordingUpdate : UpdateRecurringExpenseUseCase
{
    val commands = mutableListOf<UpdateRecurringExpenseCommand>()
    var failWith: RuntimeException? = null

    override suspend fun update(command: UpdateRecurringExpenseCommand): RecurringExpense
    {
        failWith?.let { throw it }
        commands += command
        return aRecurringExpense(command.id).copy(
            amount = command.amount,
            title = command.title,
            subcategoryId = command.subcategoryId,
            frequency = command.frequency,
            interval = command.interval,
            endDate = command.endDate,
        )
    }
}

private class RecordingDelete : DeleteRecurringExpenseUseCase
{
    val deleted = mutableListOf<RecurringExpenseId>()

    override suspend fun delete(id: RecurringExpenseId)
    {
        deleted += id
    }
}

@ExtendWith(MainDispatcherExtension::class)
class RecurringExpensesViewModelTest
{
    private val create = RecordingCreate()
    private val update = RecordingUpdate()
    private val delete = RecordingDelete()
    private val viewModel = RecurringExpensesViewModel(create, update, delete)

    private val state get() = viewModel.uiState.value
    private val form get() = state.form

    @Test
    fun `is closed until a form is opened`()
    {
        assertThat(state).isEqualTo(RecurringExpensesUiState())
    }

    @Test
    fun `opening a creation starts an empty form for the given account`()
    {
        // WHEN
        viewModel.openCreate(ACCOUNT_ID, TODAY)

        // THEN
        assertThat(form).isEqualTo(RecurringExpenseFormState.creating(ACCOUNT_ID, TODAY))
        assertThat(state.errors).isEmpty()
        assertThat(state.confirmingDelete).isNull()
    }

    @Test
    fun `opening an edit shows the rule's own fields`()
    {
        // GIVEN
        val row = aRow()

        // WHEN
        viewModel.openForEdit(row, null)

        // THEN
        assertThat(form).isEqualTo(RecurringExpenseFormState.editing(row.recurringExpense, null, row.accountName))
    }

    @Test
    fun `changing the form keeps it open and clears what the last attempt reported`()
    {
        // GIVEN a refused empty form
        viewModel.openCreate(ACCOUNT_ID, TODAY)
        viewModel.submit()
        assertThat(state.errors).isNotEmpty()

        // WHEN
        viewModel.update(form!!.withTitle("Loyer"))

        // THEN
        assertThat(form!!.title).isEqualTo("Loyer")
        assertThat(state.errors).isEmpty()
    }

    @Test
    fun `changing the form while closed does nothing`()
    {
        // WHEN
        viewModel.update(RecurringExpenseFormState.creating(ACCOUNT_ID, TODAY).withTitle("Loyer"))

        // THEN
        assertThat(form).isNull()
    }

    // ------------------------------------------------------------------ creating

    @Test
    fun `a valid creation is saved and the sheet closes`()
    {
        // GIVEN
        viewModel.openCreate(ACCOUNT_ID, TODAY)
        viewModel.update(form!!.withAmount("15,00").withTitle("Loyer"))

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(create.commands).hasSize(1)
        assertThat(create.commands.single().title).isEqualTo(TransactionTitle("Loyer"))
        assertThat(state).isEqualTo(RecurringExpensesUiState())
    }

    @Test
    fun `an invalid form is reported, nothing is saved and the sheet stays open`()
    {
        // GIVEN
        viewModel.openCreate(ACCOUNT_ID, TODAY)

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(state.errors).isNotEmpty()
        assertThat(form).isNotNull()
        assertThat(create.commands).isEmpty()
    }

    @Test
    fun `an account gone since the form was opened is reported`()
    {
        // GIVEN
        viewModel.openCreate(ACCOUNT_ID, TODAY)
        viewModel.update(form!!.withAmount("15,00").withTitle("Loyer"))
        create.failWith = AccountNotFoundException()

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(state.errors).containsExactly(RecurringExpenseFormError.ACCOUNT_GONE)
        assertThat(form).isNotNull()
    }

    @Test
    fun `submitting while closed does nothing`()
    {
        // WHEN
        viewModel.submit()

        // THEN
        assertThat(create.commands).isEmpty()
        assertThat(update.commands).isEmpty()
    }

    // ------------------------------------------------------------------ editing

    @Test
    fun `a valid edit is saved on that rule and the sheet closes`()
    {
        // GIVEN
        val row = aRow()
        viewModel.openForEdit(row, null)
        viewModel.update(form!!.withTitle("Loyer révisé"))

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(update.commands).hasSize(1)
        assertThat(update.commands.single().id).isEqualTo(row.recurringExpense.id)
        assertThat(update.commands.single().title).isEqualTo(TransactionTitle("Loyer révisé"))
        assertThat(create.commands).isEmpty()
        assertThat(state).isEqualTo(RecurringExpensesUiState())
    }

    @Test
    fun `a subcategory gone since the edit began is reported`()
    {
        // GIVEN
        viewModel.openForEdit(aRow(), null)
        update.failWith = SubcategoryNotFoundException()

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(state.errors).containsExactly(RecurringExpenseFormError.SUBCATEGORY_GONE)
        assertThat(form).isNotNull()
    }

    @Test
    fun `a rule gone since the edit began is reported, not a crash`()
    {
        // GIVEN
        viewModel.openForEdit(aRow(), null)
        update.failWith = RecurringExpenseNotFoundException()

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(state.errors).containsExactly(RecurringExpenseFormError.RULE_GONE)
        assertThat(form).isNotNull()
    }

    // ------------------------------------------------------------------ deleting

    @Test
    fun `asking to delete names the rule being edited`()
    {
        // GIVEN
        val row = aRow(aRecurringExpense().copy(title = TransactionTitle("Netflix")))
        viewModel.openForEdit(row, null)

        // WHEN
        viewModel.askToDelete()

        // THEN
        assertThat(state.confirmingDelete).isEqualTo(RecurringExpenseToDelete("Netflix"))
        assertThat(form).isNotNull()
        assertThat(delete.deleted).isEmpty()
    }

    @Test
    fun `there is nothing to delete in a new rule`()
    {
        // GIVEN
        viewModel.openCreate(ACCOUNT_ID, TODAY)

        // WHEN
        viewModel.askToDelete()

        // THEN
        assertThat(state.confirmingDelete).isNull()
    }

    @Test
    fun `there is nothing to delete while closed`()
    {
        // WHEN
        viewModel.askToDelete()

        // THEN
        assertThat(state.confirmingDelete).isNull()
    }

    @Test
    fun `backing out of the question keeps the edit open and deletes nothing`()
    {
        // GIVEN
        viewModel.openForEdit(aRow(), null)
        viewModel.askToDelete()

        // WHEN
        viewModel.dismissDelete()

        // THEN
        assertThat(state.confirmingDelete).isNull()
        assertThat(form).isNotNull()
        assertThat(delete.deleted).isEmpty()
    }

    @Test
    fun `confirming deletes that rule and closes everything`()
    {
        // GIVEN
        val row = aRow()
        viewModel.openForEdit(row, null)
        viewModel.askToDelete()

        // WHEN
        viewModel.confirmDelete()

        // THEN
        assertThat(delete.deleted).containsExactly(row.recurringExpense.id)
        assertThat(state).isEqualTo(RecurringExpensesUiState())
    }

    @Test
    fun `confirming without having been asked deletes nothing`()
    {
        // GIVEN
        viewModel.openForEdit(aRow(), null)

        // WHEN
        viewModel.confirmDelete()

        // THEN
        assertThat(delete.deleted).isEmpty()
        assertThat(form).isNotNull()
    }

    @Test
    fun `closing drops the form, the errors and the question`()
    {
        // GIVEN
        viewModel.openForEdit(aRow(), null)
        viewModel.askToDelete()

        // WHEN
        viewModel.close()

        // THEN
        assertThat(state).isEqualTo(RecurringExpensesUiState())
    }
}
