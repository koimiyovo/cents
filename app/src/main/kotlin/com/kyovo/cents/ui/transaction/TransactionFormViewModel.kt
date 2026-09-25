package com.kyovo.cents.ui.transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyovo.cents.data.DataRevision
import com.kyovo.cents.domain.exception.AccountNotFoundException
import com.kyovo.cents.domain.exception.CannotDeleteInitialDepositException
import com.kyovo.cents.domain.exception.CannotDeleteTransferException
import com.kyovo.cents.domain.exception.CannotRecordTransactionOnArchivedAccountException
import com.kyovo.cents.domain.exception.CannotUpdateInitialDepositException
import com.kyovo.cents.domain.exception.CannotUpdateTransferException
import com.kyovo.cents.domain.exception.DuplicateSubcategoryNameException
import com.kyovo.cents.domain.exception.InvalidSubcategoryEmojiException
import com.kyovo.cents.domain.exception.InvalidSubcategoryNameException
import com.kyovo.cents.domain.exception.TransactionNotFoundException
import com.kyovo.cents.domain.exception.TransferToSameAccountException
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryEmoji
import com.kyovo.cents.domain.model.SubcategoryName
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.port.input.CreateSubcategoryCommand
import com.kyovo.cents.domain.port.input.CreateSubcategoryUseCase
import com.kyovo.cents.domain.port.input.DeleteTransactionUseCase
import com.kyovo.cents.domain.port.input.RecordTransactionUseCase
import com.kyovo.cents.domain.port.input.RecordTransferUseCase
import com.kyovo.cents.domain.port.input.UpdateTransactionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/** Why a syntactically valid form still couldn't be saved (rules only the use cases can check). */
enum class SubmitFailure
{
    ACCOUNT_NOT_FOUND,
    ARCHIVED_ACCOUNT,
    SAME_ACCOUNT,

    /** Editing: the transaction is gone, or is one the domain won't let be changed. */
    TRANSACTION_UNAVAILABLE,
}

/**
 * What the deletion dialog says about the transaction it is about to erase: taken from the
 * transaction as it was when the edit began, not from the fields the user may have changed since.
 */
data class TransactionToDelete(val title: String, val signedAmountCents: Long)

/** Why the new subcategory is refused. */
enum class NewSubcategoryError
{
    NAME_REQUIRED,

    /** A subcategory of the same kind already has that name. */
    NAME_TAKEN,

    /** Not reachable from the picker (a fixed list of valid emojis), but the domain's "no" must be an answer, not a crash. */
    EMOJI_INVALID,
}

/**
 * What is being filled in the "new subcategory" dialog: the name, the emoji picked (none by default),
 * and what is wrong with it, if anything.
 */
data class NewSubcategoryDraft(
    val name: String = "",
    val error: NewSubcategoryError? = null,
    val emoji: String? = null,
)

/**
 * [form] is null while the sheet is closed, so "is the sheet open" and its content can't disagree.
 * [confirmingDelete] is set while the user is being asked whether to delete the edited transaction.
 * [newSubcategory] is set while the dialog to create a subcategory (from the form's dropdown) is up.
 */
data class TransactionFormUiState(
    val form: TransactionFormState? = null,
    val showErrors: Boolean = false,
    val failure: SubmitFailure? = null,
    val confirmingDelete: TransactionToDelete? = null,
    val newSubcategory: NewSubcategoryDraft? = null,
)

/**
 * Holds the transaction form across configuration changes: the Activity (and its composables) is
 * destroyed and recreated on rotation, a ViewModel is not — so what the user typed, and whether the
 * sheet is open, survive it. The screen only renders [uiState] and reports events back; nothing here
 * touches Compose or Android, which keeps it testable as plain Kotlin.
 *
 * The use cases are synchronous because storage is in memory. Once they hit Room they become
 * `suspend` and [submit] will launch them in `viewModelScope`.
 */
