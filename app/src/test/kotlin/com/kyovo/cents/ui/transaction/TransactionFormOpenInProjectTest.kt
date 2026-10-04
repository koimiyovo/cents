package com.kyovo.cents.ui.transaction

import com.kyovo.cents.MainDispatcherExtension
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import com.kyovo.cents.domain.model.BudgetAlert
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.model.TransferResult
import com.kyovo.cents.domain.port.input.CheckBudgetAlertsUseCase
import com.kyovo.cents.domain.port.input.CreateSubcategoryCommand
import com.kyovo.cents.domain.port.input.CreateSubcategoryUseCase
import com.kyovo.cents.domain.port.input.DeleteTransactionUseCase
import com.kyovo.cents.domain.port.input.RecordTransactionCommand
import com.kyovo.cents.domain.port.input.RecordTransactionUseCase
import com.kyovo.cents.domain.port.input.RecordTransferCommand
import com.kyovo.cents.domain.port.input.RecordTransferUseCase
import com.kyovo.cents.domain.port.input.UpdateTransactionCommand
import com.kyovo.cents.domain.port.input.UpdateTransactionUseCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Instant
import java.time.YearMonth
import java.util.Currency
import java.util.UUID

private val MOMENT = Instant.parse("2026-09-23T12:00:00Z")

private fun anAccount() = Account(
    id = AccountId(UUID.randomUUID()),
    name = AccountName("Compte"),
    type = AccountType.CHECKING,
    currency = AccountCurrency(Currency.getInstance("EUR")),
    createdAt = MOMENT,
)

private object NothingUsed : RecordTransactionUseCase, RecordTransferUseCase, UpdateTransactionUseCase,
    DeleteTransactionUseCase, CreateSubcategoryUseCase, CheckBudgetAlertsUseCase
{
    override suspend fun record(command: RecordTransactionCommand): Transaction = error("not used")
    override suspend fun record(command: RecordTransferCommand): TransferResult = error("not used")
    override suspend fun update(command: UpdateTransactionCommand): Transaction = error("not used")
    override suspend fun delete(id: TransactionId) = error("not used")
    override suspend fun create(command: CreateSubcategoryCommand): Subcategory = error("not used")
    override suspend fun check(month: YearMonth): List<BudgetAlert> = emptyList()
}

/**
 * The "+ Transaction" button of a project's page opens the form already filed under that project: the
 * most natural way to add to a trip or a renovation. The optional argument is the project; without it the
 * form opens as it always did.
 */
@ExtendWith(MainDispatcherExtension::class)
class TransactionFormOpenInProjectTest
{
    private val createRecurring = FakeCreateRecurring()
    private val viewModel = TransactionFormViewModel(
        NothingUsed, NothingUsed, NothingUsed, NothingUsed, NothingUsed, NothingUsed,
        createRecurring,
        FakeGenerateRecurring(createRecurring),
        FakeCreateProject(),
        NoProjectProgress,
        now = { MOMENT },
    )

    private val account = anAccount()
    private val state get() = viewModel.uiState.value

    @Test
    fun `opens on the project it is given`()
    {
        viewModel.open(listOf(account), preselectedAccountId = null, project = JAPAN_PROJECT)

        assertThat(state.form!!.project).isEqualTo(JAPAN_PROJECT)
    }

    @Test
    fun `opens without a project when none is given`()
    {
        viewModel.open(listOf(account), preselectedAccountId = null)

        assertThat(state.form!!.project).isNull()
    }

    @Test
    fun `the rest of the form opens as usual`()
    {
        viewModel.open(listOf(account), preselectedAccountId = account.id, project = KITCHEN_PROJECT)

        val form = state.form!!
        assertThat(form.type).isEqualTo(TransactionFormType.EXPENSE)
        assertThat(form.accountId).isEqualTo(account.id)
        assertThat(form.amountText).isEmpty()
        assertThat(form.date).isEqualTo(MOMENT)
    }

    // ------------------------------------------------------------------ a locked, simpler form

