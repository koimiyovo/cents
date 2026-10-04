package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.exception.AccountNotFoundException
import com.kyovo.cents.domain.exception.InvalidTransactionSubcategoryException
import com.kyovo.cents.domain.exception.SubcategoryNotFoundException
import com.kyovo.cents.domain.model.RecurringTransaction
import com.kyovo.cents.domain.port.input.CreateRecurringTransactionCommand
import com.kyovo.cents.domain.port.input.CreateRecurringTransactionUseCase
import com.kyovo.cents.domain.port.output.AccountRepository
import com.kyovo.cents.domain.port.output.RecurringTransactionIdGenerator
import com.kyovo.cents.domain.port.output.RecurringTransactionRepository
import com.kyovo.cents.domain.port.output.SubcategoryRepository

class CreateRecurringTransactionService(
    private val recurringTransactionRepository: RecurringTransactionRepository,
    private val accountRepository: AccountRepository,
    private val subcategoryRepository: SubcategoryRepository,
    private val idGenerator: RecurringTransactionIdGenerator
) : CreateRecurringTransactionUseCase
{
    override suspend fun create(command: CreateRecurringTransactionCommand): RecurringTransaction
    {
        accountRepository.findById(command.accountId) ?: throw AccountNotFoundException()

        val subcategory = command.subcategoryId?.let {
            subcategoryRepository.findById(it) ?: throw SubcategoryNotFoundException()
        }
        // A subcategory of the other kind would contradict the rule (an income filed under "Loyer").
        if (subcategory != null && subcategory.kind != command.category)
        {
            throw InvalidTransactionSubcategoryException()
        }

        val recurringTransaction = command.toRecurringTransaction(idGenerator.generate())
        recurringTransactionRepository.save(recurringTransaction)
        return recurringTransaction
    }
}
