package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.Subcategory

interface UpdateSubcategoryUseCase
{
    fun update(command: UpdateSubcategoryCommand): Subcategory
}