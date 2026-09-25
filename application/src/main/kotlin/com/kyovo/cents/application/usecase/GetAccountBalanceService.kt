package com.kyovo.cents.application.usecase

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
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
    override fun observe(accountId: AccountId): Flow<AccountBalance?>
    {
        return combine(accountRepository.observeAll(), transactionRepository.observeAll())
        { accounts, transactions ->
            if (accounts.none { it.id == accountId }) null
            else AccountBalance(transactions.filter { it.accountId == accountId }.sumOf { it.signedAmount })
        }.distinctUntilChanged()
    }

    override fun observeAll(): Flow<Map<AccountId, AccountBalance>>
    {
        return combine(accountRepository.observeAll(), transactionRepository.observeAll())
        { accounts, transactions ->
            val totals = transactions.groupBy { it.accountId }.mapValues { (_, list) -> list.sumOf { it.signedAmount } }
            accounts.associate { it.id to AccountBalance(totals[it.id] ?: 0L) }
        }.distinctUntilChanged()
    }
}
