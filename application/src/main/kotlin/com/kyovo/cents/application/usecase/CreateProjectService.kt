package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.exception.DuplicateProjectNameException
import com.kyovo.cents.domain.model.Project
import com.kyovo.cents.domain.port.input.CreateProjectCommand
import com.kyovo.cents.domain.port.input.CreateProjectUseCase
import com.kyovo.cents.domain.port.output.ProjectIdGenerator
import com.kyovo.cents.domain.port.output.ProjectRepository

class CreateProjectService(
    private val projectRepository: ProjectRepository,
    private val idGenerator: ProjectIdGenerator
) : CreateProjectUseCase
{
    override suspend fun create(command: CreateProjectCommand): Project
    {
        if (projectRepository.findAll().any { it.name.matches(command.name) })
        {
            throw DuplicateProjectNameException()
        }

        val project = command.toProject(idGenerator.generate())
        projectRepository.save(project)
        return project
    }
}
