package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.exception.DuplicateSubcategoryNameException
import com.kyovo.cents.domain.exception.SubcategoryNotFoundException
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.port.input.UpdateSubcategoryCommand
import com.kyovo.cents.domain.port.input.UpdateSubcategoryUseCase
import com.kyovo.cents.domain.port.output.SubcategoryRepository

class UpdateSubcategoryService(
    private val subcategoryRepository: SubcategoryRepository
) : UpdateSubcategoryUseCase
{
    override fun update(command: UpdateSubcategoryCommand): Subcategory
    {
        val existing =
            subcategoryRepository.findById(command.id) ?: throw SubcategoryNotFoundException()

        // Only the *other* subcategories of the same kind count: the kind is never changed, and
        // keeping (or re-casing) its own name is not a clash with itself.
        if (subcategoryRepository.findAll()
                .filter { it.id != command.id && it.kind == existing.kind }
                .any { it.name.matches(command.name) }
        )
        {
            throw DuplicateSubcategoryNameException()
        }

        val updated = existing.copy(name = command.name, emoji = command.emoji)
        subcategoryRepository.save(updated)
        return updated
    }
}
