package com.kyovo.cents.ui.transaction

import org.junit.jupiter.api.extension.ExtendWith
import com.kyovo.cents.MainDispatcherExtension
import com.kyovo.cents.domain.exception.DuplicateSubcategoryNameException
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.model.TransactionTitle
import com.kyovo.cents.domain.model.TransferResult
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
import java.time.Instant
import java.util.Currency
import kotlin.uuid.Uuid

private val MOMENT = Instant.parse("2026-09-23T12:00:00Z")

private const val CART = "🛒"

private fun anActiveAccount() = Account(
    id = AccountId(Uuid.random()),
    name = AccountName("Compte"),
    type = AccountType.CHECKING,
    currency = AccountCurrency(Currency.getInstance("EUR")),
    createdAt = MOMENT,
)

/** Records the commands it receives; can be told to refuse the name. */
private class RecordingCreateSubcategory : CreateSubcategoryUseCase
{
    val commands = mutableListOf<CreateSubcategoryCommand>()
    var refuseName = false

    override suspend fun create(command: CreateSubcategoryCommand): Subcategory
    {
        if (refuseName) throw DuplicateSubcategoryNameException()
        commands += command
        return Subcategory(SubcategoryId(Uuid.random()), command.kind, command.name, command.emoji)
    }
}

/** Nothing here is used by these tests: creating a subcategory doesn't touch transactions. */
private val unusedRecord = object : RecordTransactionUseCase
{
    override suspend fun record(command: RecordTransactionCommand): Transaction = error("not used")
}
private val unusedTransfer = object : RecordTransferUseCase
{
    override suspend fun record(command: RecordTransferCommand): TransferResult = error("not used")
}
private val unusedUpdate = object : UpdateTransactionUseCase
{
    override suspend fun update(command: UpdateTransactionCommand): Transaction = error("not used")
}
private val unusedDelete = object : DeleteTransactionUseCase
{
    override suspend fun delete(id: TransactionId) = error("not used")
}

/**
 * The dropdown of the form offers "+ Nouvelle sous-catégorie": a name and an optional emoji typed in
 * a small dialog, a subcategory of the kind the form records (never of the other), selected right
 * away in the form.
 */
@ExtendWith(MainDispatcherExtension::class)
class TransactionFormNewSubcategoryTest
{
    private val create = RecordingCreateSubcategory()
    private val viewModel = TransactionFormViewModel(
        unusedRecord,
        unusedTransfer,
        unusedUpdate,
        unusedDelete,
        create,
        now = { MOMENT },
    )

    private val state get() = viewModel.uiState.value

    private fun openExpenseForm()
    {
        viewModel.open(listOf(anActiveAccount()), preselectedAccountId = null)
    }

    private fun type(name: String)
    {
        viewModel.updateNewSubcategoryName(name)
    }

    @Test
    fun `asking opens the dialog with an empty name, no emoji and no error`()
    {
        // GIVEN
        openExpenseForm()

        // WHEN
        viewModel.askToCreateSubcategory()

        // THEN
        assertThat(state.newSubcategory).isEqualTo(NewSubcategoryDraft())
        assertThat(state.newSubcategory!!.emoji).isNull()
    }

    @Test
    fun `nothing opens when no form is open`()
    {
        // WHEN
        viewModel.askToCreateSubcategory()

        // THEN
        assertThat(state.newSubcategory).isNull()
    }

    @Test
    fun `a transfer has no subcategory, so nothing opens`()
    {
        // GIVEN
        openExpenseForm()
        viewModel.update(state.form!!.withType(TransactionFormType.TRANSFER))

        // WHEN
        viewModel.askToCreateSubcategory()

        // THEN
        assertThat(state.newSubcategory).isNull()
    }

    @Test
    fun `creates a subcategory of the expense kind, selects it in the form and closes the dialog`()
    {
        // GIVEN
        openExpenseForm()
        viewModel.askToCreateSubcategory()
        type("  Loisirs ")

        // WHEN
        viewModel.confirmNewSubcategory()

        // THEN the name is trimmed, the kind is the form's, and the form has selected the new one
        assertThat(create.commands).hasSize(1)
        assertThat(create.commands.single().kind).isEqualTo(RecordableTransactionCategory.EXPENSE)
        assertThat(create.commands.single().name.value).isEqualTo("Loisirs")
        assertThat(state.form!!.subcategory?.name?.value).isEqualTo("Loisirs")
        assertThat(state.newSubcategory).isNull()
    }

