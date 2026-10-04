package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.Project

interface UpdateProjectUseCase
{
    suspend fun update(command: UpdateProjectCommand): Project
}