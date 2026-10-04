package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.Project

interface CreateProjectUseCase
{
    suspend fun create(command: CreateProjectCommand): Project
}