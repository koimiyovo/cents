package com.kyovo.cents.ui.transaction

import com.kyovo.cents.MainDispatcherExtension
import com.kyovo.cents.domain.exception.CannotRecordTransactionOnArchivedAccountException
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import com.kyovo.cents.domain.model.BudgetAlert
import com.kyovo.cents.domain.model.BudgetAlertLevel
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionCategory
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.Currency
import java.util.UUID

private val NOW = Instant.parse("2026-09-23T12:00:00Z")
private val PARIS = ZoneId.of("Europe/Paris")

private class RecordingRecord : RecordTransactionUseCase
{
    var failWith: RuntimeException? = null

    override suspend fun record(command: RecordTransactionCommand): Transaction
    {
        failWith?.let { throw it }
        return command.toTransaction(TransactionId(UUID.randomUUID()), subcategoryFor(command.subcategoryId))
    }
}

private class RecordingTransfer : RecordTransferUseCase
{
    override suspend fun record(command: RecordTransferCommand): TransferResult = TransferResult(
        Transaction.transferOut(TransactionId(UUID.randomUUID()), command.fromAccountId, command.amount, command.title, command.date),
        Transaction.transferIn(TransactionId(UUID.randomUUID()), command.toAccountId, command.amount, command.title, command.date),
    )
}

private class RecordingUpdate : UpdateTransactionUseCase
{
    override suspend fun update(command: UpdateTransactionCommand): Transaction = Transaction.recorded(
        command.id, command.accountId, command.amount, command.title, command.category,
        subcategoryFor(command.subcategoryId), command.description, command.date,
    )
}

private class RecordingDelete : DeleteTransactionUseCase
{
    override suspend fun delete(id: TransactionId) = Unit
}

private object UnusedCreateSubcategory : CreateSubcategoryUseCase
{
    override suspend fun create(command: CreateSubcategoryCommand) = error("not used")
}

/** Answers with whatever it was given, and remembers which months it was asked about. */
private class FakeCheckBudgetAlerts : CheckBudgetAlertsUseCase
{
    var alerts: List<BudgetAlert> = emptyList()
    val months = mutableListOf<YearMonth>()

    override suspend fun check(month: YearMonth): List<BudgetAlert>
    {
        months += month
        return alerts
    }
}

private fun anAlert(level: BudgetAlertLevel, month: YearMonth = YearMonth.of(2026, 9)) =
    BudgetAlert(GROCERIES_SUBCATEGORY.id, month, level)

