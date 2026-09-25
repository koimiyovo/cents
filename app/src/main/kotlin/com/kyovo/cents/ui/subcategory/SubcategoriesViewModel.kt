package com.kyovo.cents.ui.subcategory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyovo.cents.domain.exception.DuplicateSubcategoryNameException
import com.kyovo.cents.domain.exception.SubcategoryNotFoundException
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.port.input.CreateSubcategoryUseCase
import com.kyovo.cents.domain.port.input.DeleteSubcategoryUseCase
import com.kyovo.cents.domain.port.input.UpdateSubcategoryUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the deletion dialog says: which subcategory, and how many transactions will lose it (they are
 * kept, uncategorised). Taken from the row the edit began on, not from what the user has typed since.
 */
data class SubcategoryToDelete(val name: String, val transactionCount: Int)

/**
 * [form] is null while the sheet is closed, so "is it open" and its content can't disagree.
 * [errors] are what is wrong with the last attempt to save; [confirmingDelete] is set while the user
 * is being asked whether to delete the subcategory being edited. [editedTransactionCount] is how many
 * transactions the subcategory being edited has (what a rename reaches); null for a new one.
 */
data class SubcategoriesUiState(
    val form: SubcategoryFormState? = null,
    val errors: Set<SubcategoryFormError> = emptySet(),
    val confirmingDelete: SubcategoryToDelete? = null,
    val editedTransactionCount: Int? = null,
)

/**
 * Holds the create/edit form and the deletion confirmation of the subcategory management screen
 * across configuration changes, like the other form view models. Nothing here touches Compose or
 * Android. The use cases suspend, so writes are launched in `viewModelScope`; the screens observe the lists.
 */
class SubcategoriesViewModel(
    private val createSubcategory: CreateSubcategoryUseCase,
    private val updateSubcategory: UpdateSubcategoryUseCase,
    private val deleteSubcategory: DeleteSubcategoryUseCase,
) : ViewModel()
{
    private val _uiState = MutableStateFlow(SubcategoriesUiState())
    val uiState: StateFlow<SubcategoriesUiState> = _uiState.asStateFlow()

    /** The row being edited, as it was when the edit began (what a deletion would erase). */
    private var editedRow: SubcategoryRow? = null

    /** Opens an empty form for a new subcategory of [kind] (the section it is created from). */
    fun openCreate(kind: RecordableTransactionCategory)
    {
        editedRow = null
        _uiState.value = SubcategoriesUiState(form = SubcategoryFormState.creating(kind))
    }

    /** Opens the form pre-filled with [row]'s subcategory, to change its name or emoji. */
    fun openForEdit(row: SubcategoryRow)
    {
        editedRow = row
        _uiState.value = SubcategoriesUiState(
            form = SubcategoryFormState.editing(row.subcategory),
            editedTransactionCount = row.transactionCount,
        )
    }

    /** Replaces the form (an edit of a field); what was reported about the last attempt is stale. */
    fun update(form: SubcategoryFormState)
    {
        _uiState.update { if (it.form == null) it else it.copy(form = form, errors = emptySet()) }
    }

    fun close()
    {
        editedRow = null
        _uiState.value = SubcategoriesUiState()
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
                is SubcategorySubmission.Invalid ->
                {
                    _uiState.update { it.copy(errors = submission.errors) }
                    return
                }

                is SubcategorySubmission.Create  -> createSubcategory.create(submission.command)
                is SubcategorySubmission.Update  -> updateSubcategory.update(submission.command)
            }
        } catch (_: DuplicateSubcategoryNameException)
        {
            _uiState.update { it.copy(errors = setOf(SubcategoryFormError.NAME_TAKEN)) }
            return
        } catch (_: SubcategoryNotFoundException)
        {
            _uiState.update { it.copy(errors = setOf(SubcategoryFormError.SUBCATEGORY_GONE)) }
            return
        }

        // Close only once the write went through.
        close()
    }

    /** Asks for confirmation before deleting the subcategory being edited. Does nothing on a new one. */
    fun askToDelete()
    {
        val row = editedRow ?: return
        if (_uiState.value.form?.editingId != row.subcategory.id) return
        _uiState.update {
            it.copy(confirmingDelete = SubcategoryToDelete(row.subcategory.name.value, row.transactionCount))
        }
    }

    fun dismissDelete()
    {
        _uiState.update { it.copy(confirmingDelete = null) }
    }

    /**
     * Deletes the subcategory, once the user has confirmed: its transactions are kept, uncategorised.
     * Never refused. Everything closes.
     */
    fun confirmDelete()
    {
        val state = _uiState.value
        if (state.confirmingDelete == null) return
        val id = state.form?.editingId ?: return
        viewModelScope.launch {
            deleteSubcategory.delete(id)
            close()
        }
    }
}
