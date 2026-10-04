package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.ProjectId
import com.kyovo.cents.domain.model.ProjectProgress
import kotlinx.coroutines.flow.Flow

interface GetProjectProgressUseCase
{
    fun observe(id: ProjectId): Flow<ProjectProgress?>
    fun observeAll(): Flow<Map<ProjectId, ProjectProgress>>
}
