package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Subcategory

interface ListSubcategoriesUseCase
{
    fun list(kind: RecordableTransactionCategory? = null): List<Subcategory>
}