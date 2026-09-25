package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.model.AccountBalance
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.port.input.GetAccountBalanceUseCase
import com.kyovo.cents.domain.port.output.AccountRepository
import com.kyovo.cents.domain.port.output.TransactionRepository

class GetAccountBalanceService(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository
) : GetAccountBalanceUseCase
{
    override fun getBalance(accountId: AccountId): AccountBalance?
    {
        accountRepository.findById(accountId) ?: return null
        
        val total = transactionRepository.findAll()
            .filter { it.accountId == accountId }
            .sumOf { it.signedAmount }
        return AccountBalance(total)
    }
}