package com.kyovo.cents.ui.transaction

import com.kyovo.cents.data.DataRevision
import com.kyovo.cents.domain.exception.AccountNotFoundException
import com.kyovo.cents.domain.exception.CannotRecordTransactionOnArchivedAccountException
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import com.kyovo.cents.domain.model.ExpenseSubcategory
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.model.TransactionTitle
import com.kyovo.cents.domain.model.TransferResult
import com.kyovo.cents.domain.port.input.RecordTransactionCommand
import com.kyovo.cents.domain.port.input.RecordTransactionUseCase
import com.kyovo.cents.domain.port.input.RecordTransferCommand
import com.kyovo.cents.domain.port.input.RecordTransferUseCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Currency
import kotlin.uuid.Uuid

private val NOW = Instant.parse("2026-09-23T12:00:00Z")

private fun anAccount(archived: Boolean = false) = Account(
    id = AccountId(Uuid.random()),
    name = AccountName("Account ${Uuid.random()}"),
    type = AccountType.CHECKING,
    currency = AccountCurrency(Currency.getInstance("EUR")),
    createdAt = NOW,
    archivedAt = if (archived) NOW else null,
)

/** Records what it is asked to save; can be told to fail instead. */
private class FakeRecordTransaction : RecordTransactionUseCase
{
    val commands = mutableListOf<RecordTransactionCommand>()
    var failWith: RuntimeException? = null

    override fun record(command: RecordTransactionCommand): Transaction
    {
        failWith?.let { throw it }
        commands += command
        return command.toTransaction(TransactionId(Uuid.random()))
    }
}

private class FakeRecordTransfer : RecordTransferUseCase
{
    val commands = mutableListOf<RecordTransferCommand>()

    override fun record(command: RecordTransferCommand): TransferResult
    {
        commands += command
        return TransferResult(
            Transaction.transferOut(TransactionId(Uuid.random()), command.fromAccountId, command.amount, command.title, command.date),
            Transaction.transferIn(TransactionId(Uuid.random()), command.toAccountId, command.amount, command.title, command.date),
        )
    }
}

class TransactionFormViewModelTest
{
    private val recordTransaction = FakeRecordTransaction()
    private val recordTransfer = FakeRecordTransfer()
    private val revision = DataRevision()
    private val viewModel = TransactionFormViewModel(recordTransaction, recordTransfer, revision, now = { NOW })

    private val checking = anAccount()
    private val savings = anAccount()
    private val archived = anAccount(archived = true)

    private val form get() = viewModel.uiState.value.form

    private fun openAndFillExpense(accountId: AccountId = checking.id)
    {
        viewModel.open(listOf(checking, savings), preselectedAccountId = accountId)
        viewModel.update(form!!.copy(amountText = "12,50", title = "Courses"))
    }

    @Test
    fun `is closed until a form is opened`()
    {
        assertThat(viewModel.uiState.value.form).isNull()
    }

    @Test
    fun `opening starts a form on the preselected account, dated now`()
    {
        // WHEN
        viewModel.open(listOf(checking, savings), preselectedAccountId = savings.id)

        // THEN
        assertThat(form!!.accountId).isEqualTo(savings.id)
        assertThat(form!!.date).isEqualTo(NOW)
        assertThat(viewModel.uiState.value.showErrors).isFalse()
    }

    @Test
    fun `opening with a single active account selects it without being asked`()
    {
        // WHEN
        viewModel.open(listOf(checking, archived), preselectedAccountId = null)

        // THEN
        assertThat(form!!.accountId).isEqualTo(checking.id)
    }

    @Test
    fun `opening with several accounts and no preselection leaves the choice to the user`()
    {
        // WHEN
        viewModel.open(listOf(checking, savings), preselectedAccountId = null)

        // THEN
        assertThat(form!!.accountId).isNull()
    }

