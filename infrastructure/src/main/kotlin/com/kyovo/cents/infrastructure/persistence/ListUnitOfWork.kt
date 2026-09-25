package com.kyovo.cents.infrastructure.persistence

import com.kyovo.cents.domain.port.output.UnitOfWork

class ListUnitOfWork(
    private val accountRepository: ListAccountRepository,
    private val transactionRepository: ListTransactionRepository,
    private val subcategoryRepository: ListSubcategoryRepository
) : UnitOfWork
{
    override suspend fun <T> execute(block: suspend () -> T): T
    {
        val accountsSnapshot = accountRepository.snapshot()
        val transactionsSnapshot = transactionRepository.snapshot()
        val subcategoriesSnapshot = subcategoryRepository.snapshot()
        return try
        {
            block()
        } catch (e: Exception)
        {
            accountRepository.restore(accountsSnapshot)
            transactionRepository.restore(transactionsSnapshot)
            subcategoryRepository.restore(subcategoriesSnapshot)
            throw e
        }
    }
}
