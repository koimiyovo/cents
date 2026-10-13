package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.port.output.AccountRepository
import com.kyovo.cents.domain.port.output.MainCurrencyRepository
import com.kyovo.cents.domain.port.output.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The transactions as the totals see them (budgets, spending breakdown and trend, projects): only those of
 * accounts held in the main currency, since without exchange rates an amount in dollars cannot be added to
 * one in euros. Everything else — saving, finding, deleting, `findAll` — is the real repository's.
 *
 * Until the user has chosen a main currency it is the euro. A transaction whose account is unknown is left out.
 * With exchange rates, converting here instead of filtering is the one change this takes.
 */
class MainCurrencyTransactionRepository(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val mainCurrencyRepository: MainCurrencyRepository,
) : TransactionRepository by transactionRepository
{
    override fun observeAll(): Flow<List<Transaction>>
    {
        return combine(
            transactionRepository.observeAll(),
            accountRepository.observeAll(),
            mainCurrencyRepository.observe().map { it ?: AccountCurrency.EUR },
        ) { transactions, accounts, mainCurrency ->
            val inMainCurrency = accounts.filter { it.currency == mainCurrency }.map { it.id }.toSet()
            transactions.filter { it.accountId in inMainCurrency }
        }.distinctUntilChanged()
    }
}
