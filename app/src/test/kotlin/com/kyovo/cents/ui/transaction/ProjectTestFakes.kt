package com.kyovo.cents.ui.transaction

import com.kyovo.cents.domain.model.Project
import com.kyovo.cents.domain.model.ProjectId
import com.kyovo.cents.domain.port.input.CreateProjectCommand
import com.kyovo.cents.domain.port.input.CreateProjectUseCase
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
