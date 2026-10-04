package com.kyovo.cents.ui.project

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyovo.cents.domain.exception.DuplicateProjectNameException
import com.kyovo.cents.domain.exception.ProjectNotFoundException
import com.kyovo.cents.domain.port.input.CreateProjectUseCase
import com.kyovo.cents.domain.port.input.DeleteProjectUseCase
import com.kyovo.cents.domain.port.input.UpdateProjectUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the deletion dialog says: which project, and how many transactions will leave it (they are kept,
 * without a project). Taken from the card the edit began on, not from what the user has typed since.
 */
data class ProjectToDelete(val name: String, val transactionCount: Int)

/**
 * [form] is null while the sheet is closed, so "is it open" and its content can't disagree. [errors] are
 * what is wrong with the last attempt to save; [confirmingDelete] is set while the user is being asked whether
 * to delete the project being edited. [editedTransactionCount] is how many transactions the project being
 * edited has; null for a new one.
 */
data class ProjectsUiState(
    val form: ProjectFormState? = null,
    val errors: Set<ProjectFormError> = emptySet(),
    val confirmingDelete: ProjectToDelete? = null,
    val editedTransactionCount: Int? = null,
)

/**
 * Holds the create/edit form and the deletion confirmation of the projects screen across configuration
 * changes, like [com.kyovo.cents.ui.subcategory.SubcategoriesViewModel]. Nothing here touches Compose or
 * Android. The use cases suspend, so writes are launched in `viewModelScope`; the screens observe the lists.
 */
class ProjectsViewModel(
    private val createProject: CreateProjectUseCase,
    private val updateProject: UpdateProjectUseCase,
    private val deleteProject: DeleteProjectUseCase,
) : ViewModel()
{
    private val _uiState = MutableStateFlow(ProjectsUiState())
    val uiState: StateFlow<ProjectsUiState> = _uiState.asStateFlow()

    /** The card being edited, as it was when the edit began (what a deletion would leave behind). */
    private var editedCard: ProjectCard? = null

    fun openCreate()
    {
        editedCard = null
        _uiState.value = ProjectsUiState(form = ProjectFormState.creating())
    }

    /** Opens the form pre-filled with [card]'s project. */
    fun openForEdit(card: ProjectCard)
    {
        editedCard = card
        _uiState.value = ProjectsUiState(
            form = ProjectFormState.editing(card.project),
            editedTransactionCount = card.transactionCount,
        )
    }

    /** Replaces the form (an edit of a field); what was reported about the last attempt is stale. */
    fun update(form: ProjectFormState)
    {
        _uiState.update { if (it.form == null) it else it.copy(form = form, errors = emptySet()) }
    }

    fun close()
    {
        editedCard = null
        _uiState.value = ProjectsUiState()
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
                is ProjectSubmission.Invalid ->
                {
                    _uiState.update { it.copy(errors = submission.errors) }
                    return
                }

                is ProjectSubmission.Create  -> createProject.create(submission.command)
                is ProjectSubmission.Update  -> updateProject.update(submission.command)
            }
        } catch (_: DuplicateProjectNameException)
        {
            _uiState.update { it.copy(errors = setOf(ProjectFormError.NAME_TAKEN)) }
            return
        } catch (_: ProjectNotFoundException)
        {
            _uiState.update { it.copy(errors = setOf(ProjectFormError.PROJECT_GONE)) }
            return
        }

        // Close only once the write went through.
        close()
    }

    /** Asks for confirmation before deleting the project being edited. Does nothing on a new one. */
    fun askToDelete()
    {
        val card = editedCard ?: return
        if (_uiState.value.form?.editingId != card.project.id) return
        _uiState.update {
            it.copy(confirmingDelete = ProjectToDelete(card.project.name.value, card.transactionCount))
        }
    }

    fun dismissDelete()
    {
        _uiState.update { it.copy(confirmingDelete = null) }
    }

    /** Deletes the project, once the user has confirmed: its transactions are kept, without a project. */
    fun confirmDelete()
    {
        val state = _uiState.value
        if (state.confirmingDelete == null) return
        val id = state.form?.editingId ?: return
        viewModelScope.launch {
            deleteProject.delete(id)
            close()
        }
    }
}
