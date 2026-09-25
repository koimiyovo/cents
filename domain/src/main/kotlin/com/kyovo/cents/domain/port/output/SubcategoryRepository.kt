package com.kyovo.cents.domain.port.output

import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryId
import kotlinx.coroutines.flow.Flow

interface SubcategoryRepository
{
    /** Adds a new subcategory at the end; an existing one is replaced where it stands. */
    suspend fun save(subcategory: Subcategory)
    suspend fun findById(id: SubcategoryId): Subcategory?
    suspend fun findAll(): List<Subcategory>
    suspend fun deleteById(id: SubcategoryId)

    /**
     * Every subcategory, in stored order: emits the current list at once, then again after each change.
     * What the screens collect — the one-shot reads above are for the services.
     */
    fun observeAll(): Flow<List<Subcategory>>
}
