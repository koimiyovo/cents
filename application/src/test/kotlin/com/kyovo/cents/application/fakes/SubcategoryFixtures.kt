package com.kyovo.cents.application.fakes

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
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
    private val state = MutableStateFlow<List<Subcategory>>(emptyList())

    /** What is stored, in order (for the tests to look at). */
    val saved: List<Subcategory> get() = state.value

    override suspend fun save(subcategory: Subcategory)
    {
        val current = state.value
        val index = current.indexOfFirst { it.id == subcategory.id }
        state.value = if (index >= 0) current.toMutableList().also { it[index] = subcategory } else current + subcategory
    }

    override suspend fun findById(id: SubcategoryId): Subcategory?
    {
        return state.value.find { it.id == id }
    }

    override suspend fun findAll(): List<Subcategory>
    {
        return state.value
    }

    override suspend fun deleteById(id: SubcategoryId)
    {
        state.value = state.value.filterNot { it.id == id }
    }

    override fun observeAll(): Flow<List<Subcategory>>
    {
        return state
    }
}

class SequentialSubcategoryIdGenerator(private val ids: List<SubcategoryId>) : SubcategoryIdGenerator
{
    private var index = 0

    override fun generate(): SubcategoryId
    {
        return ids[index++]
    }
}
