package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Subcategory
import kotlinx.coroutines.flow.Flow

interface ListSubcategoriesUseCase
{
    /**
     * The subcategories (of [kind] only, when given) ordered by name, ascending: emits the list now and
     * again each time it changes.
     */
    fun observe(kind: RecordableTransactionCategory? = null): Flow<List<Subcategory>>
}
