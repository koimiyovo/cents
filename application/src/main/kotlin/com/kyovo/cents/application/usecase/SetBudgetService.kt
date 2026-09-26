package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.exception.InvalidBudgetSubcategoryException
import com.kyovo.cents.domain.exception.SubcategoryNotFoundException
import com.kyovo.cents.domain.model.Budget
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.port.input.SetBudgetCommand
import com.kyovo.cents.domain.port.input.SetBudgetUseCase
import com.kyovo.cents.domain.port.output.BudgetRepository
import com.kyovo.cents.domain.port.output.SubcategoryRepository

class SetBudgetService(
    private val budgetRepository: BudgetRepository,
    private val subcategoryRepository: SubcategoryRepository
) : SetBudgetUseCase
{
    override suspend fun set(command: SetBudgetCommand): Budget
    {
        val subcategory = (subcategoryRepository.findById(command.subcategoryId)
            ?: throw SubcategoryNotFoundException())

        if (subcategory.kind != RecordableTransactionCategory.EXPENSE)
        {
            throw InvalidBudgetSubcategoryException()
        }

        val budget = command.toBudget()
        budgetRepository.save(budget)
        return budget
    }
}