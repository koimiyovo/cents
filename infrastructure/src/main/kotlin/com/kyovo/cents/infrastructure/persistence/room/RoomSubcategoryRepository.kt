package com.kyovo.cents.infrastructure.persistence.room

import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.port.output.SubcategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The subcategories, stored in the Room database: the adapter that replaces the in-memory list. */
class RoomSubcategoryRepository(private val dao: SubcategoryDao) : SubcategoryRepository
{
    override suspend fun save(subcategory: Subcategory)
    {
        dao.upsert(subcategory.toEntity())
    }

    override suspend fun findById(id: SubcategoryId): Subcategory?
    {
        return dao.findById(id.value)?.toDomain()
    }

    override suspend fun findAll(): List<Subcategory>
    {
        return dao.findAll().map { it.toDomain() }
    }

    override suspend fun deleteById(id: SubcategoryId)
    {
        dao.deleteById(id.value)
    }

    override fun observeAll(): Flow<List<Subcategory>>
    {
        return dao.observeAll().map { rows -> rows.map { it.toDomain() } }
    }
}
