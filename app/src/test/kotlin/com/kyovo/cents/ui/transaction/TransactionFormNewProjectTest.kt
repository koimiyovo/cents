package com.kyovo.cents.ui.transaction

import com.kyovo.cents.MainDispatcherExtension
import com.kyovo.cents.domain.exception.DuplicateProjectNameException
import com.kyovo.cents.domain.exception.ProjectNotFoundException
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import com.kyovo.cents.domain.model.BudgetAlert
import com.kyovo.cents.domain.model.Emoji
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.ProjectName
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.model.TransferResult
import com.kyovo.cents.domain.port.input.CheckBudgetAlertsUseCase
import com.kyovo.cents.domain.port.input.CreateProjectCommand
import com.kyovo.cents.domain.port.input.CreateSubcategoryCommand
import com.kyovo.cents.domain.port.input.CreateSubcategoryUseCase
import com.kyovo.cents.domain.port.input.DeleteTransactionUseCase
import com.kyovo.cents.domain.port.input.RecordTransactionCommand
import com.kyovo.cents.domain.port.input.RecordTransactionUseCase
import com.kyovo.cents.domain.port.input.RecordTransferCommand
import com.kyovo.cents.domain.port.input.RecordTransferUseCase
import com.kyovo.cents.domain.port.input.UpdateTransactionCommand
import com.kyovo.cents.domain.port.input.UpdateTransactionUseCase
import com.kyovo.cents.ui.project.NewProjectDraft
import com.kyovo.cents.ui.project.ProjectFormError
import com.kyovo.cents.ui.project.ProjectFormState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Instant
import java.time.YearMonth
import java.util.Currency
import java.util.UUID

private val MOMENT = Instant.parse("2026-09-23T12:00:00Z")

private fun anActiveAccount() = Account(
    id = AccountId(UUID.randomUUID()),
    name = AccountName("Compte"),
    type = AccountType.CHECKING,
    currency = AccountCurrency(Currency.getInstance("EUR")),
    createdAt = MOMENT,
)

/** Keeps the commands it receives; can be told to refuse them. */
private class CapturingRecord : RecordTransactionUseCase
{
    val commands = mutableListOf<RecordTransactionCommand>()
    var failWith: RuntimeException? = null

    override suspend fun record(command: RecordTransactionCommand): Transaction
    {
        failWith?.let { throw it }
        commands += command
        return command.toTransaction(TransactionId(UUID.randomUUID()), null)
    }
}

/** Keeps the commands it receives. */
private class CapturingUpdate : UpdateTransactionUseCase
{
    val commands = mutableListOf<UpdateTransactionCommand>()

    override suspend fun update(command: UpdateTransactionCommand): Transaction
    {
        commands += command
        return Transaction.recorded(
            command.id, command.accountId, command.amount, command.title, command.category,
            null, command.description, command.date, command.projectId,
        )
    }
}

private object NotUsedHere : RecordTransferUseCase, DeleteTransactionUseCase, CreateSubcategoryUseCase, CheckBudgetAlertsUseCase
{
    override suspend fun record(command: RecordTransferCommand): TransferResult = error("not used")
    override suspend fun delete(id: TransactionId) = error("not used")
    override suspend fun create(command: CreateSubcategoryCommand): Subcategory = error("not used")
    override suspend fun check(month: YearMonth): List<BudgetAlert> = emptyList()
}

/**
 * The "Projet" dropdown of the form ends with "+ Nouveau projet": a name, an optional emoji and an optional
 * target typed in a small form, a project created and selected right away in the transaction form. Only a
 * transaction that can have a project gets the option (not a transfer, not one that repeats).
 */
@ExtendWith(MainDispatcherExtension::class)
class TransactionFormNewProjectTest
{
    private val record = CapturingRecord()
    private val update = CapturingUpdate()
    private val createProject = FakeCreateProject()
    private val createRecurring = FakeCreateRecurring()
    private val viewModel = TransactionFormViewModel(
        record,
        NotUsedHere,
        update,
        NotUsedHere,
        NotUsedHere,
        NotUsedHere,
        createRecurring,
        FakeGenerateRecurring(createRecurring),
        createProject,
        now = { MOMENT },
    )

