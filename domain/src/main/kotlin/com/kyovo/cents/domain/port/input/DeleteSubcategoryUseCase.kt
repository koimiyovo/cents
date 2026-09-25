package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.SubcategoryId

interface DeleteSubcategoryUseCase
{
    /**
     * Never refused: the transactions that used the subcategory are kept and left uncategorised.
     * An unknown id is not an error.
     */
    fun delete(id: SubcategoryId)
}