class TransactionFormViewModel(
    private val recordTransaction: RecordTransactionUseCase,
    private val recordTransfer: RecordTransferUseCase,
    private val updateTransaction: UpdateTransactionUseCase,
    private val deleteTransaction: DeleteTransactionUseCase,
    private val createSubcategory: CreateSubcategoryUseCase,
    private val dataRevision: DataRevision,
    private val now: () -> Instant = { Instant.now() },
) : ViewModel()
{
    private val _uiState = MutableStateFlow(TransactionFormUiState())
    val uiState: StateFlow<TransactionFormUiState> = _uiState.asStateFlow()

    /** The transaction being edited, as it was when the edit began (what a deletion would erase). */
    private var editedTransaction: Transaction? = null

    /** An account the user asked to create from the form: for which field, and which accounts existed then. */
    private class AccountRequest(val field: AccountField, val knownIds: Set<AccountId>)

    private var accountRequest: AccountRequest? = null

    /** Opens a fresh form. Called when the user asks for a new transaction, never on recomposition. */
    fun open(accounts: List<Account>, preselectedAccountId: AccountId?)
    {
        editedTransaction = null
        accountRequest = null
        // With a single active account there is nothing to choose: skip the step.
        val onlyAccountId = selectableAccounts(accounts).singleOrNull()?.id
        _uiState.value = TransactionFormUiState(
            form = TransactionFormState.initial(
                accounts = accounts,
                preselectedAccountId = preselectedAccountId ?: onlyAccountId,
                now = now(),
            ),
        )
    }

    /**
     * Opens the form pre-filled with [transaction]'s values, to change them. [subcategory] is the one
     * it points to (null when it has none).
     */
    fun openForEdit(transaction: Transaction, subcategory: Subcategory?)
    {
        // Only incomes and expenses can be edited; anything else (a transfer, an opening deposit) is ignored.
        if (!canEditTransaction(transaction)) return
        editedTransaction = transaction
        accountRequest = null
        _uiState.value = TransactionFormUiState(form = TransactionFormState.editing(transaction, subcategory))
    }

    /**
     * The user asked to create an account from the form, for [field]. Remembers which [accounts] exist
     * now, so that the one that appears next can be recognised (see [accountsChanged]).
     */
    fun askToCreateAccount(field: AccountField, accounts: List<Account>)
    {
        accountRequest = AccountRequest(field, accounts.map { it.id }.toSet())
    }

    /**
     * The accounts changed while the form is open. If an account was asked for and a new active one has
     * appeared, the form chooses it for the field it was asked for; and if that leaves a single account to
     * choose from with none chosen yet, the form takes it.
     */
    fun accountsChanged(accounts: List<Account>)
    {
        val form = _uiState.value.form ?: return
        var updated = form

        val request = accountRequest
        if (request != null)
        {
            val created = selectableAccounts(accounts).filter { it.id !in request.knownIds }
            if (created.size == 1)
            {
                updated = updated.withAccountSelected(request.field, created.single().id)
                accountRequest = null
            }
        }

        updated = updated.withSoleAccountSelected(accounts)
        _uiState.update { if (it.form == null) it else it.copy(form = updated) }
    }

    fun update(form: TransactionFormState)
    {
        _uiState.update { if (it.form == null) it else it.copy(form = form, failure = null) }
    }

    fun close()
    {
        editedTransaction = null
        accountRequest = null
        _uiState.value = TransactionFormUiState()
    }

    /** Opens the "new subcategory" dialog. Nothing to do for a transfer, which has no subcategory. */
    fun askToCreateSubcategory()
    {
        if (_uiState.value.form?.subcategoryKind == null) return
        _uiState.update { it.copy(newSubcategory = NewSubcategoryDraft()) }
    }

    fun updateNewSubcategoryName(name: String)
    {
        _uiState.update { state -> state.copy(newSubcategory = state.newSubcategory?.copy(name = name, error = null)) }
    }

    /** Picks the emoji of the subcategory being created; null removes the one picked. */
    fun selectNewSubcategoryEmoji(emoji: String?)
    {
        _uiState.update { state -> state.copy(newSubcategory = state.newSubcategory?.copy(emoji = emoji, error = null)) }
    }

    fun dismissNewSubcategory()
    {
        _uiState.update { it.copy(newSubcategory = null) }
    }

    /**
     * Creates the subcategory, of the kind the form records (with the emoji picked, if any), and selects it in the form. A blank or
     * already-used name leaves the dialog open and says why; on success the lists refresh so the
     * new subcategory shows up in every dropdown.
     */
    fun confirmNewSubcategory()
    {
        val state = _uiState.value
        val kind = state.form?.subcategoryKind ?: return
        val draft = state.newSubcategory ?: return

        val created = try
        {
            createSubcategory.create(
                CreateSubcategoryCommand(kind, SubcategoryName(draft.name), draft.emoji?.let { SubcategoryEmoji(it) }),
            )
        } catch (_: InvalidSubcategoryNameException)
        {
            _uiState.update { it.copy(newSubcategory = draft.copy(error = NewSubcategoryError.NAME_REQUIRED)) }
            return
        } catch (_: DuplicateSubcategoryNameException)
        {
            _uiState.update { it.copy(newSubcategory = draft.copy(error = NewSubcategoryError.NAME_TAKEN)) }
            return
        } catch (_: InvalidSubcategoryEmojiException)
        {
            _uiState.update { it.copy(newSubcategory = draft.copy(error = NewSubcategoryError.EMOJI_INVALID)) }
            return
        }

        dataRevision.bump()
        _uiState.update { it.copy(form = it.form?.copy(subcategory = created), newSubcategory = null) }
    }

    /** Asks for confirmation before deleting the edited transaction. Does nothing on a new one. */
    fun askToDelete()
    {
        val original = editedTransaction ?: return
        if (_uiState.value.form?.editingId != original.id) return
        _uiState.update {
            it.copy(confirmingDelete = TransactionToDelete(original.title.value, original.signedAmount))
        }
    }

    fun dismissDeleteConfirmation()
    {
        _uiState.update { it.copy(confirmingDelete = null) }
    }

    /**
     * Deletes the edited transaction, once the user has confirmed. On success everything closes and
     * the lists refresh; if the domain refuses, the form stays open and says so.
     */
    fun confirmDelete()
    {
        val id = _uiState.value.form?.editingId ?: return
        try
        {
            deleteTransaction.delete(id)
        } catch (_: CannotDeleteInitialDepositException)
        {
            _uiState.update { it.copy(confirmingDelete = null, failure = SubmitFailure.TRANSACTION_UNAVAILABLE) }
            return
        } catch (_: CannotDeleteTransferException)
        {
            // Not reachable from this form (it only opens incomes and expenses), but the domain
            // says no to a transfer leg, and that must be an answer on screen, not a crash.
            _uiState.update { it.copy(confirmingDelete = null, failure = SubmitFailure.TRANSACTION_UNAVAILABLE) }
            return
        }
        dataRevision.bump()
        close()
    }

    /** Saves the form. On success the sheet closes; otherwise the state says what to show. */
    fun submit()
    {
        // A transfer is recorded by a suspend use case: the whole save runs in the view model's scope.
        viewModelScope.launch { save() }
    }

    private suspend fun save()
    {
        val form = _uiState.value.form ?: return
        val submission = form.stampedAt(now()).submit()
        try
        {
            when (submission)
            {
                is FormSubmission.Invalid  ->
                {
                    _uiState.update { it.copy(showErrors = true) }
                    return
                }

                is FormSubmission.Record   -> recordTransaction.record(submission.command)
                is FormSubmission.Transfer -> recordTransfer.record(submission.command)
                is FormSubmission.Update   -> updateTransaction.update(submission.command)
            }
        } catch (_: AccountNotFoundException)
        {
            _uiState.update { it.copy(failure = SubmitFailure.ACCOUNT_NOT_FOUND) }
            return
        } catch (_: CannotRecordTransactionOnArchivedAccountException)
        {
            _uiState.update { it.copy(failure = SubmitFailure.ARCHIVED_ACCOUNT) }
            return
        } catch (_: TransferToSameAccountException)
        {
            _uiState.update { it.copy(failure = SubmitFailure.SAME_ACCOUNT) }
            return
        } catch (_: TransactionNotFoundException)
        {
            _uiState.update { it.copy(failure = SubmitFailure.TRANSACTION_UNAVAILABLE) }
            return
        } catch (_: CannotUpdateInitialDepositException)
        {
            _uiState.update { it.copy(failure = SubmitFailure.TRANSACTION_UNAVAILABLE) }
            return
        } catch (_: CannotUpdateTransferException)
        {
            // Same as for deleting: not reachable from this form, but never a crash.
            _uiState.update { it.copy(failure = SubmitFailure.TRANSACTION_UNAVAILABLE) }
            return
        }

        // Bump only after the write went through, then close: the lists re-read as the sheet leaves.
        dataRevision.bump()
        close()
    }
}
