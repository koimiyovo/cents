package com.kyovo.cents.domain.model

data class Subcategory(
    val id: SubcategoryId,
    val kind: RecordableTransactionCategory,
    val name: SubcategoryName,
    val emoji: SubcategoryEmoji?
)
