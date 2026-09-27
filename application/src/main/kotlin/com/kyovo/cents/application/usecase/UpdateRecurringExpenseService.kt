package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.exception.InvalidTransactionSubcategoryException
import com.kyovo.cents.domain.exception.RecurringExpenseNotFoundException
import com.kyovo.cents.domain.exception.SubcategoryNotFoundException
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.RecurringExpense
import com.kyovo.cents.domain.port.input.UpdateRecurringExpenseCommand
import com.kyovo.cents.domain.port.input.UpdateRecurringExpenseUseCase
import com.kyovo.cents.domain.port.output.RecurringExpenseRepository
import com.kyovo.cents.domain.port.output.SubcategoryRepository

class UpdateRecurringExpenseService(
    private val recurringExpenseRepository: RecurringExpenseRepository,
    private val subcategoryRepository: SubcategoryRepository
) : UpdateRecurringExpenseUseCase
{
    override suspend fun update(command: UpdateRecurringExpenseCommand): RecurringExpense
    {
        val existing =
            recurringExpenseRepository.findById(command.id) ?: throw RecurringExpenseNotFoundException()

        val subcategory = command.subcategoryId?.let {
            subcategoryRepository.findById(it) ?: throw SubcategoryNotFoundException()
        }
        if (subcategory != null && subcategory.kind != RecordableTransactionCategory.EXPENSE)
        {
            throw InvalidTransactionSubcategoryException()
        }

        val updated = existing.copy(
            amount = command.amount,
            title = command.title,
            subcategoryId = command.subcategoryId,
            description = command.description,
            frequency = command.frequency,
            interval = command.interval,
            endDate = command.endDate,
        )
        recurringExpenseRepository.save(updated)
        return updated
    }
}
