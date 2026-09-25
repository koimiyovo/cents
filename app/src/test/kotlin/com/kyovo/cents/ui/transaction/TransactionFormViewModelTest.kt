package com.kyovo.cents.ui.transaction

import com.kyovo.cents.data.DataRevision
import com.kyovo.cents.domain.exception.AccountNotFoundException
import com.kyovo.cents.domain.exception.CannotRecordTransactionOnArchivedAccountException
import com.kyovo.cents.domain.exception.CannotDeleteInitialDepositException
import com.kyovo.cents.domain.exception.CannotDeleteTransferException
import com.kyovo.cents.domain.exception.CannotUpdateInitialDepositException
import com.kyovo.cents.domain.exception.CannotUpdateTransferException
import com.kyovo.cents.domain.exception.TransactionNotFoundException
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
import com.kyovo.cents.domain.port.input.DeleteTransactionUseCase
import com.kyovo.cents.domain.port.input.RecordTransactionCommand
import com.kyovo.cents.domain.port.input.RecordTransactionUseCase
import com.kyovo.cents.domain.port.input.RecordTransferCommand
import com.kyovo.cents.domain.port.input.RecordTransferUseCase
import com.kyovo.cents.domain.port.input.UpdateTransactionCommand
import com.kyovo.cents.domain.port.input.UpdateTransactionUseCase
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

/** Records what it is asked to update; can be told to fail instead. */
private class FakeUpdateTransaction : UpdateTransactionUseCase
{
    val commands = mutableListOf<UpdateTransactionCommand>()
    var failWith: RuntimeException? = null

    override fun update(command: UpdateTransactionCommand): Transaction
    {
        failWith?.let { throw it }
        commands += command
        return Transaction.recorded(
            command.id, command.accountId, command.amount, command.title, command.category,
            command.subcategory, command.description, command.date,
        )
    }
}

/** Records the ids it is asked to delete; can be told to refuse instead. */
private class FakeDeleteTransaction : DeleteTransactionUseCase
{
    val deleted = mutableListOf<TransactionId>()
    var failWith: RuntimeException? = null

    override fun delete(id: TransactionId)
    {
        failWith?.let { throw it }
        deleted += id
    }
}

private fun anExistingExpense(date: Instant = NOW.minusSeconds(3 * 3600)) = Transaction.recorded(
    id = TransactionId(Uuid.random()),
    accountId = AccountId(Uuid.random()),
    amount = Money(1_250),
    title = TransactionTitle("Courses"),
    category = RecordableTransactionCategory.EXPENSE,
    subcategory = ExpenseSubcategory.GROCERIES,
    description = null,
    date = date,
)

class TransactionFormViewModelTest
{
    private val recordTransaction = FakeRecordTransaction()
    private val recordTransfer = FakeRecordTransfer()
    private val revision = DataRevision()
    private val updateTransaction = FakeUpdateTransaction()
    private val deleteTransaction = FakeDeleteTransaction()
    private val viewModel = TransactionFormViewModel(
        recordTransaction, recordTransfer, updateTransaction, deleteTransaction, revision, now = { NOW },
    )

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

    @Test
    fun `opening for edit pre-fills the form from the transaction`()
    {
        // GIVEN
        val transaction = anExistingExpense()

        // WHEN
        viewModel.openForEdit(transaction)

        // THEN
        assertThat(form).isEqualTo(TransactionFormState.editing(transaction))
        assertThat(viewModel.uiState.value.showErrors).isFalse()
    }

    @Test
    fun `a transfer leg or an opening deposit is not opened for editing`()
    {
        // GIVEN
        val leg = Transaction.transferOut(TransactionId(Uuid.random()), checking.id, Money(100), TransactionTitle("Retrait"), NOW)
        val deposit = Transaction.openingDeposit(TransactionId(Uuid.random()), checking.id, Money(100), NOW)

        // WHEN
        viewModel.openForEdit(leg)
        viewModel.openForEdit(deposit)

        // THEN
        assertThat(form).isNull()
    }

    @Test
    fun `a valid edit updates the transaction instead of recording one, refreshes the lists and closes`()
    {
        // GIVEN
        val transaction = anExistingExpense()
        viewModel.openForEdit(transaction)
        viewModel.update(form!!.copy(amountText = "20"))
        val revisionBefore = revision.value.value

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(recordTransaction.commands).isEmpty()
        assertThat(updateTransaction.commands).hasSize(1)
        assertThat(updateTransaction.commands.single().id).isEqualTo(transaction.id)
        assertThat(updateTransaction.commands.single().amount).isEqualTo(Money(2_000))
        assertThat(revision.value.value).isEqualTo(revisionBefore + 1)
        assertThat(form).isNull()
    }

