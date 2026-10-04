package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.ProjectId

interface DeleteProjectUseCase
{
    suspend fun delete(id: ProjectId)
}