package com.kyovo.cents.infrastructure.persistence

import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.port.output.SubcategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class ListSubcategoryRepository : SubcategoryRepository
{
    // A StateFlow holds the current list and tells its observers about every change: the list-based
    // stand-in for what a Room query returning a Flow will do by itself.
    private val subcategories = MutableStateFlow<List<Subcategory>>(emptyList())

    override suspend fun save(subcategory: Subcategory)
    {
        // An existing subcategory is replaced where it stands (a rename must not reshuffle storage).
        val current = subcategories.value
        val index = current.indexOfFirst { it.id == subcategory.id }
        subcategories.value = if (index >= 0) current.toMutableList().also { it[index] = subcategory } else current + subcategory
    }

    override suspend fun findById(id: SubcategoryId): Subcategory?
    {
        return subcategories.value.find { it.id == id }
    }

    override suspend fun findAll(): List<Subcategory>
    {
        return subcategories.value
    }

    override suspend fun deleteById(id: SubcategoryId)
    {
        subcategories.value = subcategories.value.filterNot { it.id == id }
    }

    override fun observeAll(): Flow<List<Subcategory>>
    {
        return subcategories
    }

    internal fun snapshot(): List<Subcategory>
    {
        return subcategories.value
    }

    /** Puts the list back as it was: the observers are told, so a screen never keeps what a rollback undid. */
    internal fun restore(snapshot: List<Subcategory>)
    {
        subcategories.value = snapshot
    }
}
