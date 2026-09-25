package com.kyovo.cents.infrastructure.persistence.room

import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryEmoji
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.SubcategoryName

fun Subcategory.toEntity(): SubcategoryEntity
{
    return SubcategoryEntity(
        id = id.value,
        kind = kind.name,
        name = name.value,
        emoji = emoji?.value,
    )
}

fun SubcategoryEntity.toDomain(): Subcategory
{
    val kind = RecordableTransactionCategory.entries.find { it.name == kind }
        ?: throw IllegalStateException("Unknown subcategory kind in the database: $kind")
    return Subcategory(
        id = SubcategoryId(id),
        kind = kind,
        name = SubcategoryName(name),
        emoji = emoji?.let { SubcategoryEmoji(it) },
    )
}
