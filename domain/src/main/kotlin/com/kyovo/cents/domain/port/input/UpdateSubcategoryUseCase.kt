package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.Subcategory

interface UpdateSubcategoryUseCase
{
    suspend fun update(command: UpdateSubcategoryCommand): Subcategory
}