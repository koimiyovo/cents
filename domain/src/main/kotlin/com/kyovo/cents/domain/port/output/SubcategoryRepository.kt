package com.kyovo.cents.domain.port.output

import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryId

interface SubcategoryRepository
{
    fun save(subcategory: Subcategory)
    fun findById(id: SubcategoryId): Subcategory?
    fun findAll(): List<Subcategory>
    fun deleteById(id: SubcategoryId)
}