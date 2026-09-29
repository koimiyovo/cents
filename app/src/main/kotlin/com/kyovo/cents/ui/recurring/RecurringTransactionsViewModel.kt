package com.kyovo.cents.ui.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyovo.cents.domain.exception.AccountNotFoundException
import com.kyovo.cents.domain.exception.InvalidTransactionSubcategoryException
import com.kyovo.cents.domain.exception.RecurringTransactionNotFoundException
import com.kyovo.cents.domain.exception.SubcategoryNotFoundException
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.port.input.CreateRecurringTransactionUseCase
import com.kyovo.cents.domain.port.input.DeleteRecurringTransactionUseCase
import com.kyovo.cents.domain.port.input.GenerateRecurringTransactionsUseCase
import com.kyovo.cents.domain.port.input.UpdateRecurringTransactionUseCase
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the deletion dialog says: the rule's own title, so the user knows what they are removing. */
data class RecurringTransactionToDelete(val title: String)

/**
 * [form] is null while the sheet is closed, so "is it open" and its content can't disagree.
 * [errors] are what is wrong with the last attempt to save; [confirmingDelete] is set while the user
 * is being asked whether to delete the rule being edited. [askNotificationPermission] is raised once a rule
 * has been created, for the screen to ask for the permission its notification needs (see
 * [RecurringTransactionsViewModel.dismissNotificationPermissionAsk]).
 */
data class RecurringTransactionsUiState(
    val form: RecurringTransactionFormState? = null,
    val errors: Set<RecurringTransactionFormError> = emptySet(),
    val confirmingDelete: RecurringTransactionToDelete? = null,
    val askNotificationPermission: Boolean = false,
)

/**
 * Holds the create/edit form and the deletion confirmation of the recurring-transaction management
 * screen across configuration changes, like [com.kyovo.cents.ui.subcategory.SubcategoriesViewModel].
 * The list itself is read by the screen directly (`ListRecurringTransactionsUseCase.observe`), not held
 * here — the use cases suspend, so writes are launched in `viewModelScope`.
 */
class RecurringTransactionsViewModel(
    private val createRecurringTransaction: CreateRecurringTransactionUseCase,
    private val updateRecurringTransaction: UpdateRecurringTransactionUseCase,
    private val deleteRecurringTransaction: DeleteRecurringTransactionUseCase,
    private val generateRecurringTransactions: GenerateRecurringTransactionsUseCase,
) : ViewModel()
{
    private val _uiState = MutableStateFlow(RecurringTransactionsUiState())
    val uiState: StateFlow<RecurringTransactionsUiState> = _uiState.asStateFlow()

    /** Opens an empty form for a new rule, preselecting [accountId] when there is one obvious choice. */
    fun openCreate(accountId: AccountId?, today: LocalDate = LocalDate.now())
    {
        _uiState.value = RecurringTransactionsUiState(form = RecurringTransactionFormState.creating(accountId, today))
    }

    /** Opens the form pre-filled with [row]'s rule, to change it. [subcategory] is the one it points to. */
    fun openForEdit(row: RecurringTransactionRow, subcategory: Subcategory?)
    {
        _uiState.value = RecurringTransactionsUiState(
            form = RecurringTransactionFormState.editing(row.recurringTransaction, subcategory, row.accountName),
        )
    }

    /** Replaces the form (an edit of a field); what was reported about the last attempt is stale. */
    fun update(form: RecurringTransactionFormState)
    {
        _uiState.update { if (it.form == null) it else it.copy(form = form, errors = emptySet()) }
    }

    fun close()
    {
        _uiState.value = RecurringTransactionsUiState()
    }

    /** Saves the form. On success the sheet closes; otherwise the state says why not. */
    fun submit()
    {
        viewModelScope.launch { save() }
    }

    private suspend fun save()
    {
        val form = _uiState.value.form ?: return
        val submission = form.submit()
        try
        {
            when (submission)
            {
                is RecurringTransactionSubmission.Invalid ->
                {
                    _uiState.update { it.copy(errors = submission.errors) }
                    return
                }

                is RecurringTransactionSubmission.Create -> createRecurringTransaction.create(submission.command)
                is RecurringTransactionSubmission.Update  -> updateRecurringTransaction.update(submission.command)
            }
        } catch (_: AccountNotFoundException)
        {
            _uiState.update { it.copy(errors = setOf(RecurringTransactionFormError.ACCOUNT_GONE)) }
            return
        } catch (_: SubcategoryNotFoundException)
        {
            _uiState.update { it.copy(errors = setOf(RecurringTransactionFormError.SUBCATEGORY_GONE)) }
            return
        } catch (_: InvalidTransactionSubcategoryException)
        {
            _uiState.update { it.copy(errors = setOf(RecurringTransactionFormError.SUBCATEGORY_GONE)) }
            return
        } catch (_: RecurringTransactionNotFoundException)
        {
            _uiState.update { it.copy(errors = setOf(RecurringTransactionFormError.RULE_GONE)) }
            return
        }

        // Close only once the write went through.
        close()

        // A new rule will notify the user the day it falls, which needs the permission: ask now, while
        // they have just asked for it. Editing one does not ask again.
        if (submission is RecurringTransactionSubmission.Create)
        {
            _uiState.update { it.copy(askNotificationPermission = true) }

            // Generation only runs at launch and once a day: without this, a rule that starts today would show
            // its transaction only at the next launch. The sheet is already closed, so this never keeps the user
            // waiting; an edit does not need it, the daily run takes care of what an edit changes.
            generateRecurringTransactions.generate()
        }
    }

    /** The screen has shown the rationale (or launched the system request): the ask is done. */
    fun dismissNotificationPermissionAsk()
    {
        _uiState.update { it.copy(askNotificationPermission = false) }
    }

    /** Asks for confirmation before deleting the rule being edited. Does nothing on a new one. */
    fun askToDelete()
    {
        val form = _uiState.value.form ?: return
        if (form.editingId == null) return
        _uiState.update { it.copy(confirmingDelete = RecurringTransactionToDelete(form.title)) }
    }

    fun dismissDelete()
    {
        _uiState.update { it.copy(confirmingDelete = null) }
    }

    /** Deletes the rule, once the user has confirmed. Transactions it already generated stay. Never refused. */
    fun confirmDelete()
    {
        val state = _uiState.value
        if (state.confirmingDelete == null) return
        val id = state.form?.editingId ?: return
        viewModelScope.launch {
            deleteRecurringTransaction.delete(id)
            close()
        }
    }
}