    // Opened from a project's page, the form is that project's: its project is shown but cannot be changed (the
    // field is greyed), and the choices that make no sense there - repeating, a transfer - are not offered.

    @Test
    fun `a form opened in a project has its project locked`()
    {
        viewModel.open(listOf(account), preselectedAccountId = null, project = JAPAN_PROJECT)

        assertThat(state.form!!.projectLocked).isTrue()
    }

    @Test
    fun `a form opened without a project is not locked`()
    {
        viewModel.open(listOf(account), preselectedAccountId = null)

        assertThat(state.form!!.projectLocked).isFalse()
    }

    @Test
    fun `the locked project can be neither changed nor taken away`()
    {
        viewModel.open(listOf(account), preselectedAccountId = null, project = JAPAN_PROJECT)

        viewModel.update(state.form!!.withProject(KITCHEN_PROJECT))
        assertThat(state.form!!.project).isEqualTo(JAPAN_PROJECT)

        viewModel.update(state.form!!.withProject(null))
        assertThat(state.form!!.project).isEqualTo(JAPAN_PROJECT)
    }

    @Test
    fun `a locked form cannot repeat`()
    {
        viewModel.open(listOf(account), preselectedAccountId = null, project = JAPAN_PROJECT)

        assertThat(state.form!!.canRepeat).isFalse()
        assertThat(state.form!!.withRepeat(true).repeat).isNull()
        assertThat(state.form!!.withRepeat(true).project).isEqualTo(JAPAN_PROJECT)
    }

    @Test
    fun `a locked form cannot become a transfer, but an income is fine`()
    {
        viewModel.open(listOf(account), preselectedAccountId = null, project = JAPAN_PROJECT)

        viewModel.update(state.form!!.withType(TransactionFormType.TRANSFER))
        assertThat(state.form!!.type).isEqualTo(TransactionFormType.EXPENSE)
        assertThat(state.form!!.project).isEqualTo(JAPAN_PROJECT)

        viewModel.update(state.form!!.withType(TransactionFormType.INCOME))
        assertThat(state.form!!.type).isEqualTo(TransactionFormType.INCOME)
        assertThat(state.form!!.project).isEqualTo(JAPAN_PROJECT)
    }

    @Test
    fun `a locked form offers no new project`()
    {
        viewModel.open(listOf(account), preselectedAccountId = null, project = JAPAN_PROJECT)

        viewModel.askToCreateProject()

        assertThat(state.newProject).isNull()
    }

    @Test
    fun `a locked form still records its transaction in the project`()
    {
        val form = TransactionFormState.initial(listOf(account), account.id, MOMENT, JAPAN_PROJECT)
            .copy(amountText = "40", title = "Hotel")

        val command = (form.submit() as FormSubmission.Record).command

        assertThat(command.projectId).isEqualTo(JAPAN_PROJECT.id)
    }

    @Test
    fun `editing a transaction from a project page is not locked`()
    {
        val transaction = com.kyovo.cents.domain.model.Transaction.recorded(
            com.kyovo.cents.domain.model.TransactionId(UUID.randomUUID()), account.id,
            com.kyovo.cents.domain.model.Money(4_000), com.kyovo.cents.domain.model.TransactionTitle("Hotel"),
            com.kyovo.cents.domain.model.RecordableTransactionCategory.EXPENSE, null, null, MOMENT, JAPAN_PROJECT.id,
        )

        val form = TransactionFormState.editing(transaction, null, JAPAN_PROJECT)

        assertThat(form.projectLocked).isFalse()
        assertThat(form.withProject(KITCHEN_PROJECT).project).isEqualTo(KITCHEN_PROJECT)
    }

    @Test
    fun `the initial state carries the project`()
    {
        val form = TransactionFormState.initial(listOf(account), null, MOMENT, JAPAN_PROJECT)

        assertThat(form.project).isEqualTo(JAPAN_PROJECT)
        assertThat(TransactionFormState.initial(listOf(account), null, MOMENT).project).isNull()
    }
}
