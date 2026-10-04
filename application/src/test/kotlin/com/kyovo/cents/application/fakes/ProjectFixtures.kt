package com.kyovo.cents.application.fakes

import com.kyovo.cents.domain.model.Emoji
import com.kyovo.cents.domain.model.AlertThreshold
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.Project
import com.kyovo.cents.domain.model.ProjectId
import com.kyovo.cents.domain.model.ProjectName
import com.kyovo.cents.domain.port.input.CreateProjectCommand
import com.kyovo.cents.domain.port.input.UpdateProjectCommand
import com.kyovo.cents.domain.port.output.ProjectIdGenerator
import com.kyovo.cents.domain.port.output.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

fun aProjectId(value: String = "66666666-6666-6666-6666-666666666666"): ProjectId
{
    return ProjectId(UUID.fromString(value))
}

fun aProject(
    id: ProjectId = aProjectId(),
    name: ProjectName = ProjectName("Voyage au Japon"),
    emoji: Emoji? = null,
    target: Money? = null,
    alertThreshold: AlertThreshold = AlertThreshold.DEFAULT
): Project
{
    return Project(id, name, emoji, target, alertThreshold)
}

fun aCreateProjectCommand(
    name: ProjectName = ProjectName("Voyage au Japon"),
    emoji: Emoji? = null,
    target: Money? = null,
    alertThreshold: AlertThreshold = AlertThreshold.DEFAULT
): CreateProjectCommand
{
    return CreateProjectCommand(name, emoji, target, alertThreshold)
}

fun anUpdateProjectCommand(
    id: ProjectId = aProjectId(),
    name: ProjectName = ProjectName("Voyage au Japon"),
    emoji: Emoji? = null,
    target: Money? = null,
    alertThreshold: AlertThreshold = AlertThreshold.DEFAULT
): UpdateProjectCommand
{
    return UpdateProjectCommand(id, name, emoji, target, alertThreshold)
}

class FixedProjectIdGenerator(private val id: ProjectId) : ProjectIdGenerator
{
    override fun generate(): ProjectId = id
}

class InMemoryProjectRepository : ProjectRepository
{
    private val state = MutableStateFlow<List<Project>>(emptyList())

    /** What is stored, in order (for the tests to look at). */
    val saved: List<Project> get() = state.value

    override suspend fun save(project: Project)
    {
        val current = state.value
        val index = current.indexOfFirst { it.id == project.id }
        state.value = if (index >= 0) current.toMutableList()
            .also { it[index] = project } else current + project
    }

    override suspend fun findById(id: ProjectId): Project?
    {
        return state.value.find { it.id == id }
    }

    override suspend fun findAll(): List<Project>
    {
        return state.value
    }

    override suspend fun deleteById(id: ProjectId)
    {
        state.value = state.value.filterNot { it.id == id }
    }

    override fun observeAll(): Flow<List<Project>>
    {
        return state
    }
}