    @Test
    fun `saving an edit keeps the transaction's own date instead of stamping it with now`()
    {
        // GIVEN a transaction of this morning, while the clock says midday
        val morning = NOW.minusSeconds(4 * 3600)
        viewModel.openForEdit(anExistingExpense(date = morning))

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(updateTransaction.commands.single().date).isEqualTo(morning)
    }

    @Test
    fun `an edit with a blank title updates nothing, stays open and starts showing its errors`()
    {
        // GIVEN
        viewModel.openForEdit(anExistingExpense())
        viewModel.update(form!!.copy(title = " "))
        val revisionBefore = revision.value.value

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(updateTransaction.commands).isEmpty()
        assertThat(revision.value.value).isEqualTo(revisionBefore)
        assertThat(form).isNotNull()
        assertThat(viewModel.uiState.value.showErrors).isTrue()
    }

    @Test
    fun `an edit of a transaction that no longer exists keeps the sheet open and says why`()
    {
        // GIVEN
        viewModel.openForEdit(anExistingExpense())
        updateTransaction.failWith = TransactionNotFoundException()
        val revisionBefore = revision.value.value

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(viewModel.uiState.value.failure).isEqualTo(SubmitFailure.TRANSACTION_UNAVAILABLE)
        assertThat(form).isNotNull()
        assertThat(revision.value.value).isEqualTo(revisionBefore)
    }

    @Test
    fun `an edit refused because the transaction cannot be changed says the same`()
    {
        // GIVEN
        viewModel.openForEdit(anExistingExpense())
        updateTransaction.failWith = CannotUpdateInitialDepositException()

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(viewModel.uiState.value.failure).isEqualTo(SubmitFailure.TRANSACTION_UNAVAILABLE)
        assertThat(form).isNotNull()
    }

    @Test
    fun `opening a new transaction after an edit starts from a clean form`()
    {
        // GIVEN
        viewModel.openForEdit(anExistingExpense())
        viewModel.close()

        // WHEN
        viewModel.open(listOf(checking), preselectedAccountId = null)

        // THEN
        assertThat(form!!.isEditing).isFalse()
        assertThat(form!!.title).isEmpty()
    }

    @Test
    fun `an edit that changes the account moves the transaction to it`()
    {
        // GIVEN
        viewModel.openForEdit(anExistingExpense())
        viewModel.update(form!!.copy(accountId = savings.id))

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(updateTransaction.commands.single().accountId).isEqualTo(savings.id)
        assertThat(form).isNull()
    }

    @Test
    fun `moving a transaction to an archived account keeps the sheet open and says why`()
    {
        // GIVEN
        viewModel.openForEdit(anExistingExpense())
        viewModel.update(form!!.copy(accountId = archived.id))
        updateTransaction.failWith = CannotRecordTransactionOnArchivedAccountException()

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(viewModel.uiState.value.failure).isEqualTo(SubmitFailure.ARCHIVED_ACCOUNT)
        assertThat(form).isNotNull()
    }

    @Test
    fun `moving a transaction to an account that no longer exists keeps the sheet open and says why`()
    {
        // GIVEN
        viewModel.openForEdit(anExistingExpense())
        updateTransaction.failWith = AccountNotFoundException()

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(viewModel.uiState.value.failure).isEqualTo(SubmitFailure.ACCOUNT_NOT_FOUND)
        assertThat(form).isNotNull()
    }

    @Test
    fun `asking to delete an edited expense asks for confirmation, naming it and its amount`()
    {
        // GIVEN
        viewModel.openForEdit(anExistingExpense())

        // WHEN
        viewModel.askToDelete()

        // THEN an expense weighs negatively
        assertThat(viewModel.uiState.value.confirmingDelete).isEqualTo(TransactionToDelete("Courses", -1_250))
        assertThat(form).isNotNull()
        assertThat(deleteTransaction.deleted).isEmpty()
    }

    @Test
    fun `an income is named with a positive amount`()
    {
        // GIVEN
        val income = Transaction.recorded(
            id = TransactionId(Uuid.random()), accountId = checking.id, amount = Money(245_000),
            title = TransactionTitle("Salaire"), category = RecordableTransactionCategory.INCOME,
            subcategory = null, description = null, date = NOW,
        )
        viewModel.openForEdit(income)

        // WHEN
        viewModel.askToDelete()

        // THEN
        assertThat(viewModel.uiState.value.confirmingDelete).isEqualTo(TransactionToDelete("Salaire", 245_000))
    }

    @Test
    fun `the confirmation names the transaction as it was, not as the form was changed`()
    {
        // GIVEN the user retitled and re-priced it without saving, then changed their mind
        viewModel.openForEdit(anExistingExpense())
        viewModel.update(form!!.copy(title = "Autre chose", amountText = "999"))

        // WHEN
        viewModel.askToDelete()

        // THEN what would be erased is the stored transaction
        assertThat(viewModel.uiState.value.confirmingDelete).isEqualTo(TransactionToDelete("Courses", -1_250))
    }

