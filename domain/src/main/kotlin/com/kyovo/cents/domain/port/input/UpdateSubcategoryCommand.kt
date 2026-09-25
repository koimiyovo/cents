package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.SubcategoryEmoji
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.SubcategoryName

data class UpdateSubcategoryCommand(
    val id: SubcategoryId,
    val name: SubcategoryName,
    val emoji: SubcategoryEmoji?
)
