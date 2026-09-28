package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.exception.AccountNotFoundException
import com.kyovo.cents.domain.exception.InvalidTransactionSubcategoryException
import com.kyovo.cents.domain.exception.SubcategoryNotFoundException
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.RecurringExpense
import com.kyovo.cents.domain.port.input.CreateRecurringExpenseCommand
import com.kyovo.cents.domain.port.input.CreateRecurringExpenseUseCase
import com.kyovo.cents.domain.port.output.AccountRepository
import com.kyovo.cents.domain.port.output.RecurringExpenseIdGenerator
import com.kyovo.cents.domain.port.output.RecurringExpenseRepository
import com.kyovo.cents.domain.port.output.SubcategoryRepository

class CreateRecurringExpenseService(
    private val recurringExpenseRepository: RecurringExpenseRepository,
    private val accountRepository: AccountRepository,
    private val subcategoryRepository: SubcategoryRepository,
    private val idGenerator: RecurringExpenseIdGenerator
) : CreateRecurringExpenseUseCase
{
    override suspend fun create(command: CreateRecurringExpenseCommand): RecurringExpense
    {
        accountRepository.findById(command.accountId) ?: throw AccountNotFoundException()

        val subcategory = command.subcategoryId?.let {
            subcategoryRepository.findById(it) ?: throw SubcategoryNotFoundException()
        }
        if (subcategory != null && subcategory.kind != RecordableTransactionCategory.EXPENSE)
        {
            throw InvalidTransactionSubcategoryException()
        }

        val recurringExpense = command.toRecurringExpense(idGenerator.generate())
        recurringExpenseRepository.save(recurringExpense)
        return recurringExpense
    }
}
