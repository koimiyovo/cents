package com.kyovo.cents.ui.transaction

import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.Project
import com.kyovo.cents.domain.model.ProjectId
import com.kyovo.cents.domain.model.ProjectProgress
import com.kyovo.cents.domain.port.input.CreateProjectCommand
import com.kyovo.cents.domain.port.input.CreateProjectUseCase
import com.kyovo.cents.domain.port.input.GetProjectProgressUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

/** Records the projects it is asked to create; can be told to refuse instead. */
internal class FakeCreateProject : CreateProjectUseCase
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

/** For the tests that are not about project progress: no project has any. */
internal object NoProjectProgress : GetProjectProgressUseCase
{
    override fun observe(id: ProjectId): Flow<ProjectProgress?> = flow { emit(null) }
    override fun observeAll(): Flow<Map<ProjectId, ProjectProgress>> = flow { emit(emptyMap()) }
}

/**
 * Where the projects stand, as the tests set it. Read when asked (each [observe] emits what is there at that
 * moment), so a test can change a project between two reads - what saving a transaction does.
 */
internal class FakeProjectProgress : GetProjectProgressUseCase
{
    private val byProject = mutableMapOf<ProjectId, ProjectProgress>()

    /** Sets a project's net cost in cents against its target (null: no target), in one expense. */
    fun set(project: Project, netCents: Long, targetCents: Long? = project.target?.value)
    {
        byProject[project.id] = ProjectProgress(
            targetCents?.let { Money(it) }, Money(netCents), Money(0), 1, project.alertThreshold,
        )
    }

    val reads = mutableListOf<ProjectId>()

    override fun observe(id: ProjectId): Flow<ProjectProgress?> = flow {
        reads += id
        emit(byProject[id])
    }

    override fun observeAll(): Flow<Map<ProjectId, ProjectProgress>> = flow { emit(byProject.toMap()) }
}