    @Test
    fun `there is nothing to delete in a new transaction`()
    {
        // GIVEN
        viewModel.open(listOf(checking), preselectedAccountId = null)

        // WHEN
        viewModel.askToDelete()

        // THEN
        assertThat(viewModel.uiState.value.confirmingDelete).isNull()
    }

    @Test
    fun `dismissing the confirmation keeps the form open and deletes nothing`()
    {
        // GIVEN
        viewModel.openForEdit(anExistingExpense())
        viewModel.askToDelete()

        // WHEN
        viewModel.dismissDeleteConfirmation()

        // THEN
        assertThat(viewModel.uiState.value.confirmingDelete).isNull()
        assertThat(form).isNotNull()
        assertThat(deleteTransaction.deleted).isEmpty()
    }

    @Test
    fun `confirming deletes the transaction, refreshes the lists and closes everything`()
    {
        // GIVEN
        val transaction = anExistingExpense()
        viewModel.openForEdit(transaction)
        viewModel.askToDelete()
        val revisionBefore = revision.value.value

        // WHEN
        viewModel.confirmDelete()

        // THEN
        assertThat(deleteTransaction.deleted).containsExactly(transaction.id)
        assertThat(revision.value.value).isEqualTo(revisionBefore + 1)
        assertThat(form).isNull()
        assertThat(viewModel.uiState.value.confirmingDelete).isNull()
    }

    @Test
    fun `a refused deletion keeps the form open and says why`()
    {
        // GIVEN
        viewModel.openForEdit(anExistingExpense())
        viewModel.askToDelete()
        deleteTransaction.failWith = CannotDeleteInitialDepositException()
        val revisionBefore = revision.value.value

        // WHEN
        viewModel.confirmDelete()

        // THEN
        assertThat(viewModel.uiState.value.failure).isEqualTo(SubmitFailure.TRANSACTION_UNAVAILABLE)
        assertThat(viewModel.uiState.value.confirmingDelete).isNull()
        assertThat(form).isNotNull()
        assertThat(revision.value.value).isEqualTo(revisionBefore)
    }

    @Test
    fun `confirming with no form open deletes nothing`()
    {
        // WHEN
        viewModel.confirmDelete()

        // THEN
        assertThat(deleteTransaction.deleted).isEmpty()
    }

    @Test
    fun `closing the sheet drops a pending confirmation`()
    {
        // GIVEN
        viewModel.openForEdit(anExistingExpense())
        viewModel.askToDelete()

        // WHEN
        viewModel.close()

        // THEN
        assertThat(viewModel.uiState.value.confirmingDelete).isNull()
    }

    @Test
    fun `a later edit is about its own transaction, not a previous one`()
    {
        // GIVEN a first edit that was abandoned
        viewModel.openForEdit(anExistingExpense())
        viewModel.close()
        val second = Transaction.recorded(
            id = TransactionId(Uuid.random()), accountId = checking.id, amount = Money(500),
            title = TransactionTitle("Café"), category = RecordableTransactionCategory.EXPENSE,
            subcategory = null, description = null, date = NOW,
        )

        // WHEN
        viewModel.openForEdit(second)
        viewModel.askToDelete()
        viewModel.confirmDelete()

        // THEN
        assertThat(deleteTransaction.deleted).containsExactly(second.id)
    }

    @Test
    fun `an edit refused because the transaction is a transfer leg is an answer on screen, not a crash`()
    {
        // GIVEN
        viewModel.openForEdit(anExistingExpense())
        updateTransaction.failWith = CannotUpdateTransferException()
        val revisionBefore = revision.value.value

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(viewModel.uiState.value.failure).isEqualTo(SubmitFailure.TRANSACTION_UNAVAILABLE)
        assertThat(form).isNotNull()
        assertThat(revision.value.value).isEqualTo(revisionBefore)
    }

    @Test
    fun `a deletion refused because the transaction is a transfer leg is an answer on screen, not a crash`()
    {
        // GIVEN
        viewModel.openForEdit(anExistingExpense())
        viewModel.askToDelete()
        deleteTransaction.failWith = CannotDeleteTransferException()
        val revisionBefore = revision.value.value

        // WHEN
        viewModel.confirmDelete()

        // THEN
        assertThat(viewModel.uiState.value.failure).isEqualTo(SubmitFailure.TRANSACTION_UNAVAILABLE)
        assertThat(viewModel.uiState.value.confirmingDelete).isNull()
        assertThat(form).isNotNull()
        assertThat(revision.value.value).isEqualTo(revisionBefore)
    }
}