    private val state get() = viewModel.uiState.value
    private val account = anActiveAccount()

    private fun openExpenseForm()
    {
        viewModel.open(listOf(account), preselectedAccountId = account.id)
    }

    private fun typeInTheDialog(block: (ProjectFormState) -> ProjectFormState)
    {
        viewModel.updateNewProject(block(state.newProject!!.form))
    }

    // ------------------------------------------------------------------ opening

    @Test
    fun `asking opens the dialog with an empty form and no error`()
    {
        openExpenseForm()

        viewModel.askToCreateProject()

        assertThat(state.newProject).isEqualTo(NewProjectDraft())
        assertThat(state.newProject!!.form).isEqualTo(ProjectFormState.creating())
        assertThat(state.newProject!!.errors).isEmpty()
    }

    @Test
    fun `nothing opens when no form is open`()
    {
        viewModel.askToCreateProject()

        assertThat(state.newProject).isNull()
    }

    @Test
    fun `nothing opens on a transfer, which has no project`()
    {
        openExpenseForm()
        viewModel.update(state.form!!.withType(TransactionFormType.TRANSFER))

        viewModel.askToCreateProject()

        assertThat(state.newProject).isNull()
    }

    @Test
    fun `nothing opens on a transaction that repeats, which has no project`()
    {
        openExpenseForm()
        viewModel.update(state.form!!.withRepeat(true))

        viewModel.askToCreateProject()

        assertThat(state.newProject).isNull()
    }

    @Test
    fun `an income can get a project too`()
    {
        openExpenseForm()
        viewModel.update(state.form!!.withType(TransactionFormType.INCOME))

        viewModel.askToCreateProject()

        assertThat(state.newProject).isNotNull()
    }

    // ------------------------------------------------------------------ creating

    @Test
    fun `confirming creates the project and selects it in the form`()
    {
        openExpenseForm()
        viewModel.askToCreateProject()
        typeInTheDialog { it.withName("Voyage au Japon").withEmoji("✈️").withTarget("3000") }

        viewModel.confirmNewProject()

        assertThat(createProject.commands).containsExactly(
            CreateProjectCommand(ProjectName("Voyage au Japon"), Emoji("✈️"), Money(300_000))
        )
        assertThat(state.newProject).isNull()
        assertThat(state.form!!.project).isNotNull()
        assertThat(state.form!!.project!!.name).isEqualTo(ProjectName("Voyage au Japon"))
    }

    @Test
    fun `what was typed in the transaction form is kept`()
    {
        openExpenseForm()
        viewModel.update(state.form!!.copy(amountText = "850", title = "Billets"))
        viewModel.askToCreateProject()
        typeInTheDialog { it.withName("Voyage") }

        viewModel.confirmNewProject()

        assertThat(state.form!!.amountText).isEqualTo("850")
        assertThat(state.form!!.title).isEqualTo("Billets")
    }

    @Test
    fun `a blank name is refused in the dialog, which stays open`()
    {
        openExpenseForm()
        viewModel.askToCreateProject()

        viewModel.confirmNewProject()

        assertThat(state.newProject!!.errors).containsExactly(ProjectFormError.NAME_REQUIRED)
        assertThat(createProject.commands).isEmpty()
        assertThat(state.form!!.project).isNull()
    }

    @Test
    fun `a name already used is refused in the dialog, which stays open`()
    {
        openExpenseForm()
        viewModel.askToCreateProject()
        typeInTheDialog { it.withName("Voyage au Japon") }
        createProject.failWith = DuplicateProjectNameException()

        viewModel.confirmNewProject()

        assertThat(state.newProject!!.errors).containsExactly(ProjectFormError.NAME_TAKEN)
        assertThat(state.form!!.project).isNull()
    }

