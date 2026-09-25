package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.exception.AccountNotFoundException
import com.kyovo.cents.domain.exception.CannotRecordTransactionOnArchivedAccountException
import com.kyovo.cents.domain.exception.CannotUpdateInitialDepositException
import com.kyovo.cents.domain.exception.CannotUpdateTransferException
import com.kyovo.cents.domain.exception.SubcategoryNotFoundException
import com.kyovo.cents.domain.exception.TransactionNotFoundException
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionCategory
import com.kyovo.cents.domain.port.input.UpdateTransactionCommand
import com.kyovo.cents.domain.port.input.UpdateTransactionUseCase
import com.kyovo.cents.domain.port.output.AccountRepository
import com.kyovo.cents.domain.port.output.SubcategoryRepository
import com.kyovo.cents.domain.port.output.TransactionRepository

class UpdateTransactionService(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val subcategoryRepository: SubcategoryRepository
) : UpdateTransactionUseCase
{
    override fun update(command: UpdateTransactionCommand): Transaction
    {
        val existing =
            transactionRepository.findById(command.id) ?: throw TransactionNotFoundException()
        if (existing.category == TransactionCategory.INITIAL_DEPOSIT)
        {
            throw CannotUpdateInitialDepositException()
        }
        if (existing.category == TransactionCategory.TRANSFER_OUT ||
            existing.category == TransactionCategory.TRANSFER_IN
        )
        {
            throw CannotUpdateTransferException()
        }

        // Only a move needs the destination to be checked: a transaction that stays where it is may
        // stay on an archived account (updating one there was always allowed).
        if (command.accountId != existing.accountId)
        {
            val destination = accountRepository.findById(command.accountId)
                ?: throw AccountNotFoundException()
            if (destination.archivedAt != null)
            {
                throw CannotRecordTransactionOnArchivedAccountException()
            }
        }

        val subcategory = command.subcategoryId?.let {
            subcategoryRepository.findById(it) ?: throw SubcategoryNotFoundException()
        }

        val updated = command.toTransaction(existing, subcategory)
        transactionRepository.save(updated)
        return updated
    }
}
