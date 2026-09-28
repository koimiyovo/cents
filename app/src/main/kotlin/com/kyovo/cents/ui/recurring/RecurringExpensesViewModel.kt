package com.kyovo.cents.ui.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyovo.cents.domain.exception.AccountNotFoundException
import com.kyovo.cents.domain.exception.InvalidTransactionSubcategoryException
import com.kyovo.cents.domain.exception.RecurringExpenseNotFoundException
import com.kyovo.cents.domain.exception.SubcategoryNotFoundException
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.port.input.CreateRecurringExpenseUseCase
import com.kyovo.cents.domain.port.input.DeleteRecurringExpenseUseCase
import com.kyovo.cents.domain.port.input.UpdateRecurringExpenseUseCase
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the deletion dialog says: the rule's own title, so the user knows what they are removing. */
data class RecurringExpenseToDelete(val title: String)

/**
 * [form] is null while the sheet is closed, so "is it open" and its content can't disagree.
 * [errors] are what is wrong with the last attempt to save; [confirmingDelete] is set while the user
 * is being asked whether to delete the rule being edited.
 */
data class RecurringExpensesUiState(
    val form: RecurringExpenseFormState? = null,
    val errors: Set<RecurringExpenseFormError> = emptySet(),
    val confirmingDelete: RecurringExpenseToDelete? = null,
)

/**
 * Holds the create/edit form and the deletion confirmation of the recurring-expense management
 * screen across configuration changes, like [com.kyovo.cents.ui.subcategory.SubcategoriesViewModel].
 * The list itself is read by the screen directly (`ListRecurringExpensesUseCase.observe`), not held
 * here — the use cases suspend, so writes are launched in `viewModelScope`.
 */
class RecurringExpensesViewModel(
    private val createRecurringExpense: CreateRecurringExpenseUseCase,
    private val updateRecurringExpense: UpdateRecurringExpenseUseCase,
    private val deleteRecurringExpense: DeleteRecurringExpenseUseCase,
) : ViewModel()
{
    private val _uiState = MutableStateFlow(RecurringExpensesUiState())
    val uiState: StateFlow<RecurringExpensesUiState> = _uiState.asStateFlow()

    /** Opens an empty form for a new rule, preselecting [accountId] when there is one obvious choice. */
    fun openCreate(accountId: AccountId?, today: LocalDate = LocalDate.now())
    {
        _uiState.value = RecurringExpensesUiState(form = RecurringExpenseFormState.creating(accountId, today))
    }

    /** Opens the form pre-filled with [row]'s rule, to change it. [subcategory] is the one it points to. */
    fun openForEdit(row: RecurringExpenseRow, subcategory: Subcategory?)
    {
        _uiState.value = RecurringExpensesUiState(
            form = RecurringExpenseFormState.editing(row.recurringExpense, subcategory, row.accountName),
        )
    }

    /** Replaces the form (an edit of a field); what was reported about the last attempt is stale. */
    fun update(form: RecurringExpenseFormState)
    {
        _uiState.update { if (it.form == null) it else it.copy(form = form, errors = emptySet()) }
    }

    fun close()
    {
        _uiState.value = RecurringExpensesUiState()
    }

    /** Saves the form. On success the sheet closes; otherwise the state says why not. */
    fun submit()
    {
        viewModelScope.launch { save() }
    }

    private suspend fun save()
    {
        val form = _uiState.value.form ?: return
        try
        {
            when (val submission = form.submit())
            {
                is RecurringExpenseSubmission.Invalid ->
                {
                    _uiState.update { it.copy(errors = submission.errors) }
                    return
                }

                is RecurringExpenseSubmission.Create -> createRecurringExpense.create(submission.command)
                is RecurringExpenseSubmission.Update  -> updateRecurringExpense.update(submission.command)
            }
        } catch (_: AccountNotFoundException)
        {
            _uiState.update { it.copy(errors = setOf(RecurringExpenseFormError.ACCOUNT_GONE)) }
            return
        } catch (_: SubcategoryNotFoundException)
        {
            _uiState.update { it.copy(errors = setOf(RecurringExpenseFormError.SUBCATEGORY_GONE)) }
            return
        } catch (_: InvalidTransactionSubcategoryException)
        {
            _uiState.update { it.copy(errors = setOf(RecurringExpenseFormError.SUBCATEGORY_GONE)) }
            return
        } catch (_: RecurringExpenseNotFoundException)
        {
            _uiState.update { it.copy(errors = setOf(RecurringExpenseFormError.RULE_GONE)) }
            return
        }

        // Close only once the write went through.
        close()
    }

    /** Asks for confirmation before deleting the rule being edited. Does nothing on a new one. */
    fun askToDelete()
    {
        val form = _uiState.value.form ?: return
        if (form.editingId == null) return
        _uiState.update { it.copy(confirmingDelete = RecurringExpenseToDelete(form.title)) }
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
            deleteRecurringExpense.delete(id)
            close()
        }
    }
}
