package com.kyovo.cents.ui.transaction

import com.kyovo.cents.MainDispatcherExtension
import com.kyovo.cents.domain.exception.ProjectNotFoundException
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import com.kyovo.cents.domain.model.BudgetAlert
import com.kyovo.cents.domain.model.BudgetAlertLevel
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.model.TransactionTitle
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
import com.kyovo.cents.ui.project.ProjectAlert
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Instant
import java.time.YearMonth
import java.util.Currency
import java.util.UUID

private val NOW = Instant.parse("2026-09-23T12:00:00Z")

private val CLOSE = BudgetAlertLevel.CLOSE_TO_LIMIT
private val OVER = BudgetAlertLevel.OVER

/** The Japan project of these tests has a 1 000 € target, kept in cents. */
private const val TARGET = 100_000L

/** Saving a transaction changes where the project stands: [effect] is what the save does to it. */
private class SavesWithEffect : RecordTransactionUseCase, UpdateTransactionUseCase
{
    var effect: () -> Unit = {}
    var failWith: RuntimeException? = null

    override suspend fun record(command: RecordTransactionCommand): Transaction
    {
        failWith?.let { throw it }
        effect()
        return command.toTransaction(TransactionId(UUID.randomUUID()), null)
    }

    override suspend fun update(command: UpdateTransactionCommand): Transaction
    {
        failWith?.let { throw it }
        effect()
        return Transaction.recorded(
            command.id, command.accountId, command.amount, command.title, command.category,
            null, command.description, command.date, command.projectId,
        )
    }
}

private class DeletesWithEffect : DeleteTransactionUseCase
{
    var effect: () -> Unit = {}

    override suspend fun delete(id: TransactionId) = effect()
}

private object NothingElseUsed : RecordTransferUseCase, CreateSubcategoryUseCase, CheckBudgetAlertsUseCase
{
    override suspend fun record(command: RecordTransferCommand): TransferResult = error("not used")
    override suspend fun create(command: CreateSubcategoryCommand): Subcategory = error("not used")
    override suspend fun check(month: YearMonth): List<BudgetAlert> = emptyList()
}

