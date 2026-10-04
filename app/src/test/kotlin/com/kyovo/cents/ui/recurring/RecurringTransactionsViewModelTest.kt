package com.kyovo.cents.ui.recurring

import com.kyovo.cents.MainDispatcherExtension
import com.kyovo.cents.domain.exception.AccountNotFoundException
import com.kyovo.cents.domain.exception.RecurringTransactionNotFoundException
import com.kyovo.cents.domain.exception.SubcategoryNotFoundException
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.RecurrenceFrequency
import com.kyovo.cents.domain.model.RecurringTransaction
import com.kyovo.cents.domain.model.RecurringTransactionId
import com.kyovo.cents.domain.model.TransactionTitle
import com.kyovo.cents.domain.port.input.CreateRecurringTransactionCommand
import com.kyovo.cents.domain.port.input.CreateRecurringTransactionUseCase
import com.kyovo.cents.domain.port.input.DeleteRecurringTransactionUseCase
import com.kyovo.cents.domain.port.input.GenerateRecurringTransactionsUseCase
import com.kyovo.cents.domain.port.input.UpdateRecurringTransactionCommand
import com.kyovo.cents.domain.port.input.UpdateRecurringTransactionUseCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDate
import java.util.UUID

private val ACCOUNT_ID = AccountId(UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001"))
private val TODAY: LocalDate = LocalDate.of(2026, 9, 27)

private fun aRecurringTransaction(id: RecurringTransactionId = RecurringTransactionId(UUID.randomUUID())) = RecurringTransaction(
    id = id,
    accountId = ACCOUNT_ID,
    category = RecordableTransactionCategory.EXPENSE,
    amount = Money(1_500),
    title = TransactionTitle("Loyer"),
    subcategoryId = null,
    description = null,
    frequency = RecurrenceFrequency.MONTHLY,
    interval = 1,
    startDate = TODAY,
)

private fun aRow(rule: RecurringTransaction = aRecurringTransaction()) = RecurringTransactionRow(rule, "Compte courant", null)

/** Records what it is asked to create; can be told to refuse it. */
private class RecordingCreate : CreateRecurringTransactionUseCase
{
    val commands = mutableListOf<CreateRecurringTransactionCommand>()
    var failWith: RuntimeException? = null

    override suspend fun create(command: CreateRecurringTransactionCommand): RecurringTransaction
    {
        failWith?.let { throw it }
        commands += command
        return command.toRecurringTransaction(RecurringTransactionId(UUID.randomUUID()))
    }
}

/** Records what it is asked to update; can be told to refuse it. */
private class RecordingUpdate : UpdateRecurringTransactionUseCase
{
    val commands = mutableListOf<UpdateRecurringTransactionCommand>()
    var failWith: RuntimeException? = null

    override suspend fun update(command: UpdateRecurringTransactionCommand): RecurringTransaction
    {
        failWith?.let { throw it }
        commands += command
        return aRecurringTransaction(command.id).copy(
            amount = command.amount,
            title = command.title,
            subcategoryId = command.subcategoryId,
            frequency = command.frequency,
            interval = command.interval,
            endDate = command.endDate,
        )
    }
}

/** Counts the times it is asked to generate, and how many rules had been created at each of them. */
private class RecordingGenerate(private val create: RecordingCreate) : GenerateRecurringTransactionsUseCase
{
    val createdWhenCalled = mutableListOf<Int>()

    override suspend fun generate()
    {
        createdWhenCalled += create.commands.size
    }
}

private class RecordingDelete : DeleteRecurringTransactionUseCase
{
    val deleted = mutableListOf<RecurringTransactionId>()

    override suspend fun delete(id: RecurringTransactionId)
    {
        deleted += id
    }
}

@ExtendWith(MainDispatcherExtension::class)
class RecurringTransactionsViewModelTest
{
    private val create = RecordingCreate()
    private val update = RecordingUpdate()
    private val delete = RecordingDelete()
    private val generate = RecordingGenerate(create)
    private val viewModel = RecurringTransactionsViewModel(create, update, delete, generate)

    private val state get() = viewModel.uiState.value
    private val form get() = state.form

    @Test
    fun `is closed until a form is opened`()
    {
        assertThat(state).isEqualTo(RecurringTransactionsUiState())
    }

    @Test
    fun `opening a creation starts an empty form for the given account`()
    {
        // WHEN
        viewModel.openCreate(ACCOUNT_ID, TODAY)

        // THEN
        assertThat(form).isEqualTo(RecurringTransactionFormState.creating(ACCOUNT_ID, TODAY))
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
        assertThat(form).isEqualTo(RecurringTransactionFormState.editing(row.recurringTransaction, null, row.accountName))
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
        viewModel.update(RecurringTransactionFormState.creating(ACCOUNT_ID, TODAY).withTitle("Loyer"))

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
        assertThat(state).isEqualTo(RecurringTransactionsUiState(askNotificationPermission = true))
    }

    // ------------------------------------------------------------------ generation after a creation
    // Generation runs at launch and once a day, so a rule that starts today would only show its transaction at
    // the next launch. Creating one generates right away: the user sees what they just asked for.

    @Test
    fun `creating a rule generates its transactions right after it is saved`()
    {
        // GIVEN
        viewModel.openCreate(ACCOUNT_ID, TODAY)
        viewModel.update(form!!.withAmount("15,00").withTitle("Loyer"))

        // WHEN
        viewModel.submit()

        // THEN generation ran once, and after the rule existed (so it can see it)
        assertThat(generate.createdWhenCalled).containsExactly(1)
    }

    @Test
    fun `a refused creation does not generate`()
    {
        // GIVEN
        viewModel.openCreate(ACCOUNT_ID, TODAY)
        viewModel.update(form!!.withAmount("15,00").withTitle("Loyer"))
        create.failWith = AccountNotFoundException()

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(generate.createdWhenCalled).isEmpty()
    }

    @Test
    fun `an invalid form does not generate`()
    {
        // GIVEN
        viewModel.openCreate(ACCOUNT_ID, TODAY)

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(generate.createdWhenCalled).isEmpty()
    }

    @Test
    fun `editing a rule does not generate, the daily run takes care of it`()
    {
        // GIVEN
        viewModel.openForEdit(aRow(), null)

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(update.commands).hasSize(1)
        assertThat(generate.createdWhenCalled).isEmpty()
    }

    // ------------------------------------------------------------------ notification permission
    // A recurring expense announces itself with a notification the day it falls, which needs the
    // permission: the ask comes when a rule is created, the moment the user has just asked for it.

    @Test
    fun `creating a rule asks for the notification permission`()
    {
        // GIVEN
        viewModel.openCreate(ACCOUNT_ID, TODAY)
        viewModel.update(form!!.withAmount("15,00").withTitle("Loyer"))

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(state.askNotificationPermission).isTrue()
        assertThat(form).isNull()
    }

    @Test
    fun `editing a rule does not ask again`()
    {
        // GIVEN
        viewModel.openForEdit(aRow(), null)

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(update.commands).hasSize(1)
        assertThat(state.askNotificationPermission).isFalse()
    }

    @Test
    fun `a refused creation does not ask`()
    {
        // GIVEN
        viewModel.openCreate(ACCOUNT_ID, TODAY)
        viewModel.update(form!!.withAmount("15,00").withTitle("Loyer"))
        create.failWith = AccountNotFoundException()

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(state.askNotificationPermission).isFalse()
    }

    @Test
    fun `an invalid form does not ask`()
    {
        // GIVEN
        viewModel.openCreate(ACCOUNT_ID, TODAY)

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(state.askNotificationPermission).isFalse()
    }

    // The screen consumes the ask once it has shown the rationale (or launched the real system request):
    // it must not still be there after a later recomposition, unrelated to a save.
    @Test
    fun `dismissing the ask clears it`()
    {
        // GIVEN
        viewModel.openCreate(ACCOUNT_ID, TODAY)
        viewModel.update(form!!.withAmount("15,00").withTitle("Loyer"))
        viewModel.submit()
        assertThat(state.askNotificationPermission).isTrue()

        // WHEN
        viewModel.dismissNotificationPermissionAsk()

        // THEN
        assertThat(state).isEqualTo(RecurringTransactionsUiState())
    }

    @Test
    fun `a valid income creation is saved as an income and closes the sheet`()
    {
        // GIVEN
        viewModel.openCreate(ACCOUNT_ID, TODAY)
        viewModel.update(form!!.withCategory(RecordableTransactionCategory.INCOME).withAmount("2000").withTitle("Salaire"))

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(create.commands.single().category).isEqualTo(RecordableTransactionCategory.INCOME)
        assertThat(form).isNull()
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
        assertThat(state.errors).containsExactly(RecurringTransactionFormError.ACCOUNT_GONE)
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
        assertThat(update.commands.single().id).isEqualTo(row.recurringTransaction.id)
        assertThat(update.commands.single().title).isEqualTo(TransactionTitle("Loyer révisé"))
        assertThat(create.commands).isEmpty()
        assertThat(state).isEqualTo(RecurringTransactionsUiState())
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
        assertThat(state.errors).containsExactly(RecurringTransactionFormError.SUBCATEGORY_GONE)
        assertThat(form).isNotNull()
    }

    @Test
    fun `a rule gone since the edit began is reported, not a crash`()
    {
        // GIVEN
        viewModel.openForEdit(aRow(), null)
        update.failWith = RecurringTransactionNotFoundException()

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(state.errors).containsExactly(RecurringTransactionFormError.RULE_GONE)
        assertThat(form).isNotNull()
    }

    // ------------------------------------------------------------------ deleting

    @Test
    fun `asking to delete names the rule being edited`()
    {
        // GIVEN
        val row = aRow(aRecurringTransaction().copy(title = TransactionTitle("Netflix")))
        viewModel.openForEdit(row, null)

        // WHEN
        viewModel.askToDelete()

        // THEN
        assertThat(state.confirmingDelete).isEqualTo(RecurringTransactionToDelete("Netflix"))
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
        assertThat(delete.deleted).containsExactly(row.recurringTransaction.id)
        assertThat(state).isEqualTo(RecurringTransactionsUiState())
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
        assertThat(state).isEqualTo(RecurringTransactionsUiState())
    }
}
