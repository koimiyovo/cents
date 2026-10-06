package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.exception.DuplicateProjectNameException
import com.kyovo.cents.domain.exception.ProjectNotFoundException
import com.kyovo.cents.domain.model.Project
import com.kyovo.cents.domain.port.input.UpdateProjectCommand
import com.kyovo.cents.domain.port.input.UpdateProjectUseCase
import com.kyovo.cents.domain.port.output.ProjectRepository

class UpdateProjectService(private val projectRepository: ProjectRepository) : UpdateProjectUseCase
{
    override suspend fun update(command: UpdateProjectCommand): Project
    {
        val existingProject =
            projectRepository.findById(command.id) ?: throw ProjectNotFoundException()

        // Only the *other* projects count: keeping (or re-casing) its own name is not a clash with itself.
        if (projectRepository.findAll().any { it.id != command.id && it.name.matches(command.name) })
        {
            throw DuplicateProjectNameException()
        }

        val updatedProject =
            existingProject.copy(
                name = command.name,
                emoji = command.emoji,
                target = command.target,
                alertThreshold = command.alertThreshold
            )

        projectRepository.save(updatedProject)

        return updatedProject
    }
}