/**
 * The project's counterpart of the budget alerts: a saved transaction that brings a project up to 80 % of its
 * target, or over it, says so at once - an event the screen shows in its snackbar. Nothing is remembered between
 * saves and no worker is involved: a transaction only reaches a project through this form, so the level before
 * the save and the level after it are all it takes. An alert is told once per band reached, never when the
 * project stays where it was or goes down.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class TransactionFormProjectAlertTest
{
    private val saves = SavesWithEffect()
    private val deletes = DeletesWithEffect()
    private val progress = FakeProjectProgress()
    private val createRecurring = FakeCreateRecurring()
    private val viewModel = TransactionFormViewModel(
        saves, NothingElseUsed, saves, deletes, NothingElseUsed, NothingElseUsed,
        createRecurring,
        FakeGenerateRecurring(createRecurring),
        FakeCreateProject(),
        progress,
        getBudgetCalendar = CalendarMonths,
        now = { NOW },
    )

    private val account = Account(
        AccountId(UUID.randomUUID()), AccountName("Courant"), AccountType.CHECKING,
        AccountCurrency(Currency.getInstance("EUR")), NOW,
    )
    private val japan = JAPAN_PROJECT.copy(target = Money(TARGET))

    private val form get() = viewModel.uiState.value.form!!

    /** Everything the view model reports from now on, in order. */
    private fun TestScope.collectAlerts(): List<ProjectAlert>
    {
        val received = mutableListOf<ProjectAlert>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.projectAlerts.toList(received) }
        return received
    }

    private fun fillExpenseInJapan()
    {
        viewModel.open(listOf(account), preselectedAccountId = account.id, project = japan)
        viewModel.update(form.copy(amountText = "100", title = "Billets"))
    }

    private fun anExistingExpense(projectId: com.kyovo.cents.domain.model.ProjectId?) = Transaction.recorded(
        TransactionId(UUID.randomUUID()), account.id, Money(10_000), TransactionTitle("Billets"),
        RecordableTransactionCategory.EXPENSE, null, null, NOW, projectId,
    )

    // ------------------------------------------------------------------ crossing a band

    @Test
    fun `an expense that brings the project close to its target reports the alert`() = runTest()
    {
        val received = collectAlerts()
        progress.set(japan, netCents = 70_000)
        saves.effect = { progress.set(japan, netCents = 85_000) }
        fillExpenseInJapan()

        viewModel.submit()

        assertThat(received).containsExactly(ProjectAlert(japan, CLOSE))
    }

    @Test
    fun `an expense that takes the project straight over its target reports the over alert`() = runTest()
    {
        val received = collectAlerts()
        progress.set(japan, netCents = 70_000)
        saves.effect = { progress.set(japan, netCents = 110_000) }
        fillExpenseInJapan()

        viewModel.submit()

        assertThat(received).containsExactly(ProjectAlert(japan, OVER))
    }

    @Test
    fun `going from close to over is told again, as a new band`() = runTest()
    {
        val received = collectAlerts()
        progress.set(japan, netCents = 85_000)
        saves.effect = { progress.set(japan, netCents = 110_000) }
        fillExpenseInJapan()

        viewModel.submit()

        assertThat(received).containsExactly(ProjectAlert(japan, OVER))
    }

    // ------------------------------------------------------------------ not crossing one

    // Each project has its own threshold: 65 % is past a 60 % one and short of an 80 % one.
    @Test
    fun `the alert follows the project's own threshold`() = runTest()
    {
        val received = collectAlerts()
        val early = japan.copy(alertThreshold = com.kyovo.cents.domain.model.AlertThreshold(60))
        progress.set(early, netCents = 55_000)
        saves.effect = { progress.set(early, netCents = 65_000) }
        viewModel.open(listOf(account), preselectedAccountId = account.id, project = early)
        viewModel.update(form.copy(amountText = "100", title = "Billets"))

        viewModel.submit()

        assertThat(received).containsExactly(ProjectAlert(early, CLOSE))
    }

    @Test
    fun `65 percent is no alert for a project left at the default threshold`() = runTest()
    {
        val received = collectAlerts()
        progress.set(japan, netCents = 55_000)
        saves.effect = { progress.set(japan, netCents = 65_000) }
        fillExpenseInJapan()

        viewModel.submit()

        assertThat(received).isEmpty()
    }

    @Test
    fun `an expense that leaves the project in the same band reports nothing`() = runTest()
    {
        val received = collectAlerts()
        progress.set(japan, netCents = 85_000)
        saves.effect = { progress.set(japan, netCents = 90_000) }
        fillExpenseInJapan()

        viewModel.submit()

        assertThat(received).isEmpty()
    }

    @Test
    fun `a project already over is not told again`() = runTest()
    {
        val received = collectAlerts()
        progress.set(japan, netCents = 110_000)
        saves.effect = { progress.set(japan, netCents = 120_000) }
        fillExpenseInJapan()

        viewModel.submit()

        assertThat(received).isEmpty()
    }

    @Test
    fun `a transaction that lowers the cost, a refund, reports nothing`() = runTest()
    {
        val received = collectAlerts()
        progress.set(japan, netCents = 110_000)
        saves.effect = { progress.set(japan, netCents = 70_000) }
        fillExpenseInJapan()

        viewModel.submit()

        assertThat(received).isEmpty()
    }

    @Test
    fun `a project without a target never alerts`() = runTest()
    {
        val received = collectAlerts()
        progress.set(japan, netCents = 70_000, targetCents = null)
        saves.effect = { progress.set(japan, netCents = 900_000_000, targetCents = null) }
        fillExpenseInJapan()

        viewModel.submit()

        assertThat(received).isEmpty()
    }

    @Test
    fun `a transaction in no project reports nothing`() = runTest()
    {
        val received = collectAlerts()
        viewModel.open(listOf(account), preselectedAccountId = account.id)
        viewModel.update(form.copy(amountText = "100", title = "Billets"))

        viewModel.submit()

        assertThat(received).isEmpty()
        assertThat(progress.reads).isEmpty()
    }

    @Test
    fun `a save that is refused reports nothing`() = runTest()
    {
        val received = collectAlerts()
        progress.set(japan, netCents = 70_000)
        saves.effect = { progress.set(japan, netCents = 85_000) }
        saves.failWith = ProjectNotFoundException()
        fillExpenseInJapan()

        viewModel.submit()

        assertThat(received).isEmpty()
        assertThat(viewModel.uiState.value.failure).isEqualTo(SubmitFailure.PROJECT_NOT_FOUND)
    }

    @Test
    fun `an invalid form reports nothing and does not look at the project`() = runTest()
    {
        val received = collectAlerts()
        viewModel.open(listOf(account), preselectedAccountId = account.id, project = japan)

        viewModel.submit()

        assertThat(received).isEmpty()
        assertThat(progress.reads).isEmpty()
    }

    // ------------------------------------------------------------------ editing and deleting

    @Test
    fun `an edit that raises the project into a new band reports the alert`() = runTest()
    {
        val received = collectAlerts()
        progress.set(japan, netCents = 70_000)
        saves.effect = { progress.set(japan, netCents = 85_000) }
        viewModel.openForEdit(anExistingExpense(japan.id), null, japan)

        viewModel.submit()

        assertThat(received).containsExactly(ProjectAlert(japan, CLOSE))
    }

    @Test
    fun `moving a transaction into a project that this brings close to its target reports the alert`() = runTest()
    {
        val received = collectAlerts()
        progress.set(japan, netCents = 75_000)
        saves.effect = { progress.set(japan, netCents = 85_000) }
        viewModel.openForEdit(anExistingExpense(null), null, null)
        viewModel.update(form.withProject(japan))

        viewModel.submit()

        assertThat(received).containsExactly(ProjectAlert(japan, CLOSE))
    }

    @Test
    fun `deleting a transaction never reports an alert`() = runTest()
    {
        val received = collectAlerts()
        progress.set(japan, netCents = 70_000)
        deletes.effect = { progress.set(japan, netCents = 120_000) }
        viewModel.openForEdit(anExistingExpense(japan.id), null, japan)
        viewModel.askToDelete()

        viewModel.confirmDelete()

        assertThat(received).isEmpty()
    }

    // ------------------------------------------------------------------ delivery

    @Test
    fun `an alert reported before anyone listens is not lost`() = runTest()
    {
        progress.set(japan, netCents = 70_000)
        saves.effect = { progress.set(japan, netCents = 85_000) }
        fillExpenseInJapan()
        viewModel.submit()

        val received = collectAlerts()

        assertThat(received).containsExactly(ProjectAlert(japan, CLOSE))
    }
}
