package com.kyovo.cents.infrastructure.persistence.room

import com.kyovo.cents.domain.model.Project
import com.kyovo.cents.domain.model.ProjectId
import com.kyovo.cents.domain.port.output.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The projects, stored in the Room database: the adapter that replaces the in-memory list. */
class RoomProjectRepository(private val dao: ProjectDao) : ProjectRepository
{
    override suspend fun save(project: Project)
    {
        dao.upsert(project.toEntity())
    }

    override suspend fun findById(id: ProjectId): Project?
    {
        return dao.findById(id.value)?.toDomain()
    }

    override suspend fun findAll(): List<Project>
    {
        return dao.findAll().map { it.toDomain() }
    }

    override suspend fun deleteById(id: ProjectId)
    {
        dao.deleteById(id.value)
    }

    override fun observeAll(): Flow<List<Project>>
    {
        return dao.observeAll().map { rows -> rows.map { it.toDomain() } }
    }
}