    @Test
    fun `an income form creates an income subcategory`()
    {
        // GIVEN
        openExpenseForm()
        viewModel.update(state.form!!.withType(TransactionFormType.INCOME))
        viewModel.askToCreateSubcategory()
        type("Primes")

        // WHEN
        viewModel.confirmNewSubcategory()

        // THEN
        assertThat(create.commands.single().kind).isEqualTo(RecordableTransactionCategory.INCOME)
    }

    @Test
    fun `a blank name is refused, the dialog stays and nothing is created`()
    {
        // GIVEN
        openExpenseForm()
        viewModel.askToCreateSubcategory()
        type("   ")

        // WHEN
        viewModel.confirmNewSubcategory()

        // THEN
        assertThat(state.newSubcategory).isEqualTo(
            NewSubcategoryDraft(
                "   ",
                NewSubcategoryError.NAME_REQUIRED
            )
        )
        assertThat(create.commands).isEmpty()
    }

    @Test
    fun `a name already used is refused, the dialog stays and the form is unchanged`()
    {
        // GIVEN
        openExpenseForm()
        val formBefore = state.form
        viewModel.askToCreateSubcategory()
        type("Alimentation")
        create.refuseName = true

        // WHEN
        viewModel.confirmNewSubcategory()

        // THEN
        assertThat(state.newSubcategory).isEqualTo(
            NewSubcategoryDraft(
                "Alimentation",
                NewSubcategoryError.NAME_TAKEN
            )
        )
        assertThat(state.form).isEqualTo(formBefore)
    }

    @Test
    fun `typing again clears the error`()
    {
        // GIVEN a refused blank name
        openExpenseForm()
        viewModel.askToCreateSubcategory()
        type(" ")
        viewModel.confirmNewSubcategory()

        // WHEN
        type("Sorties")

        // THEN
        assertThat(state.newSubcategory).isEqualTo(NewSubcategoryDraft("Sorties", null))
    }

    @Test
    fun `dismissing closes the dialog without creating anything`()
    {
        // GIVEN
        openExpenseForm()
        viewModel.askToCreateSubcategory()
        type("Sorties")

        // WHEN
        viewModel.dismissNewSubcategory()

        // THEN
        assertThat(state.newSubcategory).isNull()
        assertThat(create.commands).isEmpty()
        assertThat(state.form).isNotNull()
    }

    @Test
    fun `closing the form drops the dialog with it`()
    {
        // GIVEN
        openExpenseForm()
        viewModel.askToCreateSubcategory()

        // WHEN
        viewModel.close()

        // THEN
        assertThat(state).isEqualTo(TransactionFormUiState())
    }

    @Test
    fun `the form knows which kind of subcategory it takes`()
    {
        openExpenseForm()

        assertThat(state.form!!.subcategoryKind).isEqualTo(RecordableTransactionCategory.EXPENSE)
        assertThat(state.form!!.withType(TransactionFormType.INCOME).subcategoryKind)
            .isEqualTo(RecordableTransactionCategory.INCOME)
        assertThat(state.form!!.withType(TransactionFormType.TRANSFER).subcategoryKind).isNull()
    }

    @Test
    fun `what was already typed in the form is kept`()
    {
        // GIVEN an amount already typed
        openExpenseForm()
        viewModel.update(state.form!!.copy(amountText = "12,50"))
        viewModel.askToCreateSubcategory()
        type("Sorties")

        // WHEN
        viewModel.confirmNewSubcategory()

        // THEN
        assertThat(state.form!!.amountText).isEqualTo("12,50")
    }

    @Test
    fun `the emoji picked is the one the subcategory is created with`()
    {
        // GIVEN
        openExpenseForm()
        viewModel.askToCreateSubcategory()
        type("Courses")
        viewModel.selectNewSubcategoryEmoji(CART)

        // WHEN
        viewModel.confirmNewSubcategory()

        // THEN
        assertThat(create.commands.single().emoji?.value).isEqualTo(CART)
        assertThat(state.form!!.subcategory?.emoji?.value).isEqualTo(CART)
    }

    @Test
    fun `no emoji is picked by default, and the subcategory is created without one`()
    {
        // GIVEN
        openExpenseForm()
        viewModel.askToCreateSubcategory()
        type("Courses")

        // WHEN
        viewModel.confirmNewSubcategory()

        // THEN
        assertThat(state.form!!.subcategory).isNotNull()
        assertThat(create.commands.single().emoji).isNull()
    }

