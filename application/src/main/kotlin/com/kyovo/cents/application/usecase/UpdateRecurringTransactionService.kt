package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.exception.InvalidTransactionSubcategoryException
import com.kyovo.cents.domain.exception.RecurringTransactionNotFoundException
import com.kyovo.cents.domain.exception.SubcategoryNotFoundException
import com.kyovo.cents.domain.model.RecurringTransaction
import com.kyovo.cents.domain.port.input.UpdateRecurringTransactionCommand
import com.kyovo.cents.domain.port.input.UpdateRecurringTransactionUseCase
import com.kyovo.cents.domain.port.output.RecurringTransactionRepository
import com.kyovo.cents.domain.port.output.SubcategoryRepository

class UpdateRecurringTransactionService(
    private val recurringTransactionRepository: RecurringTransactionRepository,
    private val subcategoryRepository: SubcategoryRepository
) : UpdateRecurringTransactionUseCase
{
    override suspend fun update(command: UpdateRecurringTransactionCommand): RecurringTransaction
    {
        val existing =
            recurringTransactionRepository.findById(command.id) ?: throw RecurringTransactionNotFoundException()

        val subcategory = command.subcategoryId?.let {
            subcategoryRepository.findById(it) ?: throw SubcategoryNotFoundException()
        }
        // The rule's category is fixed: its subcategory must be of that kind.
        if (subcategory != null && subcategory.kind != existing.category)
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
        recurringTransactionRepository.save(updated)
        return updated
    }
}
