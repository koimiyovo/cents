package com.kyovo.cents.application.fakes

import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryEmoji
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.SubcategoryName
import com.kyovo.cents.domain.port.input.CreateSubcategoryCommand
import com.kyovo.cents.domain.port.input.UpdateSubcategoryCommand
import com.kyovo.cents.domain.port.output.SubcategoryIdGenerator
import com.kyovo.cents.domain.port.output.SubcategoryRepository
import kotlin.uuid.Uuid

fun aSubcategoryId(value: String = "55555555-5555-5555-5555-555555555555"): SubcategoryId
{
    return SubcategoryId(Uuid.parse(value))
}

fun aSubcategory(
    id: SubcategoryId = aSubcategoryId(),
    kind: RecordableTransactionCategory = RecordableTransactionCategory.EXPENSE,
    name: SubcategoryName = SubcategoryName("Alimentation"),
    emoji: SubcategoryEmoji? = null
): Subcategory
{
    return Subcategory(id, kind, name, emoji)
}

fun aCreateSubcategoryCommand(
    kind: RecordableTransactionCategory = RecordableTransactionCategory.EXPENSE,
    name: SubcategoryName = SubcategoryName("Alimentation"),
    emoji: SubcategoryEmoji? = null
): CreateSubcategoryCommand
{
    return CreateSubcategoryCommand(kind, name, emoji)
}

fun anUpdateSubcategoryCommand(
    id: SubcategoryId = aSubcategoryId(),
    name: SubcategoryName = SubcategoryName("Alimentation"),
    emoji: SubcategoryEmoji? = null
): UpdateSubcategoryCommand
{
    return UpdateSubcategoryCommand(id, name, emoji)
}

class FixedSubcategoryIdGenerator(private val id: SubcategoryId) : SubcategoryIdGenerator
{
    override fun generate(): SubcategoryId = id
}

class InMemorySubcategoryRepository : SubcategoryRepository
{
    val saved = mutableListOf<Subcategory>()

    override fun save(subcategory: Subcategory)
    {
        val index = saved.indexOfFirst { it.id == subcategory.id }
        if (index >= 0) saved[index] = subcategory else saved.add(subcategory)
    }

    override fun findById(id: SubcategoryId): Subcategory?
    {
        return saved.find { it.id == id }
    }

    override fun findAll(): List<Subcategory>
    {
        return saved.toList()
    }

    override fun deleteById(id: SubcategoryId)
    {
        saved.removeAll { it.id == id }
    }
}