    @Test
    fun `typing again clears the error`()
    {
        openExpenseForm()
        viewModel.askToCreateProject()
        viewModel.confirmNewProject()
        assertThat(state.newProject!!.errors).isNotEmpty()

        typeInTheDialog { it.withName("Voyage") }

        assertThat(state.newProject!!.errors).isEmpty()
    }

    @Test
    fun `dismissing closes the dialog and leaves the form as it was`()
    {
        openExpenseForm()
        viewModel.update(state.form!!.copy(title = "Billets"))
        viewModel.askToCreateProject()
        typeInTheDialog { it.withName("Voyage") }

        viewModel.dismissNewProject()

        assertThat(state.newProject).isNull()
        assertThat(state.form!!.title).isEqualTo("Billets")
        assertThat(state.form!!.project).isNull()
    }

    @Test
    fun `closing the transaction form closes the dialog with it`()
    {
        openExpenseForm()
        viewModel.askToCreateProject()

        viewModel.close()

        assertThat(state.newProject).isNull()
    }

    // ------------------------------------------------------------------ saving with a project

    @Test
    fun `the transaction is recorded with the project that was just created`()
    {
        openExpenseForm()
        viewModel.update(state.form!!.copy(amountText = "850", title = "Billets"))
        viewModel.askToCreateProject()
        typeInTheDialog { it.withName("Voyage") }
        viewModel.confirmNewProject()
        val created = state.form!!.project!!

        viewModel.submit()

        assertThat(record.commands.single().projectId).isEqualTo(created.id)
        assertThat(state.form).isNull()
    }

    @Test
    fun `a transaction is recorded with the project chosen in the form`()
    {
        openExpenseForm()
        viewModel.update(state.form!!.copy(amountText = "850", title = "Billets").withProject(JAPAN_PROJECT))

        viewModel.submit()

        assertThat(record.commands.single().projectId).isEqualTo(JAPAN_PROJECT.id)
    }

    @Test
    fun `a project deleted meanwhile keeps the sheet open and says so`()
    {
        openExpenseForm()
        viewModel.update(state.form!!.copy(amountText = "850", title = "Billets").withProject(JAPAN_PROJECT))
        record.failWith = ProjectNotFoundException()

        viewModel.submit()

        assertThat(state.failure).isEqualTo(SubmitFailure.PROJECT_NOT_FOUND)
        assertThat(state.form).isNotNull()
    }

    // ------------------------------------------------------------------ editing

    @Test
    fun `an edit opens on the project of the transaction and sends it back when saved`()
    {
        // GIVEN an expense in the Japan project
        val transaction = Transaction.recorded(
            TransactionId(UUID.randomUUID()), account.id, Money(85_000),
            com.kyovo.cents.domain.model.TransactionTitle("Billets"),
            com.kyovo.cents.domain.model.RecordableTransactionCategory.EXPENSE, null, null, MOMENT, JAPAN_PROJECT.id,
        )

        // WHEN
        viewModel.openForEdit(transaction, null, JAPAN_PROJECT)
        viewModel.submit()

        // THEN
        assertThat(update.commands.single().projectId).isEqualTo(JAPAN_PROJECT.id)
    }

    @Test
    fun `an edit can create a project from the form, and move the transaction into it`()
    {
        // GIVEN an expense with no project
        val transaction = Transaction.recorded(
            TransactionId(UUID.randomUUID()), account.id, Money(85_000),
            com.kyovo.cents.domain.model.TransactionTitle("Billets"),
            com.kyovo.cents.domain.model.RecordableTransactionCategory.EXPENSE, null, null, MOMENT, null,
        )
        viewModel.openForEdit(transaction, null, null)

        // WHEN
        viewModel.askToCreateProject()
        typeInTheDialog { it.withName("Voyage") }
        viewModel.confirmNewProject()
        val created = state.form!!.project!!
        viewModel.submit()

        // THEN
        assertThat(update.commands.single().projectId).isEqualTo(created.id)
    }
}
