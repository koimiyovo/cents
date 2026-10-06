package com.kyovo.cents.ui.project

import com.kyovo.cents.MainDispatcherExtension
import com.kyovo.cents.domain.exception.DuplicateProjectNameException
import com.kyovo.cents.domain.exception.ProjectNotFoundException
import com.kyovo.cents.domain.model.AlertThreshold
import com.kyovo.cents.domain.model.Emoji
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.Project
import com.kyovo.cents.domain.model.ProjectId
import com.kyovo.cents.domain.model.ProjectName
import com.kyovo.cents.domain.model.ProjectProgress
import com.kyovo.cents.domain.port.input.CreateProjectCommand
import com.kyovo.cents.domain.port.input.CreateProjectUseCase
import com.kyovo.cents.domain.port.input.DeleteProjectUseCase
import com.kyovo.cents.domain.port.input.UpdateProjectCommand
import com.kyovo.cents.domain.port.input.UpdateProjectUseCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.UUID

private const val PLANE = "✈️"

/** Records what it is asked to create; can be told to refuse the name. */
private class RecordingCreate : CreateProjectUseCase
{
    val commands = mutableListOf<CreateProjectCommand>()
    var failWith: RuntimeException? = null

    override suspend fun create(command: CreateProjectCommand): Project
    {
        failWith?.let { throw it }
        commands += command
        return command.toProject(ProjectId(UUID.randomUUID()))
    }
}

/** Records what it is asked to update; can be told to fail. */
private class RecordingUpdate : UpdateProjectUseCase
{
    val commands = mutableListOf<UpdateProjectCommand>()
    var failWith: RuntimeException? = null

    override suspend fun update(command: UpdateProjectCommand): Project
    {
        failWith?.let { throw it }
        commands += command
        return Project(command.id, command.name, command.emoji, command.target, command.alertThreshold)
    }
}

private class RecordingDelete : DeleteProjectUseCase
{
    val deleted = mutableListOf<ProjectId>()

    override suspend fun delete(id: ProjectId)
    {
        deleted += id
    }
}

private fun aCard(name: String = "Voyage au Japon", transactions: Int = 12, target: Long? = 300_000) =
    ProjectCard(
        Project(ProjectId(UUID.randomUUID()), ProjectName(name), Emoji(PLANE), target?.let { Money(it) }),
        ProjectProgress(target?.let { Money(it) }, Money(100_000), Money(0), transactions),
    )

/**
 * Holds the create/edit form and the deletion confirmation of the projects screen across rotations,
 * like the subcategories' view model. The screen observes the list itself; this only owns the form.
 */
@ExtendWith(MainDispatcherExtension::class)
class ProjectsViewModelTest
{
    private val create = RecordingCreate()
    private val update = RecordingUpdate()
    private val delete = RecordingDelete()
    private val viewModel = ProjectsViewModel(create, update, delete)

    private val state get() = viewModel.uiState.value

    // ------------------------------------------------------------------ opening

    @Test
    fun `is closed until a form is opened`()
    {
        assertThat(state).isEqualTo(ProjectsUiState())
        assertThat(state.form).isNull()
    }

    @Test
    fun `opening a creation starts an empty form`()
    {
        viewModel.openCreate()

        assertThat(state.form).isEqualTo(ProjectFormState.creating())
        assertThat(state.errors).isEmpty()
        assertThat(state.confirmingDelete).isNull()
        assertThat(state.editedTransactionCount).isNull()
    }

    @Test
    fun `opening an edit pre-fills the form and remembers how many transactions the project has`()
    {
        val card = aCard()

        viewModel.openForEdit(card)

        assertThat(state.form).isEqualTo(ProjectFormState.editing(card.project))
        assertThat(state.editedTransactionCount).isEqualTo(12)
    }

    @Test
    fun `editing a field clears what was reported about the last attempt`()
    {
        viewModel.openCreate()
        viewModel.submit()
        assertThat(state.errors).isNotEmpty()

        viewModel.update(state.form!!.withName("Voyage"))

        assertThat(state.errors).isEmpty()
        assertThat(state.form!!.name).isEqualTo("Voyage")
    }

    @Test
    fun `closing forgets everything`()
    {
        viewModel.openForEdit(aCard())
        viewModel.askToDelete()

        viewModel.close()

        assertThat(state).isEqualTo(ProjectsUiState())
    }

    // ------------------------------------------------------------------ saving

