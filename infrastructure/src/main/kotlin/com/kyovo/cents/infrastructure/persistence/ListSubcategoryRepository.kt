package com.kyovo.cents.infrastructure.persistence

import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.port.output.SubcategoryRepository

class ListSubcategoryRepository : SubcategoryRepository
{
    private val subcategories = mutableListOf<Subcategory>()

    override fun save(subcategory: Subcategory)
    {
        // An existing subcategory is replaced where it stands (a rename must not reshuffle storage).
        val index = subcategories.indexOfFirst { it.id == subcategory.id }
        if (index >= 0) subcategories[index] = subcategory else subcategories.add(subcategory)
    }

    override fun findById(id: SubcategoryId): Subcategory?
    {
        return subcategories.find { it.id == id }
    }

    override fun findAll(): List<Subcategory>
    {
        return subcategories.toList()
    }

    override fun deleteById(id: SubcategoryId)
    {
        subcategories.removeAll { it.id == id }
    }

    internal fun snapshot(): List<Subcategory>
    {
        return subcategories.toList()
    }

    internal fun restore(snapshot: List<Subcategory>)
    {
        subcategories.clear()
        subcategories.addAll(snapshot)
    }
}
