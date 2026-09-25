package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.port.input.DeleteSubcategoryUseCase
import com.kyovo.cents.domain.port.output.SubcategoryRepository
import com.kyovo.cents.domain.port.output.TransactionRepository
import com.kyovo.cents.domain.port.output.UnitOfWork

class DeleteSubcategoryService(
    private val subcategoryRepository: SubcategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val unitOfWork: UnitOfWork
) : DeleteSubcategoryUseCase
{
    override suspend fun delete(id: SubcategoryId)
    {
        // One all-or-nothing step: never a deleted subcategory whose transactions still point to it,
        // nor transactions stripped of a subcategory that is still there.
        unitOfWork.execute {
            if (subcategoryRepository.findById(id) != null)
            {
                transactionRepository.findAll()
                    .filter { it.subcategoryId == id }
                    .forEach { transactionRepository.save(it.withoutSubcategory()) }
                subcategoryRepository.deleteById(id)
            }
        }
    }
}
