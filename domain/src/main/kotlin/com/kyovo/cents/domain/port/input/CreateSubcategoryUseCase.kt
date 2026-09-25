package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.Subcategory

interface CreateSubcategoryUseCase
{
    fun create(command: CreateSubcategoryCommand): Subcategory
}