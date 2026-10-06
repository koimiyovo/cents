package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.Emoji
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.SubcategoryName

data class CreateSubcategoryCommand(
    val kind: RecordableTransactionCategory,
    val name: SubcategoryName,
    val emoji: Emoji? = null
)
{
    fun toSubcategory(id: SubcategoryId): Subcategory
    {
        return Subcategory(id, kind, name, emoji)
    }
}