/**
 * A saved expense can push a budget over a threshold. The form is where the user just did it, so it
 * says so at once (a snackbar, in the screen): after an expense is recorded, edited or deleted, the alerts the
 * budgets have newly reached are reported as events. The check is the same one the background worker
 * runs and it remembers what it reported, so the worker will not notify the same alert again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class TransactionFormBudgetAlertTest
{
    private val record = RecordingRecord()
    private val check = FakeCheckBudgetAlerts()
    private val viewModel = TransactionFormViewModel(
        record,
        RecordingTransfer(),
        RecordingUpdate(),
        RecordingDelete(),
        UnusedCreateSubcategory,
        check,
        now = { NOW },
        zone = PARIS,
    )

    private val checking = Account(
        AccountId(UUID.randomUUID()), AccountName("Courant"), AccountType.CHECKING,
        AccountCurrency(Currency.getInstance("EUR")), NOW,
    )
    private val savings = Account(
        AccountId(UUID.randomUUID()), AccountName("Épargne"), AccountType.CHECKING,
        AccountCurrency(Currency.getInstance("EUR")), NOW,
    )

    private val form get() = viewModel.uiState.value.form!!

    /** Everything the view model reports from now on, in order. */
    private fun TestScope.collectAlerts(): List<BudgetAlert>
    {
        val received = mutableListOf<BudgetAlert>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.budgetAlerts.toList(received) }
        return received
    }

    private fun fillExpense()
    {
        viewModel.open(listOf(checking, savings), preselectedAccountId = checking.id)
        viewModel.update(form.copy(amountText = "12,50", title = "Courses", subcategory = GROCERIES_SUBCATEGORY))
    }

    @Test
    fun `an expense that reaches a threshold reports the alert`() = runTest()
    {
        // GIVEN
        val received = collectAlerts()
        check.alerts = listOf(anAlert(BudgetAlertLevel.CLOSE_TO_LIMIT))
        fillExpense()

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(received).containsExactly(anAlert(BudgetAlertLevel.CLOSE_TO_LIMIT))
    }

    @Test
    fun `an expense that reaches no new threshold reports nothing`() = runTest()
    {
        // GIVEN
        val received = collectAlerts()
        fillExpense()

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(check.months).hasSize(1)
        assertThat(received).isEmpty()
    }

    @Test
    fun `every new alert is reported, in the order the check gave them`() = runTest()
    {
        // GIVEN
        val received = collectAlerts()
        val close = anAlert(BudgetAlertLevel.CLOSE_TO_LIMIT)
        val over = BudgetAlert(FUEL_SUBCATEGORY.id, YearMonth.of(2026, 9), BudgetAlertLevel.OVER)
        check.alerts = listOf(close, over)
        fillExpense()

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(received).containsExactly(close, over)
    }

    @Test
    fun `an alert reported before anyone listens is not lost`() = runTest()
    {
        // GIVEN: the screen is away (rotation) while the expense is saved
        check.alerts = listOf(anAlert(BudgetAlertLevel.OVER))
        fillExpense()
        viewModel.submit()

        // WHEN
        val received = collectAlerts()

        // THEN
        assertThat(received).containsExactly(anAlert(BudgetAlertLevel.OVER))
    }

    @Test
    fun `the month checked is the one of the transaction's date, in the device's zone`() = runTest()
    {
        // GIVEN: 23:30 UTC on 31 August is already 1 September in Paris
        fillExpense()
        viewModel.update(form.copy(date = Instant.parse("2026-08-31T23:30:00Z")))

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(check.months).containsExactly(YearMonth.of(2026, 9))
    }

    @Test
    fun `an expense dated in a past month checks that month, not the current one`() = runTest()
    {
        // GIVEN
        fillExpense()
        viewModel.update(form.copy(date = Instant.parse("2026-07-10T10:00:00Z")))

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(check.months).containsExactly(YearMonth.of(2026, 7))
    }

    @Test
    fun `editing an expense checks too, since a bigger amount can cross a threshold`() = runTest()
    {
        // GIVEN
        val received = collectAlerts()
        check.alerts = listOf(anAlert(BudgetAlertLevel.OVER))
        val expense = Transaction.recorded(
            TransactionId(UUID.randomUUID()), checking.id, Money(1_250), TransactionTitle("Courses"),
            RecordableTransactionCategory.EXPENSE, GROCERIES_SUBCATEGORY, null, NOW,
        )
        viewModel.openForEdit(expense, GROCERIES_SUBCATEGORY)
        viewModel.update(form.copy(amountText = "300"))

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(check.months).containsExactly(YearMonth.of(2026, 9))
        assertThat(received).containsExactly(anAlert(BudgetAlertLevel.OVER))
    }

    // Deleting the expense that raised an alert lets the check forget it (the budget is back under the
    // threshold): without this, a later real expense crossing it again would find it "already reported".
    @Test
    fun `deleting an expense checks the month it was in, so a stale alert is forgotten`() = runTest()
    {
        // GIVEN
        val expense = Transaction.recorded(
            TransactionId(UUID.randomUUID()), checking.id, Money(1_250), TransactionTitle("Courses"),
            RecordableTransactionCategory.EXPENSE, GROCERIES_SUBCATEGORY, null, Instant.parse("2026-07-10T10:00:00Z"),
        )
        viewModel.openForEdit(expense, GROCERIES_SUBCATEGORY)
        viewModel.askToDelete()

        // WHEN
        viewModel.confirmDelete()

        // THEN
        assertThat(check.months).containsExactly(YearMonth.of(2026, 7))
    }

    @Test
    fun `an alert that the check reports after a deletion is shown like any other`() = runTest()
    {
        // GIVEN: e.g. over the limit back to merely close to it
        val received = collectAlerts()
        check.alerts = listOf(anAlert(BudgetAlertLevel.CLOSE_TO_LIMIT))
        val expense = Transaction.recorded(
            TransactionId(UUID.randomUUID()), checking.id, Money(1_250), TransactionTitle("Courses"),
            RecordableTransactionCategory.EXPENSE, GROCERIES_SUBCATEGORY, null, NOW,
        )
        viewModel.openForEdit(expense, GROCERIES_SUBCATEGORY)
        viewModel.askToDelete()

        // WHEN
        viewModel.confirmDelete()

        // THEN
        assertThat(received).containsExactly(anAlert(BudgetAlertLevel.CLOSE_TO_LIMIT))
    }

    @Test
    fun `deleting an income checks nothing`() = runTest()
    {
        // GIVEN
        val income = Transaction.recorded(
            TransactionId(UUID.randomUUID()), checking.id, Money(200_000), TransactionTitle("Salaire"),
            RecordableTransactionCategory.INCOME, null, null, NOW,
        )
        viewModel.openForEdit(income, null)
        viewModel.askToDelete()

        // WHEN
        viewModel.confirmDelete()

        // THEN
        assertThat(check.months).isEmpty()
    }

    @Test
    fun `an income cannot cross a budget, so nothing is checked`() = runTest()
    {
        // GIVEN
        viewModel.open(listOf(checking, savings), preselectedAccountId = checking.id)
        viewModel.update(form.withType(TransactionFormType.INCOME).copy(amountText = "2000", title = "Salaire"))

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(check.months).isEmpty()
    }

    @Test
    fun `a transfer is not spending, so nothing is checked`() = runTest()
    {
        // GIVEN
        viewModel.open(listOf(checking, savings), preselectedAccountId = checking.id)
        viewModel.update(
            form.withType(TransactionFormType.TRANSFER).copy(toAccountId = savings.id, amountText = "50", title = "Épargne"),
        )

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(check.months).isEmpty()
    }

    @Test
    fun `a refused expense is not checked, nothing was recorded`() = runTest()
    {
        // GIVEN
        val received = collectAlerts()
        check.alerts = listOf(anAlert(BudgetAlertLevel.OVER))
        record.failWith = CannotRecordTransactionOnArchivedAccountException()
        fillExpense()

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(check.months).isEmpty()
        assertThat(received).isEmpty()
    }

    @Test
    fun `an invalid form is not checked`() = runTest()
    {
        // GIVEN
        viewModel.open(listOf(checking, savings), preselectedAccountId = checking.id)

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(check.months).isEmpty()
    }

    @Test
    fun `the sheet still closes when there is an alert`() = runTest()
    {
        // GIVEN
        check.alerts = listOf(anAlert(BudgetAlertLevel.OVER))
        fillExpense()

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(viewModel.uiState.value.form).isNull()
    }
}