    @Test
    fun `a valid new project is created and the sheet closes`()
    {
        viewModel.openCreate()
        viewModel.update(state.form!!.withName("Voyage au Japon").withEmoji(PLANE).withTarget("3000"))

        viewModel.submit()

        assertThat(create.commands).containsExactly(
            CreateProjectCommand(ProjectName("Voyage au Japon"), Emoji(PLANE), Money(300_000), AlertThreshold.DEFAULT)
        )
        assertThat(state.form).isNull()
    }

    @Test
    fun `the threshold of the slider is saved with the project`()
    {
        viewModel.openCreate()
        viewModel.update(state.form!!.withName("Voyage").withTarget("1000").withThreshold(60))

        viewModel.submit()

        assertThat(create.commands.single().alertThreshold).isEqualTo(AlertThreshold(60))
    }

    @Test
    fun `an invalid form shows its errors and saves nothing`()
    {
        viewModel.openCreate()

        viewModel.submit()

        assertThat(state.errors).containsExactly(ProjectFormError.NAME_REQUIRED)
        assertThat(state.form).isNotNull()
        assertThat(create.commands).isEmpty()
    }

    @Test
    fun `a name already used keeps the sheet open and says so`()
    {
        viewModel.openCreate()
        viewModel.update(state.form!!.withName("Voyage"))
        create.failWith = DuplicateProjectNameException()

        viewModel.submit()

        assertThat(state.errors).containsExactly(ProjectFormError.NAME_TAKEN)
        assertThat(state.form).isNotNull()
    }

    @Test
    fun `an edit is saved as an update and the sheet closes`()
    {
        val card = aCard()
        viewModel.openForEdit(card)
        viewModel.update(state.form!!.withName("Japon 2027").withTarget(""))

        viewModel.submit()

        assertThat(update.commands).containsExactly(
            UpdateProjectCommand(card.project.id, ProjectName("Japon 2027"), card.project.emoji, null, AlertThreshold.DEFAULT)
        )
        assertThat(create.commands).isEmpty()
        assertThat(state.form).isNull()
    }

    @Test
    fun `an edit of a project deleted meanwhile says so, and stays open`()
    {
        viewModel.openForEdit(aCard())
        update.failWith = ProjectNotFoundException()

        viewModel.submit()

        assertThat(state.errors).containsExactly(ProjectFormError.PROJECT_GONE)
        assertThat(state.form).isNotNull()
    }

    @Test
    fun `an edit that renames to a name already used says so`()
    {
        viewModel.openForEdit(aCard())
        update.failWith = DuplicateProjectNameException()

        viewModel.submit()

        assertThat(state.errors).containsExactly(ProjectFormError.NAME_TAKEN)
    }

    // ------------------------------------------------------------------ deleting

    @Test
    fun `asking to delete names the project and says how many transactions it keeps`()
    {
        viewModel.openForEdit(aCard(name = "Travaux cuisine", transactions = 7))

        viewModel.askToDelete()

        assertThat(state.confirmingDelete).isEqualTo(ProjectToDelete("Travaux cuisine", 7))
    }

    @Test
    fun `the question is about the project as it was when the edit began, not what was typed since`()
    {
        viewModel.openForEdit(aCard(name = "Travaux cuisine"))
        viewModel.update(state.form!!.withName("Autre chose"))

        viewModel.askToDelete()

        assertThat(state.confirmingDelete!!.name).isEqualTo("Travaux cuisine")
    }

    @Test
    fun `there is nothing to delete in a new project`()
    {
        viewModel.openCreate()

        viewModel.askToDelete()

        assertThat(state.confirmingDelete).isNull()
    }

    @Test
    fun `the question can be dismissed, and the form stays open`()
    {
        viewModel.openForEdit(aCard())
        viewModel.askToDelete()

        viewModel.dismissDelete()

        assertThat(state.confirmingDelete).isNull()
        assertThat(state.form).isNotNull()
    }

    @Test
    fun `confirming deletes the project and closes everything`()
    {
        val card = aCard()
        viewModel.openForEdit(card)
        viewModel.askToDelete()

        viewModel.confirmDelete()

        assertThat(delete.deleted).containsExactly(card.project.id)
        assertThat(state).isEqualTo(ProjectsUiState())
    }

    @Test
    fun `nothing is deleted without the question having been asked`()
    {
        viewModel.openForEdit(aCard())

        viewModel.confirmDelete()

        assertThat(delete.deleted).isEmpty()
        assertThat(state.form).isNotNull()
    }
}
