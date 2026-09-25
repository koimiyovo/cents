package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.exception.DuplicateSubcategoryNameException
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.port.input.CreateSubcategoryCommand
import com.kyovo.cents.domain.port.input.CreateSubcategoryUseCase
import com.kyovo.cents.domain.port.output.SubcategoryIdGenerator
import com.kyovo.cents.domain.port.output.SubcategoryRepository

class CreateSubcategoryService(
    private val subcategoryRepository: SubcategoryRepository,
    private val idGenerator: SubcategoryIdGenerator
) : CreateSubcategoryUseCase
{
    override suspend fun create(command: CreateSubcategoryCommand): Subcategory
    {
        // Names only have to be unique among the subcategories of the same kind.
        if (subcategoryRepository.findAll()
                .filter { it.kind == command.kind }
                .any { it.name.matches(command.name) }
        )
        {
            throw DuplicateSubcategoryNameException()
        }

        val subcategory = command.toSubcategory(idGenerator.generate())
        subcategoryRepository.save(subcategory)
        return subcategory
    }
}
