package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.Project
import kotlinx.coroutines.flow.Flow

interface ListProjectsUseCase
{
    fun observe(): Flow<List<Project>>
}
