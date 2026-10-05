package com.kyovo.cents.ui.transaction

import com.kyovo.cents.MainDispatcherExtension
import com.kyovo.cents.domain.exception.AccountNotFoundException
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import com.kyovo.cents.domain.model.BudgetAlert
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.RecurrenceFrequency
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
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Currency
import java.util.UUID

private val PARIS: ZoneId = ZoneId.of("Europe/Paris")
private val NOW = Instant.parse("2026-09-23T12:00:00Z")

private val ACCOUNT = Account(
    id = AccountId(UUID.randomUUID()),
    name = AccountName("Compte courant"),
    type = AccountType.CHECKING,
    currency = AccountCurrency(Currency.getInstance("EUR")),
    createdAt = NOW,
)

/** Counts what is recorded: a repeating form must not also record the transaction itself. */
private class CountingRecord : RecordTransactionUseCase
{
    var calls = 0

    override suspend fun record(command: RecordTransactionCommand): Transaction
    {
        calls++
        return command.toTransaction(TransactionId(UUID.randomUUID()), null)
    }
}

private object Unused : RecordTransferUseCase, UpdateTransactionUseCase, DeleteTransactionUseCase,
    CreateSubcategoryUseCase, CheckBudgetAlertsUseCase
{
    override suspend fun record(command: RecordTransferCommand): TransferResult = error("not used")
    override suspend fun update(command: UpdateTransactionCommand): Transaction = error("not used")
    override suspend fun delete(id: TransactionId) = error("not used")
    override suspend fun create(command: CreateSubcategoryCommand): Subcategory = error("not used")
    override suspend fun check(month: YearMonth): List<BudgetAlert> = emptyList()
}

@ExtendWith(MainDispatcherExtension::class)
class TransactionFormRepeatViewModelTest
{
    private val record = CountingRecord()
    private val create = FakeCreateRecurring()
    private val generate = FakeGenerateRecurring(create)
    private val viewModel = TransactionFormViewModel(
        record, Unused, Unused, Unused, Unused, Unused,
        createRecurringTransaction = create,
        generateRecurringTransactions = generate,
        createProject = FakeCreateProject(),
        getProjectProgress = NoProjectProgress,
        getBudgetCalendar = CalendarMonths,
        now = { NOW },
        zone = PARIS,
    )

    private val state get() = viewModel.uiState.value

    private fun openRepeatingExpense()
    {
        viewModel.open(listOf(ACCOUNT), preselectedAccountId = ACCOUNT.id)
        viewModel.update(state.form!!.copy(amountText = "700", title = "Loyer").withRepeat(true))
    }

    @Test
    fun `a repeating form creates the rule instead of recording the transaction`()
    {
        openRepeatingExpense()

        viewModel.submit()

        assertThat(create.commands).hasSize(1)
        assertThat(create.commands.single().title.value).isEqualTo("Loyer")
        assertThat(create.commands.single().startDate).isEqualTo(LocalDate.of(2026, 9, 23))
        assertThat(create.commands.single().frequency).isEqualTo(RecurrenceFrequency.MONTHLY)
        assertThat(record.calls).isZero()
    }

    @Test
    fun `the sheet closes once the rule is created`()
    {
        openRepeatingExpense()

        viewModel.submit()

        assertThat(state.form).isNull()
    }

    @Test
    fun `the first occurrence is generated right away, after the rule exists`()
    {
        openRepeatingExpense()

        viewModel.submit()

        assertThat(generate.createdWhenCalled).containsExactly(1)
    }

    @Test
    fun `the user is asked for the notification permission, the rule will notify`()
    {
        openRepeatingExpense()

        viewModel.submit()

        assertThat(state.askNotificationPermission).isTrue()
        viewModel.dismissNotificationPermissionAsk()
        assertThat(state.askNotificationPermission).isFalse()
    }

    @Test
    fun `a plain transaction asks nothing and generates nothing`()
    {
        viewModel.open(listOf(ACCOUNT), preselectedAccountId = ACCOUNT.id)
        viewModel.update(state.form!!.copy(amountText = "12,50", title = "Courses"))

        viewModel.submit()

        assertThat(record.calls).isEqualTo(1)
        assertThat(create.commands).isEmpty()
        assertThat(generate.createdWhenCalled).isEmpty()
        assertThat(state.askNotificationPermission).isFalse()
    }

    @Test
    fun `an invalid pace keeps the sheet open and shows the errors`()
    {
        openRepeatingExpense()
        viewModel.update(state.form!!.withInterval(""))

        viewModel.submit()

        assertThat(state.form).isNotNull()
        assertThat(state.showErrors).isTrue()
        assertThat(create.commands).isEmpty()
        assertThat(generate.createdWhenCalled).isEmpty()
    }

    @Test
    fun `a refused rule keeps the sheet open, says why, and generates nothing`()
    {
        openRepeatingExpense()
        create.failWith = AccountNotFoundException()

        viewModel.submit()

        assertThat(state.form).isNotNull()
        assertThat(state.failure).isEqualTo(SubmitFailure.ACCOUNT_NOT_FOUND)
        assertThat(generate.createdWhenCalled).isEmpty()
        assertThat(state.askNotificationPermission).isFalse()
    }

    @Test
    fun `an income rule is created with the income category`()
    {
        viewModel.open(listOf(ACCOUNT), preselectedAccountId = ACCOUNT.id)
        viewModel.update(
            state.form!!.copy(amountText = "2000", title = "Salaire")
                .withType(TransactionFormType.INCOME).withRepeat(true),
        )

        viewModel.submit()

        assertThat(create.commands.single().category).isEqualTo(RecordableTransactionCategory.INCOME)
    }

    @Test
    fun `a new form opens without repetition, and does not keep the previous one`()
    {
        openRepeatingExpense()
        viewModel.close()

        viewModel.open(listOf(ACCOUNT), preselectedAccountId = ACCOUNT.id)

        assertThat(state.form!!.repeat).isNull()
    }
}
