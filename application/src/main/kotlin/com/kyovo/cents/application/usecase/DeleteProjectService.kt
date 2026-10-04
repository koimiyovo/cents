package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.model.ProjectId
import com.kyovo.cents.domain.port.input.DeleteProjectUseCase
import com.kyovo.cents.domain.port.output.ProjectRepository
import com.kyovo.cents.domain.port.output.TransactionRepository
import com.kyovo.cents.domain.port.output.UnitOfWork

class DeleteProjectService(
    private val projectRepository: ProjectRepository,
    private val transactionRepository: TransactionRepository,
    private val unitOfWork: UnitOfWork
) : DeleteProjectUseCase
{
    override suspend fun delete(id: ProjectId)
    {
        // One all-or-nothing step: never a deleted project whose transactions still point to it.
        unitOfWork.execute {
            if (projectRepository.findById(id) != null)
            {
                transactionRepository.findAll()
                    .filter { it.projectId == id }
                    .forEach { transactionRepository.save(it.withoutProject()) }
                projectRepository.deleteById(id)
            }
        }
    }
}
