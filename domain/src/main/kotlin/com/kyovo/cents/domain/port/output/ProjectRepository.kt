package com.kyovo.cents.domain.port.output

import com.kyovo.cents.domain.model.Project
import com.kyovo.cents.domain.model.ProjectId
import kotlinx.coroutines.flow.Flow

interface ProjectRepository
{
    suspend fun save(project: Project)
    suspend fun findById(id: ProjectId): Project?
    suspend fun findAll(): List<Project>
    suspend fun deleteById(id: ProjectId)
    fun observeAll(): Flow<List<Project>>
}