    @Test
    fun `a valid form is recorded, the lists are told to refresh, and the sheet closes`()
    {
        // GIVEN
        openAndFillExpense()
        viewModel.update(form!!.copy(subcategory = ExpenseSubcategory.GROCERIES))
        val revisionBefore = revision.value.value

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(recordTransaction.commands).containsExactly(
            RecordTransactionCommand(
                accountId = checking.id,
                amount = Money(1250),
                title = TransactionTitle("Courses"),
                category = RecordableTransactionCategory.EXPENSE,
                subcategory = ExpenseSubcategory.GROCERIES,
                description = null,
                date = NOW,
            ),
        )
        assertThat(revision.value.value).isEqualTo(revisionBefore + 1)
        assertThat(form).isNull()
    }

    @Test
    fun `a transfer goes to the transfer use case`()
    {
        // GIVEN
        viewModel.open(listOf(checking, savings), preselectedAccountId = checking.id)
        viewModel.update(
            form!!.withType(TransactionFormType.TRANSFER)
                .copy(toAccountId = savings.id, amountText = "50", title = "Épargne"),
        )

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(recordTransfer.commands).hasSize(1)
        assertThat(recordTransfer.commands.single().fromAccountId).isEqualTo(checking.id)
        assertThat(recordTransfer.commands.single().toAccountId).isEqualTo(savings.id)
        assertThat(recordTransaction.commands).isEmpty()
    }

    @Test
    fun `an invalid form records nothing, stays open and starts showing its errors`()
    {
        // GIVEN
        viewModel.open(listOf(checking, savings), preselectedAccountId = null)
        val revisionBefore = revision.value.value

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(recordTransaction.commands).isEmpty()
        assertThat(revision.value.value).isEqualTo(revisionBefore)
        assertThat(form).isNotNull()
        assertThat(viewModel.uiState.value.showErrors).isTrue()
    }

    @Test
    fun `a day picked on purpose is saved as picked, not moved to today`()
    {
        // GIVEN
        openAndFillExpense()
        val threeDaysAgo = NOW.minusSeconds(3 * 24 * 3600)
        viewModel.update(form!!.copy(date = threeDaysAgo))

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(recordTransaction.commands.single().date).isEqualTo(threeDaysAgo)
    }

    @Test
    fun `a rule only the use case can check keeps the sheet open and says why`()
    {
        // GIVEN
        openAndFillExpense()
        recordTransaction.failWith = CannotRecordTransactionOnArchivedAccountException()
        val revisionBefore = revision.value.value

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(viewModel.uiState.value.failure).isEqualTo(SubmitFailure.ARCHIVED_ACCOUNT)
        assertThat(form).isNotNull()
        assertThat(revision.value.value).isEqualTo(revisionBefore)
    }

    @Test
    fun `a missing account is reported as such`()
    {
        // GIVEN
        openAndFillExpense()
        recordTransaction.failWith = AccountNotFoundException()

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(viewModel.uiState.value.failure).isEqualTo(SubmitFailure.ACCOUNT_NOT_FOUND)
    }

    @Test
    fun `editing the form clears a previous failure`()
    {
        // GIVEN
        openAndFillExpense()
        recordTransaction.failWith = AccountNotFoundException()
        viewModel.submit()

        // WHEN
        viewModel.update(form!!.copy(title = "Autre titre"))

        // THEN
        assertThat(viewModel.uiState.value.failure).isNull()
        assertThat(form!!.title).isEqualTo("Autre titre")
    }

    @Test
    fun `editing while closed does nothing`()
    {
        // WHEN
        viewModel.update(
            TransactionFormState(type = TransactionFormType.EXPENSE, accountId = null, date = NOW),
        )

        // THEN
        assertThat(form).isNull()
    }

    @Test
    fun `closing throws the form away, so the next one starts clean`()
    {
        // GIVEN
        openAndFillExpense()
        viewModel.submit() // valid: records and closes
        viewModel.open(listOf(checking, savings), preselectedAccountId = null)
        viewModel.update(form!!.copy(title = "Brouillon"))

        // WHEN
        viewModel.close()
        viewModel.open(listOf(checking, savings), preselectedAccountId = null)

        // THEN
        assertThat(form!!.title).isEmpty()
        assertThat(viewModel.uiState.value.showErrors).isFalse()
    }
}