    @Test
    fun `picking null removes the emoji picked before`()
    {
        // GIVEN
        openExpenseForm()
        viewModel.askToCreateSubcategory()
        viewModel.selectNewSubcategoryEmoji(CART)

        // WHEN
        viewModel.selectNewSubcategoryEmoji(null)

        // THEN
        assertThat(state.newSubcategory!!.emoji).isNull()
    }

    @Test
    fun `typing the name keeps the emoji`()
    {
        // GIVEN
        openExpenseForm()
        viewModel.askToCreateSubcategory()

        // WHEN
        viewModel.selectNewSubcategoryEmoji(CART)
        type("Courses")

        // THEN
        assertThat(state.newSubcategory).isEqualTo(NewSubcategoryDraft("Courses", null, CART))
    }

    @Test
    fun `a refused name keeps the emoji picked, so it is not lost when correcting the name`()
    {
        // GIVEN
        openExpenseForm()
        viewModel.askToCreateSubcategory()
        viewModel.selectNewSubcategoryEmoji(CART)
        type(" ")

        // WHEN
        viewModel.confirmNewSubcategory()

        // THEN
        assertThat(state.newSubcategory).isEqualTo(
            NewSubcategoryDraft(
                " ",
                NewSubcategoryError.NAME_REQUIRED,
                CART
            )
        )
    }

    // The picker only offers valid emojis; a blank one is the domain's "no", answered on screen.
    @Test
    fun `an emoji the domain refuses is an error on screen, not a crash, and nothing is created`()
    {
        // GIVEN
        openExpenseForm()
        viewModel.askToCreateSubcategory()
        type("Courses")
        viewModel.selectNewSubcategoryEmoji("  ")

        // WHEN
        viewModel.confirmNewSubcategory()

        // THEN
        assertThat(state.newSubcategory!!.error).isEqualTo(NewSubcategoryError.EMOJI_INVALID)
        assertThat(create.commands).isEmpty()
    }

    private fun anExistingExpense() = Transaction.recorded(
        id = TransactionId(Uuid.random()),
        accountId = AccountId(Uuid.random()),
        amount = Money(1_250),
        title = TransactionTitle("Courses"),
        category = RecordableTransactionCategory.EXPENSE,
        subcategory = GROCERIES_SUBCATEGORY,
        description = null,
        date = MOMENT,
    )

    @Test
    fun `an expense subcategory just created does not follow the form to an income`()
    {
        // GIVEN a subcategory created from an expense form
        openExpenseForm()
        viewModel.askToCreateSubcategory()
        type("Sorties")
        viewModel.confirmNewSubcategory()

        // WHEN the user switches to an income
        viewModel.update(state.form!!.withType(TransactionFormType.INCOME))

        // THEN the subcategory doesn't stay selected: it is of the other kind
        assertThat(state.form!!.subcategory).isNull()
    }

    @Test
    fun `creating from an edit form selects it and keeps editing the same transaction`()
    {
        // GIVEN a transaction opened for editing
        val transaction = anExistingExpense()
        viewModel.openForEdit(transaction)
        viewModel.askToCreateSubcategory()
        type("Sorties")

        // WHEN
        viewModel.confirmNewSubcategory()

        // THEN the new subcategory replaces the one it had, in the form only
        assertThat(state.form!!.subcategory?.name?.value).isEqualTo("Sorties")
        assertThat(state.form!!.editingId).isEqualTo(transaction.id)
        // and what a deletion would ask about is still the stored transaction
        viewModel.askToDelete()
        assertThat(state.confirmingDelete).isEqualTo(TransactionToDelete("Courses", -1_250))
    }

    @Test
    fun `confirming without a dialog open does nothing`()
    {
        // GIVEN
        openExpenseForm()

        // WHEN
        viewModel.confirmNewSubcategory()

        // THEN
        assertThat(create.commands).isEmpty()
        assertThat(state.newSubcategory).isNull()
    }

    @Test
    fun `typing a name or picking an emoji without a dialog open does nothing`()
    {
        // GIVEN
        openExpenseForm()

        // WHEN
        type("Sorties")
        viewModel.selectNewSubcategoryEmoji(CART)

        // THEN no dialog appears by itself
        assertThat(state.newSubcategory).isNull()
    }
}
